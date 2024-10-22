import static org.junit.Assert.*;
import org.junit.Test;

public class WeatherNodeTest {

    /**
     * Test the WeatherNode constructor and getters.
     * Ensure that the values are correctly initialised and retrieved.
     */
    @Test
    public void testWeatherNodeConstructorAndGetters() {
        String data = "Hi CAMILLE";
        int lamport = 5;
        long last_update = 1628505600000L;

        WeatherNode node = new WeatherNode(data, lamport, last_update);

        assertEquals(data, node.getData());
        assertEquals(lamport, node.getLamport());
        assertEquals(last_update, node.getLastUpdate());
    }

    /**
     * Test the toFileFormat() method.
     * Ensure WeatherNode is correctly converted to correct formatted string for backup file.
     */
    @Test
    public void testToFileFormat() {
        String data = "Hi CAMILLE";
        int lamport = 5;
        long last_update = 1628505600000L;

        WeatherNode node = new WeatherNode(data, lamport, last_update);
        String expected = "BEGIN_ENTRY\n" +
                          "data = Hi CAMILLE;\n" +  
                          "lamport = 5;\n" +
                          "last_update = 1628505600000;\n" +
                          "END_ENTRY\n\n";

        assertEquals(expected, node.toFileFormat());
    }

    /**
     * Test the toWeatherNode method.
     * Ensure that a formatted string is correctly parsed back into a WeatherNode object.
     */
    @Test
    public void testToWeatherNode() {
        String input = "BEGIN_ENTRY\n" +
                       "data = Hi CAMILLE;\n" +  
                       "lamport = 5;\n" +
                       "last_update = 1628505600000;\n" +
                       "END_ENTRY\n\n";

        WeatherNode node = WeatherNode.toWeatherNode(input);

        assertEquals("Hi CAMILLE", node.getData());
        assertEquals(5, node.getLamport());
        assertEquals(1628505600000L, node.getLastUpdate());
    }

    /**
     * Test toWeatherNode with invalid data.
     * Ensure that incorrect formats are handled and throw expected exceptions.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testToWeatherNodeInvalid() {
		// Missing last_update
        String invalidInput = "BEGIN_ENTRY\n" +
                                "data = Hi CAMILLE;\n" +  
                                "lamport = 5;\n" + 
                                "END_ENTRY\n\n";

        WeatherNode.toWeatherNode(invalidInput);
    }
}
