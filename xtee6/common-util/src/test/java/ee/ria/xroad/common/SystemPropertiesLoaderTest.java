/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify system properties loading.
 */
public class SystemPropertiesLoaderTest {

    /**
     * Test to ensure a properties file with one section is loaded correctly.
     */
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

    /**
     * Test to ensure a properties file with multiple sections is loaded correctly.
     */
    @Test
    public void loadPropertiesOneFileMultipleSections() {
        Map<String, String> p =
                load(f("system-properties-1.ini"), "section1", "section2");
        assertEquals("value4", p.get("section2.foo-key1"));
        assertEquals("value5", p.get("section2.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
    }

    /**
     * Test to ensure a single section from multiple property files is loaded correctly.
     */
    @Test
    public void loadPropertiesMultipleFilesOneSection() {
        Map<String, String> p =
                load(f("system-properties-1.ini", "system-properties-2.ini"),
                        "section1");
        assertEquals("valueX", p.get("section1.foo-key1"));
        assertEquals("valueY", p.get("section1.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
    }

    /**
     * Test to ensure multiple sections from multiple property files are loaded correctly.
     */
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
