import org.junit.Before;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public class ConcurrencyTest {

	private static String testBackupFile = "backup.txt";
	private AggregationServer server;
	private Thread serverThread;
	private ByteArrayOutputStream getOutputStream1;
	private ByteArrayOutputStream getOutputStream2;
	private PrintStream originalOut;

	@Before
	public void setUp() throws Exception {
		Path path = Paths.get("backup.txt"); // delete test backup file
		if (Files.exists(path)) {
			Files.delete(path);
		}
		Files.createFile(path);

		CountDownLatch latchServer = new CountDownLatch(1);

		// serverThread
		serverThread = new Thread(() -> {
			try {
				AggregationServer.main(new String[] { "9999" });
				AggregationServer.DATA_FILE = "backup.txt"; // refresh backup file
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				latchServer.countDown();
			}
		});

		serverThread.start();
		latchServer.await(); // Wait for server to start

		// Preserve original System.out
		originalOut = System.out;
	}

	@After
	public void tearDown() throws Exception {
		serverThread.interrupt();
		serverThread.join();
		System.setOut(originalOut);
	}

	@AfterClass
	public static void tearDownAll() throws Exception {
		Path path = Paths.get(testBackupFile); // delete test backup file
		if (Files.exists(path)) {
			Files.delete(path);
		}
		Files.createFile(path);
	}

	/**
	 * Test simultaneous GET and PUT requests
	 */
	@Test
	public void testSimultaneousGetAndPut() throws Exception {
		CountDownLatch latch = new CountDownLatch(2); // Latch to wait for requests

		// Start the GET request
		getOutputStream1 = new ByteArrayOutputStream(); // Capture output stream of GET client
		Thread getThread = new Thread(() -> {
			try {
				PrintStream getPrintStream = new PrintStream(getOutputStream1);
				System.setOut(getPrintStream);
				System.out.println("Executing GET request:");
				GETClient.main(new String[] { "http://localhost:9999" });

				System.out.flush();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				System.setOut(originalOut);
				latch.countDown();
			}
		});
		Thread putThread = new Thread(() -> {
			try {
				ContentServer.main(new String[] { "http://localhost:9999", "data/data1.txt" });
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				latch.countDown();
			}
		});

		getThread.start();
		putThread.start();

		latch.await(); // Wait for both GET and PUT requests to finish

		String getOutput = getOutputStream1.toString().trim();
		assertTrue(getOutput.contains("aaaaaa"));
	}

	/**
	 * This test simulates two simultaneous PUT requests followed by a GET request.
	 *
	 * Expected outcome:
	 * - The GET request should retrieve weather data associated with both PUT
	 * requests.
	 * - The output should contain "aaaaaa" from data1.txt and "bbbbb" from
	 * data2.txt.
	 * - Lamport clock values should reflect correct ordering of events.
	 */
	@Test
	public void testPutPutGet() throws Exception {
		CountDownLatch latch = new CountDownLatch(2); // Latch to wait for PUT requests

		// Start two PUT requests in parallel
		Thread putThread1 = new Thread(() -> {
			try {
				ContentServer.main(new String[] { "http://localhost:9999", "data/data1.txt" });
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				latch.countDown();
			}
		});

		Thread putThread2 = new Thread(() -> {
			try {
				ContentServer.main(new String[] { "http://localhost:9999", "data/data2.txt" });
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				latch.countDown();
			}
		});

		putThread1.start();
		putThread2.start();

		latch.await(); // Wait for both PUT requests to finish

		// Now start the GET request
		getOutputStream1 = new ByteArrayOutputStream(); // capture output stream of get client
		Thread getThread1 = new Thread(() -> {
			try {
				PrintStream getPrintStream = new PrintStream(getOutputStream1);
				System.setOut(getPrintStream); // Redirect System.out for GETClient

				System.out.println("Executing GET request:");
				GETClient.main(new String[] { "http://localhost:9999" });

				System.out.flush();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				System.setOut(originalOut);
			}
		});

		getThread1.start();
		getThread1.join();

		String getOutput = getOutputStream1.toString().trim();
		assertTrue(getOutput.contains("aaaaaa")); // Should retrieve data from data1.txt
		assertTrue(getOutput.contains("bbbbb")); // Should retrieve data from data2.txt

	}

	/**
	 * This test simulates a PUT request, followed by a GET request, followed by
	 * another PUT request.
	 *
	 * Expected outcome:
	 * - The first GET request should only retrieve data from the first PUT request.
	 * - The second PUT request should not affect the result of the first GET
	 * request.
	 * - The output should contain "aaaaaa" from data1.txt, but not "bbbbb" from
	 * data2.txt.
	 * - Lamport clock values should reflect the correct ordering of events.
	 */
	@Test
	public void testPutGetPut() throws Exception {
		CountDownLatch latch = new CountDownLatch(1); // Latch to wait for PUT request

		Thread putThread1 = new Thread(() -> {
			try {
				ContentServer.main(new String[] { "http://localhost:9999", "data/data1.txt" });
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				latch.countDown();
			}
		});

		putThread1.start();
		latch.await(); // Wait for the first PUT request to finish

		// Start GET request after first PUT
		getOutputStream1 = new ByteArrayOutputStream(); // capture output stream of GET client
		Thread getThread1 = new Thread(() -> {
			try {
				PrintStream getPrintStream = new PrintStream(getOutputStream1);
				System.setOut(getPrintStream); // Redirect System.out for GETClient

				System.out.println("Executing GET request:");
				GETClient.main(new String[] { "http://localhost:9999" });

				System.out.flush();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				System.setOut(originalOut);
			}
		});

		getThread1.start();
		getThread1.join();

		// Now start the second PUT request
		Thread putThread2 = new Thread(() -> {
			try {
				ContentServer.main(new String[] { "http://localhost:9999", "data/data2.txt" });
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		putThread2.start();
		putThread2.join();

		String getOutput = getOutputStream1.toString().trim();
		assertTrue(getOutput.contains("aaaaaa")); // retrieve weather data id from data 1 (first put)
		assertFalse(getOutput.contains("bbbbb")); // NOT retrieve weather data id from data 2 (second put)
	}
}
