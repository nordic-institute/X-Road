package ee.cyber.sdsb.common.messagelog;

import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.util.CryptoUtils;

public class MessageLogProperties {

    private static final String PREFIX = "ee.cyber.sdsb.message-log.";

    public static final String TIMESTAMP_IMMEDIATELY =
            PREFIX + "timestamp-immediately";

    public static final String TIMESTAMP_RECORDS_LIMIT =
            PREFIX + "timestamp-records-limit";

    public static final String ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD =
            PREFIX + "acceptable-timestamp-failure-period";

    public static final String KEEP_RECORDS_FOR =
            PREFIX + "keep-records-for";

    public static final String ARCHIVE_MAX_FILESIZE =
            PREFIX + "archive-max-filesize";

    public static final String ARCHIVE_INTERVAL =
            PREFIX + "archive-interval";

    public static final String ARCHIVE_PATH =
            PREFIX + "archive-path";

    public static final String CLEAN_INTERVAL =
            PREFIX + "clean-interval";

    public static final String HASH_ALGO_ID =
            PREFIX + "hash-algo-id";

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
     * Maximum number of records to time-stamp in one batch.
     */
    public static int getTimestampRecordsLimit() {
        return getInt(System.getProperty(TIMESTAMP_RECORDS_LIMIT), 1000);
    }

    /**
     * Time period in seconds, how long is time-stamping allowed to be failed
     * before message log stops accepting any more messages.
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
     * The path where timestamped log records are archived.
     */
    public static String getArchivePath() {
        return System.getProperty(ARCHIVE_PATH, "/var/lib/sdsb");
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
