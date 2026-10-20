package org.autonomous_vehicles.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonTest {

    @Test
    public void testRoundTripOfNestedValues() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "a \"quoted\" \\ name\n\twith é");
        value.put("count", 42L);
        value.put("ratio", 0.1 + 0.2);
        value.put("flags", List.of(true, false));
        value.put("nothing", null);
        value.put("matrix", List.of(List.of(1.5, -2.0), List.of()));
        value.put("empty", Map.of());

        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    public void testDoublesAreBitIdenticalAfterARoundTrip() {
        double[] values = {0.1 + 0.2, 1e-300, -4.9e-324, 1.7976931348623157e308, -0.0, 0.7907853666212759};

        for (double value : values) {
            Object parsed = Json.parse(Json.write(List.of(value)));
            double back = (Double) ((List<?>) parsed).getFirst();
            assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(back), "value " + value);
        }
    }

    @Test
    public void testIntegersAreLongsAndOtherNumbersDoubles() {
        assertEquals(List.of(3L, -7L, 2.5, 1000.0, 9007199254740993L),
                Json.parse("[3, -7, 2.5, 1e3, 9007199254740993]"));
    }

    @Test
    public void testEscapesAreDecoded() {
        assertEquals("A/é\n", Json.parse("\"\\u0041\\/\\u00e9\\n\""));
    }

    @Test
    public void testNumberArraysAreWrittenOnOneLine() {
        assertEquals("{\n  \"w\": [1, 2.5]\n}\n", Json.write(Map.of("w", List.of(1, 2.5))));
    }

    @Test
    public void testInvalidTextIsRejectedWithItsPosition() {
        IllegalArgumentException trailing = assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2,]"));
        assertTrue(trailing.getMessage().contains("at character 6"), trailing.getMessage());

        for (String invalid : new String[]{"", "{", "[1 2]", "{\"a\": 1, \"a\": 2}", "\"open", "01x", "1.", "tru",
                "{a: 1}", "[1] 2", "NaN"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(invalid), invalid);
        }
    }

    @Test
    public void testNonFiniteNumbersCannotBeWritten() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> Json.write(List.of(Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }
}
