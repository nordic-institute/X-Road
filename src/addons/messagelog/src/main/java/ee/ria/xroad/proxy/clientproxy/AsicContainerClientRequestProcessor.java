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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.conf.globalconf.ConfigurationConstants;
import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory;
import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectoryV2;
import ee.ria.xroad.common.conf.globalconf.ConfigurationPartMetadata;
import ee.ria.xroad.common.conf.globalconf.FileConsumer;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.messagelog.LogRecordManager;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.messagelog.MessageLogEncryption;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
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

    private static final String INVALID_PARAM_COMBINATION_FAULT_MESSAGE = "Parameters \"" + PARAM_REQUEST_ONLY
            + "\" and \"" + PARAM_RESPONSE_ONLY + "\" cannot be used at the same time.";

    private static final String MISSING_CONSTRAINT_FAULT_MESSAGE = "Parameter \"" + PARAM_UNIQUE
            + "\" not applicable without \"" + PARAM_REQUEST_ONLY + "\" or \"" + PARAM_RESPONSE_ONLY + "\".";

    private static final String MISSING_PARAMETER_FAULT_MESSAGE = "Parameter \"%s\"  must be specified.";

    private static final String DOCUMENTS_NOT_FOUND_FAULT_MESSAGE = "No signed documents found";

    private static final String MISSING_TIMESTAMPS_FAULT_MESSAGE =
            "Some message signatures have not been timestamped yet!";

    private static final String MISSING_TIMESTAMP_FAULT_MESSAGE = "Message signature has not been timestamped yet!";

    private static final String TIMESTAMPING_FAILED_FAULT_MESSAGE = "Could not create missing timestamp!";

    private final String target;

    AsicContainerClientRequestProcessor(String target, HttpServletRequest request, HttpServletResponse response) {
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
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        } catch (Exception ex) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    X_INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void handleVerificationConfRequest() throws Exception {
        // GlobalConf.verifyValidity() is not necessary here.

        ConfigurationDirectoryV2 confDir = new ConfigurationDirectoryV2(SystemProperties.getConfigurationPath());

        servletResponse.setContentType(MimeTypes.ZIP);
        servletResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=\"verificationconf.zip\"");
        try (VerificationConfWriter writer = new VerificationConfWriter(confDir.getInstanceIdentifier(),
                servletResponse.getOutputStream())) {
            confDir.eachFile(writer);
        }
    }

    private void handleAsicRequest() throws Exception {
        ClientId clientId = getClientIdFromRequest();

        verifyClientAuthentication(clientId);

        handleAsicRequest(clientId);
    }

    private void verifyClientAuthentication(ClientId clientId) throws Exception {
        log.trace("verifyClientAuthentication({})", clientId);
        try {
            IsAuthentication.verifyClientAuthentication(clientId, getIsAuthenticationData(servletRequest));
        } catch (CodedException ex) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_UNAUTHORIZED, ex);
        }
    }

    private void handleAsicRequest(ClientId clientId) throws Exception {
        String queryId = getParameter(PARAM_QUERY_ID, false);

        AsicContainerNameGenerator nameGen = new AsicContainerNameGenerator(
                AsicContainerClientRequestProcessor::getRandomAlphanumeric, MAX_RANDOM_GEN_ATTEMPTS);

        boolean requestOnly = hasParameter(PARAM_REQUEST_ONLY);
        boolean responseOnly = hasParameter(PARAM_RESPONSE_ONLY);
        if (requestOnly && responseOnly) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_BAD_REQUEST, ErrorCodes.X_BAD_REQUEST,
                    INVALID_PARAM_COMBINATION_FAULT_MESSAGE);
        }

        Boolean response = responseOnly ? Boolean.TRUE : (requestOnly ? Boolean.FALSE : null);
        boolean unique = hasParameter(PARAM_UNIQUE);

        ensureTimestamped(clientId, queryId, response, hasParameter(PARAM_FORCE));

        if (unique && response != null) {
            writeAsicContainer(clientId, queryId, nameGen, response);
        } else if (!unique) {
            writeContainers(clientId, queryId, nameGen, response);
        } else {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_BAD_REQUEST, ErrorCodes.X_BAD_REQUEST,
                    MISSING_CONSTRAINT_FAULT_MESSAGE);
        }
    }

    private void ensureTimestamped(ClientId id, String queryId, Boolean response, boolean force) throws Exception {
        final List<MessageRecord> records = LogRecordManager.getByQueryId(queryId, id, response, Function.identity());

        if (records.isEmpty()) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                    DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
        }

        for (MessageRecord record : records) {
            if (record.getTimestampRecord() == null) {
                if (force) {
                    if (MessageLog.timestamp(record) == null) {
                        throw new Exception(TIMESTAMPING_FAILED_FAULT_MESSAGE);
                    }
                } else {
                    throw new Exception(MISSING_TIMESTAMP_FAULT_MESSAGE);
                }
            }
        }
    }

    private boolean hasParameter(String param) {
        return servletRequest.getParameterMap().containsKey(param);
    }

    private void writeContainers(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
            Boolean response) throws Exception {

        final String filename = AsicUtils.escapeString(queryId)
                + (response == null ? "" : (response ? "-response" : "-request"));

        LogRecordManager.getByQueryId(queryId, clientId, response, records -> {
            if (records.isEmpty()) {
                throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                        DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
            }
            try (ZipOutputStream zos = startZipResponse(filename)) {
                final MessageLogEncryption messageLogEncryption = MessageLogEncryption.getInstance();
                zos.setLevel(0);
                for (MessageRecord record : records) {
                    if (record.getTimestampRecord() == null) {
                        // Only happens if there are matching messages that are sent after
                        // the ensureTimestamped check was made. Ignore to emulate the previous behavior.
                        continue;
                    }
                    messageLogEncryption.prepareDecryption(record);
                    String type = record.isResponse() ? AsicContainerNameGenerator.TYPE_RESPONSE
                            : AsicContainerNameGenerator.TYPE_REQUEST;
                    zos.putNextEntry(new ZipEntry(nameGen.getArchiveFilename(queryId, type)));

                    try (EntryStream es = new EntryStream(zos)) {
                        record.toAsicContainer().write(es);
                    }

                    zos.closeEntry();
                }
            } catch (CodedException ce) {
                throw ce;
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }
            return null;
        });
    }

    /**
     * It seems that ZipOutputStream#finish is broken and leaks native memory. Therefore, we need to
     * use ZipOutputStream#close and avoid closing the underlying stream; therefore this filter.
     */
    static class EntryStream extends FilterOutputStream {

        EntryStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() {
            //NOP
        }
    }

    private void writeAsicContainer(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
            boolean response) throws Exception {
        String filename = nameGen.getArchiveFilename(queryId,
                response ? AsicContainerNameGenerator.TYPE_RESPONSE : AsicContainerNameGenerator.TYPE_REQUEST);
        servletResponse.setContentType(MimeTypes.ASIC_ZIP);
        servletResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        LogRecordManager.getByQueryIdUnique(queryId, clientId, response, record -> {
            try {
                if (record == null) {
                    throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_NOT_FOUND, ErrorCodes.X_NOT_FOUND,
                            DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
                }
                if (record.getTimestampRecord() != null) {
                    throw new CodedException(X_INTERNAL_ERROR, MISSING_TIMESTAMP_FAULT_MESSAGE);
                }
                MessageLogEncryption.getInstance().prepareDecryption(record);
                record.toAsicContainer().write(servletResponse.getOutputStream());
            } catch (CodedException ce) {
                throw ce;
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }
            return null;
        });
    }

    private ZipOutputStream startZipResponse(String filename) throws IOException {
        servletResponse.setContentType(MimeTypes.ZIP);
        servletResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".zip\"");

        return new ZipOutputStream(servletResponse.getOutputStream());
    }

    private ClientId getClientIdFromRequest() {
        String instanceIdentifier = getParameter(PARAM_INSTANCE_IDENTIFIER, false);
        String memberClass = getParameter(PARAM_MEMBER_CLASS, false);
        String memberCode = getParameter(PARAM_MEMBER_CODE, false);
        String subsystemCode = getParameter(PARAM_SUBSYSTEM_CODE, true);

        return ClientId.create(instanceIdentifier, memberClass, memberCode, subsystemCode);
    }

    private String getParameter(String param, boolean optional) {
        String paramValue = servletRequest.getParameter(param);

        if (paramValue == null && !optional) {
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_BAD_REQUEST, ErrorCodes.X_BAD_REQUEST,
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

    private static class VerificationConfWriter implements FileConsumer, Closeable {

        private static final String PREFIX = "verificationconf/";

        private final String instanceIdentifier;
        private final ZipOutputStream zos;

        VerificationConfWriter(String instanceIdentifier, OutputStream out) {
            this.instanceIdentifier = instanceIdentifier;
            zos = new ZipOutputStream(out);
        }

        @Override
        public void consume(ConfigurationPartMetadata metadata, InputStream contents) throws Exception {
            if (metadata.getContentIdentifier().equals(ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS)) {
                zos.putNextEntry(new ZipEntry(buildPath(metadata)));
                IOUtils.copy(contents, zos);
                zos.closeEntry();
            }
        }

        private String buildPath(ConfigurationPartMetadata metadata) {
            return PREFIX + metadata.getInstanceIdentifier() + "/" + ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
        }

        @Override
        public void close() throws IOException {
            zos.putNextEntry(new ZipEntry(PREFIX + ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE));
            zos.write(instanceIdentifier.getBytes());
            zos.closeEntry();

            zos.close();
        }
    }
}
