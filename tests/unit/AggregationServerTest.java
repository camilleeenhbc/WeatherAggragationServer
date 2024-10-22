import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Unit tests for AggregationServer class
 */
public class AggregationServerTest {

	private ServerSocket mockServerSocket;
	private Socket mockSocket;
	private BufferedReader mockReader;
	private PrintWriter mockWriter;
	private WeatherNode mockWeatherNode;
	private AggregationServer server;
	private LamportClock mockLamportClock;

	@Before
	public void setUp() throws Exception {
		// Mock ServerSocket and Socket
		mockServerSocket = mock(ServerSocket.class);
		mockSocket = mock(Socket.class);

		// Mock BufferedReader and PrintWriter
		mockReader = mock(BufferedReader.class);
		mockWriter = mock(PrintWriter.class);

		InputStream mockInputStream = new ByteArrayInputStream("".getBytes());
		when(mockSocket.getInputStream()).thenReturn(mockInputStream);
		when(mockSocket.getOutputStream()).thenReturn(mock(OutputStream.class));

		// Set up a mock WeatherNode and LamportClock
		mockWeatherNode = mock(WeatherNode.class);
		mockLamportClock = mock(LamportClock.class);

		server = new AggregationServer(mockServerSocket); // Create server with mocked ServerSocket
		server.lamport = mockLamportClock; // Inject mocked LamportClock
		AggregationServer.weather = new ConcurrentHashMap<>(); // Reset the weather map
		AggregationServer.DATA_FILE = "tests/mock_data/test_backup.txt"; // Set data file path
	}

	/**
	 * Test for loading backup data from file
	 */
	@Test
	public void testLoadBackup() throws Exception {
		AggregationServer.DATA_FILE = "tests/mock_data/test_load_backup.txt"; // use mock backup file
		AggregationServer.loadBackup();
		assertEquals(3, AggregationServer.weather.size());
	}

	/**
	 * Test for loading backup data from file - corrupted data
	 * Expected output: no data is loaded to weather map
	 */
	@Test
	public void testLoadBackup_CorruptedData() throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter("tests/mock_data/test_load_backup_corrupted.txt"));
		writer.write("BEGIN_ENTRY\ncorrupted data\nEND_ENTRY");
		writer.close();

		AggregationServer.DATA_FILE = "tests/mock_data/test_load_backup_corrupted.txt";
		AggregationServer.loadBackup();

		assertEquals(0, AggregationServer.weather.size()); // no entries is loaded
	}

	/**
	 * Test for saving weather data to the backup file
	 */
	@Test
	public void testUpdateBackup() throws Exception {
		AggregationServer.DATA_FILE = "tests/mock_data/test_backup_update.txt";

		// Add a mock weather node for testing purposes
		WeatherNode mockNode = new WeatherNode("{\"id\":\"testId\",\"data\":\"testData\"}", 5, 1620000000000L);
		AggregationServer.weather.put("testId", mockNode);

		AggregationServer.updateBackup();

		// Open the file and verify that the weather data was correctly written
		BufferedReader reader = new BufferedReader(new FileReader(AggregationServer.DATA_FILE));
		StringBuilder fileContent = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			fileContent.append(line).append("\n");
		}
		reader.close();

		// Ensure that the content contains the expected weather data
		assertTrue(fileContent.toString().contains("\"id\":\"testId\""));
		assertTrue(fileContent.toString().contains("\"data\":\"testData\""));
		assertTrue(fileContent.toString().contains("lamport = 5;"));
		assertTrue(fileContent.toString().contains("last_update = 1620000000000;"));

		File testBackupFile = new File(AggregationServer.DATA_FILE);
		assertTrue(testBackupFile.delete()); // clean up after test
	}

	/**
	 * Test for managing outdated data based on expiration time
	 */
	@Test
	public void testManageContentServers() throws InterruptedException {
		// mock weather node
		WeatherNode oldNode = mock(WeatherNode.class);
		when(oldNode.getLastUpdate()).thenReturn(System.currentTimeMillis() - 40 * 1000); // mock last update time
		AggregationServer.weather.put("expired", oldNode);

		Thread contentThread = new Thread(AggregationServer::manageContentServers); // start thread
		contentThread.start();
		Thread.sleep(2000);

		// check if the expired weather data node has been removed
		assertNull(AggregationServer.weather.get("expired"));
		contentThread.interrupt();
		contentThread.join();
	}

	/**
	 * Test managing outdated data when the size limit exceeds 20 entries
	 */
	@Test
	public void testManageOutdatedData() throws InterruptedException {
		// add 25 weather nodes to the weather map
		for (int i = 0; i < 25; i++) {
			WeatherNode mockNode = mock(WeatherNode.class);
			when(mockNode.getLamport()).thenReturn(i); // mocks different Lamport values
			AggregationServer.weather.put(String.valueOf(i), mockNode);
		}

		// Start managing outdated data
		Thread outdatedDataThread = new Thread(AggregationServer::manageOutdatedData);
		outdatedDataThread.start();
		Thread.sleep(6000); // wait for thread

		// assert maximum 20 entries are kept
		assertEquals(20, AggregationServer.weather.size());
		outdatedDataThread.interrupt();
		outdatedDataThread.join();
	}

	/**
	 * Test for starting the server and listening to client connections
	 */
	@Test
	public void testListenSocket() throws Exception {
		when(mockServerSocket.accept()).thenReturn(mockSocket);
		Thread connectionThread = new Thread(() -> {
			server.listenSocket();
		});
		connectionThread.start();

		// Verify that the server "accepts" a connection
		verify(mockServerSocket, timeout(1000).atLeastOnce()).accept();
		verify(mockSocket, atLeast(1)).getInputStream();
		verify(mockSocket, atLeast(1)).getOutputStream();

		connectionThread.interrupt();
		connectionThread.join();
	}

	/**
	 * Test for handling invalid HTTP methods
	 */
	@Test
	public void testHandleInvalidMethod() throws Exception {
		// Mock socket input for an invalid HTTP method
		when(mockReader.readLine()).thenReturn("POST /weather.json", "");

		AggregationServer.ClientHandler handler = server.new ClientHandler(mockSocket, server);
		handler.in = mockReader;
		handler.out = mockWriter;

		handler.run();
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 400 Bad Request"));

	}

	/**
	 * Test for handling GET request
	 */
	@Test
	public void testHandleGetRequest_Success() throws Exception {
		// Mock socket input for GET request
		when(mockReader.readLine()).thenReturn("GET /weather.json", "Lamport-Clock: 5", "");

		AggregationServer.ClientHandler handler = server.new ClientHandler(mockSocket, server);
		handler.in = mockReader;
		handler.out = mockWriter;

		handler.handleGetRequest("GET /weather.json");
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 200 OK"));
		verify(mockWriter, atLeast(1)).println(contains("Lamport-Clock: " + server.lamport.getTimestamp()));

	}

	/**
	 * Test for handling GET request
	 */
	@Test
	public void testHandleGetRequest_500() throws Exception {
		// Mock socket input for GET request
		when(mockReader.readLine()).thenReturn("GET /weather.json", "");

		AggregationServer.ClientHandler handler = server.new ClientHandler(mockSocket, server);
		handler.in = mockReader;
		handler.out = mockWriter;

		handler.handleGetRequest("GET /weather.json");
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 500 Internal Server Error"));
	}

	/**
	 * Test for handling PUT request
	 */
	@Test
	public void testHandlePutRequest_500() throws Exception {
		// Mock socket input for PUT request
		when(mockReader.readLine()).thenReturn("PUT /weather.json", "Content-Length: 30", "Lamport-Clock: 5", "");

		AggregationServer.ClientHandler handler = server.new ClientHandler(mockSocket, server);
		handler.in = mockReader;
		handler.out = mockWriter;

		handler.handlePutRequest();
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 500 Internal Server Error"));
	}

	/**
	 * Test for handling PUT request
	 */
	@Test
	public void testHandlePutRequest_Success() throws Exception {
		// mock socket input for PUT request
		when(mockReader.readLine())
				.thenReturn(
						"PUT /weather.json HTTP/1.1",
						"User-Agent: ATOMClient/1/0",
						"Content-Length: 419",
						"Lamport-Clock: 1",
						"" // End of headers
				);

		// Mock the body being read correctly as a stream of characters
		String requestBody = "{\n" +
				"    \"id\": \"bbbbb\",\n" +
				"    \"name\": \"Adelaide (West Terrace /  ngayirdapira)\",\n" +
				"    \"state\": \"SA\",\n" +
				"    \"time_zone\": \"CST\",\n" +
				"    \"lat\": \"-34.9\",\n" +
				"    \"lon\": \"138.6\",\n" +
				"    \"local_date_time_full\": \"20230715160000\",\n" +
				"    \"air_temp\": \"13.3\",\n" +
				"    \"apparent_t\": \"9.5\",\n" +
				"    \"cloud\": \"Partly cloudy\",\n" +
				"    \"dewpt\": \"5.7\",\n" +
				"    \"press\": \"1023.9\",\n" +
				"    \"rel_hum\": \"60\",\n" +
				"    \"wind_dir\": \"S\",\n" +
				"    \"wind_spd_kmh\": \"15\",\n" +
				"    \"wind_spd_kt\": \"8\"\n" +
				"}";

		// Mock the char[] reading for the body content
		char[] bodyChars = requestBody.toCharArray();
		when(mockReader.read(any(char[].class), eq(0), eq(419)))
				.thenAnswer(invocation -> {
					char[] buffer = invocation.getArgument(0);
					System.arraycopy(bodyChars, 0, buffer, 0, 419);
					return 419;
				});

		AggregationServer.ClientHandler handler = server.new ClientHandler(mockSocket, server);
		handler.in = mockReader;
		handler.out = mockWriter;

		handler.handlePutRequest();
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 201 Created"));
		verify(mockWriter, atLeast(1)).println(contains("Lamport-Clock: " + server.lamport.getTimestamp()));

		// rest mock data streams
		reset(mockWriter);
		reset(mockReader);

		// mock the second PUT request with the same content and headers
		when(mockReader.readLine())
				.thenReturn(
						"PUT /weather.json HTTP/1.1",
						"User-Agent: ATOMClient/1/0",
						"Content-Length: 419",
						"Lamport-Clock: 2",
						"");

		// Mock the body reading again
		when(mockReader.read(any(char[].class), eq(0), eq(419)))
				.thenAnswer(invocation -> {
					char[] buffer = invocation.getArgument(0);
					System.arraycopy(bodyChars, 0, buffer, 0, 419);
					return 419;
				});

		// second PUT request (should return 200 OK)
		handler.handlePutRequest();
		verify(mockWriter, atLeast(1)).println(contains("HTTP/1.1 200 OK"));
		verify(mockWriter, atLeast(1)).println(contains("Lamport-Clock: " + server.lamport.getTimestamp()));
	}
}
