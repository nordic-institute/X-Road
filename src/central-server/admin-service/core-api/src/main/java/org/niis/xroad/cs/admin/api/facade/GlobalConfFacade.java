/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.api.facade;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * GlobalConf facade.
 * Pure facade / wrapper, just delegates to GlobalConf. Zero business logic.
 * Use related service for methods that are more than pure delegates.
 * Exists to make testing easier by offering non-static methods.
 */
public interface GlobalConfFacade {

    /**
     * {@link GlobalConf#getMemberName(ClientId)}
     */
    String getMemberName(ClientId identifier);

    /**
     * {@link GlobalConf#getGlobalGroupDescription(GlobalGroupId)}
     */
    String getGlobalGroupDescription(GlobalGroupId identifier);

    /**
     * {@link GlobalConf#getMembers(String...)}
     */
    List<MemberInfo> getMembers(String... instanceIdentifiers);

    /**
     * {@link GlobalConf#getMemberClasses(String...)}
     */
    Set<String> getMemberClasses(String instanceIdentifier);

    /**
     * {@link GlobalConf#getMemberClasses(String...)}
     */
    Set<String> getMemberClasses();

    /**
     * {@link GlobalConf#getInstanceIdentifier()}
     */
    String getInstanceIdentifier();

    /**
     * {@link GlobalConf#getInstanceIdentifiers()}
     */
    List<String> getInstanceIdentifiers();

    /**
     * {@link GlobalConf#getGlobalGroups(String...)} ()}
     */
    List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers);

    /**
     * {@link GlobalConf#verifyValidity()}
     */
    void verifyValidity();

    /**
     * {@link GlobalConf#existsSecurityServer(SecurityServerId)}
     */
    boolean existsSecurityServer(SecurityServerId securityServerId);

    /**
     * {@link GlobalConf#getSubjectName(SignCertificateProfileInfo.Parameters, X509Certificate)}
     */
    ClientId getSubjectName(SignCertificateProfileInfoParameters signCertificateProfileInfoParameters,
                            X509Certificate cert) throws Exception;

    /**
     * {@link GlobalConf#getApprovedCAs(String)}
     */
    Collection<ApprovedCAInfo> getApprovedCAs(String instanceIdentifier);

    /**
     * {@link GlobalConf#getAllCaCerts(String)}
     */
    Collection<X509Certificate> getAllCaCerts(String instanceIdentifier);

    /**
     * {@link GlobalConf#getServerOwner(SecurityServerId)}
     */
    ClientId getServerOwner(SecurityServerId serverId);

    /**
     * {@link GlobalConf#getManagementRequestService()}
     */
    ClientId getManagementRequestService();

    /**
     * {@link GlobalConf#getSecurityServers(String...)}
     */
    List<SecurityServerId.Conf> getSecurityServers(String... instanceIdentifiers);

    /**
     * {@link GlobalConf#getSecurityServerAddress(SecurityServerId)}
     */
    String getSecurityServerAddress(SecurityServerId securityServerId);

    /**
     * {@link GlobalConf#getApprovedTsps(String)}
     */
    List<SharedParameters.ApprovedTSA> getApprovedTsps(String instanceIdentifier);

    /**
     * {@link GlobalConf#isSecurityServerClient(ClientId, SecurityServerId)}}
     */
    boolean isSecurityServerClient(ClientId client,
                                   SecurityServerId securityServer);

    /**
     * {@link GlobalConf#getApprovedCA(String, X509Certificate)}}
     */
    ApprovedCAInfo getApprovedCA(String instanceIdentifier, X509Certificate cert) throws CodedException;

    /**
     * {@link GlobalConf#reload()}
     */
    void reload();

    /**
     * {@link GlobalConf#reload()}
     */
    SecurityServerId getServerId(X509Certificate cert) throws Exception;
}
