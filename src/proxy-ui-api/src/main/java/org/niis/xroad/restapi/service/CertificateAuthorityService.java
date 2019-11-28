/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;
import ee.ria.xroad.common.certificateprofile.impl.AuthCertificateProfileInfoParameters;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ServerConfRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service that handles approved certificate authorities
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class CertificateAuthorityService {

    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;
    private final GlobalConfFacade globalConfFacade;
    private final ServerConfRepository serverConfRepository;
    private final ClientService clientService;

    /**
     * constructor
     * @param globalConfService
     * @param globalConfFacade
     * @param serverConfRepository
     * @param serverConfService
     * @param clientService
     */
    @Autowired
    public CertificateAuthorityService(GlobalConfService globalConfService,
            GlobalConfFacade globalConfFacade,
            ServerConfRepository serverConfRepository,
            ServerConfService serverConfService,
            ClientService clientService) {
        this.globalConfService = globalConfService;
        this.globalConfFacade = globalConfFacade;
        this.serverConfRepository = serverConfRepository;
        this.serverConfService = serverConfService;
        this.clientService = clientService;
    }

    /**
     * Return certificate authorities
     * @param keyUsageInfo list CAs for this type of key usage. If null, list all.
     * @return
     */
    public Collection<ApprovedCAInfo> getCertificateAuthorities(KeyUsageInfo keyUsageInfo) {
        Collection<ApprovedCAInfo> matchingCas = globalConfService.getApprovedCAsForThisInstance();
        if (keyUsageInfo == KeyUsageInfo.SIGNING) {
            // need to remove "authentication only" CAs
            matchingCas = matchingCas.stream()
                    .filter(ca -> !(Boolean.TRUE.equals(ca.getAuthenticationOnly())))
                    .collect(Collectors.toList());
        }
        return matchingCas;
    }

    /**
     * Return correct CertificateProfileInfo for given parameters
     * @param caName
     * @param keyUsageInfo
     * @param memberId member when key usage = signing, ignored otherwise
     * @return
     * @throws CertificateAuthorityNotFoundException if matching CA was not found
     * @throws CertificateProfileInstantiationException if instantiation of certificate profile failed
     * @throws WrongKeyUsageException if attempted to read signing profile from authenticationOnly ca
     * @throws ClientNotFoundException if client with memberId was not found
     */
    public CertificateProfileInfo getCertificateProfile(String caName, KeyUsageInfo keyUsageInfo, ClientId memberId)
            throws CertificateAuthorityNotFoundException, CertificateProfileInstantiationException,
            WrongKeyUsageException, ClientNotFoundException {
        ApprovedCAInfo caInfo = getCertificateAuthority(caName);
        if (Boolean.TRUE == caInfo.getAuthenticationOnly() && KeyUsageInfo.SIGNING == keyUsageInfo) {
            throw new WrongKeyUsageException();
        }
        if (memberId != null) {
            // validate that the member exists or has a subsystem on this server
            if (!clientService.getLocalClientMemberIds().contains(memberId)) {
                throw new ClientNotFoundException("client with id " + memberId + ", or subsystem for it, not found");
            }
        }
        CertificateProfileInfoProvider provider = null;
        try {
            provider = new GetCertificateProfile(caInfo.getCertificateProfileInfo()).instance();
        } catch (Exception e) {
            throw new CertificateProfileInstantiationException(e);
        }
        SecurityServerId serverId = serverConfService.getSecurityServerId();

        if (KeyUsageInfo.AUTHENTICATION == keyUsageInfo) {
            String ownerName = globalConfFacade.getMemberName(serverConfService.getSecurityServerOwnerId());
            AuthCertificateProfileInfoParameters params = new AuthCertificateProfileInfoParameters(
                    serverId, ownerName);
            return provider.getAuthCertProfile(params);
        } else if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            String memberName = globalConfFacade.getMemberName(memberId);
            SignCertificateProfileInfoParameters params = new SignCertificateProfileInfoParameters(
                    serverId, memberId, memberName);
            return provider.getSignCertProfile(params);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Return ApprovedCAInfo for CA with given CN name
     * @param caName CN name
     * @throws CertificateAuthorityNotFoundException if matching CA was not found
     */
    public ApprovedCAInfo getCertificateAuthority(String caName) throws CertificateAuthorityNotFoundException {
        Collection<ApprovedCAInfo> cas = globalConfService.getApprovedCAsForThisInstance();
        Optional<ApprovedCAInfo> ca = cas.stream()
                .filter(item -> caName.equals(item.getName()))
                .findFirst();
        if (!ca.isPresent()) {
            throw new CertificateAuthorityNotFoundException("certificate authority "
                    + caName + " not_found");
        }
        return ca.get();
    }
}
