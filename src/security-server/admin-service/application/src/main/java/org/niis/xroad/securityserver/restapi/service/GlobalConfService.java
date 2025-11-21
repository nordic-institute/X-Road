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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.CostType;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.common.core.exception.ErrorCode.GLOBAL_CONF_OUTDATED;

/**
 * Global configuration service.
 * Contains methods that add some extra logic to the methods provided by {@link GlobalConfProvider}.
 * To avoid method explosion, do not add pure delegate methods here, use GlobalConfFacade directly instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class GlobalConfService {
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfService serverConfService;

    /**
     * @param securityServerId
     * @return whether the security server exists in current instance's global configuration
     */
    public boolean securityServerExists(SecurityServerId securityServerId) {
        if (!globalConfProvider.getInstanceIdentifiers().contains(securityServerId.getXRoadInstance())) {
            // unless we check instance existence like this, we will receive
            // XrdRuntimeException: InternalError: Invalid instance identifier: x -exception
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
        return new HashSet<>(existingIdentifiers).containsAll(identifiers);
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
        return new HashSet<>(existingIdentifiers).containsAll(identifiers);
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
     *
     * @throws GlobalConfOutdatedException if conf is outdated
     */
    public void verifyGlobalConfValidity() throws GlobalConfOutdatedException {
        try {
            globalConfProvider.verifyValidity();
        } catch (XrdRuntimeException e) {
            if (isCausedByOutdatedGlobalconf(e)) {
                throw new GlobalConfOutdatedException(e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("global conf validity check failed", e);
        }
    }

    static boolean isCausedByOutdatedGlobalconf(XrdRuntimeException e) {
        return GLOBAL_CONF_OUTDATED.code().equals(e.getErrorCode());
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

    public Map<String, CostType> getOcspResponderAddressesAndCostTypes(X509Certificate certificate) {
        return globalConfProvider.getOcspResponderAddressesAndCostTypes(globalConfProvider.getInstanceIdentifier(), certificate);
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
                .toList();
    }

    /**
     * init TimestampingService DTO with name and url. id will be null
     */
    private TimestampingService createTspType(SharedParameters.ApprovedTSA approvedTSA) {
        TimestampingService tsp = new TimestampingService();
        tsp.setUrl(approvedTSA.getUrl());
        tsp.setName(approvedTSA.getName());
        tsp.setCostType(approvedTSA.getCostType() != null ? approvedTSA.getCostType().name() : CostType.UNDEFINED.name());
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
     * Find member's name in the global conf
     *
     * @param memberClass
     * @param memberCode
     * @return
     */
    public String findMemberName(String memberClass, String memberCode) {
        String instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        ClientId clientId = ClientId.Conf.create(instanceIdentifier, memberClass, memberCode);
        return globalConfProvider.getMemberName(clientId);
    }


    public OptionalInt getGlobalConfigurationVersion() {
        return globalConfProvider.getVersion();
    }
}
