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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String ADDON_GLOB = "*.ini";
    private static final String OVERRIDE_GLOB = "override-*.ini";
    private static final String CONF_FILE_DOES_NOT_EXIST_WARN = "Configuration file {} does not exist";
    static final Comparator<Path> LOADING_ORDER_COMPARATOR = Comparator.comparing(Path::getFileName);

    @Getter
    static final class FileWithSections {
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
    private final List<FileWithSections> optionalLocalFiles = new ArrayList<>();
    private final List<String> mutuallyAlternativeFiles = new ArrayList<>();

    private boolean withCommon;
    private boolean withLocal;
    private boolean withAddOn;
    private boolean withAtLeastOneOf;
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
     * @param fileName     the file name of the INI.
     * @param sectionNames optional section names to be parsed from the INI.
     *                     If not specified, all sections are parsed.
     * @return this instance
     */
    public SystemPropertiesLoader with(String fileName,
            String... sectionNames) {
        files.add(new FileWithSections(fileName, sectionNames));
        return this;
    }

    /**
     * Specifies the optional local ini file to be loaded.
     * @param fileName     the file name of the INI.
     * @param sectionNames optional section names to be parsed from the INI.
     *                     If not specified, all sections are parsed.
     * @return this instance
     */
    public SystemPropertiesLoader withLocalOptional(String fileName,
            String... sectionNames) {
        optionalLocalFiles.add(new FileWithSections(fileName, sectionNames));
        return this;
    }

    /**
     * Specifies the mutually alternative configuration files to be loaded. The triggered mechanism attempts to
     * load all described files, with the minimum requirement of loading at least one file. If none of the files
     * are found or loaded, a FileNotFoundException is produced listing the files that could not be loaded.
     * Built to handle alternative module configurations in installations that consist of different components
     * (i.e. configuring Signer in proxy installation or in center installation)
     * @param filePaths file paths to be loaded alternatively to each other
     * @return this instance for chaining
     */
    public SystemPropertiesLoader withAtLeastOneOf(String... filePaths) {
        withAtLeastOneOf = true;
        Collections.addAll(mutuallyAlternativeFiles, filePaths);
        return this;
    }

    /**
     * Does the actual loading of the INI files. Glob-defined files are loaded in alphabetical
     * order based on the filename.
     */
    public void load() {
        loadCommonConfFile();
        loadSpecifiedIniFiles();
        loadMutuallyAlternativeFiles();
        loadAddonConfFiles();
        loadOverrideConfFiles();
        loadLocalConfFile();
        loadOptionalLocalFiles();

        log.debug("Loaded properties:\n{}", loadedProperties);
    }

    private void loadCommonConfFile() {
        if (withCommon) {
            load(new FileWithSections(SystemProperties.CONF_FILE_COMMON));
        }
    }

    private void loadSpecifiedIniFiles() {
        files.forEach(this::load);
    }

    private void loadMutuallyAlternativeFiles() {
        if (withAtLeastOneOf) {
            try {
                loadMutuallyAlternativeFilesInEntryOrder(mutuallyAlternativeFiles);
            } catch (FileNotFoundException e) {
                log.error("Configuration loading failed", e);
            }
        }
    }

    private void loadAddonConfFiles() {
        if (withAddOn) {
            try {
                Path addOnDir = Paths.get(SystemProperties.CONF_FILE_ADDON_PATH);
                loadFilesInOrder(addOnDir, ADDON_GLOB, LOADING_ORDER_COMPARATOR);
            } catch (NoSuchFileException e) {
                log.warn(CONF_FILE_DOES_NOT_EXIST_WARN, e.getFile());
            } catch (IOException e) {
                log.error("Cannot load addon configuration", e);
            }
        }
    }

    private void loadOverrideConfFiles() {
        if (withOverrides) {
            try {
                Path overrideDir = Paths.get(SystemProperties.getConfPath(), "conf.d");
                loadFilesInOrder(overrideDir, OVERRIDE_GLOB, LOADING_ORDER_COMPARATOR);
            } catch (NoSuchFileException e) {
                log.warn(CONF_FILE_DOES_NOT_EXIST_WARN, e.getFile());
            } catch (IOException e) {
                log.error("Cannot load override configuration", e);
            }
        }
    }

    private void loadLocalConfFile() {
        if (withLocal) {
            load(new FileWithSections(SystemProperties.CONF_FILE_USER_LOCAL));
        }
    }

    private void loadOptionalLocalFiles() {
        optionalLocalFiles.forEach(f -> {
            if (Files.isReadable(Paths.get(f.getName()))) {
                load(f);
            }
        });
    }

    // ------------------------------------------------------------------------

    protected SystemPropertiesLoader(String prefix) {
        this.prefix = prefix;
    }

    static List<Path> getFilePaths(Path dir, String glob) throws IOException {
        try (DirectoryStream<Path> dStream = Files.newDirectoryStream(dir, glob)) {
            List<Path> filePaths = new ArrayList<>();
            dStream.forEach(filePaths::add);
            return filePaths;
        }
    }

    void loadFilesInOrder(Path dir, String glob, Comparator<Path> comp) throws IOException {
        getFilePaths(dir, glob).stream()
                .sorted(comp)
                .forEach(path -> load(new FileWithSections(path.toString())));
    }

    void loadMutuallyAlternativeFilesInEntryOrder(List<String> filePaths) throws FileNotFoundException {
        if (filePaths == null || filePaths.size() == 0) {
            return;
        }

        List<Path> viablePaths = new ArrayList<>();
        for (String stringPath : filePaths) {
            Path path = Paths.get(stringPath);
            if (Files.exists(path) && Files.isReadable(path)) {
                viablePaths.add(path);
            }
        }

        if (viablePaths.size() > 0) {
            viablePaths.forEach(path -> load(new FileWithSections(path.toString())));
        } else {
            throw new FileNotFoundException("None of the following configuration files were found: "
                    + String.join(", ", filePaths));
        }
    }

    private void load(FileWithSections file) {
        INIConfiguration ini = new INIConfiguration();
        // turn off list delimiting (before parsing),
        // otherwise we lose everything after first ","
        // in loadSection/sec.getString(key)
        ini.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);
        try (Reader r = Files.newBufferedReader(Paths.get(file.getName()))) {
            ini.read(r);

            for (String sectionName : ini.getSections()) {
                if (isEmpty(file.getSections())
                        || contains(file.getSections(), sectionName)) {
                    loadSection(sectionName, ini.getSection(sectionName));
                }
            }
        } catch (NoSuchFileException e) {
            log.warn(CONF_FILE_DOES_NOT_EXIST_WARN, e.getFile());
        } catch (ConfigurationException | IOException e) {
            log.warn("Error while loading {}: {}", file.getName(), e);
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
