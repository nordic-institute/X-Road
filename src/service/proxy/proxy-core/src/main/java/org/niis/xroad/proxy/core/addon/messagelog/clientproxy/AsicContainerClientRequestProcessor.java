/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.messagelog.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.messagelog.MessageRecordEncryption;
import org.niis.xroad.common.messagelog.archive.EncryptionConfig;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;
import static org.niis.xroad.proxy.core.clientproxy.AbstractClientProxyHandler.getIsAuthenticationData;
import static org.niis.xroad.proxy.core.util.MetadataRequests.ASIC;
import static org.niis.xroad.proxy.core.util.MetadataRequests.VERIFICATIONCONF;

@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public class AsicContainerClientRequestProcessor extends MessageProcessorBase {

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

    private static final String MISSING_TIMESTAMP_FAULT_MESSAGE = "Message signature has not been timestamped yet!";

    private static final String TIMESTAMPING_FAILED_FAULT_MESSAGE = "Could not create missing timestamp!";

    private static final String CONTENT_DISPOSITION_FILENAME_PREFIX = "attachment; filename=\"";


    private final String target;
    private final EncryptionConfigProvider encryptionConfigProvider;
    private final ConfClientRpcClient confClientRpcClient;
    private final MessageRecordEncryption messageRecordEncryption;

    public AsicContainerClientRequestProcessor(CommonBeanProxy commonBeanProxy, ConfClientRpcClient confClientRpcClient,
                                               EncryptionConfigProvider encryptionConfigProvider,
                                               MessageRecordEncryption messageRecordEncryption,
                                               String target, RequestWrapper request, ResponseWrapper response) {
        super(commonBeanProxy, request, response, null);
        this.target = target;
        this.encryptionConfigProvider = encryptionConfigProvider;
        this.confClientRpcClient = confClientRpcClient;
        this.messageRecordEncryption = messageRecordEncryption;
    }

    public boolean canProcess() {
        return switch (target) {
            case ASIC, VERIFICATIONCONF -> true;
            default -> false;
        };
    }

    @Override
    public void process() {
        try {
            switch (target) {
                case ASIC -> handleAsicRequest();
                case VERIFICATIONCONF -> handleVerificationConfRequest();
                default -> {
                }
            }
        } catch (CodedExceptionWithHttpStatus ex) {
            throw ex;
        } catch (CodedException ex) {
            log.error("ERROR:", ex);
            throw new CodedExceptionWithHttpStatus(INTERNAL_SERVER_ERROR_500, ex);
        } catch (Exception ex) {
            log.error("ERROR:", ex);
            throw new CodedExceptionWithHttpStatus(INTERNAL_SERVER_ERROR_500,
                    X_INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void handleVerificationConfRequest() throws IOException {
        jResponse.setContentType(MimeTypes.ZIP);
        jResponse.putHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=\"verificationconf.zip\"");
        try (OutputStream out = jResponse.getOutputStream()) {
            out.write(confClientRpcClient.getVerificationConfZip());
        }
    }

    private void handleAsicRequest() throws Exception {
        ClientId clientId = getClientIdFromRequest();

        verifyClientAuthentication(clientId);

        handleAsicRequest(clientId);
    }

    private void verifyClientAuthentication(ClientId clientId) {
        log.trace("verifyClientAuthentication({})", clientId);
        try {
            verifyClientAuthentication(clientId, getIsAuthenticationData(jRequest, commonBeanProxy.getProxyProperties().logClientCert()));
        } catch (CodedException ex) {
            throw new CodedExceptionWithHttpStatus(UNAUTHORIZED_401, ex);
        }
    }

    private void handleAsicRequest(ClientId clientId) throws Exception {
        String queryId = getParameter(PARAM_QUERY_ID, false);
        AsicContainerNameGenerator nameGen = new AsicContainerNameGenerator();
        boolean requestOnly = hasParameter(PARAM_REQUEST_ONLY);
        boolean responseOnly = hasParameter(PARAM_RESPONSE_ONLY);
        if (requestOnly && responseOnly) {
            throw new CodedExceptionWithHttpStatus(BAD_REQUEST_400, ErrorCodes.X_BAD_REQUEST,
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
            throw new CodedExceptionWithHttpStatus(BAD_REQUEST_400, ErrorCodes.X_BAD_REQUEST,
                    MISSING_CONSTRAINT_FAULT_MESSAGE);
        }
    }

    private void ensureTimestamped(ClientId id, String queryId, Boolean response, boolean force) throws Exception {
        final List<MessageRecord> records = commonBeanProxy.getLogRecordManager().getByQueryId(queryId, id, response, Function.identity());

        if (records.isEmpty()) {
            throw new CodedExceptionWithHttpStatus(NOT_FOUND_404, ErrorCodes.X_NOT_FOUND,
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

    private boolean hasParameter(String param) throws Exception {
        return jRequest.getParametersMap().containsKey(param);
    }

    private void writeContainers(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
                                 Boolean response) throws IOException {

        if (encryptionConfigProvider.isEncryptionEnabled()) {
            writeEncryptedContainers(clientId, queryId, nameGen, response);
        } else {
            final String filename = AsicUtils.escapeString(queryId)
                    + (response == null ? "" : (response ? "-response" : "-request")) + ".zip";
            final CheckedSupplier<OutputStream> supplier = () -> {
                jResponse.setContentType(MimeTypes.ZIP);
                jResponse.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");
                return jResponse.getOutputStream();
            };

            writeContainers(clientId, queryId, nameGen, response, supplier);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws IOException;
    }

    private void writeEncryptedContainers(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
                                          Boolean response) throws IOException {

        final String filename = AsicUtils.escapeString(queryId)
                + (response == null ? "" : (response ? "-response" : "-request")) + ".zip.gpg";

        final Path tempFile = Files.createTempFile(Paths.get(commonBeanProxy.getCommonProperties().tempFilesPath()), "asic", null);

        try {
            final EncryptionConfig encryptionConfig =
                    encryptionConfigProvider.forClientId(clientId);
            final CheckedSupplier<OutputStream> supplier = () -> {
                jResponse.setContentType(MimeTypes.BINARY);
                jResponse.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");
                return encryptionConfig.createEncryptionStream(tempFile,
                        commonBeanProxy.getCommonProperties().tempFilesPath());
            };

            writeContainers(clientId, queryId, nameGen, response, supplier);

            try (InputStream is = Files.newInputStream(tempFile); var out = jResponse.getOutputStream()) {
                IOUtils.copyLarge(is, out);
            }

        } finally {
            Files.deleteIfExists(tempFile);
        }

    }

    private void writeContainers(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
                                 Boolean response, CheckedSupplier<OutputStream> outputSupplier) {

        commonBeanProxy.getLogRecordManager().getByQueryId(queryId, clientId, response, records -> {
            if (records.isEmpty()) {
                throw new CodedExceptionWithHttpStatus(NOT_FOUND_404, ErrorCodes.X_NOT_FOUND,
                        DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
            }
            try (OutputStream os = outputSupplier.get(); ZipOutputStream zos = new ZipOutputStream(os)) {
                zos.setLevel(0);
                for (var messageRecord : records) {
                    if (messageRecord.getTimestampRecord() == null) {
                        // Only happens if there are matching messages that are sent after
                        // the ensureTimestamped check was made. Ignore to emulate the previous behavior.
                        continue;
                    }
                    messageRecordEncryption.prepareDecryption(messageRecord);
                    final ZipEntry entry = new ZipEntry(
                            nameGen.getArchiveFilename(queryId, messageRecord.isResponse(), messageRecord.getId()));
                    entry.setLastModifiedTime(FileTime.from(messageRecord.getTime(), TimeUnit.MILLISECONDS));
                    zos.putNextEntry(entry);

                    try (EntryStream es = new EntryStream(zos)) {
                        messageRecord.toAsicContainer().write(es);
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
                                    boolean response) {

        commonBeanProxy.getLogRecordManager().getByQueryIdUnique(queryId, clientId, response, record -> {
            try {
                if (record == null) {
                    throw new CodedExceptionWithHttpStatus(NOT_FOUND_404, ErrorCodes.X_NOT_FOUND,
                            DOCUMENTS_NOT_FOUND_FAULT_MESSAGE);
                }
                if (record.getTimestampRecord() == null) {
                    throw new CodedException(X_INTERNAL_ERROR, MISSING_TIMESTAMP_FAULT_MESSAGE);
                }
                messageRecordEncryption.prepareDecryption(record);
                final AsicContainer asicContainer = record.toAsicContainer();

                String filename = nameGen.getArchiveFilename(queryId, response, record.getId());
                if (encryptionConfigProvider.isEncryptionEnabled()) {
                    filename += ".gpg";
                    jResponse.setContentType(MimeTypes.BINARY);
                } else {
                    jResponse.setContentType(MimeTypes.ASIC_ZIP);
                }
                jResponse.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");

                if (encryptionConfigProvider.isEncryptionEnabled()) {
                    final var encryptionConfig = encryptionConfigProvider.forClientId(clientId);
                    encryptContainer(encryptionConfig, asicContainer);
                } else {
                    asicContainer.write(jResponse.getOutputStream());
                }

            } catch (CodedException ce) {
                throw ce;
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }
            return null;
        });
    }

    private void encryptContainer(EncryptionConfig encryptionConfig, AsicContainer asicContainer) throws IOException {
        final Path tempFile = Files.createTempFile(
                Paths.get(commonBeanProxy.getCommonProperties().tempFilesPath()), "asic", null);
        try {
            try (OutputStream os = encryptionConfig.createEncryptionStream(tempFile,
                    commonBeanProxy.getCommonProperties().tempFilesPath())) {
                asicContainer.write(os);
            }
            try (InputStream is = Files.newInputStream(tempFile); var out = jResponse.getOutputStream()) {
                IOUtils.copyLarge(is, out);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private ClientId.Conf getClientIdFromRequest() throws Exception {
        String instanceIdentifier = getParameter(PARAM_INSTANCE_IDENTIFIER, false);
        String memberClass = getParameter(PARAM_MEMBER_CLASS, false);
        String memberCode = getParameter(PARAM_MEMBER_CODE, false);
        String subsystemCode = getParameter(PARAM_SUBSYSTEM_CODE, true);

        return ClientId.Conf.create(instanceIdentifier, memberClass, memberCode, subsystemCode);
    }

    private String getParameter(String param, boolean optional) throws Exception {
        String paramValue = jRequest.getParameter(param);

        if (paramValue == null && !optional) {
            throw new CodedExceptionWithHttpStatus(BAD_REQUEST_400, ErrorCodes.X_BAD_REQUEST,
                    String.format(MISSING_PARAMETER_FAULT_MESSAGE, param));
        }

        return paramValue;
    }

}
