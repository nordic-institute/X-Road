package org.niis.xroad.configuration.migration;

import java.util.HashMap;
import java.util.Map;

/**
 * Some properties have their paths changed. This class is used to map old paths to new paths.
 */
public class LegacyConfigPathMapping {
    private static final Map<String, String> MAPPING = new HashMap<>();

    static {
        MAPPING.put("proxy.configuration-anchor-file", "configuration-client.configuration-anchor-file");
    }

    String map(String oldPath) {
        return MAPPING.getOrDefault(oldPath, oldPath);
    }
}
