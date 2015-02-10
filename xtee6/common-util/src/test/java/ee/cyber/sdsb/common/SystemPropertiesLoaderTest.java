package ee.cyber.sdsb.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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

        SystemPropertiesLoader loader = SystemPropertiesLoader.create("");
        SystemPropertiesLoader spy = Mockito.spy(loader);

        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                properties.put(args[0].toString(), args[1].toString());
                return null;
            }
        }).when(spy).setProperty(Mockito.anyString(), Mockito.anyString());


        for (String fileName : fileNames) {
            spy.with(fileName, sectionNames);
        }

        spy.load();

        return properties;
    }

    private static String[] f(String... a) {
        return a;
    }
}
