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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory;
import ee.ria.xroad.common.conf.globalconf.ConfigurationPartMetadata;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.messagelog.LogRecordManager;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.util.MessageProcessorBase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.metadata.MetadataRequests.ASIC;
import static ee.ria.xroad.common.metadata.MetadataRequests.VERIFICATIONCONF;
import static ee.ria.xroad.proxy.clientproxy.AbstractClientProxyHandler.getIsAuthenticationData;

@Slf4j
class AsicContainerClientRequestProcessor extends MessageProcessorBase {

    private static final int RANDOM_LENGTH = 10;
    private static final int MAX_RANDOM_GEN_ATTEMPTS = 1000;

    static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";
    static final String PARAM_MEMBER_CLASS = "memberClass";
    static final String PARAM_MEMBER_CODE = "memberCode";
    static final String PARAM_SUBSYSTEM_CODE = "subsystemCode";
    static final String PARAM_QUERY_ID = "queryId";
    static final String PARAM_REQUEST_ONLY = "requestOnly";
    static final String PARAM_RESPONSE_ONLY = "responseOnly";
    static final String PARAM_UNIQUE = "unique";
    static final String PARAM_FORCE = "force";

    private static final String INVALID_PARAM_COMBINATION_FAULT_MESSAGE =
            "Parameters \"" + PARAM_REQUEST_ONLY + "\" and \""
                    + PARAM_RESPONSE_ONLY + "\" cannot be used at the same time.";

    private static final String MISSING_CONSTRAINT_FAULT_MESSAGE =
            "Parameter \"" + PARAM_UNIQUE + "\" not applicable without \""
                    + PARAM_REQUEST_ONLY + "\" or \"" + PARAM_RESPONSE_ONLY + "\".";

    private static final String MISSING_PARAMETER_FAULT_MESSAGE =
            "Parameter \"%s\"  must be specified.";

    private static final String DOCUMENTS_NOT_FOUND_FAULT_MESSAGE =
            "No signed documents found";

    private static final String MISSING_TIMESTAMPS_FAULT_MESSAGE =
            "Some message signatures have not been timestamped yet!";

    private static final String MISSING_TIMESTAMP_FAULT_MESSAGE =
            "Message signature has not been timestamped yet!";

    private static final String TIMESTAMPING_FAILED_FAULT_MESSAGE =
            "Could not create missing timestamp!";

    private final String target;

    private final LogRecordManager logRecordManager = new LogRecordManager();

    AsicContainerClientRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response) {
        super(request, response, null);

        this.target = target;
    }

    public boolean canProcess() {
        switch (target) {
        case ASIC:
        case VERIFICATIONCONF:
            return true;
        default:
            return false;
        }
    }

    @Override
    public void process() throws Exception {
        try {
            switch (target) {
            case ASIC:
                handleAsicRequest();
                return;
            case VERIFICATIONCONF:
                handleVerificationConfRequest();
                return;
            default:
                break;
            }
        } catch (CodedExceptionWithHttpStatus ex) {
            throw ex;
        } catch (CodedException ex) {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        } catch (Exception ex) {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ErrorCodes.X_INTERNAL_ERROR,
                    ex.getMessage());
        }
    }

    private void handleVerificationConfRequest() throws Exception {
        // GlobalConf.verifyValidity() is not necessary here.

        ConfigurationDirectory confDir = new ConfigurationDirectory(
                SystemProperties.getConfigurationPath());

        servletResponse.setContentType(MimeTypes.ZIP);
        servletResponse.setHeader("Content-Disposition",
                "filename=\"verificationconf.zip\"");
        try (VerificationConfWriter writer = new VerificationConfWriter(
                confDir.getInstanceIdentifier(),
                servletResponse.getOutputStream())) {
            confDir.eachFile(writer);
        }
    }

    private void handleAsicRequest() throws Exception {
        ClientId clientId = getClientIdFromRequest();

        verifyClientAuthentication(clientId);

        handleAsicRequest(clientId);
    }

    private void verifyClientAuthentication(ClientId clientId)
            throws Exception {
        log.trace("verifyClientAuthentication({})", clientId);
        try {
            IsAuthentication.verifyClientAuthentication(clientId,
                    getIsAuthenticationData(servletRequest));
        } catch (CodedException ex) {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_UNAUTHORIZED, ex);
        }
    }

    private void handleAsicRequest(ClientId clientId) throws Exception {
        String queryId = getParameter(PARAM_QUERY_ID, false);

        AsicContainerNameGenerator nameGen = new AsicContainerNameGenerator(
                AsicContainerClientRequestProcessor::getRandomAlphanumeric,
                MAX_RANDOM_GEN_ATTEMPTS);

        boolean requestOnly = hasParameter(PARAM_REQUEST_ONLY);
        boolean responseOnly = hasParameter(PARAM_RESPONSE_ONLY);

        if (requestOnly && responseOnly) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_BAD_REQUEST, ErrorCodes.X_BAD_REQUEST,
                    INVALID_PARAM_COMBINATION_FAULT_MESSAGE);
        }

        boolean unique = hasParameter(PARAM_UNIQUE);

        if (requestOnly) {
            if (unique) {
                writeAsicContainer(clientId, queryId, nameGen, false);
            } else {
                writeRequestContainers(clientId, queryId, nameGen);
            }
        } else if (responseOnly) {
            if (unique) {
                writeAsicContainer(clientId, queryId, nameGen, true);
            } else {
                writeResponseContainers(clientId, queryId, nameGen);
            }
        } else {
            if (!unique) {
                writeAllContainers(clientId, queryId, nameGen);
            } else {
                throw new CodedExceptionWithHttpStatus(
                        HttpServletResponse.SC_BAD_REQUEST,
                        ErrorCodes.X_BAD_REQUEST,
                        MISSING_CONSTRAINT_FAULT_MESSAGE);
            }
        }
    }

    private boolean hasParameter(String param) {
        return servletRequest.getParameterMap().containsKey(param);
    }

    private void writeAllContainers(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen) throws Exception {
        String filename = AsicUtils.escapeString(queryId);
        List<MessageRecord> requests =
                timestampedRecords(clientId, queryId, false);
        List<MessageRecord> responses =
                timestampedRecords(clientId, queryId, true);

        if (!requests.isEmpty() || !responses.isEmpty()) {
            try (ZipOutputStream zos = startZipResponse(filename)) {
                writeContainers(requests, queryId, nameGen, zos, "request");
                writeContainers(responses, queryId, nameGen, zos, "response");
            }
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }
    }

    private void writeRequestContainers(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen) throws Exception {
        String filename = AsicUtils.escapeString(queryId) + "-request";
        List<MessageRecord> records =
                timestampedRecords(clientId, queryId, false);
        if (!records.isEmpty()) {
            try (ZipOutputStream zos = startZipResponse(filename)) {
                writeContainers(records, queryId, nameGen, zos, "request");
            }
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }
    }

    private void writeResponseContainers(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen) throws Exception {
        String filename = AsicUtils.escapeString(queryId) + "-response";
        List<MessageRecord> records =
                timestampedRecords(clientId, queryId, true);
        if (!records.isEmpty()) {
            try (ZipOutputStream zos = startZipResponse(filename)) {
                writeContainers(records, queryId, nameGen, zos, "response");
            }
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }
    }

    private List<MessageRecord> timestampedRecords(ClientId clientId,
            String queryId, boolean response) throws Exception {
        List<MessageRecord> allRecords =
                logRecordManager.getByQueryId(queryId, clientId, response);

        List<MessageRecord> timestampedRecords;
        if (hasParameter(PARAM_FORCE)) {
            timestampedRecords = allRecords.stream()
                    .map(this::ensureRecordTimestamped)
                    .collect(Collectors.toList());
        } else {
            verifyAllRecordsTimestamped(allRecords);
            timestampedRecords = allRecords;
        }
        return timestampedRecords;
    }

    private void verifyAllRecordsTimestamped(List<MessageRecord> allRecords)
            throws Exception {
        int allCount = allRecords.size();
        List<MessageRecord> timestampedRecords = allRecords.stream()
                .filter(r -> r.getTimestampRecord() != null)
                .collect(Collectors.toList());
        int timestampedCount = timestampedRecords.size();

        if (allCount != timestampedCount) {
            throw new Exception(MISSING_TIMESTAMPS_FAULT_MESSAGE);
        }
    }

    private void writeContainers(List<MessageRecord> requests, String queryId,
            AsicContainerNameGenerator nameGen, ZipOutputStream zos,
            String type) throws Exception {

        for (MessageRecord record : requests) {
            String filename = nameGen.getArchiveFilename(queryId, type);
            zos.putNextEntry(new ZipEntry(filename));
            zos.write(record.toAsicContainer().getBytes());
            zos.closeEntry();
        }
    }

    private void writeAsicContainer(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen, boolean response)
                    throws Exception {
        MessageRecord request = getTimestampedRecord(clientId, queryId, response);
        String filename = nameGen.getArchiveFilename(queryId,
                response ? "response" : "request");

        servletResponse.setContentType(MimeTypes.ASIC_ZIP);
        servletResponse.setHeader("Content-Disposition", "filename=\""
                + filename + "\"");

        servletResponse.getOutputStream().write(
                request.toAsicContainer().getBytes());
    }

    @SneakyThrows
    private MessageRecord getTimestampedRecord(ClientId clientId,
            String queryId, boolean response) {
        MessageRecord record = logRecordManager
                .getByQueryIdUnique(queryId, clientId, response);
        if (record != null) {
            return ensureRecordTimestamped(record);
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }
    }

    @SneakyThrows
    private MessageRecord ensureRecordTimestamped(MessageRecord record) {
        if (record.getTimestampRecord() == null) {
            if (hasParameter(PARAM_FORCE)) {
                TimestampRecord timestamp = MessageLog.timestamp(record);
                if (timestamp == null) {
                    throw new Exception(TIMESTAMPING_FAILED_FAULT_MESSAGE);
                }
                return (MessageRecord) logRecordManager.get(record.getId());
            } else {
                throw new Exception(MISSING_TIMESTAMP_FAULT_MESSAGE);
            }
        }
        return record;
    }

    private ZipOutputStream startZipResponse(String filename) throws IOException {
        servletResponse.setContentType(MimeTypes.ZIP);
        servletResponse.setHeader("Content-Disposition", "filename=\""
                + filename + ".zip\"");

        return new ZipOutputStream(servletResponse.getOutputStream());
    }

    private ClientId getClientIdFromRequest() throws Exception {
        String instanceIdentifier =
                getParameter(PARAM_INSTANCE_IDENTIFIER, false);
        String memberClass = getParameter(PARAM_MEMBER_CLASS, false);
        String memberCode = getParameter(PARAM_MEMBER_CODE, false);
        String subsystemCode = getParameter(PARAM_SUBSYSTEM_CODE, true);

        return ClientId.create(instanceIdentifier, memberClass, memberCode,
                subsystemCode);
    }

    private String getParameter(String param, boolean optional) {
        String paramValue = servletRequest.getParameter(param);
        if (paramValue == null && !optional) {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorCodes.X_BAD_REQUEST,
                    String.format(MISSING_PARAMETER_FAULT_MESSAGE, param));
        }
        return paramValue;
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        return null; // nothing to return
    }

    private static String getRandomAlphanumeric() {
        return RandomStringUtils.randomAlphanumeric(RANDOM_LENGTH);
    }

    private static class VerificationConfWriter implements ConfigurationDirectory.FileConsumer, Closeable {

        private static final String PREFIX = "verificationconf/";

        private final String instanceIdentifier;
        private final ZipOutputStream zos;

        VerificationConfWriter(String instanceIdentifier,
                OutputStream out) {
            this.instanceIdentifier = instanceIdentifier;
            zos = new ZipOutputStream(out);
        }

        @Override
        public void consume(ConfigurationPartMetadata metadata,
                InputStream contents) throws Exception {
            if (metadata.getContentIdentifier()
                    .equals(SharedParameters.CONTENT_ID_SHARED_PARAMETERS)) {
                zos.putNextEntry(new ZipEntry(buildPath(metadata)));
                IOUtils.copy(contents, zos);
                zos.closeEntry();
            }
        }

        private String buildPath(ConfigurationPartMetadata metadata) {
            return PREFIX + metadata.getInstanceIdentifier() + "/"
                    + SharedParameters.FILE_NAME_SHARED_PARAMETERS;
        }

        @Override
        public void close() throws IOException {
            zos.putNextEntry(new ZipEntry(PREFIX
                    + ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE));
            zos.write(instanceIdentifier.getBytes());
            zos.closeEntry();

            zos.close();
        }
    }

}
