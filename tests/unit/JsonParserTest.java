import static org.junit.Assert.*;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonParserTest {

	/**
	 * Test the parse() method
	 * Ensure it correctly parses a valid JSON string and returns a linked hash map
	 * of key-value pairs.
	 * 
	 * Expected input: A valid JSON string with keys and values
	 * Expected output: A LinkedHashMap containing key-value pairs generated from
	 * son string.
	 */
	@Test
	public void testParseValidJson() throws Exception {
		String jsonString = "{\n    \"name\": \"John\",\n    \"age\": \"30\"\n}";
		LinkedHashMap<String, String> expectedMap = new LinkedHashMap<>();
		expectedMap.put("name", "John");
		expectedMap.put("age", "30");

		LinkedHashMap<String, String> result = JsonParser.parse(jsonString);

		assertEquals(expectedMap, result);
	}

	/**
	 * Test the parse() method with an invalid json string format
	 * 
	 * Expected input: A json string not enclosed in curly braces
	 * Expected output: Exception thrown due to invalid json format
	 */
	@Test(expected = Exception.class)
	public void testParseInvalidJsonFormat() throws Exception {
		String invalidJsonString = "\"name\": \"John\", \"age\": \"30\""; // Not enclosed in {}
		JsonParser.parse(invalidJsonString); // This should throw an Exception
	}

	/**
	 * Test the parse() method with invalid key-value format
	 * Ensure exception is properly thrown with invalid key-value format
	 * 
	 * Expected input: A son string with an invalid key-value format.
	 * Expected output: Exception thrown.
	 */
	@Test(expected = Exception.class)
	public void testParseInvalidJsonField() throws Exception {
		String invalidJsonString = "{\n    \"name\": \"John\",\n    \"age\" \"30\"\n}"; // Missing colon
		JsonParser.parse(invalidJsonString); // This should throw an Exception
	}

	/**
	 * Test the toJson() method to ensure it correctly converts a map of key-value
	 * pairs into a properly formatted JSON string.
	 * 
	 * Expected input: A linked hash map (String, String) with key-value pairs.
	 * Expected output: A json string with proper formatting.
	 */
	@Test
	public void testToJson() {
		LinkedHashMap<String, String> jsonMap = new LinkedHashMap<>();
		jsonMap.put("name", "Camille");
		jsonMap.put("age", "3");

		String expectedJsonString = "{\n    \"name\": \"Camille\",\n    \"age\": \"3\"\n}";
		String result = JsonParser.toJson(jsonMap);

		assertEquals(expectedJsonString, result);
	}

	/**
	 * Test the getId() method to ensure it correctly retrieves the 'id' field
	 * from the provided JSON string.
	 * 
	 * Expected input: A JSON string with an 'id' field.
	 * Expected output: The value of the 'id' field.
	 */
	@Test
	public void testGetIdValid() throws Exception {
		String jsonString = "{\n    \"id\": \"123\",\n    \"name\": \"John\",\n    \"age\": \"30\"\n}";
		String expectedId = "123";

		String result = JsonParser.getId(jsonString);

		assertEquals(expectedId, result);
	}

	/**
	 * Test the getId() method when there is no 'id' field in the JSON.
	 * 
	 * Expected input: A JSON string without an 'id' field.
	 * Expected output: Exception thrown due to missing 'id'.
	 */
	@Test(expected = Exception.class)
	public void testGetIdMissing() throws Exception {
		String jsonString = "{\n    \"name\": \"John\",\n    \"age\": \"30\"\n}";
		JsonParser.getId(jsonString); // This should throw an Exception since 'id' is missing
	}

	/**
	 * Test the getId() method with invalid JSON format.
	 * 
	 * Expected input: A JSON string with an invalid format.
	 * Expected output: Exception thrown due to invalid JSON format.
	 */
	@Test(expected = Exception.class)
	public void testGetIdInvalidJsonFormat() throws Exception {
		String invalidJsonString = "\"id\": \"123\", \"name\": \"John\", \"age\": \"30\""; // Not enclosed in {}
		JsonParser.getId(invalidJsonString); // This should throw an Exception
	}
}
