/**
 * 
 * Helper class to store weather node informations in Aggregation Server.
 * This stores data as a jsonString, lamport clock value and last update
 * timestamps
 * This has helper function to convert/reverse weather node to formatted string
 * used for backup (replica) file.
 * 
 */
public class WeatherNode {
	private String data; // jsonString
	private int lamport;
	private long last_update;

	// Constructor
	public WeatherNode(String data, int lamport, long last_update) {
		this.data = data;
		this.lamport = lamport;
		this.last_update = last_update;
	}

	// GETTERS
	/**
	 * Get the jsonString data stored in the WeatherNode.
	 * 
	 * @return jsonString data as a string.
	 */
	public String getData() {
		return this.data;
	}

	public String getDataFormatted() {
		StringBuilder formattedData = new StringBuilder();
		String[] lines = this.data.split("\n"); // Split the stored data by line

		for (int i = 0; i < lines.length; i++) {
			formattedData.append("    ").append(lines[i]); // Add a tab before each line
			if (i != lines.length - 1) {
				formattedData.append("\n"); // Only add a new line if it's not the last line
			}
		}

		return formattedData.toString();
	}

	/**
	 * Get the lamport value stored in the WeatherNode.
	 * 
	 * @return lamport clock value
	 */
	public int getLamport() {
		return this.lamport;
	}

	/**
	 * Get the last update timestamp value stored in the WeatherNode.
	 * 
	 * @return last update timestamp value as long integer
	 */
	public long getLastUpdate() {
		return this.last_update;
	}

	/**
	 * Convert the WeatherNode object to formatted string written in backup file.
	 * 
	 * @return A formatted string representing the WeatherNode's data, Lamport
	 *         clock, and last update timestamp.
	 */
	public String toFileFormat() {
		return "BEGIN_ENTRY\n" +
				"data = " + this.data + ";\n" +
				"lamport = " + this.lamport + ";\n" +
				"last_update = " + this.last_update + ";\n" +
				"END_ENTRY\n\n";
	}

	/**
	 * Parse backup file formatted string back into WeatherNode.
	 * 
	 * @param input formatted string the backup file.
	 * @return WeatherNode from parsed input.
	 */
	public static WeatherNode toWeatherNode(String input) {
		String data = "";
		int lamport = 0;
		long last_update = 0;

		input = input.replace("BEGIN_ENTRY", "").replace("END_ENTRY", "").trim();

		String[] lines = input.split(";\n");

		for (String line : lines) {
			String[] keyValue = line.split("=", 2);

			String key = keyValue[0].trim();
			String value = keyValue[1].trim().replace(";", "");

			if (key.equals("data")) {
				data = value;
			} else if (key.equals("lamport")) {
				lamport = Integer.parseInt(value);
			} else if (key.equals("last_update")) {
				last_update = Long.parseLong(value);
			}
		}
		// Handle faulty formatted string
		if (data == "" || lamport == 0 || last_update == 0) {
			throw new IllegalArgumentException("Invalid backup file formatted string");
		}

		return new WeatherNode(data, lamport, last_update);
	}
}