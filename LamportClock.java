/**
 * Lamport Clock was used for maintaining orders between clients and servers
 * communication
 */
public class LamportClock {
	private int timestamp = 0;

	/** Increment clock's timestamp */
	public synchronized void increment() {
		this.timestamp++;
	};

	/** Getter for lamport clock timestamp */
	public synchronized int getTimestamp() {
		return this.timestamp;
	};

	/**
	 * Update clock timestamp with receieved lamport clokc value from other
	 * components
	 */
	public synchronized void sync(int received) {
		// Throws illegal argument exception if receiving negative value
		if (received < 0) {
			throw new IllegalArgumentException("Received timestamp cannot be negative");
		}
		// Take the maximum value of its own lamport clock value and received lamport
		// value, incremented by 1
		this.timestamp = Math.max(this.timestamp, received) + 1;
	};

}
