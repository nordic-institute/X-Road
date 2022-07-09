/**
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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational monitoring buffer simulation class to measure heap size usage.
 */
@Slf4j
public final class OpMonitoringBufferMemoryUsage {
    private static final int DEFAULT_COUNT = 20000;
    private static final int DEFAULT_LONG_STRING_LENGTH = 50;
    private static final int DEFAULT_SHORT_LONG_STRING_LENGTH = 10;

    private static final long MILLIS = 1451606401000L; // 2016.01.01 00:00:01

    private static final int MB = 1024 * 1024;

    private static final Long DUMMY_LONG_10 = getDummyLong(10);
    private static final Integer DUMMY_INT_2 = getDummyInteger(2);

    private static final Options OPTIONS = getOptions();

    private OpMonitoringBufferMemoryUsage() {
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

        int count = cmd.getOptionValue("count") != null
                ? Integer.parseInt(cmd.getOptionValue("count"))
                : DEFAULT_COUNT;

        int shortStrLen = cmd.getOptionValue("short-string-length") != null
                ? Integer.parseInt(cmd.getOptionValue("short-string-length"))
                : DEFAULT_SHORT_LONG_STRING_LENGTH;

        int longStrLen = cmd.getOptionValue("long-string-length") != null
                ? Integer.parseInt(cmd.getOptionValue("long-string-length"))
                : DEFAULT_LONG_STRING_LENGTH;

        Runtime runtime = Runtime.getRuntime();

        long before = getUsedBytes(runtime);

        createBuffer(count, shortStrLen, longStrLen);

        long after = getUsedBytes(runtime);

        log.info("Records count {}, used heap {}MB", count,
                (after - before) / MB);
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

        Option count = new Option("c", "count", true,
                "records count (default: " + DEFAULT_COUNT + ")");
        count.setRequired(false);
        options.addOption(count);

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
        formatter.printHelp("OpMonitoringBufferMemoryUsage", OPTIONS);
    }

    private static long getUsedBytes(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static Map<Long, OpMonitoringData> createBuffer(int count, int shortStrLen, int longStrLen) {
        Map<Long, OpMonitoringData> buffer = new LinkedHashMap<>();
        OpMonitoringData record;

        for (long i = 0; i < count; ++i) {
            record = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, MILLIS);
            record.setRequestInTs(MILLIS);
            record.setRequestOutTs(MILLIS);
            record.setResponseInTs(MILLIS);
            record.setResponseOutTs(MILLIS, true);

            record.setClientId(createClient(shortStrLen, longStrLen));
            record.setServiceId(createService(shortStrLen, longStrLen));
            record.setRepresentedParty(createRepresentedParty(shortStrLen, longStrLen));

            record.setMessageId(getDummyStr(longStrLen));
            record.setMessageUserId(getDummyStr(longStrLen));
            record.setMessageIssue(getDummyStr(longStrLen));
            record.setMessageProtocolVersion("4.0");

            record.setClientSecurityServerAddress(getDummyStr(longStrLen));
            record.setServiceSecurityServerAddress(getDummyStr(longStrLen));

            record.setRequestSize(DUMMY_LONG_10);
            record.setRequestMimeSize(DUMMY_LONG_10);
            record.setRequestAttachmentCount(DUMMY_INT_2);
            record.setResponseSize(DUMMY_LONG_10);
            record.setResponseMimeSize(DUMMY_LONG_10);
            record.setResponseAttachmentCount(DUMMY_INT_2);

            record.setSucceeded(true);

            buffer.put(i, record);
        }

        return buffer;
    }

    private static ClientId.Conf createClient(int shortStrLen, int longStrLen) {
        return ClientId.Conf.create(getDummyStr(shortStrLen),
                getDummyStr(shortStrLen),
                getDummyStr(longStrLen),
                getDummyStr(longStrLen));
    }

    private static ServiceId.Conf createService(int shortStrLen, int longStrLen) {
        return ServiceId.Conf.create(getDummyStr(shortStrLen),
                getDummyStr(shortStrLen), getDummyStr(longStrLen),
                getDummyStr(longStrLen), getDummyStr(longStrLen),
                getDummyStr(shortStrLen));
    }

    private static RepresentedParty createRepresentedParty(
            int shortStrLen, int longStrLen) {
        return new RepresentedParty(getDummyStr(shortStrLen),
                getDummyStr(longStrLen));
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
