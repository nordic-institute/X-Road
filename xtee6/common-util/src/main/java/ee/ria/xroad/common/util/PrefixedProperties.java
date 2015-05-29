package ee.ria.xroad.common.util;

import java.util.Properties;

import lombok.RequiredArgsConstructor;

/**
 * This class loads only those properties, that start with specified properties.
 */
@RequiredArgsConstructor
public class PrefixedProperties extends Properties {

    private final String prefix;

    @Override
    public synchronized Object put(Object key, Object value) {
        String keyString = key.toString();
        int idx = keyString.indexOf(prefix);
        if (idx > -1) {
            return super.put(keyString.substring(prefix.length()), value);
        }

        return null;
    }

}
