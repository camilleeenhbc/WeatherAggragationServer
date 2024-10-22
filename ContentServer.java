import java.util.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.ConnectException;

public class ContentServer {
	protected static LamportClock lamport;
	protected Socket contentSocket;

	// constructor
	public ContentServer(Socket socket) {
		this.contentSocket = socket;
		lamport = new LamportClock();
	}

	/**
	 * Parses a URL to extract the hostname and port.
	 * 
	 * @param url URL string to parse, e.g., "http://example.com:8080" or
	 *            "example.com:8080"
	 * @return a Map containing "hostname" and "port" extracted from the URL
	 * @throws IllegalArgumentException if the URL is improperly formatted or if the
	 *                                  port cannot be parsed
	 */
	public static Map<String, Object> getAddress(String url) {
		Map<String, Object> addressMap = new HashMap<>();
		try {
			if (url.contains("://")) {
				url = url.split("://")[1];
			}
			String hostname = url.split(":")[0];
			if (hostname.contains(".")) {
				hostname = hostname.split("\\.")[0];
			}
			int port = Integer.parseInt(url.split(":")[1]);

			addressMap.put("hostname", hostname);
			addressMap.put("port", port);

		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to get servername and port from URL: " + url, e);
		}

		return addressMap; // return hostname and port
	}

	/**
	 * Reads a file and converts it into a json formatted string.
	 * 
	 * @param filepath path of file to read
	 * @return JSON-formatted string of the file contents
	 */
	public static String convertToJSON(String filepath) {
		// read file into LinkedHashMap
		Map<String, String> jsonMap = new LinkedHashMap<>();
		try {
			File myObj = new File(filepath);
			Scanner myReader = new Scanner(myObj);

			while (myReader.hasNextLine()) {
				String line = myReader.nextLine();
				String[] parts = line.split(":");
				if (parts.length == 2) {
					String key = parts[0].trim();
					String value = parts[1].trim();
					jsonMap.put(key, value);
				}
			}
			// return "" if file is empty/invalid form
			if (jsonMap.isEmpty()) {
				return "";
			}
			myReader.close();

		} catch (FileNotFoundException e) {
			System.err.println("File not Found: " + e.getMessage());
			return "";
		}

		// Convert to JSON
		try {
			return JsonParser.toJson(jsonMap);
		} catch (Exception e) {
			System.err.println("Failed to parse data to json: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Sends PUT request to server with the provided json string
	 * 
	 * @param jsonString json formatted string to be sent to the server
	 */
	public void sendPutRequest(String jsonString) throws IOException {

		PrintWriter out = new PrintWriter(contentSocket.getOutputStream(), true);
		lamport.increment();
		// Format PUT request
		String request = "PUT /weather.json HTTP/1.1\r\n" +
				"User-Agent: ATOMClient/1/0\r\n" +
				"Content-Type: application/json\r\n" +
				"Content-Length: " + jsonString.length() + "\r\n" +
				"Lamport-Clock: " + lamport.getTimestamp() + "\r\n\r\n" +
				jsonString;

		System.out.println(request);
		System.out.println("\n");
		out.println(request);

		// Process server response
		processResponse(contentSocket);
		out.flush();
		out.close();
	}

	/**
	 * Reads and processes the server's response, updating the Lamport clock if
	 * necessary.
	 * 
	 * @param server the server socket from which to read the response
	 */
	public void processResponse(Socket server) {
		try {
			InputStream inputStream = server.getInputStream();
			if (inputStream == null) {
				throw new IOException("Input stream is null");
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

			String responseLine;

			while ((responseLine = in.readLine()) != null) {
				System.out.println(responseLine);
				if (responseLine.isEmpty()) {
					break;
				}
				// update lamport
				if (responseLine.startsWith("Lamport-Clock:")) {
					int receivedLamportValue = Integer.parseInt(responseLine.split(":")[1].trim());
					lamport.sync(receivedLamportValue);
				}
			}

			StringBuilder responseBody = new StringBuilder();
			while ((responseLine = in.readLine()) != null) {
				responseBody.append(responseLine).append("\n");
			}

			System.out.println(responseBody);

			in.close(); // Close input stream after reading the response
		} catch (IOException e) {

			System.err.println("Failed to process server response: " + e.getMessage());
		}
	}

	/**
	 * Main flow to start the content server and send a PUT request with a JSON
	 * payload
	 * 
	 * @param args command line input
	 */
	public static void main(String[] args) {
		int retries = 3;
		boolean success = false;

		// get server address to connect from command line arrgs
		Map<String, Object> address = getAddress(args[0]);
		String hostname = (String) address.get("hostname");
		int port = (int) address.get("port");

		// retries 3 times if server not available
		while (retries > 0 && !success) {
			try {
				// start content server and send PUT request

				Socket contentSocket = new Socket(hostname, port);
				ContentServer content = new ContentServer(contentSocket);

				String filepath = args[1];
				String jsonObject = convertToJSON(filepath);
				content.sendPutRequest(jsonObject);
				success = true; // set success if no exception are thrown

			} catch (IOException i) {
				System.out.println("Could not connect to server. Retrying...");
				retries--;
				if (retries > 0) {
					System.out.println("Retrying... (" + retries + " attempts left)");
					try {
						Thread.sleep(1000); // sleep before retry
					} catch (InterruptedException t) {
						t.printStackTrace();
					}
				} else {
					System.out.println("Failed to connect after multiple attempts. Exiting."); // failed afer 3 retries
				}
			} catch (Exception e) {
				System.err.println("Exception: An error occurred. Exiting." + e.getMessage());
				break;
			}
		}
	}
}