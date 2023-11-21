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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.service.ConfigurationVerifier;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.repository.AnchorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ANCHOR_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ANCHOR_UPLOAD_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GENERIC_INTERNAL_ERROR;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_MALFORMED_ANCHOR;
import static org.niis.xroad.restapi.exceptions.ErrorDeviation.newError;

/**
 * Service that handles system services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SystemService {

    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;
    private final AnchorRepository anchorRepository;
    private final ConfigurationVerifier configurationVerifier;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final AuditDataHelper auditDataHelper;
    private final SecurityServerAddressChangeStatus addressChangeStatus;

    @Setter
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;
    @Setter
    private String tempFilesPath = SystemProperties.getTempFilesPath();
    @Setter
    @Value("${script.internal-configuration-verifier.path}")
    private String internalConfVerificationScriptPath;

    private static final String ANCHOR_DOWNLOAD_FILENAME_PREFIX = "configuration_anchor_UTC_";
    private static final String ANCHOR_DOWNLOAD_DATE_TIME_FORMAT = "yyyy-MM-dd_HH_mm_ss";
    private static final String ANCHOR_DOWNLOAD_FILE_EXTENSION = ".xml";

    /**
     * Return a list of configured timestamping services
     * @return
     */
    public List<TspType> getConfiguredTimestampingServices() {
        return serverConfService.getConfiguredTimestampingServices();
    }

    /**
     * Audit log tsp name and url
     * @param tspType
     */
    private void auditLog(TspType tspType) {
        auditDataHelper.put(RestApiAuditProperty.TSP_NAME, tspType.getName());
        auditDataHelper.put(RestApiAuditProperty.TSP_URL, tspType.getUrl());
    }

    public void addConfiguredTimestampingService(TspType tspTypeToAdd)
            throws TimestampingServiceNotFoundException, DuplicateConfiguredTimestampingServiceException {
        auditLog(tspTypeToAdd);

        // Check that the timestamping service is an approved timestamping service
        Optional<TspType> match = globalConfService.getApprovedTspsForThisInstance().stream()
                .filter(tsp -> tspTypeToAdd.getName().equals(tsp.getName())
                        && tspTypeToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (!match.isPresent()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(tspTypeToAdd.getName(),
                    tspTypeToAdd.getUrl(), "not found"));
        }

        // Check that the timestamping service is not already configured
        Optional<TspType> existingTsp = getConfiguredTimestampingServices().stream()
                .filter(tsp -> tspTypeToAdd.getName().equals(tsp.getName())
                        && tspTypeToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (existingTsp.isPresent()) {
            throw new DuplicateConfiguredTimestampingServiceException(
                    getExceptionMessage(tspTypeToAdd.getName(), tspTypeToAdd.getUrl(),
                            "is already configured")
            );
        }
        serverConfService.getConfiguredTimestampingServices().add(tspTypeToAdd);
    }

    /**
     * Deletes a configured timestamping service from serverconf
     * @param tspTypeToDelete
     * @throws TimestampingServiceNotFoundException
     */
    public void deleteConfiguredTimestampingService(TspType tspTypeToDelete)
            throws TimestampingServiceNotFoundException {
        auditLog(tspTypeToDelete);

        List<TspType> configuredTimestampingServices = getConfiguredTimestampingServices();

        Optional<TspType> delete = configuredTimestampingServices.stream()
                .filter(tsp -> tspTypeToDelete.getName().equals(tsp.getName())
                        && tspTypeToDelete.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (!delete.isPresent()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(tspTypeToDelete.getName(),
                    tspTypeToDelete.getUrl(), "not found")
            );
        }

        configuredTimestampingServices.remove(delete.get());
    }

    private String getExceptionMessage(String name, String url, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamping service with name ").append(name).append(" and url ").append(url);
        sb.append(" ").append(message);
        return sb.toString();
    }

    /**
     * Generate internal auth cert CSR
     * @param distinguishedName
     * @return
     * @throws InvalidDistinguishedNameException if {@code distinguishedName} does not conform to
     * <a href="http://www.ietf.org/rfc/rfc1779.txt">RFC 1779</a> or
     * <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>
     */
    public byte[] generateInternalCsr(String distinguishedName) throws InvalidDistinguishedNameException {
        auditDataHelper.put(RestApiAuditProperty.SUBJECT_NAME, distinguishedName);
        byte[] csrBytes = null;
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(internalKeyPath);
            csrBytes = CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new InvalidDistinguishedNameException(e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new DeviationAwareRuntimeException(e, newError(ERROR_GENERIC_INTERNAL_ERROR));
        }
        return csrBytes;
    }

    /**
     * Get configuration anchor file
     * @return
     * @throws AnchorNotFoundException if anchor file is not found
     */
    public AnchorFile getAnchorFile() throws AnchorNotFoundException {
        AnchorFile anchorFile = new AnchorFile(calculateAnchorHexHash(readAnchorFile()));
        ConfigurationAnchor anchor = anchorRepository.loadAnchorFromFile();
        anchorFile.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(anchor.getGeneratedAt()));
        return anchorFile;
    }

    /**
     * Calculate the hex hash of the given anchor file. Used to verify/preview an anchor file before
     * uploading it
     * @param anchorBytes
     * @param shouldVerifyAnchorInstance if the anchor instance should be verified
     * @return
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws MalformedAnchorException if the Anchor content is wrong
     */
    public AnchorFile getAnchorFileFromBytes(byte[] anchorBytes, boolean shouldVerifyAnchorInstance)
            throws InvalidAnchorInstanceException, MalformedAnchorException {
        ConfigurationAnchor anchor = createAnchorFromBytes(anchorBytes);
        if (shouldVerifyAnchorInstance) {
            verifyAnchorInstance(anchor);
        }
        AnchorFile anchorFile = new AnchorFile(calculateAnchorHexHash(anchorBytes));
        anchorFile.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(anchor.getGeneratedAt()));
        return anchorFile;
    }

    /**
     * Upload a new configuration anchor. This method should be used when initializing a new Security Server.
     * This method will throw {@link AnchorAlreadyExistsException} if an anchor already exists. When updating an
     * existing anchor one should use {@link #replaceAnchor(byte[])} instead.
     * @param anchorBytes
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws AnchorUploadException in case of external process exceptions
     * @throws MalformedAnchorException if the Anchor content is wrong
     * @throws ConfigurationDownloadException if the configuration download request succeeds but configuration-client
     * returns an error
     * @throws ConfigurationVerifier.ConfigurationVerificationException when a known exception happens during
     * @throws AnchorAlreadyExistsException if there already is an anchor -> a new one cannot be uploaded. Instead the
     * old anchor should be updated by using {@link #uploadAnchor(byte[], boolean)}
     * verification
     */
    public void uploadInitialAnchor(byte[] anchorBytes) throws InvalidAnchorInstanceException, AnchorUploadException,
            MalformedAnchorException, ConfigurationDownloadException,
            ConfigurationVerifier.ConfigurationVerificationException, AnchorAlreadyExistsException {
        if (isAnchorImported()) {
            throw new AnchorAlreadyExistsException("Anchor already exists - cannot upload a second one");
        }
        uploadAnchor(anchorBytes, false);
    }

    /**
     * Replace the current configuration anchor with a new one. When uploading the first anchor (in Security Server
     * init phase) one should use {@link #uploadInitialAnchor(byte[])};
     * @param anchorBytes
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws AnchorUploadException in case of external process exceptions
     * @throws MalformedAnchorException if the Anchor content is wrong
     * @throws ConfigurationDownloadException if the configuration download request succeeds but configuration-client
     * returns an error
     * @throws ConfigurationVerifier.ConfigurationVerificationException when a known exception happens during
     * verification
     */
    public void replaceAnchor(byte[] anchorBytes) throws InvalidAnchorInstanceException, AnchorUploadException,
            MalformedAnchorException, ConfigurationDownloadException,
            ConfigurationVerifier.ConfigurationVerificationException {
        uploadAnchor(anchorBytes, true);
    }

    /**
     * Upload a new configuration anchor. A temporary anchor file is created on the filesystem in order to run
     * the verification process with configuration-client module (via external script).
     * @param anchorBytes
     * @param shouldVerifyAnchorInstance whether the anchor instance should be verified or not. Usually it should
     * always be verified (and this parameter should be true) but e.g. when initializing a new Security Server it
     * cannot be verified (and this parameter should be set to false)
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws AnchorUploadException in case of external process exceptions
     * @throws MalformedAnchorException if the Anchor content is wrong
     * @throws ConfigurationDownloadException if the configuration download request succeeds but configuration-client
     * returns an error
     * @throws ConfigurationVerifier.ConfigurationVerificationException when a known exception happens during
     * verification
     */
    // SonarQube: "InterruptedException" should not be ignored -> it has already been handled at this point
    @SuppressWarnings("squid:S2142")
    private void uploadAnchor(byte[] anchorBytes, boolean shouldVerifyAnchorInstance)
            throws InvalidAnchorInstanceException, AnchorUploadException, MalformedAnchorException,
            ConfigurationDownloadException, ConfigurationVerifier.ConfigurationVerificationException {
        auditDataHelper.calculateAndPutAnchorHash(anchorBytes);
        ConfigurationAnchor anchor = createAnchorFromBytes(anchorBytes);
        auditDataHelper.putDate(RestApiAuditProperty.GENERATED_AT, anchor.getGeneratedAt());
        if (shouldVerifyAnchorInstance) {
            verifyAnchorInstance(anchor);
        }
        File tempAnchor = null;
        try {
            tempAnchor = createTemporaryAnchorFile(anchorBytes);
            configurationVerifier.verifyConfiguration(internalConfVerificationScriptPath, tempAnchor.getAbsolutePath());
            anchorRepository.saveAndReplace(tempAnchor);
            globalConfService.executeDownloadConfigurationFromAnchor();
        } catch (InterruptedException | ProcessNotExecutableException | ProcessFailedException e) {
            throw new AnchorUploadException(e);
        } catch (IOException e) {
            throw new RuntimeException("Cannot upload a new anchor", e);
        } finally {
            if (tempAnchor != null) {
                boolean deleted = tempAnchor.delete();
                if (!deleted) {
                    log.error("Temporary anchor could not be deleted: " + tempAnchor.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Is global conf initialized -> it is if whe can find a Configuration anchor
     * @return
     */
    public boolean isAnchorImported() {
        boolean isGlobalConfInitialized = false;
        try {
            AnchorFile anchorFile = getAnchorFile();
            if (anchorFile != null) {
                isGlobalConfInitialized = true;
            }
        } catch (AnchorNotFoundException e) {
            // global conf does not exist
        }
        return isGlobalConfInitialized;
    }

    /**
     * Sends a management request to change Security Server address
     * @param newAddress new address
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException
     */
    public Integer changeSecurityServerAddress(String newAddress) throws GlobalConfOutdatedException,
            ManagementRequestSendingFailedException {
        auditDataHelper.put(RestApiAuditProperty.ADDRESS, newAddress);
        if (addressChangeStatus.getAddressChangeRequest().isPresent()) {
            throw new ConflictException("Address change request already submitted.");
        }
        if (globalConfService.getSecurityServerAddress(currentSecurityServerId.getServerId()).equals(newAddress)) {
            throw new ConflictException("Can not change to the same address.");
        }
        Integer requestId = managementRequestSenderService.sendAddressChangeRequest(newAddress);
        addressChangeStatus.setAddress(newAddress);

        return requestId;
    }

    /**
     * Simple helper to create a ConfigurationAnchorV2 instance from bytes
     * @param anchorBytes
     * @return
     * @throws MalformedAnchorException if the anchor is malformed or somehow invalid
     */
    private ConfigurationAnchor createAnchorFromBytes(byte[] anchorBytes) throws MalformedAnchorException {
        ConfigurationAnchor anchor = null;
        try {
            anchor = new ConfigurationAnchor(anchorBytes);
        } catch (CodedException ce) {
            if (isCausedByMalformedAnchorContent(ce)) {
                throw new MalformedAnchorException("Anchor is invalid");
            } else {
                throw ce;
            }
        }
        return anchor;
    }

    /**
     * Create a temporary anchor file on the filesystem. This is needed for verifying the anchor with
     * configuration-client module (this might be changed in the future). This method does not delete the created
     * temporary file. Remember to delete the file after it is no longer needed.
     * @param anchorBytes
     * @return temporary anchor file
     * @throws IOException if temp file creation fails
     */
    private File createTemporaryAnchorFile(byte[] anchorBytes) throws IOException {
        try {
            String tempAnchorPrefix = "temp-internal-anchor-";
            String tempAnchorSuffix = ".xml";
            File tempDirectory = tempFilesPath != null ? new File(tempFilesPath) : null;
            File tempAnchor = File.createTempFile(tempAnchorPrefix, tempAnchorSuffix, tempDirectory);
            FileUtils.writeByteArrayToFile(tempAnchor, anchorBytes);
            return tempAnchor;
        } catch (Exception e) {
            log.error("Creating temporary anchor file failed", e);
            throw e;
        }
    }

    /**
     * Verify that the anchor has been generated in the current instance
     * @param anchor
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     */
    private void verifyAnchorInstance(ConfigurationAnchor anchor) throws InvalidAnchorInstanceException {
        String anchorInstanceId = anchor.getInstanceIdentifier();
        String ownerInstance = currentSecurityServerId.getServerId().getOwner().getXRoadInstance();
        if (!anchorInstanceId.equals(ownerInstance)) {
            String errorMessage = String.format("Cannot upload an anchor from instance %s into instance %s",
                    anchorInstanceId, ownerInstance);
            throw new InvalidAnchorInstanceException(errorMessage);
        }
    }

    /**
     * Read anchor file's content
     * @return
     * @throws AnchorNotFoundException if anchor file is not found
     */
    public byte[] readAnchorFile() throws AnchorNotFoundException {
        try {
            return anchorRepository.readAnchorFile();
        } catch (NoSuchFileException e) {
            throw new AnchorNotFoundException("Anchor file not found");
        }
    }

    /**
     * Generate anchor file download name with the anchor file created at date/time. The name format is:
     * "configuration_anchor_UTC_yyyy-MM-dd_HH_mm_ss.xml".
     * @return
     */
    public String getAnchorFilenameForDownload() {
        DateFormat df = new SimpleDateFormat(ANCHOR_DOWNLOAD_DATE_TIME_FORMAT);
        ConfigurationAnchor anchor = anchorRepository.loadAnchorFromFile();
        return ANCHOR_DOWNLOAD_FILENAME_PREFIX + df.format(anchor.getGeneratedAt()) + ANCHOR_DOWNLOAD_FILE_EXTENSION;
    }

    /**
     * Return anchor file's hash as a hex string
     * @return
     */
    private String calculateAnchorHexHash(byte[] anchor) {
        try {
            return CryptoUtils.hexDigest(CryptoUtils.DEFAULT_ANCHOR_HASH_ALGORITHM_ID, anchor).toUpperCase();
        } catch (Exception e) {
            log.error("can't create hex digest for anchor file");
            throw new RuntimeException(e);
        }
    }

    static boolean isCausedByMalformedAnchorContent(CodedException e) {
        return (X_MALFORMED_GLOBALCONF).equals(e.getFaultCode());
    }

    /**
     * Return the node type of the server
     * @return server node type
     */
    public SystemProperties.NodeType getServerNodeType() {
        return SystemProperties.getServerNodeType();
    }

    /**
     * Thrown when attempt to add timestamping service that is already configured
     */
    public static class DuplicateConfiguredTimestampingServiceException extends ServiceException {
        public DuplicateConfiguredTimestampingServiceException(String s) {
            super(s, new ErrorDeviation(ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE));
        }
    }

    /**
     * Thrown when attempting to upload an anchor from a wrong instance
     */
    public static class InvalidAnchorInstanceException extends ServiceException {
        public InvalidAnchorInstanceException(String s) {
            super(s, new ErrorDeviation(ERROR_INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID));
        }
    }

    /**
     * Thrown when uploading a conf anchor fails
     */
    public static class AnchorUploadException extends ServiceException {
        public AnchorUploadException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_ANCHOR_UPLOAD_FAILED));
        }
    }

    /**
     * Thrown e.g. if Anchor upload or preview fails because of invalid content
     */
    public static class MalformedAnchorException extends ServiceException {
        public MalformedAnchorException(String s) {
            super(s, new ErrorDeviation(ERROR_MALFORMED_ANCHOR));
        }
    }

    /**
     * Thrown if user tries to upload a new anchor instead of updating the old
     */
    public static class AnchorAlreadyExistsException extends ServiceException {
        public AnchorAlreadyExistsException(String s) {
            super(s, new ErrorDeviation(ERROR_ANCHOR_EXISTS));
        }
    }
}
