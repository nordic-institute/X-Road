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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GLOBAL_CONF_DOWNLOAD_REQUEST;

/**
 * Global configuration service.
 * Contains methods that add some extra logic to the methods provided by {@link GlobalConfProvider}.
 * To avoid method explosion, do not add pure delegate methods here, use GlobalConfFacade directly instead.
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class GlobalConfService {
    private static final int CONF_CLIENT_ADMIN_PORT = SystemProperties.getConfigurationClientAdminPort();
    private static final int REST_TEMPLATE_TIMEOUT_MS = 60000;

    private final GlobalConfProvider globalConfProvider;
    private final ServerConfService serverConfService;
    private final RestTemplate restTemplate;
    private final String downloadConfigurationAnchorUrl;

    @Autowired
    public GlobalConfService(GlobalConfProvider globalConfProvider, ServerConfService serverConfService,
                             @Value("${url.download-configuration-anchor}") String downloadConfigurationAnchorUrl,
                             RestTemplateBuilder restTemplateBuilder) {
        this.globalConfProvider = globalConfProvider;
        this.serverConfService = serverConfService;
        this.downloadConfigurationAnchorUrl = String.format(downloadConfigurationAnchorUrl, CONF_CLIENT_ADMIN_PORT);
        this.restTemplate = restTemplateBuilder
                .readTimeout(Duration.ofMillis(REST_TEMPLATE_TIMEOUT_MS))
                .build();
    }

    /**
     * @param securityServerId
     * @return whether the security server exists in current instance's global configuration
     */
    public boolean securityServerExists(SecurityServerId securityServerId) {
        if (!globalConfProvider.getInstanceIdentifiers().contains(securityServerId.getXRoadInstance())) {
            // unless we check instance existence like this, we will receive
            // CodedException: InternalError: Invalid instance identifier: x -exception
            // which is hard to turn correctly into http 404 instead of 500
            return false;
        }
        return globalConfProvider.existsSecurityServer(securityServerId);
    }

    /**
     * @param identifiers global group identifiers
     * @return whether the global groups exist in global configuration
     * Global groups may or may not have entries in IDENTIFIER table
     */
    public boolean globalGroupsExist(Collection<? extends XRoadId> identifiers) {
        var existingIdentifiers = globalConfProvider.getGlobalGroups().stream()
                .map(GlobalGroupInfo::id)
                .map(XRoadId.class::cast)
                .collect(Collectors.toSet());
        return existingIdentifiers.containsAll(identifiers);
    }

    /**
     * @param identifiers client identifiers
     * @return whether the clients exist in global configuration.
     * Clients may or may not have entries in IDENTIFIER table
     */
    public boolean clientsExist(Collection<? extends XRoadId> identifiers) {
        var existingIdentifiers = globalConfProvider.getMembers().stream()
                .map(MemberInfo::id)
                .map(XRoadId.class::cast)
                .collect(Collectors.toSet());
        return existingIdentifiers.containsAll(identifiers);
    }

    /**
     * @return member classes for current instance
     */
    public Set<String> getMemberClassesForThisInstance() {
        return globalConfProvider.getMemberClasses(globalConfProvider.getInstanceIdentifier());
    }

    public String getSecurityServerAddress(SecurityServerId securityServerId) {
        return globalConfProvider.getSecurityServerAddress(securityServerId);
    }

    /**
     * Check the validity of the GlobalConf
     * @throws GlobalConfOutdatedException if conf is outdated
     */
    public void verifyGlobalConfValidity() throws GlobalConfOutdatedException {
        try {
            globalConfProvider.verifyValidity();
        } catch (CodedException e) {
            if (isCausedByOutdatedGlobalconf(e)) {
                throw new GlobalConfOutdatedException(e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("global conf validity check failed", e);
        }
    }

    static boolean isCausedByOutdatedGlobalconf(CodedException e) {
        return X_OUTDATED_GLOBALCONF.equals(e.getFaultCode());
    }

    /**
     * @return approved CAs for current instance
     */
    public Collection<ApprovedCAInfo> getApprovedCAsForThisInstance() {
        return globalConfProvider.getApprovedCAs(globalConfProvider.getInstanceIdentifier());
    }

    /**
     * @return approved CA matching given CA cert (top level or intermediate), for current instance
     */
    public ApprovedCAInfo getApprovedCAForThisInstance(X509Certificate certificate) {
        return globalConfProvider.getApprovedCA(globalConfProvider.getInstanceIdentifier(), certificate);
    }

    /**
     * @return CA certs for current instance
     */
    public Collection<X509Certificate> getAllCaCertsForThisInstance() {
        return globalConfProvider.getAllCaCerts(globalConfProvider.getInstanceIdentifier());
    }

    /**
     * @return approved timestamping services for current instance.
     * {@link TimestampingService#getId()} is null for all returned items
     */
    public List<TimestampingService> getApprovedTspsForThisInstance() {
        List<SharedParameters.ApprovedTSA> approvedTspTypes =
                globalConfProvider.getApprovedTsps(globalConfProvider.getInstanceIdentifier());
        return approvedTspTypes.stream()
                .map(this::createTspType)
                .collect(Collectors.toList());
    }

    /**
     * init TimestampingService DTO with name and url. id will be null
     */
    private TimestampingService createTspType(SharedParameters.ApprovedTSA approvedTSA) {
        TimestampingService tsp = new TimestampingService();
        tsp.setUrl(approvedTSA.getUrl());
        tsp.setName(approvedTSA.getName());
        return tsp;
    }

    /**
     * Checks if given client is one of this security server's clients
     */
    public boolean isSecurityServerClientForThisInstance(ClientId client) {
        return globalConfProvider.isSecurityServerClient(client,
                serverConfService.getSecurityServerId());
    }

    /**
     * Sends an http request to configuration-client in order to trigger the downloading of the global conf
     * @throws ConfigurationDownloadException if the request succeeds but configuration-client returns an error
     * @throws DeviationAwareRuntimeException if the request fails
     */
    public void executeDownloadConfigurationFromAnchor() throws ConfigurationDownloadException {
        log.info("Starting to download GlobalConf");
        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(downloadConfigurationAnchorUrl, String.class);
        } catch (RestClientException e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_GLOBAL_CONF_DOWNLOAD_REQUEST));
        }
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new ConfigurationDownloadException(response.getBody());
        }
    }

    /**
     * Find member's name in the global conf
     * @param memberClass
     * @param memberCode
     * @return
     */
    public String findMemberName(String memberClass, String memberCode) {
        String instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        ClientId clientId = ClientId.Conf.create(instanceIdentifier, memberClass, memberCode);
        return globalConfProvider.getMemberName(clientId);
    }
}
