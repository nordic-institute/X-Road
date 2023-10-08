/*
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
package ee.ria.xroad.common.messagelog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.archive.GroupingStrategy;
import ee.ria.xroad.common.util.CryptoUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Contains constants for messagelog properties.
 */
public final class MessageLogProperties {

    private static final int DEFAULT_ARCHIVE_MAX_FILESIZE = 33554432;

    private static final int DEFAULT_KEEP_RECORDS_FOR = 30;

    private static final int DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = 14400;

    private static final int DEFAULT_TIMESTAMP_RECORDS_LIMIT = 10000;

    private static final int DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT = 20000;

    private static final int DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT = 60000;

    private static final int DEFAULT_TIMESTAMP_RETRY_DELAY = 60;

    private static final int DEFAULT_ARCHIVE_TRANSACTION_BATCH_SIZE = 10000;
    private static final int DEFAULT_CLEAN_TRANSACTION_BATCH_SIZE = 10000;

    private static final long DEFAULT_MAX_LOGGABLE_MESSAGE_BODY_SIZE = 10 * 1024 * 1024;
    private static final long MAX_LOGGABLE_MESSAGE_BODY_SIZE_LIMIT = 1024 * 1024 * 1024;

    private static final String PREFIX = "xroad.message-log.";

    /**
     * Property name of the timestamper client connect timeout (milliseconds).
     */
    public static final String TIMESTAMPER_CLIENT_CONNECT_TIMEOUT = PREFIX + "timestamper-client-connect-timeout";

    /**
     * Property name of the timestamper client read timeout (milliseconds).
     */
    public static final String TIMESTAMPER_CLIENT_READ_TIMEOUT = PREFIX + "timestamper-client-read-timeout";

    public static final String TIMESTAMP_IMMEDIATELY = PREFIX + "timestamp-immediately";

    public static final String TIMESTAMP_RECORDS_LIMIT = PREFIX + "timestamp-records-limit";

    /**
     * Property name of the timestamp retry delay (seconds).
     */
    public static final String TIMESTAMP_RETRY_DELAY = PREFIX + "timestamp-retry-delay";

    public static final String ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = PREFIX + "acceptable-timestamp-failure-period";

    public static final String KEEP_RECORDS_FOR = PREFIX + "keep-records-for";

    public static final String ARCHIVE_MAX_FILESIZE = PREFIX + "archive-max-filesize";

    public static final String ARCHIVE_INTERVAL = PREFIX + "archive-interval";

    public static final String ARCHIVE_PATH = PREFIX + "archive-path";

    public static final String ARCHIVE_TRANSACTION_BATCH = PREFIX + "archive-transaction-batch";

    public static final String CLEAN_INTERVAL = PREFIX + "clean-interval";

    private static final String CLEAN_TRANSACTION_BATCH = "clean-transaction-batch";

    public static final String HASH_ALGO_ID = PREFIX + "hash-algo-id";

    public static final String ARCHIVE_TRANSFER_COMMAND = PREFIX + "archive-transfer-command";

    /**
     * log archive grouping strategy, one of
     * none, member, subsystem
     **/
    public static final String ARCHIVE_GROUPING = PREFIX + "archive-grouping";

    /**
     * Property name for toggling SOAP body logging on/off
     *
     * @deprecated
     **/
    @Deprecated
    public static final String SOAP_BODY_LOGGING_ENABLED = PREFIX + "soap-body-logging";

    /**
     * Property name for toggling message body logging on/off
     **/
    public static final String MESSAGE_BODY_LOGGING_ENABLED = PREFIX + "message-body-logging";

    /**
     * Prefix for enable-overriding message body logging
     **/
    private static final String MESSAGE_BODY_LOGGING_ENABLE = PREFIX + "enabled-body-logging";

    /**
     * Prefix for disable-overriding message body logging
     **/
    private static final String MESSAGE_BODY_LOGGING_DISABLE = PREFIX + "disabled-body-logging";

    /**
     * Postfix for overriding message body logging for local producers
     **/
    private static final String MESSAGE_BODY_LOGGING_LOCAL_PRODUCER = "-local-producer-subsystems";

    /**
     * Postfix for overriding message body logging for remote producers
     **/
    private static final String MESSAGE_BODY_LOGGING_REMOTE_PRODUCER = "-remote-producer-subsystems";

    /**
     * max loggable body size for rest messages
     **/
    private static final String MAX_LOGGABLE_MESSAGE_BODY_SIZE = PREFIX + "max-loggable-message-body-size";

    /**
     * is truncating body in logging allowed
     **/
    private static final String REST_TRUNCATED_BODY_ALLOWED = PREFIX + "truncated-body-allowed";

    public static final String ARCHIVE_ENCRYPTION_ENABLED = PREFIX + "archive-encryption-enabled";

    public static final String ARCHIVE_GPG_HOME_DIRECTORY = PREFIX + "archive-gpg-home-directory";

    public static final String ARCHIVE_ENCRYPTION_KEYS_CONFIG = PREFIX + "archive-encryption-keys-config";

    public static final String ARCHIVE_DEFAULT_ENCRYPTION_KEY = PREFIX + "archive-default-encryption-key";

    public static final String MESSAGELOG_ENCRYPTION_ENABLED = PREFIX + "messagelog-encryption-enabled";

    public static final String MESSAGELOG_KEYSTORE = PREFIX + "messagelog-keystore";

    public static final String MESSAGELOG_KEYSTORE_PASSWORD = PREFIX + "messagelog-keystore-password";
    public static final String MESSAGELOG_KEYSTORE_PASSWORD_ENV = MESSAGELOG_KEYSTORE_PASSWORD.toUpperCase()
            .replace('.', '_');
    public static final String MESSAGELOG_KEY_ID = PREFIX + "messagelog-key-id";

    public static final int NUM_COMPONENTS = 4;
    public static final int FIRST_COMPONENT = 0;
    public static final int SECOND_COMPONENT = 1;
    public static final int THIRD_COMPONENT = 2;
    public static final int FOURTH_COMPONENT = 3;

    private MessageLogProperties() {
    }

    /**
     * @return the timestamper client connect timeout in milliseconds. A timeout of zero is
     * interpreted as an infinite timeout. '20000' by default.
     */
    public static int getTimestamperClientConnectTimeout() {
        return getInt(System.getProperty(TIMESTAMPER_CLIENT_CONNECT_TIMEOUT),
                DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT);
    }

    /**
     * @return the timestamper client read timeout in milliseconds. A timeout of zero is
     * interpreted as an infinite timeout. '60000' by default.
     */
    public static int getTimestamperClientReadTimeout() {
        return getInt(System.getProperty(TIMESTAMPER_CLIENT_READ_TIMEOUT),
                DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT);
    }

    /**
     * @return the timestamp retry delay in seconds. A retry delay of zero is
     * interpreted as retry delay is disabled. '60' by default.
     */
    public static int getTimestampRetryDelay() {
        return getInt(System.getProperty(TIMESTAMP_RETRY_DELAY),
                DEFAULT_TIMESTAMP_RETRY_DELAY);
    }

    /**
     * @return true if the time-stamp is created synchronously for each request message. This is a security policy to
     * guarantee the time-stamp at the time of logging the message.
     */
    public static boolean shouldTimestampImmediately() {
        return "true".equalsIgnoreCase(System.getProperty(TIMESTAMP_IMMEDIATELY, "false"));
    }

    /**
     * @return the maximum number of records to time-stamp in one batch.
     */
    public static int getTimestampRecordsLimit() {
        return getInt(System.getProperty(TIMESTAMP_RECORDS_LIMIT), DEFAULT_TIMESTAMP_RECORDS_LIMIT);
    }

    /**
     * @return the time period in seconds, how long is time-stamping allowed to be failed before message log stops
     * accepting any more messages.
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
     * @return number of archived item in one transaction.
     */
    public static int getArchiveTransactionBatchSize() {
        return getInt(System.getProperty(ARCHIVE_TRANSACTION_BATCH), DEFAULT_ARCHIVE_TRANSACTION_BATCH_SIZE);
    }

    /**
     * @return the time interval as Cron expression for cleaning archived records from online database.
     */
    public static String getCleanInterval() {
        return System.getProperty(CLEAN_INTERVAL, "0 0 0/12 1/1 * ? *");
    }

    /**
     * @return the time in days to keep time-stamped and archived records in the database.
     */
    public static int getKeepRecordsForDays() {
        return getInt(System.getProperty(KEEP_RECORDS_FOR), DEFAULT_KEEP_RECORDS_FOR);
    }

    /**
     * @return the maximum size for archived files in bytes. Defaults to 32 MB.
     */
    public static long getArchiveMaxFilesize() {
        return getInt(System.getProperty(ARCHIVE_MAX_FILESIZE), DEFAULT_ARCHIVE_MAX_FILESIZE);
    }

    /**
     * @return the path where timestamped log records are archived.
     */
    public static String getArchivePath() {
        return System.getProperty(ARCHIVE_PATH, "/var/lib/xroad");
    }

    /**
     * @return the hash algorithm that is used for hashing in message log.
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

    public static GroupingStrategy getArchiveGrouping() {
        return GroupingStrategy.valueOf(
                System.getProperty(ARCHIVE_GROUPING, GroupingStrategy.NONE.name()).toUpperCase());
    }

    private static int getInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    /**
     * Returns global setting for message body logging.
     *
     * @return true if body logging is enabled.
     */
    public static boolean isMessageBodyLoggingEnabled() {
        // for backwards compatibility
        final String enabled = System.getProperty(SOAP_BODY_LOGGING_ENABLED);
        if (enabled != null) {
            return "true".equalsIgnoreCase(enabled);
        }
        return "true".equalsIgnoreCase(System.getProperty(MESSAGE_BODY_LOGGING_ENABLED, "true"));
    }

    /**
     * Returns list of remote producer subsystem ClientIds for which global SOAP body logging setting is overridden.
     *
     * @return list of ClientId.
     */
    public static Collection<ClientId> getMessageBodyLoggingRemoteProducerOverrides() {
        return getMessageBodyLoggingOverrides(false);
    }

    /**
     * Returns list of local producer subsystem ClientIds for which global SOAP body logging setting is overridden.
     *
     * @return list of ClientId.
     */
    public static Collection<ClientId> getMessageBodyLoggingLocalProducerOverrides() {
        return getMessageBodyLoggingOverrides(true);
    }

    /**
     * Returns maximum loggable REST body size
     */
    public static long getMaxLoggableBodySize() {
        final Long value = Long.getLong(MAX_LOGGABLE_MESSAGE_BODY_SIZE, DEFAULT_MAX_LOGGABLE_MESSAGE_BODY_SIZE);
        if (value < 0 || value > MAX_LOGGABLE_MESSAGE_BODY_SIZE_LIMIT) {
            throw new IllegalArgumentException(String.format("%s must be between 0 and %d",
                    MAX_LOGGABLE_MESSAGE_BODY_SIZE, MAX_LOGGABLE_MESSAGE_BODY_SIZE_LIMIT));
        }
        return value;
    }

    public static boolean isTruncatedBodyAllowed() {
        return Boolean.getBoolean(REST_TRUNCATED_BODY_ALLOWED);
    }

    public static int getCleanTransactionBatchSize() {
        return Integer.getInteger(CLEAN_TRANSACTION_BATCH, DEFAULT_CLEAN_TRANSACTION_BATCH_SIZE);
    }

    public static boolean isArchiveEncryptionEnabled() {
        return Boolean.getBoolean(ARCHIVE_ENCRYPTION_ENABLED);
    }

    public static Path getArchiveGPGHome() {
        return Paths.get(System.getProperty(ARCHIVE_GPG_HOME_DIRECTORY, "/etc/xroad/gpghome"));
    }

    public static Path getArchiveEncryptionKeysConfig() {
        final String property = System.getProperty(ARCHIVE_ENCRYPTION_KEYS_CONFIG);
        return property == null ? null : Paths.get(property);
    }

    public static String getArchiveDefaultEncryptionKey() {
        return System.getProperty(ARCHIVE_DEFAULT_ENCRYPTION_KEY);
    }

    /** @return keystore path for messagelog encryption keys or null if one is not defined */
    public static Path getMessageLogKeyStore() {
        final String property = System.getProperty(MESSAGELOG_KEYSTORE);
        return property == null ? null : Paths.get(property);
    }

    public static String getMessageLogKeyId() {
        return System.getProperty(MESSAGELOG_KEY_ID);
    }

    public static boolean isMessageLogEncryptionEnabled() {
        return Boolean.getBoolean(MESSAGELOG_ENCRYPTION_ENABLED);
    }

    public static char[] getMessageLogKeyStorePassword() {
        final String property = System.getProperty(MESSAGELOG_KEYSTORE_PASSWORD,
                System.getenv().get(MESSAGELOG_KEYSTORE_PASSWORD_ENV));
        return property == null ? null : property.toCharArray();
    }

    private static String getMessageBodyLoggingOverrideParameterName(boolean enable, boolean local) {
        String prefix = enable ? MESSAGE_BODY_LOGGING_ENABLE : MESSAGE_BODY_LOGGING_DISABLE;
        String postfix = local ? MESSAGE_BODY_LOGGING_LOCAL_PRODUCER : MESSAGE_BODY_LOGGING_REMOTE_PRODUCER;

        return prefix + postfix;
    }

    private static String getMessageBodyLoggingOverrideParameter(boolean enable, boolean local) {
        return System.getProperty(getMessageBodyLoggingOverrideParameterName(enable, local), "");
    }

    /**
     * Check that "enableBodyLogging..." parameters are not used if body logging is toggled on, and vice versa.
     */
    private static void validateBodyLoggingOverrideParameters() {
        boolean checkEnableOverrides = isMessageBodyLoggingEnabled();

        validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, true);
        validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, false);
    }

    /**
     * Check that given parameter is not in use, and if it is throws IllegalStateException.
     *
     * @param enable
     * @param local
     */
    private static void validateBodyLoggingOverrideParamNotUsed(boolean enable, boolean local) {
        if (!getMessageBodyLoggingOverrideParameter(enable, local).isEmpty()) {
            throw new IllegalStateException(getMessageBodyLoggingOverrideParameterName(enable, local)
                    + " should not be used when " + MESSAGE_BODY_LOGGING_ENABLED
                    + " is " + isMessageBodyLoggingEnabled());
        }
    }

    private static Collection<ClientId> getMessageBodyLoggingOverrides(boolean local) {
        validateBodyLoggingOverrideParameters();

        return parseClientIdParameters(getMessageBodyLoggingOverrideParameter(!isMessageBodyLoggingEnabled(), local));
    }

    /**
     * Given one parameter parses it to collection of ClientIds. Parameter should be of format
     * FI/GOV/1710128-9/MANSIKKA, FI/GOV/1710128-9/MUSTIKKA, that is: comma separated list of slash-separated subsystem
     * identifiers.
     *
     * @param clientIdParameters
     * @return
     */
    private static Collection<ClientId> parseClientIdParameters(String clientIdParameters) {
        Collection<ClientId> toReturn = new ArrayList<>();
        Iterable<String> splitSubsystemParams = Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(clientIdParameters);

        Splitter codeSplitter = Splitter.on("/").trimResults();

        for (String oneSubsystemParam : splitSubsystemParams) {
            List<String> codes = Lists.newArrayList(codeSplitter.split(oneSubsystemParam));

            if (codes.size() != NUM_COMPONENTS) {
                throw new IllegalStateException("Message body logging override parameter should be comma-separated "
                        + "list of four slash-separated codesidentifying one subsystem, for example "
                        + "\"FI/ORG/1234567-1/subsystem1\", detected bad value: " + oneSubsystemParam);
            }
            ClientId id = ClientId.Conf.create(codes.get(FIRST_COMPONENT), codes.get(SECOND_COMPONENT),
                    codes.get(THIRD_COMPONENT), codes.get(FOURTH_COMPONENT));
            toReturn.add(id);
        }

        return toReturn;
    }

}
