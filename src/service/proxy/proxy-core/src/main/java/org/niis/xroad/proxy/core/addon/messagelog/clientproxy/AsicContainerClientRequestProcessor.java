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

import ee.ria.xroad.common.HttpStatus;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.asic.AsicUtils;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeHttpException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.MessageRecord;
import org.niis.xroad.messagelog.MessageRecordEncryption;
import org.niis.xroad.messagelog.archive.EncryptionConfig;
import org.niis.xroad.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.proxy.core.addon.messagelog.LogRecordManager;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.util.AddonRequestContext;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;

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

import static org.niis.xroad.common.core.exception.ErrorCode.BAD_REQUEST;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NOT_FOUND;
import static org.niis.xroad.proxy.core.util.MetadataRequests.ASIC;
import static org.niis.xroad.proxy.core.util.MetadataRequests.VERIFICATIONCONF;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class AsicContainerClientRequestProcessor {

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

    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final ClientAuthenticationService clientAuthenticationService;
    private final EncryptionConfigProvider encryptionConfigProvider;
    private final ConfClientRpcClient confClientRpcClient;
    private final MessageRecordEncryption messageRecordEncryption;
    private final LogRecordManager logRecordManager;
    private final CommonProperties commonProperties;

    public boolean canProcess(String target) {
        return switch (target) {
            case ASIC, VERIFICATIONCONF -> true;
            default -> false;
        };
    }

    public void process(AddonRequestContext ctx) {
        globalConfProvider.verifyValidity();
        try {
            switch (ctx.target()) {
                case ASIC -> handleAsicRequest(ctx);
                case VERIFICATIONCONF -> handleVerificationConfRequest(ctx);
                default -> {
                }
            }
        } catch (XrdRuntimeHttpException ex) {
            throw ex;
        } catch (XrdRuntimeException ex) {
            log.error("ERROR:", ex);
            throw XrdRuntimeHttpException.from(ex)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        } catch (Exception ex) {
            log.error("ERROR:", ex);
            throw XrdRuntimeHttpException.builder(INTERNAL_ERROR)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .details(ex.getMessage())
                    .build();
        }
    }

    private void handleVerificationConfRequest(AddonRequestContext ctx) throws IOException {
        ctx.response().setContentType(MimeTypes.ZIP);
        ctx.response().putHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=\"verificationconf.zip\"");
        try (OutputStream out = ctx.response().getOutputStream()) {
            out.write(confClientRpcClient.getVerificationConfZip());
        }
    }

    private void handleAsicRequest(AddonRequestContext ctx) throws Exception {
        ClientId clientId = getClientIdFromRequest(ctx.request());

        verifyClientAuthentication(ctx.request(), clientId);

        handleAsicRequest(ctx, clientId);
    }

    private void verifyClientAuthentication(RequestWrapper request, ClientId clientId) {
        log.trace("verifyClientAuthentication({})", clientId);
        try {
            clientAuthenticationService.verifyClientAuthentication(clientId,
                    clientAuthenticationService.getIsAuthenticationData(request, proxyProperties.logClientCert()));
        } catch (XrdRuntimeException ex) {
            throw XrdRuntimeHttpException.from(ex)
                    .httpStatus(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    private void handleAsicRequest(AddonRequestContext ctx, ClientId clientId) throws Exception {
        String queryId = getParameter(ctx.request(), PARAM_QUERY_ID, false);
        AsicContainerNameGenerator nameGen = new AsicContainerNameGenerator();
        boolean requestOnly = hasParameter(ctx.request(), PARAM_REQUEST_ONLY);
        boolean responseOnly = hasParameter(ctx.request(), PARAM_RESPONSE_ONLY);
        if (requestOnly && responseOnly) {
            throw XrdRuntimeHttpException.builder(BAD_REQUEST)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .details(INVALID_PARAM_COMBINATION_FAULT_MESSAGE)
                    .build();
        }

        Boolean response = responseOnly ? Boolean.TRUE : (requestOnly ? Boolean.FALSE : null);
        boolean unique = hasParameter(ctx.request(), PARAM_UNIQUE);

        ensureTimestamped(clientId, queryId, response, hasParameter(ctx.request(), PARAM_FORCE));

        if (unique && response != null) {
            writeAsicContainer(ctx.response(), clientId, queryId, nameGen, response);
        } else if (!unique) {
            writeContainers(ctx.response(), clientId, queryId, nameGen, response);
        } else {
            throw XrdRuntimeHttpException.builder(BAD_REQUEST)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .details(MISSING_CONSTRAINT_FAULT_MESSAGE)
                    .build();
        }
    }

    private void ensureTimestamped(ClientId id, String queryId, Boolean response, boolean force) {
        final List<MessageRecord> records = logRecordManager.getByQueryId(queryId, id, response, Function.identity());

        if (records.isEmpty()) {
            throw XrdRuntimeHttpException.builder(NOT_FOUND)
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .details(DOCUMENTS_NOT_FOUND_FAULT_MESSAGE)
                    .build();
        }

        for (MessageRecord record : records) {
            if (record.getTimestampRecord() == null) {
                if (force) {
                    if (MessageLog.timestamp(record) == null) {
                        throw XrdRuntimeHttpException.builder(INTERNAL_ERROR)
                                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                                .details(TIMESTAMPING_FAILED_FAULT_MESSAGE)
                                .build();
                    }
                } else {
                    throw XrdRuntimeHttpException.builder(INTERNAL_ERROR)
                            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                            .details(MISSING_TIMESTAMP_FAULT_MESSAGE)
                            .build();
                }
            }
        }
    }

    private boolean hasParameter(RequestWrapper request, String param) throws Exception {
        return request.getParametersMap().containsKey(param);
    }

    private void writeContainers(ResponseWrapper response, ClientId clientId, String queryId,
                                 AsicContainerNameGenerator nameGen, Boolean isResponse) throws IOException {

        if (encryptionConfigProvider.isEncryptionEnabled()) {
            writeEncryptedContainers(response, clientId, queryId, nameGen, isResponse);
        } else {
            final String filename = AsicUtils.escapeString(queryId)
                    + (isResponse == null ? "" : (isResponse ? "-response" : "-request")) + ".zip";
            final CheckedSupplier<OutputStream> supplier = () -> {
                response.setContentType(MimeTypes.ZIP);
                response.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");
                return response.getOutputStream();
            };

            writeContainers(clientId, queryId, nameGen, isResponse, supplier);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws IOException;
    }

    @SuppressWarnings("squid:S2095")
    private void writeEncryptedContainers(ResponseWrapper response, ClientId clientId, String queryId,
                                          AsicContainerNameGenerator nameGen, Boolean isResponse) throws IOException {

        final String filename = AsicUtils.escapeString(queryId)
                + (isResponse == null ? "" : (isResponse ? "-response" : "-request")) + ".zip.gpg";

        final Path tempFile = Files.createTempFile(Paths.get(commonProperties.tempFilesPath()), "asic", null);

        try {
            final EncryptionConfig encryptionConfig = encryptionConfigProvider.forClientId(clientId);
            final CheckedSupplier<OutputStream> supplier = () -> {
                response.setContentType(MimeTypes.BINARY);
                response.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");
                return encryptionConfig.createEncryptionStream(tempFile);
            };

            writeContainers(clientId, queryId, nameGen, isResponse, supplier);

            try (InputStream is = Files.newInputStream(tempFile); var out = response.getOutputStream()) {
                IOUtils.copyLarge(is, out);
            }

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void writeContainers(ClientId clientId, String queryId, AsicContainerNameGenerator nameGen,
                                 Boolean response, CheckedSupplier<OutputStream> outputSupplier) {

        logRecordManager.getByQueryId(queryId, clientId, response, records -> {
            if (records.isEmpty()) {
                throw XrdRuntimeHttpException.builder(NOT_FOUND)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .details(DOCUMENTS_NOT_FOUND_FAULT_MESSAGE)
                        .build();
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
            } catch (XrdRuntimeException ce) {
                throw ce;
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
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

    private void writeAsicContainer(ResponseWrapper response, ClientId clientId, String queryId,
                                    AsicContainerNameGenerator nameGen, boolean isResponse) {

        logRecordManager.getByQueryIdUnique(queryId, clientId, isResponse, record -> {
            try {
                if (record == null) {
                    throw XrdRuntimeHttpException.builder(NOT_FOUND)
                            .httpStatus(HttpStatus.NOT_FOUND)
                            .details(DOCUMENTS_NOT_FOUND_FAULT_MESSAGE)
                            .build();
                }
                if (record.getTimestampRecord() == null) {
                    throw XrdRuntimeException.systemInternalError(MISSING_TIMESTAMP_FAULT_MESSAGE);
                }
                messageRecordEncryption.prepareDecryption(record);
                final AsicContainer asicContainer = record.toAsicContainer();

                String filename = nameGen.getArchiveFilename(queryId, isResponse, record.getId());
                if (encryptionConfigProvider.isEncryptionEnabled()) {
                    filename += ".gpg";
                    response.setContentType(MimeTypes.BINARY);
                } else {
                    response.setContentType(MimeTypes.ASIC_ZIP);
                }
                response.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                        CONTENT_DISPOSITION_FILENAME_PREFIX + filename + "\"");

                if (encryptionConfigProvider.isEncryptionEnabled()) {
                    final var encryptionConfig = encryptionConfigProvider.forClientId(clientId);
                    encryptContainer(response, encryptionConfig, asicContainer);
                } else {
                    asicContainer.write(response.getOutputStream());
                }

            } catch (XrdRuntimeException ce) {
                throw ce;
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
            }
            return null;
        });
    }

    private void encryptContainer(ResponseWrapper response, EncryptionConfig encryptionConfig,
                                  AsicContainer asicContainer) throws IOException {
        final Path tempFile = Files.createTempFile(
                Paths.get(commonProperties.tempFilesPath()), "asic", null);
        try {
            try (OutputStream os = encryptionConfig.createEncryptionStream(tempFile)) {
                asicContainer.write(os);
            }
            try (InputStream is = Files.newInputStream(tempFile); var out = response.getOutputStream()) {
                IOUtils.copyLarge(is, out);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private ClientId.Conf getClientIdFromRequest(RequestWrapper request) throws Exception {
        String instanceIdentifier = getParameter(request, PARAM_INSTANCE_IDENTIFIER, false);
        String memberClass = getParameter(request, PARAM_MEMBER_CLASS, false);
        String memberCode = getParameter(request, PARAM_MEMBER_CODE, false);
        String subsystemCode = getParameter(request, PARAM_SUBSYSTEM_CODE, true);

        return ClientId.Conf.create(instanceIdentifier, memberClass, memberCode, subsystemCode);
    }

    private String getParameter(RequestWrapper request, String param, boolean optional) throws Exception {
        String paramValue = request.getParameter(param);

        if (paramValue == null && !optional) {
            throw XrdRuntimeHttpException.builder(BAD_REQUEST)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .details(String.format(MISSING_PARAMETER_FAULT_MESSAGE, param))
                    .build();
        }

        return paramValue;
    }

}
