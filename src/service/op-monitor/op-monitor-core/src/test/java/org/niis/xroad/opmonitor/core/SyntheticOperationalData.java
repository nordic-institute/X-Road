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
package org.niis.xroad.opmonitor.core;

import org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Seeded, high-entropy operational data records. Text fields are random, so compression cannot shrink them
 * the way it shrinks repeated text.
 */
final class SyntheticOperationalData {
    enum Variant {
        /** Every field filled and varied per record, short texts, about 3% failures. */
        TYPICAL,
        /** Long unique REST paths and user texts, about 20% failures with long unique fault strings. */
        PESSIMISTIC
    }

    private static final long SEED = 20_261_011L;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String PRINTABLE = IntStream.rangeClosed(' ', '~')
            .filter(c -> c != '"' && c != '\\')
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    private static final List<String> SERVICE_CODES = List.of("getRandom", "getOrganizationList", "getPersonData", "searchRegistry",
            "submitDocument", "listPermits", "getVehicleInfo", "validateCertificate");
    private static final List<String> REST_METHODS = List.of("GET", "POST", "PUT", "DELETE");
    private static final int MEMBERS = 40;
    private static final int SUBSYSTEMS = 9;
    private static final long FIRST_REQUEST_IN_TS = 1_700_000_000_000L;
    private static final int TYPICAL_FAILURE_PERCENT = 3;
    private static final int PESSIMISTIC_FAILURE_PERCENT = 20;
    private static final int PESSIMISTIC_PATH_MIN = 100;
    private static final int PESSIMISTIC_PATH_MAX = 255;
    private static final int PESSIMISTIC_USER_TEXT_MIN = 8;
    private static final int PESSIMISTIC_USER_TEXT_MAX = 32;
    private static final int PESSIMISTIC_FAULT_STRING_MIN = 300;
    private static final int PESSIMISTIC_FAULT_STRING_MAX = 2048;
    private static final int TYPICAL_FAULT_STRING_MIN = 60;
    private static final int TYPICAL_FAULT_STRING_MAX = 120;

    private SyntheticOperationalData() {
    }

    static List<OperationalDataRecord> records(Variant variant, int count) {
        var random = new Random(SEED);
        var records = new ArrayList<OperationalDataRecord>(count);
        for (int i = 0; i < count; i++) {
            records.add(record(variant, random, i));
        }
        return records;
    }

    private static OperationalDataRecord record(Variant variant, Random random, int index) {
        boolean pessimistic = variant == Variant.PESSIMISTIC;
        var record = new OperationalDataRecord();
        int clientMember = random.nextInt(MEMBERS);
        int serviceMember = random.nextInt(MEMBERS);
        var serviceCode = SERVICE_CODES.get(random.nextInt(SERVICE_CODES.size()));

        record.setSecurityServerInternalIp("10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(256));
        record.setSecurityServerType((index % 2 == 0 ? SecurityServerType.CLIENT : SecurityServerType.PRODUCER).getTypeString());
        timestamps(record, random, index);

        record.setClientXRoadInstance("DEV");
        record.setClientMemberClass("COM");
        record.setClientMemberCode(memberCode(clientMember));
        record.setClientSubsystemCode("Subsystem" + random.nextInt(SUBSYSTEMS));
        record.setServiceXRoadInstance("DEV");
        record.setServiceMemberClass("COM");
        record.setServiceMemberCode(memberCode(serviceMember));
        record.setServiceSubsystemCode("Subsystem" + random.nextInt(SUBSYSTEMS));
        record.setServiceCode(serviceCode);
        record.setServiceVersion("v" + (1 + random.nextInt(2)));
        record.setServiceType(random.nextBoolean() ? "REST" : "WSDL");
        record.setRepresentedPartyClass("COM");
        record.setRepresentedPartyCode(memberCode(random.nextInt(MEMBERS)));

        record.setRestMethod(REST_METHODS.get(random.nextInt(REST_METHODS.size())));
        record.setRestPath(pessimistic
                ? restPath(random, PESSIMISTIC_PATH_MIN, PESSIMISTIC_PATH_MAX)
                : "/r1/DEV/COM/" + memberCode(serviceMember) + "/" + serviceCode + "/" + text(random, ALPHANUMERIC, 6, 12));
        record.setXRoadVersion("8.0.0");
        record.setMessageId(uuid(random));
        record.setMessageUserId(pessimistic
                ? text(random, ALPHANUMERIC, PESSIMISTIC_USER_TEXT_MIN, PESSIMISTIC_USER_TEXT_MAX)
                : "EE" + (10_000_000_000L + random.nextLong(80_000_000_000L)));
        record.setMessageIssue(pessimistic
                ? text(random, ALPHANUMERIC, PESSIMISTIC_USER_TEXT_MIN, PESSIMISTIC_USER_TEXT_MAX)
                : "case-" + random.nextInt(1_000_000));
        record.setMessageProtocolVersion("4.0");
        record.setClientSecurityServerAddress("ss" + clientMember + ".example.org");
        record.setServiceSecurityServerAddress("ss" + serviceMember + ".example.org");
        record.setXRequestId(uuid(random));

        sizes(record, random);
        outcome(record, random, pessimistic);
        return record;
    }

    private static void timestamps(OperationalDataRecord record, Random random, int index) {
        long requestIn = FIRST_REQUEST_IN_TS + index * 37L + random.nextInt(30);
        long requestOut = requestIn + 1 + random.nextInt(20);
        long responseIn = requestOut + 5 + random.nextInt(400);
        record.setRequestInTs(requestIn);
        record.setRequestOutTs(requestOut);
        record.setResponseInTs(responseIn);
        record.setResponseOutTs(responseIn + 1 + random.nextInt(20));
    }

    private static void sizes(OperationalDataRecord record, Random random) {
        long requestSize = 200 + random.nextInt(20_000);
        long responseSize = 200 + random.nextInt(200_000);
        record.setRequestSize(requestSize);
        record.setResponseSize(responseSize);
        record.setRequestMimeSize(requestSize + random.nextInt(2_000));
        record.setResponseMimeSize(responseSize + random.nextInt(2_000));
        record.setRequestAttachmentCount(random.nextInt(3));
        record.setResponseAttachmentCount(random.nextInt(3));
    }

    private static void outcome(OperationalDataRecord record, Random random, boolean pessimistic) {
        int failurePercent = pessimistic ? PESSIMISTIC_FAILURE_PERCENT : TYPICAL_FAILURE_PERCENT;
        boolean succeeded = random.nextInt(100) >= failurePercent;
        record.setSucceeded(succeeded);
        if (succeeded) {
            record.setStatusCode(200 + random.nextInt(2));
            return;
        }
        record.setStatusCode(500);
        record.setFaultCode("Server.ServerProxy.ServiceFailed." + text(random, ALPHANUMERIC, 12, 24));
        record.setFaultString(pessimistic
                ? text(random, PRINTABLE, PESSIMISTIC_FAULT_STRING_MIN, PESSIMISTIC_FAULT_STRING_MAX)
                : text(random, PRINTABLE, TYPICAL_FAULT_STRING_MIN, TYPICAL_FAULT_STRING_MAX));
    }

    private static String memberCode(int member) {
        return "%08d".formatted(10_000_000 + member * 7_919);
    }

    private static String restPath(Random random, int minLength, int maxLength) {
        var path = new StringBuilder();
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        while (path.length() < length) {
            path.append('/').append(text(random, ALPHANUMERIC, 4, 16));
        }
        path.setLength(length);
        return path.toString();
    }

    private static String uuid(Random random) {
        return new UUID(random.nextLong(), random.nextLong()).toString();
    }

    private static String text(Random random, String alphabet, int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        var text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }
}
