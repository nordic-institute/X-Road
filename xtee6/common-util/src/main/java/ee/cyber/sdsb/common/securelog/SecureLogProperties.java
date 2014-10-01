package ee.cyber.sdsb.common.securelog;

import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.util.CryptoUtils;

public class SecureLogProperties {

    private static final String PREFIX = "ee.cyber.sdsb.secureLog.";

    public static final String TIMESTAMP_IMMEDIATELY =
            PREFIX + "timestampImmediately";

    public static final String TIMESTAMP_INTERVAL =
            PREFIX + "timestampInterval";

    public static final String TIMESTAMP_RECORDS_LIMIT =
            PREFIX + "timestampRecordsLimit";

    public static final String ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD =
            PREFIX + "acceptableTimestampFailurePeriod";

    public static final String KEEP_RECORDS_FOR =
            PREFIX + "keepRecordsFor";

    public static final String ARCHIVE_MAX_FILESIZE =
            PREFIX + "archiveMaxFilesize";

    public static final String ARCHIVE_INTERVAL =
            PREFIX + "archiveInterval";

    public static final String CLEAN_INTERVAL =
            PREFIX + "cleanInterval";

    public static final String HASH_ALGO_ID =
            PREFIX + "hashAlgoId";

    /**
     * If set to true, the time-stamp is created synchronously for each
     * request message. This is a security policy to guarantee the
     * time-stamp at the time of logging the message.
     */
    public static boolean shouldTimestampImmediately() {
        return "true".equalsIgnoreCase(
                System.getProperty(TIMESTAMP_IMMEDIATELY, "false"));
    }

    /**
     * Time interval as Cron expression, how often the Timestamper executes.
     */
    public static String getTimestampInterval() {
        return System.getProperty(TIMESTAMP_INTERVAL, "0 0/5 * * * ?");
    }

    /**
     * Maximum number of records to time-stamp in one batch.
     */
    public static int getTimestampRecordsLimit() {
        return getInt(System.getProperty(TIMESTAMP_RECORDS_LIMIT), 1000);
    }

    /**
     * Time period in seconds, how long is time-stamping allowed to be failed
     * before secureLog stops accepting any more messages.
     */
    public static int getAcceptableTimestampFailurePeriodSeconds() {
        return getInt(System.getProperty(ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD),
                1800);
    }

    /**
     * Time interval as Cron expression for archiving time-stamped records.
     */
    public static String getArchiveInterval() {
        return System.getProperty(ARCHIVE_INTERVAL, "0 0 0/6 1/1 * ? *");
    }

    /**
     * Time interval as Cron expression for cleaning archived records from
     * online database.
     */
    public static String getCleanInterval() {
        return System.getProperty(CLEAN_INTERVAL, "0 0 0/12 1/1 * ? *");
    }

    /**
     * Time in days to keep time-stamped and archived records in the database.
     */
    public static int getKeepRecordsForDays() {
        return getInt(System.getProperty(KEEP_RECORDS_FOR), 7);
    }

    /**
     * Maximum size for archived files in KB.
     */
    public static long getArchiveMaxFilesize() {
        return getInt(System.getProperty(ARCHIVE_MAX_FILESIZE), 33554432);
    }

    /**
     * The hash algorithm that is used for hashing in secure log.
     */
    public static String getHashAlg() {
        return System.getProperty(HASH_ALGO_ID, CryptoUtils.SHA256_ID);
    }

    private static int getInt(String value, int defaultValue) {
        if (!StringUtils.isBlank(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
            }
        }

        return defaultValue;
    }
}
