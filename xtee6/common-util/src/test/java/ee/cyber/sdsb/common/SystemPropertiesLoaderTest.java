package ee.cyber.sdsb.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SystemPropertiesLoaderTest {

    @Test
    public void loadPropertiesOneFileOneSection() {
        Map<String, String> p =
                load(f("system-properties-1.ini"), "section1");
        assertEquals("value1", p.get("section1.foo-key1"));
        assertEquals("value2", p.get("section1.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
        assertEquals("value1", p.get("section1.foo-bar-key1"));
        assertNull(p.get("section1.foo-key4"));
    }

    @Test
    public void loadPropertiesOneFileMultipleSections() {
        Map<String, String> p =
                load(f("system-properties-1.ini"), "section1", "section2");
        assertEquals("value4", p.get("section2.foo-key1"));
        assertEquals("value5", p.get("section2.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
    }

    @Test
    public void loadPropertiesMultipleFilesOneSection() {
        Map<String, String> p =
                load(f("system-properties-1.ini", "system-properties-2.ini"),
                        "section1");
        assertEquals("valueX", p.get("section1.foo-key1"));
        assertEquals("valueY", p.get("section1.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
    }

    @Test
    public void loadPropertiesMultipleFilesMultipleSections() {
        Map<String, String> p =
                load(f("system-properties-1.ini", "system-properties-2.ini"),
                        "section1", "section2", "jvm");
        assertEquals("valueFoo", p.get("section2.foo-key1"));
        assertEquals("valueY", p.get("section1.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
        assertEquals("valueX", p.get("section2.foo-keyX"));

        assertEquals("foo=bar", p.get("jvm.arg-0"));
        assertEquals("baz=xyz", p.get("jvm.arg-1"));
    }

    private static Map<String, String> load(String[] fileNames,
            String... sectionNames) {
        final Map<String, String> properties = new HashMap<String, String>();

        SystemPropertiesLoader loader = new SystemPropertiesLoader("") {
            @Override
            void onProperty(String key, String value) {
                properties.put(key, value);
            }
            @Override
            public void load() {
            }
        };

        for (String fileName : fileNames) {
            loader.load(fileName, sectionNames);
        }

        return properties;
    }

    private static String[] f(String... a) {
        return a;
    }
}
