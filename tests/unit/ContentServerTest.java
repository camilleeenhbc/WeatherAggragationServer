import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * Unit Test class for ContentServer
 * Sending invalid id, null data, will be tested in integration testing -
 * BasicIntegration test
 */
public class ContentServerTest {

	private Socket mockSocket;
	private ByteArrayOutputStream mockOutputStream;
	private ContentServer contentServer;

	/**
	 * Setup mock sockets and mock data streams for testing.
	 */
	@Before
	public void setUp() throws Exception {
		// mock socket and its data streams
		mockSocket = mock(Socket.class);
		mockOutputStream = new ByteArrayOutputStream();
		when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);

		// Initialise ContentServer with the mocked socket
		contentServer = new ContentServer(mockSocket);
	}

	/**
	 * Test that the getAddress method correctly parses valid URLs
	 * and returns the correct hostname and port.
	 */
	@Test
	public void testGetAddress() {
		// Test a full URL with "http://"
		Map<String, Object> address = ContentServer.getAddress("http://localhost:8080");
		assertEquals("localhost", address.get("hostname"));
		assertEquals(8080, address.get("port"));

		// Test a URL without the scheme
		address = ContentServer.getAddress("localhost:8080");
		assertEquals("localhost", address.get("hostname"));
		assertEquals(8080, address.get("port"));
	}

	/**
	 * Test getAddress() with an invalid URL should throw IllegalArgumentException.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testGetAddressInvalid() {
		ContentServer.getAddress("invalid_url");
	}

	/**
	 * Test convertToJson() with a valid file that is properly formatted.
	 */
	@Test
	public void testConvertToJsonValidFile() throws IOException {
		// create dummy test file with content
		File testFile = new File("testFile.txt");
		PrintWriter writer = new PrintWriter(testFile);
		writer.println("key1: value1");
		writer.println("key2: value2");
		writer.close();

		// convert test file to JSON format
		String jsonResult = ContentServer.convertToJSON("testFile.txt");

		String expectedJson = "{\n    \"key1\": \"value1\",\n    \"key2\": \"value2\"\n}";
		assertEquals(expectedJson, jsonResult);
		testFile.delete(); // clean up test file
	}

	/**
	 * Test convertToJson() returns empty string when converting an empty file.
	 */
	@Test
	public void testConvertToJsonEmptyFile() throws IOException {
		File emptyFile = new File("emptyFile.txt");
		emptyFile.createNewFile();

		String jsonResult = ContentServer.convertToJSON("emptyFile.txt");

		assertEquals("", jsonResult);

		emptyFile.delete();
	}

	/**
	 * Test that sendPutRequest correctly sends a PUT request
	 * with the correct format, headers, and JSON body.
	 */
	@Test
	public void testSendPutRequestFormat() throws Exception {
		// Mock the InputStream to simulate server response
		InputStream mockInputStream = new ByteArrayInputStream(
				"HTTP/1.1 200 OK\r\nLamport-Clock: 15\r\n\r\n".getBytes());
		when(mockSocket.getInputStream()).thenReturn(mockInputStream);

		String mockJson = "{\"data\":\"sunny\"}";

		// Send the PUT request
		contentServer.sendPutRequest(mockJson);

		// verify PUT request was correctly formatted
		String request = mockOutputStream.toString();
		assertTrue(request.contains("PUT /weather.json HTTP/1.1"));
		assertTrue(request.contains("User-Agent: ATOMClient/1/0"));
		assertTrue(request.contains("Content-Type: application/json"));
		assertTrue(request.contains("Lamport-Clock: 1")); // Initial lamport value is 1
		assertTrue(request.contains(mockJson));
	}

	/**
	 * Test that processResponse correctly updates the Lamport clock
	 * on a successful server response (200 OK).
	 */
	@Test
	public void testProcessResponseSuccess() throws Exception {
		// simulate a successful server response
		InputStream mockInputStream = new ByteArrayInputStream(
				"HTTP/1.1 200 OK\r\nLamport-Clock: 15\r\n\r\n".getBytes());
		when(mockSocket.getInputStream()).thenReturn(mockInputStream);

		// Process the response and verify that the Lamport clock is updated
		contentServer.processResponse(mockSocket);
		assertEquals(16, contentServer.lamport.getTimestamp()); // lamport is sync after process response successfully
	}

	/**
	 * Test that processResponse handles server errors (500)
	 * without updating the Lamport clock.
	 */
	@Test
	public void testProcessResponseFailure() throws Exception {
		// simulate a failed server response
		InputStream mockInputStream = new ByteArrayInputStream(
				"HTTP/1.1 500 Internal Server Error\n".getBytes());
		when(mockSocket.getInputStream()).thenReturn(mockInputStream);

		// Process the response and verify that the Lamport clock remains unchanged
		contentServer.processResponse(mockSocket);
		assertEquals(0, contentServer.lamport.getTimestamp()); // lamport is not updated as request failed
	}

	/**
	 * Test that sendPutRequest correctly processes the server response
	 * and updates the Lamport clock.
	 */
	@Test
	public void testSendPutRequest() throws Exception {
		// mock the InputStream to simulate a successful server response
		InputStream mockInputStream = new ByteArrayInputStream(
				"HTTP/1.1 200 OK\r\nLamport-Clock: 15\r\n\r\n".getBytes());
		when(mockSocket.getInputStream()).thenReturn(mockInputStream);

		String mockJson = "{\"data\":\"sunny\"}";

		// Send the PUT request
		contentServer.sendPutRequest(mockJson);

		// verify PUT request format
		String request = mockOutputStream.toString();
		assertTrue(request.contains("PUT /weather.json HTTP/1.1"));
		assertTrue(request.contains("User-Agent: ATOMClient/1/0"));
		assertTrue(request.contains("Content-Type: application/json"));
		assertTrue(request.contains("Lamport-Clock: 1"));
		assertTrue(request.contains(mockJson));

		assertEquals(ContentServer.lamport.getTimestamp(), 16); // check lamport is updated
	}

	/**
	 * Test that sendPutRequest retries 3 times upon failure,
	 * as expected when the response indicates a failure (server unavailable).
	 */
	@Test
	public void testSendPutRequestWithRetriesUsingMain() throws Exception {
		ByteArrayOutputStream outContent = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		System.setOut(new PrintStream(outContent));
		String[] args = { "http://localhost:8080", "data/testFile.txt" };

		// Run the main method to trigger the PUT request with retries (when server
		// unavailable)
		ContentServer.main(args);
		String output = outContent.toString();

		// Check for the retry messages
		assertTrue(output.contains("Retrying... (2 attempts left)"));
		assertTrue(output.contains("Could not connect to server. Retrying..."));
		assertTrue(output.contains("Retrying... (1 attempts left)"));
		assertTrue(output.contains("Failed to connect after multiple attempts. Exiting."));
		System.setOut(originalOut);
	}
}
