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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;

import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * <p>SystemPropertiesLoader reads INI files and creates a Java system property
 * from each entry in the INI file. Properties in the INI file are grouped
 * using INI sections -- for each section's parameters, a new property is
 * created as: [PREFIX].[SECTION].[PARAM], where the [PREFIX] is a prefix
 * for all properties loaded with this loader's instance, [SECTION] is the
 * name of the section and [PARAM] is the name of the param.</p>
 *
 * <p>For example, the following INI file loaded with prefix <pre>com.example.</pre>
 * <pre>
 * [section1]
 * foo-bar = value1
 * foo-baz = value2
 *
 * [section2]
 * bar-baz = value3
 * </pre>
 * produces the following system properties:
 * <pre>
 * com.example.section1.foo-bar = value1
 * com.example.section1.foo-baz = value2
 * com.example.section2.bar-baz = value3
 * </pre>
 *
 * <p>
 * The SystemPropertiesLoader should be called as soon as possible when the
 * main class is created. For example:
 * <pre>
 * public class FooMain {
 *
 *     static {
 *         SystemPropertiesLoader.create().withCommonAndLocal()
 *             .with("my-ini-file1.ini")
 *             .with("my-ini-file2.ini")
 *             .load();
 *     }
 *
 *     public static void main(String args[]) throws Exception {
 *         // ...
 *     }
 *
 * }
 * </pre>
 * </p>
 */
@Slf4j
public class SystemPropertiesLoader {

    private static final String DEFAULT_PREFIX = SystemProperties.PREFIX;

    @Getter
    private static final class FileWithSections {
        private final String name;
        private final String[] sections;
        private FileWithSections(String name, String... sections) {
            this.name = name;
            this.sections = sections;
        }
    }

    private final Map<String, String> loadedProperties =
            new HashMap<String, String>() {
                @Override
                public String toString() {
                    StringBuilder sb = new StringBuilder();

                    List<String> sortedKeys = new ArrayList<>(keySet());
                    Collections.sort(sortedKeys);

                    for (String key : sortedKeys) {
                        sb.append("\t");
                        sb.append(key);
                        sb.append(" = ");
                        sb.append(get(key));
                        sb.append(System.lineSeparator());
                    }

                    return sb.toString();
                }
    };

    private final String prefix;
    private final List<FileWithSections> files = new ArrayList<>();

    private boolean withCommon;
    private boolean withLocal;
    private boolean withAddOn;
    private boolean withOverrides = true;

    // ------------------------------------------------------------------------

    /**
     * Creates a new instance using the default prefix.
     * @return new loader instance
     */
    public static SystemPropertiesLoader create() {
        return create(DEFAULT_PREFIX);
    }

    /**
     * Creates a new instance using the specified prefix.
     * @param prefix the prefix
     * @return new loader instance
     */
    public static SystemPropertiesLoader create(String prefix) {
        return new SystemPropertiesLoader(prefix);
    }

    /**
     * Specifies that the system common INI file should be included when
     * loading.
     * @return this instance
     */
    public SystemPropertiesLoader withCommon() {
        withCommon = true;
        return this;
    }

    /**
     * Specifies that the system override INI files should not be included when
     * loading.
     * @return this instance
     */
    public SystemPropertiesLoader withoutOverrides() {
        withOverrides = false;
        return this;
    }

    /**
     * Specifies that the system local INI file should be included when
     * loading.
     * @return this instance
     */
    public SystemPropertiesLoader withLocal() {
        withLocal = true;
        return this;
    }

    /**
     * Specifies that the addon INI files should be included when
     * loading.
     * @return this instance
     */
    public SystemPropertiesLoader withAddOn() {
        withAddOn = true;
        return this;
    }

    /**
     * Specifies that the system common and local INI files should be included
     * when loading.
     * @return this instance
     */
    public SystemPropertiesLoader withCommonAndLocal() {
        return withCommon().withLocal();
    }

    /**
     * Specifies the ini file to be loaded.
     * @param fileName the file name of the INI.
     * @param sectionNames optional section names to be parsed from the INI.
     * If not specified, all sections are parsed.
     * @return this instance
     */
    public SystemPropertiesLoader with(String fileName,
            String... sectionNames) {
        files.add(new FileWithSections(fileName, sectionNames));
        return this;
    }

    /**
     * Does the actual loading of the INI files.
     */
    public void load() {
        if (withCommon) {
            load(new FileWithSections(SystemProperties.CONF_FILE_COMMON));
        }

        files.forEach(this::load);

        if (withAddOn) {
            try {
                Files.newDirectoryStream(FileSystems.getDefault().getPath(
                        SystemProperties.CONF_FILE_ADDON_PATH), "*.ini")
                        .forEach(path -> {
                            load(new FileWithSections(path.toString()));
                        });
            } catch (IOException e) {
                log.error("Cannot load addon configuration: {}",
                        e);
            }
        }

        if (withOverrides) {
            try {
                Files.newDirectoryStream(FileSystems.getDefault().getPath(
                        SystemProperties.getConfPath(), "conf.d"), "override-*.ini")
                        .forEach(path -> load(new FileWithSections(path.toString())));
            } catch (IOException e) {
                log.error("Cannot load override configuration: {}", e);
            }
        }

        if (withLocal) {
            load(new FileWithSections(SystemProperties.CONF_FILE_USER_LOCAL));
        }

        log.debug("Loaded properties:\n{}", loadedProperties);
    }

    // ------------------------------------------------------------------------

    protected SystemPropertiesLoader(String prefix) {
        this.prefix = prefix;
    }

    private void load(FileWithSections file) {
        try {
            // turn off list delimiting (before parsing),
            // otherwise we lose everything after first ","
            // in loadSection/sec.getString(key)
            HierarchicalINIConfiguration ini = new HierarchicalINIConfiguration();
            ini.setDelimiterParsingDisabled(true);
            ini.load(file.getName());

            for (String sectionName : ini.getSections()) {
                if (isEmpty(file.getSections())
                        || contains(file.getSections(), sectionName)) {
                    loadSection(sectionName, ini.getSection(sectionName));
                }
            }
        } catch (ConfigurationException e) {
            log.warn("Error while loading {}: {}", file.getName(),
                    e);
        }
    }

    private void loadSection(String sectionName, SubnodeConfiguration sec) {
        sec.getKeys().forEachRemaining(key ->
            setProperty(prefix + sectionName + "." + key, sec.getString(key)));
    }

    protected void setProperty(String key, String value) {
        System.setProperty(key, value);

        loadedProperties.put(key, value);
    }
}
