import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit Testing for Lamport Clock
 */
public class LamportClockTest {
	private LamportClock clock;

	/**
	 * Initialise new lamport before each test.
	 * This ensures tests have its own fresh instance of lamport, with initial value
	 * set as 0
	 */
	@Before
	public void setUp() {
		clock = new LamportClock(); // setup new lamport clock before each test

	}

	/**
	 * Test to verify initial value of lamport clock
	 * Lamport value should be initliased as 0
	 */
	@Test
	public void testInitialValue() {
		// Initial value should be 0
		assertEquals(0, clock.getTimestamp());
	};

	/**
	 * Test increment() functionality
	 * Lamport value should be incremented by 1 after each call
	 */
	@Test
	public void testIncrement() {
		assertEquals(0, clock.getTimestamp());

		clock.increment();
		assertEquals(1, clock.getTimestamp());

		clock.increment();
		assertEquals(2, clock.getTimestamp());
	};

	/**
	 * Test sync() functionality
	 * This should take the maximum value of its own lamport clock value and
	 * received lamport value, incremented by 1
	 * Take another value (int) as an parameter
	 */
	@Test
	public void testSync() {
		// Initial value should be 0
		assertEquals(0, clock.getTimestamp());

		// Sync with (larger) received value 5
		// Timestamp should update to max(0, 5) + 1 = 6
		clock.sync(5);
		assertEquals(6, clock.getTimestamp());

		// Sync with (smaller) received value 3
		// Timestamp should remain 6 (max(6, 3) + 1 = 7)
		clock.sync(3);
		assertEquals(7, clock.getTimestamp());

		// Sync with (equal) received value 3
		// Timestamp should incremented by 1 (max(7, 7) + 1 = 8)
		clock.sync(7);
		assertEquals(8, clock.getTimestamp());
	};

	/**
	 * Test IllegalArgumentException when receiving negative timestamps
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testSyncWithNegativeTimestamp() {
		assertEquals(0, clock.getTimestamp());
		clock.sync(-5);

	}
}
