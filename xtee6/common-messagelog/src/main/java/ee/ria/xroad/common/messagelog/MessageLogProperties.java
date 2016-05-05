/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Contains constants for messagelog properties.
 */
public final class MessageLogProperties {

    private static final int DEFAULT_ARCHIVE_MAX_FILESIZE = 33554432;

    private static final int DEFAULT_KEEP_RECORDS_FOR = 30;

    private static final int DEFAULT_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = 1800;

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

    /** Property name for toggling SOAP body logging on/off **/
    public static final String SOAP_BODY_LOGGING_ENABLED =
            PREFIX + "soap-body-logging";

    /** Prefix for enable-overriding SOAP body logging **/
    private static final String SOAP_BODY_LOGGING_ENABLE =
            PREFIX + "enabled-body-logging";

    /** Prefix for disable-overriding SOAP body logging **/
    private static final String SOAP_BODY_LOGGING_DISABLE =
            PREFIX + "disabled-body-logging";

    /** Postfix for overriding SOAP body logging for local producers **/
    private static final String SOAP_BODY_LOGGING_LOCAL_PRODUCER =
            "-local-producer-subsystems";

    /** Postfix for overriding SOAP body logging for remote producers **/
    private static final String SOAP_BODY_LOGGING_REMOTE_PRODUCER =
            "-remote-producer-subsystems";
    public static final int NUM_COMPONENTS = 4;
    public static final int FIRST_COMPONENT = 0;
    public static final int SECOND_COMPONENT = 1;
    public static final int THIRD_COMPONENT = 2;
    public static final int FOURTH_COMPONENT = 3;

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

    /**
     * Returns global setting for SOAP body logging
     * @return true if body logging is enabled
     */
    public static boolean isSoapBodyLoggingEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(
                SOAP_BODY_LOGGING_ENABLED, "false"));

    }

    /**
     * Returns list of remote producer subsystem ClientIds for which global SOAP body logging
     * setting is overridden
     * @return list of ClientId
     */
    public static Collection<ClientId> getSoapBodyLoggingRemoteProducerOverrides()  {
        return getSoapBodyLoggingOverrides(false);
    }

    /**
     * Returns list of local producer subsystem ClientIds for which global SOAP body logging
     * setting is overridden
     * @return list of ClientId
     */
    public static Collection<ClientId> getSoapBodyLoggingLocalProducerOverrides()  {
        return getSoapBodyLoggingOverrides(true);
    }


    private static String getSoapBodyLoggingOverrideParameterName(boolean enable, boolean local) {
        String prefix = enable ? SOAP_BODY_LOGGING_ENABLE : SOAP_BODY_LOGGING_DISABLE;
        String postfix = local ? SOAP_BODY_LOGGING_LOCAL_PRODUCER : SOAP_BODY_LOGGING_REMOTE_PRODUCER;
        return prefix + postfix;
    }

    private static String getSoapBodyLoggingOverrideParameter(boolean enable, boolean local) {
        return System.getProperty(getSoapBodyLoggingOverrideParameterName(enable, local), "");
    }

    /**
     * Check that "enableBodyLogging..." parameters are not used if body logging is toggled on,
     * and vice versa.
     */
    private static void validateBodyLoggingOverrideParameters() {
        boolean checkEnableOverrides = isSoapBodyLoggingEnabled();
        validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, true);
        validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, false);
    }

    /**
     * Check that given parameter is not in use, and if it is throws IllegalStateException
     * @param enable
     * @param local
     */
    private static void validateBodyLoggingOverrideParamNotUsed(boolean enable,
                                                                boolean local) {
        if (!getSoapBodyLoggingOverrideParameter(enable, local).isEmpty()) {
            throw new IllegalStateException(getSoapBodyLoggingOverrideParameterName(enable, local)
                    + " should not be used when "
                    + SOAP_BODY_LOGGING_ENABLED + " is " + isSoapBodyLoggingEnabled());
        }
    }

    private static Collection<ClientId> getSoapBodyLoggingOverrides(boolean local)  {
        validateBodyLoggingOverrideParameters();
        return parseClientIdParameters(getSoapBodyLoggingOverrideParameter(
                !isSoapBodyLoggingEnabled(),
                local));
    }

    /**
     * Given one parameter parses it to collection of ClientIds. Parameter
     * should be of format FI/GOV/1710128-9/MANSIKKA, FI/GOV/1710128-9/MUSTIKKA, that is:
     * comma separated list of slash-separated subsystem identifiers.
     * @param clientIdParameters
     * @return
     */
    private static Collection<ClientId> parseClientIdParameters(String clientIdParameters)  {
        Collection<ClientId> toReturn = new ArrayList<>();
        Iterable<String> splitSubsystemParams = Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(clientIdParameters);
        Splitter codeSplitter = Splitter.on("/").trimResults();
        for (String oneSubsystemParam: splitSubsystemParams) {
            List<String> codes = Lists.newArrayList(codeSplitter.split(oneSubsystemParam));
            if (codes.size() != NUM_COMPONENTS) {
                throw new IllegalStateException(
                        " SOAP body logging override parameter should be comma-separated list of four "
                                + "slash-separated codes"
                                + " identifying one subsystem,"
                                + " for example \"FI/ORG/1234567-1/subsystem1\", detected bad value: "
                                + oneSubsystemParam);
            }
            ClientId id = ClientId.create(codes.get(FIRST_COMPONENT),
                    codes.get(SECOND_COMPONENT), codes.get(THIRD_COMPONENT), codes.get(FOURTH_COMPONENT));
            toReturn.add(id);
        }
        return toReturn;
    }


}
