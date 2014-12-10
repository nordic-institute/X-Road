package ee.cyber.sdsb.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class SystemPropertiesLoader {

    private static final String DEFAULT_PREFIX = SystemProperties.PREFIX;

    private final String prefix;

    private final Map<String, String> loadedProperties = new HashMap<>();

    public SystemPropertiesLoader() {
        this(DEFAULT_PREFIX);
    }

    public SystemPropertiesLoader(String prefix) {
        this.prefix = prefix;

        load();  // TODO: why call load from constructor?
        debug();
    }

    public void load() {
        load(SystemProperties.CONF_FILE_COMMON);
        loadWithCommonAndLocal();
        load(SystemProperties.CONF_FILE_USER_LOCAL);
    }

    public void load(String fileName, String... sectionNames) {
        try {
            HierarchicalINIConfiguration file =
                    new HierarchicalINIConfiguration(fileName);
            for (String sectionName : file.getSections()) {
                if (ArrayUtils.isEmpty(sectionNames)
                        || ArrayUtils.contains(sectionNames, sectionName)) {
                    loadSection(sectionName, file.getSection(sectionName));
                }
            }
        } catch (ConfigurationException e) {
            log.warn("Error while loading {}: {}", fileName, e.getMessage());
        }
    }

    protected void loadWithCommonAndLocal() {
        // to be implemented by subclass
    }

    private void loadSection(String sectionName, SubnodeConfiguration section) {
        Iterator<String> it = section.getKeys();
        while (it.hasNext()) {
            String key = it.next();
            String value = section.getString(key);
            onProperty(prefix + sectionName + "." + key, value);
        }
    }

    private void debug() {
        StringBuilder sb = new StringBuilder();
        sb.append("Loaded properties:\n");

        List<String> keys = new ArrayList<>(loadedProperties.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            sb.append("\t");
            sb.append(key);
            sb.append(" = ");
            sb.append(loadedProperties.get(key));
            sb.append(System.lineSeparator());
        }

        log.debug(sb.toString());
    }

    void onProperty(String key, String value) {
        System.setProperty(key, value);

        loadedProperties.put(key, value);
    }
}
