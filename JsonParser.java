import java.util.LinkedHashMap;
import java.util.Map;

public class JsonParser {

	/**
	 * Parses a JSON string and converts it into a map of key-value pairs.
	 *
	 * @param jsonString The JSON string to be parsed. The string must be in valid
	 *                   JSON format, starting with "{" and ending with "}".
	 *                   Each key-value pair in the JSON should be separated by
	 *                   commas and formatted as "key": "value".
	 * @return A LinkedHashMap where keys and values from the JSON string are stored
	 *         as string key-value pairs.
	 * @throws Exception If the JSON string is not properly formatted or if a
	 *                   key-value pair is malformed.
	 */
	public static LinkedHashMap<String, String> parse(String jsonString) throws Exception {
		LinkedHashMap<String, String> jsonMap = new LinkedHashMap<>();

		// Validate
		jsonString = jsonString.trim();
		if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
			jsonString = jsonString.substring(1, jsonString.length() - 1).trim();
		} else {
			throw new Exception("Invalid JSON format");
		}

		String[] fields = jsonString.split(",\n");

		for (String field : fields) {
			// Split each pair into key and value based on the colon
			String[] pair = field.split(":", 2);

			if (pair.length != 2) {
				throw new Exception("Invalid JSON key-value field: " + field);
			}

			// Remove surrounding quotes and trim excess spaces from keys and values
			String key = pair[0].trim().replace("\"", "");
			String value = pair[1].trim().replace("\"", "");

			jsonMap.put(key, value);
		}

		return jsonMap;
	}

	/**
	 * Converts a map of key-value pairs into a JSON-formatted string.
	 *
	 * @param jsonMap The map containing string key-value pairs to be converted into
	 *                JSON format.
	 *                The map's keys and values are assumed to be non-null strings.
	 * @return A string representing the input map in JSON format, with each
	 *         key-value pair
	 *         formatted as "key": "value" and enclosed in curly braces.
	 */
	public static String toJson(Map<String, String> jsonMap) {
		StringBuilder jsonBuilder = new StringBuilder();
		jsonBuilder.append("{\n");

		for (Map.Entry<String, String> entry : jsonMap.entrySet()) {
			jsonBuilder.append("    \"").append(entry.getKey()).append("\": \"").append(entry.getValue())
					.append("\",\n");
		}

		// Remove the trailing comma and close the JSON object
		if (jsonMap.size() > 0) {
			jsonBuilder.deleteCharAt(jsonBuilder.length() - 2);
		}

		jsonBuilder.append("}");
		return jsonBuilder.toString();
	}

	/**
	 * Extracts the value associated with the "id" key from a JSON string.
	 *
	 * @param jsonString The JSON string from which to extract the "id" value. The
	 *                   string must be in valid JSON format, starting
	 *                   with "{" and ending with "}" and containing an "id" key.
	 * @return The value associated with the "id" key in the JSON string.
	 * @throws Exception If the JSON string is not properly formatted, if no "id"
	 *                   key is present,
	 *                   or if the key-value pair is malformed.
	 */
	public static String getId(String jsonString) throws Exception {

		if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
			jsonString = jsonString.trim().substring(1, jsonString.length() - 1);
		} else {
			throw new Exception("Invalid JSON format");
		}
		String[] fields = jsonString.split(",\n");

		for (String field : fields) {
			// Split each pair into key and value based on the colon
			String[] pair = field.split(":", 2);

			if (pair.length != 2) {
				throw new Exception("Invalid JSON key-value field: " + field);
			}

			// Remove surrounding quotes and trim excess spaces from keys and values
			String key = pair[0].trim().replace("\"", "");
			String value = pair[1].trim().replace("\"", "");

			if (key.equals("id")) {
				return value;
			}
		}
		throw new Exception("ID not found in the JSON string");
	}
}
