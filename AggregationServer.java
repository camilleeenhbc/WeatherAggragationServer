import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class AggregationServer {
	private final Object clockLock = new Object(); // lock for lamport
	protected LamportClock lamport;
	protected static ConcurrentHashMap<String, WeatherNode> weather = new ConcurrentHashMap<>();

	protected ServerSocket server;
	protected static String DATA_FILE = "backup.txt";
	private static final long EXPIRATION_TIME = 30 * 1000;
	private static volatile boolean isRunning = true; // Ensure proper thread visibility

	/**
	 * Constructs the AggregationServer and initialises the server socket.
	 * 
	 * @param port The port number to listen on.
	 * @throws IOException if an I/O error occurs when opening the socket.
	 */
	public AggregationServer(ServerSocket serverSocket) throws IOException {
		this.server = serverSocket;
		this.lamport = new LamportClock();
		System.out.println("Server started");
		loadBackup();
	}

	/**
	 * Listens for client connections and starts a new thread to handle each
	 * connection.
	 * 
	 * @throws IOException if an I/O error occurs when waiting for a connection.
	 */
	public void listenSocket() {
		while (isRunning && !Thread.currentThread().isInterrupted()) {
			try {
				Socket clientSocket = server.accept(); // Accept client connections
				new Thread(new ClientHandler(clientSocket, this)).start(); // Pass LamportClock to ClientHandler
			} catch (SocketException e) {
				if (!isRunning) {
					System.out.println("Server stopped.");
					break;
				} else {
					System.err.println("Listening socket exception" + e);

				}
			} catch (IOException e) {
				System.err.println("Connection refused: " + e.getMessage());

			}
		}
	}

	/**
	 * Stops the server and closes the server socket.
	 * 
	 * @throws IOException if an I/O error occurs when closing the socket.
	 */
	public void stop() throws IOException {
		isRunning = false;
		if (server != null && !server.isClosed()) {
			server.close();
		}
		System.out.println("Server has been stopped.");
	}

	/**
	 * Loads weather data from backup file into the weather map.
	 * Each entry in the file corresponds to a weather station's data,
	 * formatted to allow reconstruction of `WeatherNode` objects.
	 */
	protected static void loadBackup() {
		try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
			StringBuilder weatherJson = new StringBuilder();
			String stationId = null;
			String line;
			boolean inEntry = false;

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				if (line.equals("BEGIN_ENTRY")) {
					inEntry = true;
					weatherJson.setLength(0);
				} else if (line.equals("END_ENTRY")) {
					inEntry = false;
					WeatherNode node = WeatherNode.toWeatherNode(weatherJson.toString());
					stationId = JsonParser.getId(node.getData());
					weather.put(stationId, node);
				} else if (inEntry) {
					weatherJson.append(line).append("\n");
				}
			}
			System.out.println("Weather data loaded from backup");
		} catch (Exception e) {
			System.err.println("Failed to load weather data from back up: " + e.getMessage());
		}
	}

	/**
	 * Updates backup file with the current weather data from the weather map.
	 * This will be called after any changes made on weather map stored in
	 * Aggregation Server.
	 */
	protected static void updateBackup() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
			Set<String> keys = weather.keySet();

			for (String key : keys) {
				String weatherData = weather.get(key).toFileFormat();
				if (weatherData != null) {
					writer.write(weatherData);
				}
			}

			System.out.println("Weather data saved to backup.");
		} catch (IOException e) {
			System.err.println("Failed to save weather data to back up: ");
		}
	}

	/**
	 * Manages content servers by checking for outdated weather data.
	 * Weather data entries expire if they haven't been updated for 30 seconds.
	 */
	public static void manageContentServers() {
		while (true) {
			try {
				Thread.sleep(1000);
				long currentTime = System.currentTimeMillis();
				for (String id : weather.keySet()) {
					long lastUpdate = weather.get(id).getLastUpdate();
					// check if data expired (30 seconds)
					if (currentTime - lastUpdate > EXPIRATION_TIME) {
						weather.remove(id);
						updateBackup();
						System.out.println("Removed outdated weather data: " + id);
					}
				}
			} catch (InterruptedException e) {
				System.err.println("Manage Content Servers interrupted: " + e.getMessage());
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Manages outdated data by removing entries based on their Lamport order.
	 * If the number of entries exceeds 20, it removes the oldest entry based on
	 * the smallest Lamport clock value.
	 */
	public static void manageOutdatedData() {
		while (true) {
			try {
				Thread.sleep(1000);

				// only stores 20 data in aggregation server
				if (weather.size() > 20) {
					String outdatedData = null;
					int smallestLamportValue = Integer.MAX_VALUE;

					for (String id : weather.keySet()) {
						// remove oldest data, based on its lamport value
						int lamportValue = weather.get(id).getLamport();
						if (lamportValue < smallestLamportValue) {
							smallestLamportValue = lamportValue;
							outdatedData = id;
						}
					}

					if (outdatedData != null) {
						weather.remove(outdatedData);
						updateBackup();
						System.out.println("Removed oldest weather data due to size limit: " + outdatedData);
					}
				}
			} catch (InterruptedException e) {
				System.err.println("Manage outdated data interrupted: " + e.getMessage());
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * ClientHandler class to handle individual client connections.
	 */
	protected class ClientHandler implements Runnable {
		protected Socket clientSocket;
		protected BufferedReader in;
		protected PrintWriter out;
		protected AggregationServer server;

		/**
		 * Constructs a ClientHandler for handling client connections.
		 * 
		 * @param clientSocket The socket for the client connection.
		 * @throws IOException if an I/O error occurs when getting the I/O stream.
		 */
		public ClientHandler(Socket clientSocket, AggregationServer server) throws IOException {
			this.server = server;
			this.clientSocket = clientSocket;
			this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			this.out = new PrintWriter(clientSocket.getOutputStream(), true);
		}

		@Override
		public void run() {
			try {
				String requestLine = in.readLine();
				if (requestLine == null || requestLine.trim().isEmpty()) {
					sendStatusCode(400); // bad request
					this.clientSocket.close();
					return;
				}

				String method = requestLine.split(" ")[0].trim();

				// PUT
				if (method.equalsIgnoreCase("PUT")) {
					handlePutRequest();
				}
				// GET
				else if (method.equalsIgnoreCase("GET")) {
					handleGetRequest(requestLine);
				} else {
					// send 400 for invalid/unsupported method
					System.out.println("Invalid method");
					sendStatusCode(400);
				}
			} catch (IOException e) {
				System.err.println("I/O Exception handles client request: " + e.getMessage());
			} finally {
				try {
					this.clientSocket.close();
				} catch (IOException e) {
					System.err.println("Failed to close client socket" + e.getMessage());
				}
			}
		}

		/**
		 * Sends the appropriate HTTP status code based on the provided code.
		 * 
		 * @param code The HTTP status code to send.
		 */
		public void sendStatusCode(int code) {
			switch (code) {
				case 200:
					out.println("HTTP/1.1 200 OK");
					break;
				case 201:
					out.println("HTTP/1.1 201 Created");
					break;
				case 204:
					out.println("HTTP/1.1 204 No Content");
					break;
				case 400:
					out.println("HTTP/1.1 400 Bad Request");
					break;
				case 404:
					out.println("HTTP/1.1 404 Not Found");
					break;
				case 500:
					out.println("HTTP/1.1 500 Internal Server Error");
					break;
				default:
					break;
			}
			out.flush();
		}

		/**
		 * Handles a GET request from the client.
		 * 
		 * This will process a GET request from clients, update lamport value and sends
		 * corresponding response with appropriate HTTP status code.
		 * - 200 OK: data retrieved successfully
		 * - 404 Not Found: data the requested station ID does not exist
		 * - 500 Internal Server Error: if the Lamport clock header is missing or an
		 * error occurs
		 * 
		 * @param requestLine request line from the client containing the GET
		 *                    request details.
		 * @throws IOException if an I/O error occurs while reading the request.
		 */
		public synchronized void handleGetRequest(String requestLine) throws IOException {
			System.out.println("Handling GET Request");
			try {
				StringBuilder data = new StringBuilder();
				String stationId = null;
				int receivedLamportValue = 0;
				// get stationId if given
				if (requestLine.startsWith("GET /weather.json")) {
					String[] parts = requestLine.split("\\?id=");
					if (parts.length > 1) {
						stationId = parts[1].split(" ")[0].trim();
					}
				}

				String headerLine;
				while (!(headerLine = in.readLine()).isEmpty()) {
					if (headerLine.startsWith("Lamport-Clock:")) {
						receivedLamportValue = Integer.parseInt(headerLine.split(":")[1].trim());
						// lock lamport to ensure mutual exclusion
						synchronized (server.clockLock) {
							server.lamport.sync(receivedLamportValue);
						}
					}
				}
				// send 500 if lamport clock not provided
				if (receivedLamportValue == 0) {
					sendStatusCode(500);
					return;
				}

				if (stationId != null) {
					// retrieve data with given stationId
					if (!weather.isEmpty()) {
						WeatherNode node = weather.get(stationId);
						if (node == null) {
							sendStatusCode(404); // return 404 if data not found
							return;
						}
						data.append(node.getData());
					} else {
						sendStatusCode(404);
						return;
					}
				} else if (!weather.isEmpty()) {
					// retrieve all data if stationId not specified
					data.append("[\n");
					int dataCount = 0;
					for (WeatherNode node : weather.values()) {
						if (dataCount > 0) {
							data.append(",\n");
						}
						data.append(node.getDataFormatted());
						dataCount++;
					}
					data.append("\n]\n");
				} else {
					// return empty array if weather map is empty
					data.append("[]\n");
				}

				System.out.println(data);

				// lock lamport to ensure mutual exclusion
				synchronized (server.clockLock) {
					server.lamport.increment(); // increment lamport
					// send the response
					sendStatusCode(200);
					out.println("Content-Type: application/json");
					out.println("Content-Length: " + data.length());
					out.println("Lamport-Clock: " + server.lamport.getTimestamp());
					out.println();
					out.println(data);
				}
			} catch (Exception e) {
				sendStatusCode(500);
			}
		}

		/**
		 * Handles a PUT request from the client.
		 * 
		 * This method reads PUT request from client, updates lamport and sends response
		 * back to client. This will respond with appropriate HTTP status codes:
		 * - 204 No Content: if the request has no content
		 * - 500 Internal Server Error: if Lamport clock is missing or error occurs
		 * - 201 Created: if the entry is new and successfully created
		 * - 200 OK: if the entry is updated successfully
		 * 
		 * @throws IOException if an I/O error occurs while reading the request.
		 */
		public synchronized void handlePutRequest() throws IOException {
			int contentLength = 0;
			int receivedLamportValue = 0;
			String headerLine;

			while (!(headerLine = in.readLine()).isEmpty()) {
				if (headerLine.startsWith("Content-Length:")) {
					contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
				}
				if (headerLine.startsWith("Lamport-Clock:")) {
					receivedLamportValue = Integer.parseInt(headerLine.split(":")[1].trim());
					synchronized (server.clockLock) {
						server.lamport.sync(receivedLamportValue);
					}
				}
			}

			// return 204 if no content is provided
			if (contentLength == 0) {
				synchronized (server.clockLock) {
					server.lamport.increment();
					sendStatusCode(204);
					out.println("Lamport-Clock: " + server.lamport.getTimestamp());
				}
				return;
			}

			// return 500 if lamport is missing
			if (receivedLamportValue == 0) {
				sendStatusCode(500);
				return;
			}

			// read the request body (jsonData), based on content-length
			char[] bodyChars = new char[contentLength];
			in.read(bodyChars, 0, contentLength);
			String jsonData = new String(bodyChars);
			try {
				String jsonId = JsonParser.getId(jsonData);
				boolean isNewEntry = !weather.containsKey(jsonId);
				weather.put(jsonId,
						new WeatherNode(jsonData, server.lamport.getTimestamp(), System.currentTimeMillis()));
				updateBackup();

				// lock lamport to ensure mutual exclusion
				synchronized (server.clockLock) {
					server.lamport.increment(); // increment lamport before sending message
					if (isNewEntry) {
						sendStatusCode(201); // return 201 for first-time connection
					} else {
						sendStatusCode(200); // return 200 for request success
					}
					out.println("Lamport-Clock: " + server.lamport.getTimestamp());
				}

			} catch (Exception e) {
				sendStatusCode(500);

			}
		}
	}

	/**
	 * The main method to start the AggregationServer.
	 * Initialises server socket and spawns 3 threads for managing outdated data,
	 * content servers, and handling client connections.
	 * 
	 * @param args Command line arguments for the server. The first argument
	 *             specifies the port.
	 */
	public static void main(String[] args) {
		try {
			int port = (args.length > 0) ? Integer.parseInt(args[0]) : 4567;
			ServerSocket serverSocket = new ServerSocket(port);

			// Start aggregation server socket
			AggregationServer server = new AggregationServer(serverSocket);

			// Manage outdated data
			Thread outdatedDataThread = new Thread(() -> {
				manageOutdatedData();
			});

			// Manage outdated data
			Thread contentThread = new Thread(() -> {
				manageContentServers();
			});

			// Listen to client sockets
			Thread clientConnectionThread = new Thread(() -> {
				server.listenSocket();
			});

			// start multi-threading to handle multiple jobs at the same time
			outdatedDataThread.start();
			contentThread.start();
			clientConnectionThread.start();

		} catch (IOException i) {
			System.err.println("Server main thread error: " + i.getMessage());
		}
	}
}
