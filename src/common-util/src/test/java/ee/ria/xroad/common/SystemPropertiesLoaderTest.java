/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

/**
 * Tests to verify system properties loading.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(SystemPropertiesLoader.class)
public class SystemPropertiesLoaderTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final List<Path> testFiles = ImmutableList.of(
            Paths.get("src/test/resources/loading_order_inis/override-x.ini"),
            Paths.get("src/test/resources/loading_order_inis/override-a.ini"),
            Paths.get("src/test/resources/loading_order_inis/override-1.ini")
    );

    /**
     * Test to ensure a properties file with one section is loaded correctly.
     */
    @Test
    public void loadPropertiesOneFileOneSection() {
        Map<String, String> p =
                load(f("src/test/resources/system-properties-1.ini"), "section1");
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
                load(f("src/test/resources/system-properties-1.ini"), "section1", "section2");
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
                load(f("src/test/resources/system-properties-1.ini", "src/test/resources/system-properties-2.ini"),
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
                load(f("src/test/resources/system-properties-1.ini", "src/test/resources/system-properties-2.ini"),
                        "section1", "section2", "jvm");
        assertEquals("valueFoo", p.get("section2.foo-key1"));
        assertEquals("valueY", p.get("section1.foo-key2"));
        assertEquals("value3", p.get("section1.foo-key3"));
        assertEquals("valueX", p.get("section2.foo-keyX"));

        assertEquals("foo=bar", p.get("jvm.arg-0"));
        assertEquals("baz=xyz", p.get("jvm.arg-1"));
    }

    @Test
    public void listFilePathsBasedOnGlob() throws IOException {
        String testGlob = "*.ini";

        List<Path> testFilePaths = SystemPropertiesLoader.getFilePaths(
                Paths.get("src/test/resources/loading_order_inis"), testGlob);

        //order does not matter here
        assertEquals(testFilePaths.size(), testFilePaths.size());
        assertTrue(testFilePaths.containsAll(testFiles));
    }

    /**
     * Test for loading files in a predetermined (alphabetical) order
     * - file paths are mock-returned in non alphabetical order
     * - expectation is for the loader to call load with paths in alphabetical order
     *
     * @throws IOException if fileset not present
     */
    @Test
    public void loadTestFilesInAlphabeticalOrder() throws Exception {
        final List<Path> expectedFilePaths = ImmutableList.of(
                Paths.get("src/test/resources/loading_order_inis/override-1.ini"),
                Paths.get("src/test/resources/loading_order_inis/override-a.ini"),
                Paths.get("src/test/resources/loading_order_inis/override-x.ini")
        );

        final List<Path> actualFilePaths = new ArrayList<>();

        SystemPropertiesLoader spy = PowerMockito.spy(SystemPropertiesLoader.create(""));
        mockStatic(SystemPropertiesLoader.class);

        //instead of listing files in a non-determined order, use the same three in a specifically selected order
        //to eliminate false positives
        PowerMockito.when(SystemPropertiesLoader.getFilePaths(Mockito.any(Path.class), Mockito.anyString()))
                .thenReturn(testFiles);

        //collect the actual Path order from the load calls
        PowerMockito.doAnswer(invocation -> {
            SystemPropertiesLoader.FileWithSections file =
                    (SystemPropertiesLoader.FileWithSections) invocation.getArguments()[0];
            actualFilePaths.add(Paths.get(file.getName()));
            return null;
        }).when(spy, "load", Mockito.any(SystemPropertiesLoader.FileWithSections.class));

        spy.loadFilesInOrder(
                Paths.get("dummy"),
                "dummy",
                Comparator.comparing(Path::getFileName));

        assertThat(actualFilePaths, is(expectedFilePaths));
    }

    /**
     * Test loading a mix of existing and non-existing files. Expectation is to receive load calls
     * for the existing files in entry order.
     *
     * @throws FileNotFoundException If none of the input files can be loaded
     * @throws Exception             If something goes wrong with capturing calls to load
     */
    @Test
    public void loadMutuallyAlternativeFiles() throws Exception {
        final List<String> initialFileNames = ImmutableList.of(
                "no/such.file",
                "src/test/resources/loading_order_inis/override-x.ini",
                "this/file/does/not/exist.exe",
                "src/test/resources/loading_order_inis/override-1.ini",
                "src/test/resources/loading_order_inis/override-a.ini"
        );

        final List<String> expectedFileNames = ImmutableList.of(
                "src/test/resources/loading_order_inis/override-x.ini",
                "src/test/resources/loading_order_inis/override-1.ini",
                "src/test/resources/loading_order_inis/override-a.ini"
        );

        final List<String> actualFileNames = new ArrayList<>();

        SystemPropertiesLoader spy = PowerMockito.spy(SystemPropertiesLoader.create(""));

        PowerMockito.doAnswer(invocation -> {
            SystemPropertiesLoader.FileWithSections file =
                    (SystemPropertiesLoader.FileWithSections) invocation.getArguments()[0];
            actualFileNames.add(file.getName());
            return null;
        }).when(spy, "load", Mockito.any(SystemPropertiesLoader.FileWithSections.class));

        spy.loadMutuallyAlternativeFilesInEntryOrder(initialFileNames);

        assertThat(actualFileNames, is(expectedFileNames));
    }

    /**
     * Test loading non-existing files with the mutually alternative file loading mechanism. Expectation is that
     * FileNotFoundException is thrown listing all the attempted files in the message.
     *
     * @throws FileNotFoundException Expected exception when loader is unable to find any of the input files
     */
    @Test
    public void loadNonExistingMutuallyAlternativeFiles() throws FileNotFoundException {
        final List<String> initialFileNames = ImmutableList.of(
                "no/such.file",
                "this/file/does/not/exist.exe",
                "completely/fake/file.ini",
                "not/even/close/to/finding/this.one"
        );

        expectedException.expect(FileNotFoundException.class);
        expectedException.expectMessage("None of the following configuration files were found: "
                + String.join(", ", initialFileNames));

        SystemPropertiesLoader testLoader = SystemPropertiesLoader.create("");
        testLoader.loadMutuallyAlternativeFilesInEntryOrder(initialFileNames);

    }

    private static Map<String, String> load(String[] fileNames,
            String... sectionNames) {
        final Map<String, String> properties = new HashMap<>();

        SystemPropertiesLoader loader = SystemPropertiesLoader.create("");
        SystemPropertiesLoader spy = Mockito.spy(loader);

        Mockito.doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            properties.put(args[0].toString(), args[1].toString());
            return null;
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
