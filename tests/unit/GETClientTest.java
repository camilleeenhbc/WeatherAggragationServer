import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import java.util.Map;
import java.io.*;
import java.net.Socket;

/**
 * Unit Test for GETClient
 */
public class GETClientTest {

	private Socket mockSocket;
	private ByteArrayOutputStream mockOutputStream;
	private GETClient client;

	@Before
	public void setUp() throws Exception {
		// Mock sockets and its output, input streams
		mockSocket = mock(Socket.class);
		mockOutputStream = new ByteArrayOutputStream();
		when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);

		// Initialise ContentServer with the mocked socket
		client = new GETClient(mockSocket, null);

	}

	/**
	 * Test that the getAddress method correctly parses valid URLs
	 * and returns the correct hostname and port.
	 */
	@Test
	public void testGetAddress() {
		// Test a full URL with "http://"
		Map<String, Object> address = GETClient.getAddress("http://localhost:8080");
		assertEquals("localhost", address.get("hostname"));
		assertEquals(8080, address.get("port"));
		assertNull(address.get("stationId"));

		// Test a URL without the scheme
		address = GETClient.getAddress("localhost:8080");
		assertEquals("localhost", address.get("hostname"));
		assertEquals(8080, address.get("port"));
		assertNull(address.get("stationId"));
	}

	/**
	 * Test that the getAddress method correctly parses valid URLs
	 * and returns the correct hostname and port.
	 */
	@Test
	public void testGetAddressWithStationId() {
		Map<String, Object> address = GETClient.getAddress("http://localhost:8080?id=aaaaa");
		assertEquals("localhost", address.get("hostname"));
		assertEquals(8080, address.get("port"));
		assertEquals("aaaaa", address.get("stationId"));
	}

	/**
	 * Test getAddress() with an invalid URL should throw IllegalArgumentException.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testGetAddressInvalid() {
		ContentServer.getAddress("invalid_url");
	}

	/**
	 * Test that the GETClient correctly sends a formatted GET request
	 */
	@Test
	public void testGetRequestFormat() throws Exception {
		// Mock data for server response
		when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(
				("HTTP/1.1 200 OK\nLamport-Clock: 3\n\nWeather data: Sunny\n").getBytes()));
		when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);

		// ensure initial lamport value is set to 0
		assertEquals(0, client.lamport.getTimestamp());

		// Send GET request
		client.sendGetRequest();
		verify(mockSocket, times(1)).getOutputStream();

		// check if GET request if properly formatted
		String request = mockOutputStream.toString();
		assertTrue(request.contains("GET /weather.json"));
		assertTrue(request.contains("Host: "));
		assertTrue(request.contains("User-Agent: ATOMClient/1/0"));
		assertTrue(request.contains("Accept: */*"));
		assertTrue(request.contains("Lamport-Clock: 1"));
	}

	/**
	 * Test that the processResponse method correctly processes the server's
	 * response and updates the Lamport clock
	 */
	@Test
	public void testProcessResponseSuccess() throws Exception {
		// mock data for server success response
		when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(
				("HTTP/1.1 200 OK\nLamport-Clock: 3\n\nWeather data: Sunny\n").getBytes()));

		// ensure initial lamport value is set to 0
		assertEquals(0, client.lamport.getTimestamp());

		ByteArrayOutputStream outContent = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outContent));

		client.processResponse(mockSocket);

		verify(mockSocket, times(1)).getInputStream();

		String printedOutput = outContent.toString();
		assertTrue(printedOutput.contains("Server response:"));
		assertTrue(printedOutput.contains("HTTP/1.1 200 OK"));
		assertTrue(printedOutput.contains("Lamport-Clock: 3"));
		assertTrue(printedOutput.contains("Weather data: Sunny"));

		// ensure lamport value is properly updated when receiving message
		assertEquals(4, client.lamport.getTimestamp());
	}

	/**
	 * Test that the processResponse method correctly processes the server's
	 * response and updates the Lamport clock
	 */
	@Test
	public void testProcessResponseFailure() throws Exception {
		// Mock data for server internal error response
		when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(
				("HTTP/1.1 500 Internal Server Error\n").getBytes()));

		// Ensure initial lamport value is set to 0
		assertEquals(0, client.lamport.getTimestamp());

		ByteArrayOutputStream outContent = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outContent));

		client.processResponse(mockSocket);
		verify(mockSocket, times(1)).getInputStream();

		String printedOutput = outContent.toString();
		assertTrue(printedOutput.contains("Server response:"));
		assertTrue(printedOutput.contains("HTTP/1.1 500 Internal Server Error"));

		assertEquals(0, client.lamport.getTimestamp()); // lamport is not updated as request failed

	}

	/**
	 * Test that sendGetRequest retries 3 times upon failure,
	 * as expected when the response indicates a failure (server unavailable).
	 */
	@Test
	public void testSendPutRequestWithRetriesUsingMain() throws Exception {
		ByteArrayOutputStream outContent = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		System.setOut(new PrintStream(outContent));
		String[] args = { "http://localhost:8080" };

		// Run the main method to trigger the PUT request with retries (when server
		// unavailable)
		GETClient.main(args);
		String output = outContent.toString();

		// Check for the retry messages
		assertTrue(output.contains("Retrying... (2 attempts left)"));
		assertTrue(output.contains("Could not connect to server. Retrying..."));
		assertTrue(output.contains("Retrying... (1 attempts left)"));
		assertTrue(output.contains("Failed to connect after multiple attempts. Exiting."));
		System.setOut(originalOut);
	}
}
