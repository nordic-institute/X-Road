package ee.cyber.sdsb.common;

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

@Slf4j
public class SystemPropertiesLoader {

    private static final String DEFAULT_PREFIX = SystemProperties.PREFIX;

    @Getter
    private static class FileWithSections {
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

    // ------------------------------------------------------------------------

    public static SystemPropertiesLoader create() {
        return new SystemPropertiesLoader();
    }

    public static SystemPropertiesLoader create(String prefix) {
        return new SystemPropertiesLoader(prefix);
    }

    public SystemPropertiesLoader withCommon() {
        withCommon = true;
        return this;
    }

    public SystemPropertiesLoader withLocal() {
        withLocal = true;
        return this;
    }

    public SystemPropertiesLoader withCommonAndLocal() {
        return withCommon().withLocal();
    }

    public SystemPropertiesLoader with(String fileName,
            String... sectionNames) {
        files.add(new FileWithSections(fileName, sectionNames));
        return this;
    }

    public void load() {
        if (withCommon) {
            load(new FileWithSections(SystemProperties.CONF_FILE_COMMON));
        }

        files.forEach(this::load);

        if (withLocal) {
            load(new FileWithSections(SystemProperties.CONF_FILE_USER_LOCAL));
        }

        log.debug("Loaded properties:\n{}", loadedProperties);
    }

    // ------------------------------------------------------------------------

    private SystemPropertiesLoader() {
        this(DEFAULT_PREFIX);
    }

    private SystemPropertiesLoader(String prefix) {
        this.prefix = prefix;
    }

    private void load(FileWithSections file) {
        try {
            HierarchicalINIConfiguration ini =
                    new HierarchicalINIConfiguration(file.getName());
            for (String sectionName : ini.getSections()) {
                if (isEmpty(file.getSections())
                        || contains(file.getSections(), sectionName)) {
                    loadSection(sectionName, ini.getSection(sectionName));
                }
            }
        } catch (ConfigurationException e) {
            log.warn("Error while loading {}: {}", file.getName(),
                    e.getMessage());
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
