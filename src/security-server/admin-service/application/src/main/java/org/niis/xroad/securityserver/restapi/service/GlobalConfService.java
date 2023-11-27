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
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
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
 * Contains methods that add some extra logic to the methods provided by {@link GlobalConfFacade}.
 * To avoid method explosion, do not add pure delegate methods here, use GlobalConfFacade directly instead.
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class GlobalConfService {
    private static final int CONF_CLIENT_ADMIN_PORT = SystemProperties.getConfigurationClientAdminPort();
    private static final int REST_TEMPLATE_TIMEOUT_MS = 60000;

    private final GlobalConfFacade globalConfFacade;
    private final ServerConfService serverConfService;
    private final RestTemplate restTemplate;
    private final String downloadConfigurationAnchorUrl;

    @Autowired
    public GlobalConfService(GlobalConfFacade globalConfFacade, ServerConfService serverConfService,
            @Value("${url.download-configuration-anchor}") String downloadConfigurationAnchorUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.globalConfFacade = globalConfFacade;
        this.serverConfService = serverConfService;
        this.downloadConfigurationAnchorUrl = String.format(downloadConfigurationAnchorUrl, CONF_CLIENT_ADMIN_PORT);
        this.restTemplate = restTemplateBuilder
                .setReadTimeout(Duration.ofMillis(REST_TEMPLATE_TIMEOUT_MS))
                .build();
    }

    /**
     * @param securityServerId
     * @return whether the security server exists in current instance's global configuration
     */
    public boolean securityServerExists(SecurityServerId securityServerId) {
        if (!globalConfFacade.getInstanceIdentifiers().contains(securityServerId.getXRoadInstance())) {
            // unless we check instance existence like this, we will receive
            // CodedException: InternalError: Invalid instance identifier: x -exception
            // which is hard to turn correctly into http 404 instead of 500
            return false;
        }
        return globalConfFacade.existsSecurityServer(securityServerId);
    }

    /**
     * @param identifiers global group identifiers
     * @return whether the global groups exist in global configuration
     * Global groups may or may not have entries in IDENTIFIER table
     */
    public boolean globalGroupsExist(Collection<? extends XRoadId> identifiers) {
        List<XRoadId> existingIdentifiers = globalConfFacade.getGlobalGroups().stream()
                .map(GlobalGroupInfo::getId)
                .collect(Collectors.toList());
        return existingIdentifiers.containsAll(identifiers);
    }

    /**
     * @param identifiers client identifiers
     * @return whether the clients exist in global configuration.
     * Clients may or may not have entries in IDENTIFIER table
     */
    public boolean clientsExist(Collection<? extends XRoadId> identifiers) {
        List<XRoadId> existingIdentifiers = globalConfFacade.getMembers().stream()
                .map(MemberInfo::getId)
                .collect(Collectors.toList());
        return existingIdentifiers.containsAll(identifiers);
    }

    /**
     * @return member classes for current instance
     */
    public Set<String> getMemberClassesForThisInstance() {
        return globalConfFacade.getMemberClasses(globalConfFacade.getInstanceIdentifier());
    }

    public String getSecurityServerAddress(SecurityServerId securityServerId) {
        return globalConfFacade.getSecurityServerAddress(securityServerId);
    }

    /**
     * Check the validity of the GlobalConf
     * @throws GlobalConfOutdatedException if conf is outdated
     */
    public void verifyGlobalConfValidity() throws GlobalConfOutdatedException {
        try {
            globalConfFacade.verifyValidity();
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
        return globalConfFacade.getApprovedCAs(globalConfFacade.getInstanceIdentifier());
    }

    /**
     * @return approved CA matching given CA cert (top level or intermediate), for current instance
     */
    public ApprovedCAInfo getApprovedCAForThisInstance(X509Certificate certificate) {
        return globalConfFacade.getApprovedCA(globalConfFacade.getInstanceIdentifier(), certificate);
    }

    /**
     * @return CA certs for current instance
     */
    public Collection<X509Certificate> getAllCaCertsForThisInstance() {
        return globalConfFacade.getAllCaCerts(globalConfFacade.getInstanceIdentifier());
    }

    /**
     * @return approved timestamping services for current instance.
     * {@link TspType#getId()} is null for all returned items
     */
    public List<TspType> getApprovedTspsForThisInstance() {
        List<SharedParameters.ApprovedTSA> approvedTspTypes =
                globalConfFacade.getApprovedTsps(globalConfFacade.getInstanceIdentifier());
        List<TspType> tsps = approvedTspTypes.stream()
                .map(this::createTspType)
                .collect(Collectors.toList());
        return tsps;
    }

    /**
     * init TspType DTO with name and url. id will be null
     */
    private TspType createTspType(SharedParameters.ApprovedTSA approvedTSA) {
        TspType tsp = new TspType();
        tsp.setUrl(approvedTSA.getUrl());
        tsp.setName(approvedTSA.getName());
        return tsp;
    }

    /**
     * Checks if given client is one of this security server's clients
     */
    public boolean isSecurityServerClientForThisInstance(ClientId client) {
        return globalConfFacade.isSecurityServerClient(client,
                serverConfService.getSecurityServerId());
    }

    /**
     * Sends an http request to configuration-client in order to trigger the downloading of the global conf
     * @throws ConfigurationDownloadException if the request succeeds but configuration-client returns an error
     * @throws DeviationAwareRuntimeException if the request fails
     */
    public void executeDownloadConfigurationFromAnchor() throws ConfigurationDownloadException {
        log.info("Starting to download GlobalConf");
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.getForEntity(downloadConfigurationAnchorUrl, String.class);
        } catch (RestClientException e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_GLOBAL_CONF_DOWNLOAD_REQUEST));
        }
        if (response != null && response.getStatusCode() != HttpStatus.OK) {
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
        String instanceIdentifier = globalConfFacade.getInstanceIdentifier();
        ClientId clientId = ClientId.Conf.create(instanceIdentifier, memberClass, memberCode);
        return globalConfFacade.getMemberName(clientId);
    }
}
