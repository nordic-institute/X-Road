package ee.ria.xroad.common.conf.globalconf;

import java.net.URLEncoder;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Utility methods for configuration directory.
 */
public final class ConfigurationUtils {

    private ConfigurationUtils() {
    }

    /**
     * @param expireDateStr the ISO date as string
     * @return DateTime object
     */
    public static DateTime parseISODateTime(String expireDateStr) {
        return ISODateTimeFormat.dateTimeParser().parseDateTime(expireDateStr);
    }

    /**
     * Formats the instance identifier to a form suitable for directory names.
     * @param instanceIdentifier the instance identifier
     * @return escaped string
     * @throws Exception if an error occurs while encoding the input
     */
    public static String escapeInstanceIdentifier(String instanceIdentifier)
            throws Exception {
        return URLEncoder.encode(instanceIdentifier, "UTF-8");
    }
}
