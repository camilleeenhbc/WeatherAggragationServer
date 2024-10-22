import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.HashMap;

public class GETClient {
	protected LamportClock lamport;
	protected String stationId;
	protected Socket clientSocket;

	/**
	 * GETClinent constructor
	 * Initialise client socket with input hostname and port
	 * Initialise fresh lamport clock
	 * 
	 * @param hostname
	 * @param port
	 */
	public GETClient(Socket socket, String stationId) throws IOException {
		this.clientSocket = socket;
		this.stationId = stationId;
		this.lamport = new LamportClock();
	}

	/**
	 * Extracts the hostname, port, and optional station ID from the input URL.
	 * 
	 * 
	 * @param url URL string to parse, e.g., "http://example.com:8080" or
	 *            "example.com:8080?stationId=123"
	 * @return Map containing "hostname", "port", and "stationId" extracted from URL
	 * @throws IllegalArgumentException if URL is improperly formatted or if the
	 *                                  port cannot be parsed
	 */
	public static Map<String, Object> getAddress(String url) {
		Map<String, Object> addressMap = new HashMap<>();
		try {
			if (url.contains("://")) {
				url = url.split("://")[1];
			}
			String hostname = url.split(":")[0];
			int port;
			String stationId = null;
			if (hostname.contains(".")) {
				hostname = hostname.split("\\.")[0];
			}
			if (url.split(":")[1].contains("?")) {
				String[] parts = url.split(":")[1].split("\\?");
				port = Integer.parseInt(parts[0]);
				stationId = parts[1].split("=")[1];
			} else {
				port = Integer.parseInt(url.split(":")[1]);
			}
			addressMap.put("hostname", hostname);
			addressMap.put("port", port);
			addressMap.put("stationId", stationId);

		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to get servername and port from URL: " + url, e);
		}

		return addressMap; // return hostname and port
	}

	/**
	 * Build and format GET request
	 * Send GET request to the server and handle the response.
	 */
	public void sendGetRequest() throws IOException {
		PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
		lamport.increment(); // increment lamport clock before sending message

		// Build formatted GET request
		String request = "GET /weather.json";
		if (stationId != null && !stationId.isEmpty()) {
			request += "?id=" + stationId + " HTTP/1.1\r\n";
		} else {
			request += " HTTP/1.1\r\n"; // No stationId
		}
		request += "Host: example.com" + "\r\n" +
				"User-Agent: ATOMClient/1/0\r\n" +
				"Accept: */**\r\n" +
				"Lamport-Clock: " + lamport.getTimestamp() + "\r\n";

		System.out.println(request);
		out.println(request);
		out.flush();

		// Wait and process response from server
		processResponse(clientSocket);

		out.close();

	}

	/**
	 * Reads the server's response, processes it, and updates the Lamport clock if
	 * needed.
	 * 
	 * @param server Server socket object that is connected to
	 */
	public void processResponse(Socket clientSocket) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
			System.out.println("Server response:");

			// Read in and output server response
			String responseLine;
			while ((responseLine = in.readLine()) != null) {
				System.out.println(responseLine);
				if (responseLine.isEmpty()) {
					break;
				}
				if (responseLine.startsWith("Lamport-Clock:")) {
					// Update lamport value
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
			System.err.println("Process response error:" + e.getMessage());
		}
	}

	/**
	 * Main flow to run GETClient.
	 * This takes arguments of address and port to create a GETClient, connect to
	 * server and send formatted GET request to server
	 */
	public static void main(String[] args) {
		int retries = 3;
		boolean success = false;

		// get server address from command line args
		Map<String, Object> address = getAddress(args[0]);
		String hostname = (String) address.get("hostname");
		int port = (int) address.get("port");
		String stationId = (String) address.get("stationId");

		// retries 3 times if server not available
		while (retries > 0 && !success) {
			try {
				// initialise GETClient and send request
				Socket clientSocket = new Socket(hostname, port);
				GETClient client = new GETClient(clientSocket, stationId);
				client.sendGetRequest();
				success = true;

			} catch (IOException i) {
				System.out.println(" Could not connect to server. Retrying...");
				retries--;
				if (retries > 0) {
					try {
						System.out.println("Retrying... (" + retries + " attempts left)");
						Thread.sleep(1000); //  sleep before retries
					} catch (InterruptedException e) {
						System.err.println("Connection refused" + e.getMessage());
					}
				} else {
					System.out.println("Failed to connect after multiple attempts. Exiting."); // failed after 3 attempts
				}
			}
		}
	}
}
