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
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CertUtils;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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

    @Setter
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;

    /**
     * constructor
     */
    @Autowired
    public SystemService(GlobalConfService globalConfService, ServerConfService serverConfService) {
        this.globalConfService = globalConfService;
        this.serverConfService = serverConfService;
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
     * @throws InvalidDistinguishedNameException
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
}
