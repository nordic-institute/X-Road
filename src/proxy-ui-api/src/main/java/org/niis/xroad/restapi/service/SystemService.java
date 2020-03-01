/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchorV2;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.restapi.dto.AnchorFile;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.repository.AnchorRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

/**
 * Service that handles system services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class SystemService {

    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;
    private final AnchorRepository anchorRepository;

    @Setter
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;

    private static final String ANCHOR_DOWNLOAD_FILENAME_PREFIX = "configuration_anchor_UTC_";
    private static final String ANCHOR_DOWNLOAD_DATE_TIME_FORMAT = "yyyy-MM-dd_HH_mm_ss";
    private static final String ANCHOR_DOWNLOAD_FILE_EXTENSION = ".xml";

    /**
     * constructor
     */
    @Autowired
    public SystemService(GlobalConfService globalConfService, ServerConfService serverConfService,
                         AnchorRepository anchorRepository) {
        this.globalConfService = globalConfService;
        this.serverConfService = serverConfService;
        this.anchorRepository = anchorRepository;
    }

    /**
     * Return a list of configured timestamping services
     * @return
     */
    public List<TspType> getConfiguredTimestampingServices() {
        return serverConfService.getConfiguredTimestampingServices();
    }

    public void addConfiguredTimestampingService(TimestampingService timestampingServiceToAdd)
            throws TimestampingServiceNotFoundException, DuplicateConfiguredTimestampingServiceException {
        // Check that the timestamping service is an approved timestamping service
        Optional<TspType> match = globalConfService.getApprovedTspsForThisInstance().stream()
                .filter(tsp -> timestampingServiceToAdd.getName().equals(tsp.getName())
                        && timestampingServiceToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (!match.isPresent()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(timestampingServiceToAdd.getName(),
                    timestampingServiceToAdd.getUrl(), "not found"));
        }

        // Check that the timestamping service is not already configured
        Optional<TspType> existingTsp = getConfiguredTimestampingServices().stream()
                .filter(tsp -> timestampingServiceToAdd.getName().equals(tsp.getName())
                        && timestampingServiceToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (existingTsp.isPresent()) {
            throw new DuplicateConfiguredTimestampingServiceException(
                    getExceptionMessage(timestampingServiceToAdd.getName(), timestampingServiceToAdd.getUrl(),
                            "is already configured")
            );
        }

        TspType tspType = new TspType();
        tspType.setName(timestampingServiceToAdd.getName());
        tspType.setUrl(timestampingServiceToAdd.getUrl());

        serverConfService.getConfiguredTimestampingServices().add(tspType);
    }

    /**
     * Deletes a configured timestamping service from serverconf
     * @param timestampingService
     * @throws TimestampingServiceNotFoundException
     */
    public void deleteConfiguredTimestampingService(TimestampingService timestampingService)
            throws TimestampingServiceNotFoundException {
        List<TspType> configuredTimestampingServices = getConfiguredTimestampingServices();

        Optional<TspType> delete = configuredTimestampingServices.stream()
                .filter(tsp -> timestampingService.getName().equals(tsp.getName())
                        && timestampingService.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (!delete.isPresent()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(timestampingService.getName(),
                    timestampingService.getUrl(), "not found")
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
        byte[] csrBytes = null;
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(internalKeyPath);
            csrBytes = CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new InvalidDistinguishedNameException(e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new DeviationAwareRuntimeException(e);
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
        ConfigurationAnchorV2 anchor = anchorRepository.loadAnchorFromFile();
        anchorFile.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(anchor.getGeneratedAt()));
        return anchorFile;
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
        ConfigurationAnchorV2 anchor = anchorRepository.loadAnchorFromFile();
        return ANCHOR_DOWNLOAD_FILENAME_PREFIX + df.format(anchor.getGeneratedAt()) + ANCHOR_DOWNLOAD_FILE_EXTENSION;
    }

    /**
     * Return anchor file's hash as a colon delimited hex string
     * @return
     */
    private String calculateAnchorHexHash(byte[] anchor) {
        try {
            String hash = CryptoUtils.hexDigest(CryptoUtils.DEFAULT_ANCHOR_HASH_ALGORITHM_ID, anchor);
            return StringUtils.join(hash.toUpperCase().split("(?<=\\G.{2})"), ':');
        } catch (Exception e) {
            log.error("can't create hex digest for anchor file");
            throw new RuntimeException(e);
        }
    }
    /**
     * Thrown when attempt to add timestamping service that is already configured
     */
    public static class DuplicateConfiguredTimestampingServiceException extends ServiceException {
        public static final String ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE
                = "timestamping_service_already_configured";

        public DuplicateConfiguredTimestampingServiceException(String s) {
            super(s, new ErrorDeviation(ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE));
        }
    }
}
