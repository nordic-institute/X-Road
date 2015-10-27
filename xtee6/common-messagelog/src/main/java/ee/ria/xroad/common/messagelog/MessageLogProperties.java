package ee.ria.xroad.common.messagelog;

import ee.ria.xroad.common.util.CryptoUtils;

/**
 * Contains constants for messagelog properties.
 */
public final class MessageLogProperties {

    private static final int DEFAULT_ARCHIVE_MAX_FILESIZE = 33554432;

    private static final int DEFAULT_KEEP_RECORDS_FOR = 30;

    private static final int DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = 14400;

    private static final int DEFAULT_TIMESTAMP_RECORDS_LIMIT = 10000;

    private static final String PREFIX = "xroad.message-log.";

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

    public static final String ARCHIVE_TRANSFER_COMMAND =
            PREFIX + "archive-transfer-command";

    private MessageLogProperties() {
    }

    /**
     * @return true if the time-stamp is created synchronously for each
     * request message. This is a security policy to guarantee the
     * time-stamp at the time of logging the message.
     */
    public static boolean shouldTimestampImmediately() {
        return "true".equalsIgnoreCase(
                System.getProperty(TIMESTAMP_IMMEDIATELY, "false"));
    }

    /**
     * @return the maximum number of records to time-stamp in one batch.
     */
    public static int getTimestampRecordsLimit() {
        return getInt(System.getProperty(TIMESTAMP_RECORDS_LIMIT),
                DEFAULT_TIMESTAMP_RECORDS_LIMIT);
    }

    /**
     * @return the time period in seconds, how long is time-stamping allowed to be failed
     * before message log stops accepting any more messages.
     */
    public static int getAcceptableTimestampFailurePeriodSeconds() {
        return getInt(System.getProperty(ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD),
                DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD);
    }

    /**
     * @return the time interval as Cron expression for archiving time-stamped records.
     */
    public static String getArchiveInterval() {
        return System.getProperty(ARCHIVE_INTERVAL, "0 0 0/6 1/1 * ? *");
    }

    /**
     * @return the time interval as Cron expression for cleaning archived records from
     * online database.
     */
    public static String getCleanInterval() {
        return System.getProperty(CLEAN_INTERVAL, "0 0 0/12 1/1 * ? *");
    }

    /**
     * @return the time in days to keep time-stamped and archived records in the database.
     */
    public static int getKeepRecordsForDays() {
        return getInt(System.getProperty(KEEP_RECORDS_FOR),
                DEFAULT_KEEP_RECORDS_FOR);
    }

    /**
     * @return the maximum size for archived files in bytes. Defaults to 32 MB.
     */
    public static long getArchiveMaxFilesize() {
        return getInt(System.getProperty(ARCHIVE_MAX_FILESIZE),
                DEFAULT_ARCHIVE_MAX_FILESIZE);
    }

    /**
     * @return the path where timestamped log records are archived.
     */
    public static String getArchivePath() {
        return System.getProperty(ARCHIVE_PATH, "/var/lib/xroad");
    }

    /**
     * @return the hash algorithm that is used for hashing in secure log.
     */
    public static String getHashAlg() {
        return System.getProperty(HASH_ALGO_ID, CryptoUtils.SHA512_ID);
    }

    /**
     * @return the archive files transfer command. Defaults to null.
     */
    public static String getArchiveTransferCommand() {
        return System.getProperty(ARCHIVE_TRANSFER_COMMAND, null);
    }

    private static int getInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
