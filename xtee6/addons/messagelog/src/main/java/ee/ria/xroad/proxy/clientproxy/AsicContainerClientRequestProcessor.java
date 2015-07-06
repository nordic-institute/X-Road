package ee.ria.xroad.proxy.clientproxy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;

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
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.messagelog.LogRecordManager;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import static ee.ria.xroad.common.metadata.MetadataRequests.ASIC;
import static ee.ria.xroad.common.metadata.MetadataRequests.VERIFICATIONCONF;
import static ee.ria.xroad.proxy.clientproxy.AbstractClientProxyHandler.getClientCert;

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
        } catch (Throwable ex) {
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
                    getClientCert(servletRequest));
        } catch (CodedException ex) {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_UNAUTHORIZED, ex);
        }
    }

    private void handleAsicRequest(ClientId clientId) throws Exception, IOException {
        String queryId = getParameter(PARAM_QUERY_ID, false);

        AsicContainerNameGenerator nameGen = new AsicContainerNameGenerator(
                AsicContainerClientRequestProcessor::getRandomAlphanumeric,
                MAX_RANDOM_GEN_ATTEMPTS);

        boolean requestOnly =
                servletRequest.getParameterMap().containsKey(PARAM_REQUEST_ONLY);
        boolean responseOnly =
                servletRequest.getParameterMap().containsKey(PARAM_RESPONSE_ONLY);
        boolean unique =
                servletRequest.getParameterMap().containsKey(PARAM_UNIQUE);

        if (!requestOnly && !responseOnly) {
            if (!unique) {
                writeAllContainers(clientId, queryId, nameGen);
            } else {
                throw new CodedExceptionWithHttpStatus(
                        HttpServletResponse.SC_BAD_REQUEST,
                        ErrorCodes.X_BAD_REQUEST,
                        MISSING_CONSTRAINT_FAULT_MESSAGE);
            }
        } else if (requestOnly && !responseOnly) {
            if (unique) {
                writeAsicContainer(clientId, queryId, nameGen, false);
            } else {
                writeRequestContainers(clientId, queryId, nameGen);
            }
        } else if (responseOnly && !requestOnly) {
            if (unique) {
                writeAsicContainer(clientId, queryId, nameGen, true);
            } else {
                writeResponseContainers(clientId, queryId, nameGen);
            }
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorCodes.X_BAD_REQUEST,
                    INVALID_PARAM_COMBINATION_FAULT_MESSAGE);
        }
    }

    private void writeAllContainers(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen) throws IOException, Exception {
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
            AsicContainerNameGenerator nameGen) throws IOException, Exception {
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
            AsicContainerNameGenerator nameGen) throws IOException, Exception {
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
        int allCount = allRecords.size();

        List<MessageRecord> timestampedRecords = allRecords.stream()
                .filter(r -> r.getTimestampRecord() != null)
                .collect(Collectors.toList());
        int timestampedCount = timestampedRecords.size();

        if (allCount != timestampedCount) {
            throw new Exception(MISSING_TIMESTAMPS_FAULT_MESSAGE);
        }
        return timestampedRecords;
    }

    private void writeContainers(List<MessageRecord> requests, String queryId,
            AsicContainerNameGenerator nameGen, ZipOutputStream zos,
            String type) throws Exception, IOException {

        for (MessageRecord record : requests) {
            String filename = nameGen.getArchiveFilename(queryId, type);
            zos.putNextEntry(new ZipEntry(filename));
            zos.write(record.toAsicContainer().getBytes());
            zos.closeEntry();
        }
    }

    private void writeAsicContainer(ClientId clientId, String queryId,
            AsicContainerNameGenerator nameGen, boolean response)
                    throws Exception, IOException {
        MessageRecord request =
                logRecordManager.getByQueryIdUnique(queryId, clientId, response);
        if (request != null) {
            if (request.getTimestampRecord() == null) {
                throw new Exception(MISSING_TIMESTAMP_FAULT_MESSAGE);
            }
            String filename = nameGen.getArchiveFilename(queryId,
                    response ? "response" : "request");

            servletResponse.setContentType(MimeTypes.ASIC_ZIP);
            servletResponse.setHeader("Content-Disposition", "filename=\""
                    + filename + "\"");

            servletResponse.getOutputStream().write(
                    request.toAsicContainer().getBytes());
        } else {
            throw new CodedExceptionWithHttpStatus(
                    HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }
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

        public VerificationConfWriter(String instanceIdentifier,
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
