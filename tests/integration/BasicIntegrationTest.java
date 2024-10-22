import org.junit.BeforeClass;
import org.junit.AfterClass;

import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class BasicIntegrationTest {
	private static Thread serverThread;
	private static ByteArrayOutputStream outputStream;
	private static PrintStream originalOut;

	/**
	 * Set up method that runs before the class tests.
	 * Starts server in a separate thread and redirects System.out to capture test
	 * output.
	 * 
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		System.out.println("Setting up...");
		Path path = Paths.get("tests/mock_data/test_backup_basic.txt"); // delete test backup file
		if (Files.exists(path)) {
			Files.delete(path);
		}
		Files.createFile(path);

		serverThread = new Thread(() -> {
			try {
				AggregationServer.main(new String[] { "3333" });
				AggregationServer.DATA_FILE = "tests/mock_data/test_backup_basic.txt"; // refresh backup file

			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		serverThread.start();
		Thread.sleep(1000);

		outputStream = new ByteArrayOutputStream();
		originalOut = System.out;
		System.setOut(new PrintStream(outputStream));
	}

	/**
	 * Tear down method that runs after all class tests have been executed.
	 * Join server thread.
	 * 
	 */
	@AfterClass
	public static void tearDown() throws Exception {
		serverThread.interrupt();
		serverThread.join();
		System.setOut(originalOut);
		Path path = Paths.get("tests/mock_data/test_backup_basic.txt"); // delete test backup file
		if (Files.exists(path)) {
			Files.delete(path);
		}

	}

	/**
	 * Test for a successful PUT request, should result in HTTP 201 or 200.
	 * 
	 */
	@Test
	public void testPutSuccess() throws Exception {

		Thread putThread = new Thread(() -> {
			try {
				ContentServer.main(new String[] {
						"http://localhost:3333",
						"tests/mock_data/data_test.txt"
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		putThread.start();
		putThread.join();

		String output = outputStream.toString();
		assertTrue(output.contains("HTTP/1.1 201 Created") ||
				output.contains("HTTP/1.1 200 OK"));

	}

	/**
	 * Test for a PUT request that should result in HTTP 204 (No Content) status.
	 */
	@Test
	public void testPut204_EmptyContent() throws Exception {

		Thread putThread = new Thread(() -> {
			try {
				ContentServer.main(new String[] {
						"http://localhost:3333",
						"tests/mock_data/data_null_test.txt"
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		putThread.start();
		putThread.join();

		String output = outputStream.toString();
		assertTrue(output.contains("HTTP/1.1 204 No Content"));
	}

	/**
	 * Test for a PUT request that should result in HTTP 500 (Internal Server Error)
	 * status. - Invalid json format (no ID)
	 * 
	 */
	@Test
	public void testPut500_NoIdData() throws Exception {

		Thread putThread = new Thread(() -> {
			try {
				ContentServer.main(new String[] {
						"http://localhost:3333",
						"tests/mock_data/data_no_id.txt"
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		putThread.start();
		putThread.join();

		String output = outputStream.toString();
		assertTrue(output.contains("HTTP/1.1 500 Internal Server Error"));
	}

	/**
	 * Test for a GET request with a nonexistent resource, expecting an HTTP 404
	 * (Not Found) status.
	 * 
	 */
	@Test
	public void testGet404_NonExistentId() throws Exception {

		Thread getThread = new Thread(() -> {
			try {
				GETClient.main(new String[] {
						"http://localhost:3333?id=nonexistent",
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		getThread.start();
		getThread.join();
		String output = outputStream.toString();
		assertTrue(output.contains("HTTP/1.1 404 Not Found"));

	}

	@Test
	public void testGet200() throws Exception {

		Thread putThread = new Thread(() -> {
			try {
				ContentServer.main(new String[] {
						"http://localhost:3333",
						"tests/mock_data/data_test.txt"
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		putThread.start();
		putThread.join();
		Thread.sleep(1000);
		Thread getThread = new Thread(() -> {
			try {
				GETClient.main(new String[] {
						"http://localhost:3333?id=testttt",
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		getThread.start();
		getThread.join();
		String output = outputStream.toString();
		assertTrue(output.contains("HTTP/1.1 200 OK"));

	}
}
