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

import ee.ria.xroad.common.SystemPropertySource;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.String.valueOf;

/**
 * Contains constants for messagelog properties.
 */
@Slf4j
@Deprecated(forRemoval = true)
public final class MessageLogProperties {

    private static final int DEFAULT_ARCHIVE_MAX_FILESIZE = 33554432;

    private static final int DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = 14400;

    private static final int DEFAULT_TIMESTAMP_RECORDS_LIMIT = 10000;

    private static final int DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT = 20000;

    private static final int DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT = 60000;

    private static final int DEFAULT_TIMESTAMP_RETRY_DELAY = 60;

    private static final String DEFAULT_MAX_LOGGABLE_MESSAGE_BODY_SIZE = valueOf(10 * 1024 * 1024);
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

    public static final String ARCHIVE_MAX_FILESIZE = PREFIX + "archive-max-filesize";

    public static final String HASH_ALGO_ID = PREFIX + "hash-algo-id";

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

    private static volatile Map<String, Set<String>> keyMappings = null;

    private MessageLogProperties() {
    }

    /**
     * @return the timestamper client connect timeout in milliseconds. A timeout of zero is
     * interpreted as an infinite timeout. '20000' by default.
     */
    public static int getTimestamperClientConnectTimeout() {
        return getInt(getProperty(TIMESTAMPER_CLIENT_CONNECT_TIMEOUT),
                DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT);
    }

    /**
     * @return the timestamper client read timeout in milliseconds. A timeout of zero is
     * interpreted as an infinite timeout. '60000' by default.
     */
    public static int getTimestamperClientReadTimeout() {
        return getInt(getProperty(TIMESTAMPER_CLIENT_READ_TIMEOUT),
                DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT);
    }

    /**
     * @return the timestamp retry delay in seconds. A retry delay of zero is
     * interpreted as retry delay is disabled. '60' by default.
     */
    public static int getTimestampRetryDelay() {
        return getInt(getProperty(TIMESTAMP_RETRY_DELAY),
                DEFAULT_TIMESTAMP_RETRY_DELAY);
    }

    /**
     * @return true if the time-stamp is created synchronously for each request message. This is a security policy to
     * guarantee the time-stamp at the time of logging the message.
     */
    public static boolean shouldTimestampImmediately() {
        return "true".equalsIgnoreCase(getProperty(TIMESTAMP_IMMEDIATELY, "false"));
    }

    /**
     * @return the maximum number of records to time-stamp in one batch.
     */
    public static int getTimestampRecordsLimit() {
        return getInt(getProperty(TIMESTAMP_RECORDS_LIMIT), DEFAULT_TIMESTAMP_RECORDS_LIMIT);
    }

    /**
     * @return the time period in seconds, how long is time-stamping allowed to be failed before message log stops
     * accepting any more messages.
     */
    public static int getAcceptableTimestampFailurePeriodSeconds() {
        return getInt(getProperty(ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD),
                DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD);
    }

    /**
     * @return the maximum size for archived files in bytes. Defaults to 32 MB.
     */
    public static long getArchiveMaxFilesize() {
        return getInt(getProperty(ARCHIVE_MAX_FILESIZE), DEFAULT_ARCHIVE_MAX_FILESIZE);
    }

    /**
     * @return the hash algorithm that is used for hashing in message log.
     */
    public static DigestAlgorithm getHashAlg() {
        return Optional.ofNullable(getProperty(HASH_ALGO_ID))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    public static GroupingStrategy getArchiveGrouping() {
        return GroupingStrategy.valueOf(
                getProperty(ARCHIVE_GROUPING, GroupingStrategy.NONE.name()).toUpperCase());
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
        final String enabled = getProperty(SOAP_BODY_LOGGING_ENABLED);
        if (enabled != null) {
            return "true".equalsIgnoreCase(enabled);
        }
        return "true".equalsIgnoreCase(getProperty(MESSAGE_BODY_LOGGING_ENABLED, "true"));
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
        final Long value = Long.parseLong(getProperty(MAX_LOGGABLE_MESSAGE_BODY_SIZE, DEFAULT_MAX_LOGGABLE_MESSAGE_BODY_SIZE));
        if (value < 0 || value > MAX_LOGGABLE_MESSAGE_BODY_SIZE_LIMIT) {
            throw new IllegalArgumentException(String.format("%s must be between 0 and %d",
                    MAX_LOGGABLE_MESSAGE_BODY_SIZE, MAX_LOGGABLE_MESSAGE_BODY_SIZE_LIMIT));
        }
        return value;
    }

    public static boolean isTruncatedBodyAllowed() {
        return Boolean.getBoolean(REST_TRUNCATED_BODY_ALLOWED);
    }

    public static boolean isArchiveEncryptionEnabled() {
        return "true".equalsIgnoreCase(getProperty(ARCHIVE_ENCRYPTION_ENABLED));
    }

    public static Path getArchiveEncryptionKeysConfig() {
        final String property = getProperty(ARCHIVE_ENCRYPTION_KEYS_CONFIG);
        return property == null ? null : Paths.get(property);
    }

    public static String getArchiveDefaultEncryptionKey() {
        return getProperty(ARCHIVE_DEFAULT_ENCRYPTION_KEY);
    }

    /**
     * @return keystore path for messagelog encryption keys or null if one is not defined
     */
    public static Path getMessageLogKeyStore() {
        final String property = getProperty(MESSAGELOG_KEYSTORE);
        return property == null ? null : Paths.get(property);
    }

    public static String getMessageLogKeyId() {
        return getProperty(MESSAGELOG_KEY_ID);
    }

    public static boolean isMessageLogEncryptionEnabled() {
        return "true".equalsIgnoreCase(getProperty(MESSAGELOG_ENCRYPTION_ENABLED));
    }

    public static char[] getMessageLogKeyStorePassword() {
        final String property = getProperty(MESSAGELOG_KEYSTORE_PASSWORD,
                System.getenv().get(MESSAGELOG_KEYSTORE_PASSWORD_ENV));
        return property == null ? null : property.toCharArray();
    }

    public static Map<String, Set<String>> getKeyMappings() {
        if (keyMappings == null) {
            synchronized (MessageLogProperties.class) {
                if (keyMappings == null) {
                    Path keyMapping = MessageLogProperties.getArchiveEncryptionKeysConfig();
                    try {
                        keyMappings = readKeyMappings(keyMapping);
                    } catch (Exception e) {
                        throw XrdRuntimeException.systemInternalError("Failed to read messagelog key mappings", e);
                    }
                }
            }
        }
        return keyMappings;
    }

    private static String getMessageBodyLoggingOverrideParameterName(boolean enable, boolean local) {
        String prefix = enable ? MESSAGE_BODY_LOGGING_ENABLE : MESSAGE_BODY_LOGGING_DISABLE;
        String postfix = local ? MESSAGE_BODY_LOGGING_LOCAL_PRODUCER : MESSAGE_BODY_LOGGING_REMOTE_PRODUCER;

        return prefix + postfix;
    }

    private static String getMessageBodyLoggingOverrideParameter(boolean enable, boolean local) {
        return getProperty(getMessageBodyLoggingOverrideParameterName(enable, local), "");
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

    private static String getProperty(String key) {
        return SystemPropertySource.getPropertyResolver().getProperty(key);
    }

    private static String getProperty(String key, String defaultValue) {
        return SystemPropertySource.getPropertyResolver().getProperty(key, defaultValue);
    }

    /*
     * Reads a mapping file in format
     * <pre>
     * #comment on its own line is ignored
     * memberidentifier=keyid
     * memberidentifier= keyid2
     * another\=member = =this is a valid key id#not a comment
     * </pre>
     * and returns the mappings.
     *
     * A member identifier can be listed multiple times.
     *
     * If the member identifier contains '=' it can be escaped using '\='.
     * A literal '\=' must be written as '\\='
     *
     * If the member identifier starts with '#', it can be escaped using '\#'
     * A literal '\#' in member identifier must be written as '\\#'
     */
    private static Map<String, Set<String>> readKeyMappings(Path mappingFile) throws IOException {
        if (mappingFile != null && Files.exists(mappingFile)) {
            final Map<String, Set<String>> mappings = new HashMap<>();
            try {
                final List<String> lines = Files.readAllLines(mappingFile);
                for (int i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i);
                    if (line.isEmpty() || COMMENT.matcher(line).matches()) {
                        continue;
                    }
                    final String[] mapping = SPLITTER.split(line, 2);
                    if (mapping.length != 2 || mapping[0].trim().isEmpty() || mapping[1].trim().isEmpty()) {
                        log.warn("Invalid gpg key mapping at {}:{} ignored", mappingFile, i + 1);
                        continue;
                    }
                    final String identifier = mapping[0].trim().replace("\\=", "=").replace("\\#", "#");
                    final String keyId = mapping[1].trim();
                    mappings.computeIfAbsent(identifier, k -> new HashSet<>()).add(keyId);
                }
                return mappings;
            } catch (IOException e) {
                log.error("Unable to read member identifier to gpg key mapping file", e);
                throw e;
            }
        }
        return Collections.emptyMap();
    }

    private static final Pattern SPLITTER = Pattern.compile("\\s*(?<!\\\\)=\\s*");
    private static final Pattern COMMENT = Pattern.compile("^\\s*(?!\\\\)#.*$");
}
