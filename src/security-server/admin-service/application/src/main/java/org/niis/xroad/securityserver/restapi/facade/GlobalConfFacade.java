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
package org.niis.xroad.securityserver.restapi.facade;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GlobalConf facade.
 * Pure facade / wrapper, just delegates to GlobalConf. Zero business logic.
 * Use {@link GlobalConfService} for methods are more than pure delegates.
 * Exists to make testing easier by offering non-static methods.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class GlobalConfFacade {
    private final GlobalConfProvider globalConfProvider;

    /**
     * {@link GlobalConfProvider#getMemberName(ClientId)}
     */
    public String getMemberName(ClientId identifier) {
        return globalConfProvider.getMemberName(identifier);
    }

    /**
     * {@link GlobalConfProvider#getGlobalGroupDescription(GlobalGroupId)}
     */
    public String getGlobalGroupDescription(GlobalGroupId identifier) {
        return globalConfProvider.getGlobalGroupDescription(identifier);
    }

    /**
     * {@link GlobalConfProvider#getMembers(String...)}
     */
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        return globalConfProvider.getMembers(instanceIdentifiers);
    }

    /**
     * {@link GlobalConfProvider#getMemberClasses(String...)}
     */
    public Set<String> getMemberClasses(String instanceIdentifier) {
        return globalConfProvider.getMemberClasses(instanceIdentifier);
    }

    /**
     * {@link GlobalConfProvider#getMemberClasses(String...)}
     */
    public Set<String> getMemberClasses() {
        return globalConfProvider.getMemberClasses();
    }

    /**
     * {@link GlobalConfProvider#getInstanceIdentifier()}
     */
    public String getInstanceIdentifier() {
        return globalConfProvider.getInstanceIdentifier();
    }

    /**
     * {@link GlobalConfProvider#getInstanceIdentifiers()}
     */
    public Set<String> getInstanceIdentifiers() {
        return new HashSet<>(globalConfProvider.getInstanceIdentifiers());
    }

    /**
     * {@link GlobalConfProvider#getGlobalGroups(String...)} ()}
     */
    public List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers) {
        return globalConfProvider.getGlobalGroups(instanceIdentifiers);
    }

    /**
     * {@link GlobalConfProvider#verifyValidity()}
     */
    public void verifyValidity() {
        globalConfProvider.verifyValidity();
    }

    /**
     * {@link GlobalConfProvider#existsSecurityServer(SecurityServerId)}
     */
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        return globalConfProvider.existsSecurityServer(securityServerId);
    }

    /**
     * {@link GlobalConfProvider#getSubjectName(SignCertificateProfileInfo.Parameters, X509Certificate)}
     *
     * @param signCertificateProfileInfoParameters
     * @param cert
     * @return
     * @throws Exception
     */
    public ClientId.Conf getSubjectName(SignCertificateProfileInfoParameters signCertificateProfileInfoParameters,
                                        X509Certificate cert) throws Exception {
        return globalConfProvider.getSubjectName(signCertificateProfileInfoParameters, cert);
    }

    /**
     * {@link GlobalConfProvider#getApprovedCAs(String)}
     */
    public Collection<ApprovedCAInfo> getApprovedCAs(String instanceIdentifier) {
        return globalConfProvider.getApprovedCAs(instanceIdentifier);
    }

    /**
     * {@link GlobalConfProvider#getAllCaCerts(String)}
     */
    public Collection<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return globalConfProvider.getAllCaCerts(instanceIdentifier);
    }

    /**
     * {@link GlobalConfProvider#getServerOwner(SecurityServerId)}
     */
    public ClientId getServerOwner(SecurityServerId serverId) {
        return globalConfProvider.getServerOwner(serverId);
    }

    /**
     * {@link GlobalConfProvider#getManagementRequestService()}
     */
    public ClientId getManagementRequestService() {
        return globalConfProvider.getManagementRequestService();
    }

    /**
     * {@link GlobalConfProvider#getSecurityServers(String...)}
     */
    public List<SecurityServerId.Conf> getSecurityServers(String... instanceIdentifiers) {
        return globalConfProvider.getSecurityServers(instanceIdentifiers);
    }

    /**
     * {@link GlobalConfProvider#getSecurityServerAddress(SecurityServerId)}
     */
    public String getSecurityServerAddress(SecurityServerId securityServerId) {
        return globalConfProvider.getSecurityServerAddress(securityServerId);
    }

    /**
     * {@link GlobalConfProvider#getApprovedTsps(String)}
     */
    public List<SharedParameters.ApprovedTSA> getApprovedTsps(String instanceIdentifier) {
        return globalConfProvider.getApprovedTsps(instanceIdentifier);
    }

    /**
     * {@link GlobalConfProvider#isSecurityServerClient(ClientId, SecurityServerId)}}
     */
    public boolean isSecurityServerClient(ClientId client,
                                          SecurityServerId securityServer) {
        return globalConfProvider.isSecurityServerClient(client, securityServer);
    }

    /**
     * {@link GlobalConfProvider#getApprovedCA(String, X509Certificate)}}
     */
    public ApprovedCAInfo getApprovedCA(String instanceIdentifier, X509Certificate cert) throws CodedException {
        return globalConfProvider.getApprovedCA(instanceIdentifier, cert);
    }

    public void reload() {
        globalConfProvider.reload();
    }

    public SecurityServerId getServerId(X509Certificate cert) throws Exception {
        return globalConfProvider.getServerId(cert);
    }
}
