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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates operational data records into the database.
 */
@Slf4j
public final class OperationalDataRecordsGenerator {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_BATCH_COUNT = 10;
    private static final int DEFAULT_LONG_STRING_LENGTH = 200;
    private static final int DEFAULT_SHORT_LONG_STRING_LENGTH = 50;
    // 2016.01.01 00:00:01
    private static final long DEFAULT_FIRST_TIMESTAMP = 1451606401;

    private static final String SECURITY_SERVER_INTERNAL_IP = "111.111.111.111";

    private static final Long DUMMY_LONG_10 = getDummyLong(10);
    private static final Integer DUMMY_INT_2 = getDummyInteger(2);
    private static final String DUMMY_UUID = UUID.randomUUID().toString();

    private static final Options OPTIONS = getOptions();

    private static final String SERVICE_TYPE_WSDL = "WSDL";

    private OperationalDataRecordsGenerator() {
    }

    /**
     * Main function.
     * @param args args
     * @throws Exception if something goes wrong.
     */
    public static void main(String args[]) throws Exception {

        CommandLine cmd = parseCommandLine(args);

        if (cmd.hasOption("help")) {
            usage();

            System.exit(0);
        }

        long startTimestamp = cmd.getOptionValue("timestamp") != null
                ? Long.parseLong(cmd.getOptionValue("timestamp"))
                : DEFAULT_FIRST_TIMESTAMP;

        int batchSize = cmd.getOptionValue("batch-size") != null
                ? Integer.parseInt(cmd.getOptionValue("batch-size"))
                : DEFAULT_BATCH_SIZE;

        int batchCount = cmd.getOptionValue("batch-count") != null
                ? Integer.parseInt(cmd.getOptionValue("batch-count"))
                : DEFAULT_BATCH_COUNT;

        String longString = cmd.getOptionValue("long-string-length") != null
                ? getDummyStr(Integer.parseInt(cmd.getOptionValue(
                "long-string-length")))
                : getDummyStr(DEFAULT_LONG_STRING_LENGTH);

        String shortString = cmd.getOptionValue("short-string-length") != null
                ? getDummyStr(Integer.parseInt(cmd.getOptionValue(
                "short-string-length")))
                : getDummyStr(DEFAULT_SHORT_LONG_STRING_LENGTH);

        log.info("first timestamp: {}, batch-size: {}, batch-count: {}",
                startTimestamp, batchSize, batchCount);

        for (int i = 0; i < batchCount; ++i) {
            storeRecords(batchSize, startTimestamp++, longString, shortString);
        }

        log.info("{} records generated", batchCount * batchSize);
    }

    private static CommandLine parseCommandLine(String args[]) {
        try {
            return new BasicParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            log.error("Parsing command line failed: {}", e.getMessage());

            usage();

            System.exit(1);
        }

        return null;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option firstTimestamp = new Option("t", "timestamp", true,
                "first timestamp (default: " + DEFAULT_FIRST_TIMESTAMP + "),"
                + " every record in one batch has the same timestamp, for"
                + " next batch timestamp is increased by 1");
        firstTimestamp.setRequired(false);
        options.addOption(firstTimestamp);

        Option batchSize = new Option("s", "batch-size", true,
                "batch size (default: " + DEFAULT_BATCH_SIZE + ")");
        batchSize.setRequired(false);
        options.addOption(batchSize);

        Option batchCount = new Option("c", "batch-count", true,
                "batch count (default: " + DEFAULT_BATCH_COUNT + ")");
        batchCount.setRequired(false);
        options.addOption(batchCount);

        Option longStringLength = new Option("lsl", "long-string-length", true,
                "long string length (default: "
                        + DEFAULT_LONG_STRING_LENGTH + ")");
        longStringLength.setRequired(false);
        options.addOption(longStringLength);

        Option shortStringLength = new Option("ssl", "short-string-length",
                true, "short string length (default: "
                        + DEFAULT_SHORT_LONG_STRING_LENGTH + ")");
        shortStringLength.setRequired(false);
        options.addOption(shortStringLength);

        Option usage = new Option("h", "help", false, "help");
        usage.setRequired(false);
        options.addOption(usage);

        return options;
    }

    private static void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("OperationalDataRecordsGenerator", OPTIONS);
    }

    private static void storeRecords(int count, long timestamp,
            String longString, String shortString) throws Exception {
        List<OperationalDataRecord> records = generateRecords(count, timestamp,
                longString, shortString);

        OperationalDataRecordManager.storeRecords(records, timestamp);
    }

    private static List<OperationalDataRecord> generateRecords(int count,
            long timestamp, String longString, String shortString) {
        List<OperationalDataRecord> records = new ArrayList<>();
        OperationalDataRecord record;

        for (int i = 0; i < count; ++i) {
            long millis = timestamp * 1000L;

            record = new OperationalDataRecord();
            record.setSecurityServerInternalIp(SECURITY_SERVER_INTERNAL_IP);
            record.setSecurityServerType(OpMonitoringData.SecurityServerType
                    .PRODUCER.getTypeString());

            record.setRequestInTs(millis);
            record.setRequestOutTs(millis);
            record.setResponseInTs(millis);
            record.setResponseOutTs(millis);

            record.setClientXRoadInstance(shortString);
            record.setClientMemberClass(shortString);
            record.setClientMemberCode(longString);
            record.setClientSubsystemCode(longString);

            record.setServiceXRoadInstance(shortString);
            record.setServiceMemberClass(shortString);
            record.setServiceMemberCode(longString);
            record.setServiceSubsystemCode(longString);
            record.setServiceCode(longString);
            record.setServiceVersion(shortString);

            record.setRepresentedPartyClass(shortString);
            record.setRepresentedPartyCode(longString);

            record.setMessageId(longString);
            record.setMessageUserId(longString);
            record.setMessageIssue(longString);
            record.setMessageProtocolVersion("4.0");

            record.setClientSecurityServerAddress(longString);
            record.setServiceSecurityServerAddress(longString);

            record.setRequestSize(DUMMY_LONG_10);
            record.setResponseSize(DUMMY_LONG_10);
            record.setRequestMimeSize(DUMMY_LONG_10);
            record.setRequestAttachmentCount(DUMMY_INT_2);
            record.setResponseMimeSize(DUMMY_LONG_10);
            record.setResponseAttachmentCount(DUMMY_INT_2);
            record.setXRequestId(DUMMY_UUID);
            record.setServiceType(SERVICE_TYPE_WSDL);

            record.setSucceeded(true);

            records.add(record);
        }

        return records;
    }

    private static String getDummyStr(int length) {
        return Strings.repeat("X", length);
    }

    private static Long getDummyLong(int length) {
        return Long.parseLong(Strings.repeat("1", length));
    }

    private static Integer getDummyInteger(int length) {
        return Integer.parseInt(Strings.repeat("1", length));
    }
}
