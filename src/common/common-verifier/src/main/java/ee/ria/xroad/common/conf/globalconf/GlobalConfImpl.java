/**
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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedCATypeV2;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedTSAType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.GlobalGroupType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.MemberClassType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.MemberType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.OcspInfoType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.SecurityServerType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.SubsystemType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.X509CertificateHolder;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;
import static ee.ria.xroad.common.util.CryptoUtils.certHash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Global configuration implementation
 */
@Slf4j
public class GlobalConfImpl implements GlobalConfProvider {

    private volatile ConfigurationDirectoryV2 confDir;

    GlobalConfImpl() {
        try {
            confDir = new ConfigurationDirectoryV2(getConfigurationPath());
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    @Override
    public void reload() {
        ConfigurationDirectoryV2 original = confDir;
        try {
            confDir = new ConfigurationDirectoryV2(getConfigurationPath(), original);
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    // ------------------------------------------------------------------------
    @Override
    public boolean isValid() {
        // it is important to get handle of confDir as this variable is volatile
        ConfigurationDirectoryV2 checkDir = confDir;
        String mainInstance = checkDir.getInstanceIdentifier();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            if (now.isAfter(checkDir.getPrivate(mainInstance).getExpiresOn())) {
                log.warn("Main privateParameters expired at {}", checkDir.getPrivate(mainInstance).getExpiresOn());
                return false;
            }
            if (now.isAfter(checkDir.getShared(mainInstance).getExpiresOn())) {
                log.warn("Main sharedParameters expired at {}", checkDir.getShared(mainInstance).getExpiresOn());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Error checking global configuration validity", e);

            return false;
        }
    }

    @Override
    public String getInstanceIdentifier() {
        return confDir.getInstanceIdentifier();
    }

    @Override
    public List<String> getInstanceIdentifiers() {
        return getSharedParameters().stream()
                .map(SharedParametersV2::getInstanceIdentifier)
                .collect(Collectors.toList());
    }

    @Override
    public List<SecurityServerId.Conf> getSecurityServers(
            String... instanceIdentifiers) {
        List<SecurityServerId.Conf> serverIds = new ArrayList<>();

        for (SharedParametersV2 p : getSharedParameters(instanceIdentifiers)) {
            for (SecurityServerType s : p.getSecurityServers()) {
                MemberType owner = SharedParametersV2.getOwner(s);
                serverIds.add(SecurityServerId.Conf.create(
                        p.getInstanceIdentifier(),
                        owner.getMemberClass().getCode(),
                        owner.getMemberCode(), s.getServerCode()));
            }
        }

        return serverIds;
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        List<MemberInfo> clients = new ArrayList<>();

        for (SharedParametersV2 p : getSharedParameters(instanceIdentifiers)) {
            for (MemberType member : p.getMembers()) {
                clients.add(new MemberInfo(p.createMemberId(member), member
                        .getName()));

                for (SubsystemType subsystem : member.getSubsystem()) {
                    clients.add(new MemberInfo(p.createSubsystemId(member,
                            subsystem), member.getName()));
                }
            }
        }

        return clients;
    }

    @Override
    public String getMemberName(ClientId clientId) {
        SharedParametersV2 p;
        try {
            p = confDir.getShared(clientId.getXRoadInstance());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p == null ? null : p.getMembers().stream()
                .filter(m -> p.createMemberId(m).memberEquals(clientId))
                .map(MemberType::getName)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(
            String... instanceIdentifiers) {
        List<GlobalGroupInfo> globalGroups = new ArrayList<>();

        for (SharedParametersV2 p : getSharedParameters(instanceIdentifiers)) {
            for (GlobalGroupType globalGroup : p.getGlobalGroups()) {
                globalGroups.add(
                        new GlobalGroupInfo(p.createGlobalGroupId(globalGroup),
                                globalGroup.getDescription()));
            }
        }

        return globalGroups;
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        SharedParametersV2 p;
        try {
            p = confDir.getShared(globalGroupId.getXRoadInstance());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p == null ? null : p.getGlobalGroups().stream()
                .filter(g -> g.getGroupCode().equals(
                        globalGroupId.getGroupCode()))
                .map(GlobalGroupType::getDescription)
                .findFirst().orElse(null);
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifiers) {
        return getSharedParameters(instanceIdentifiers).stream()
                .flatMap(p -> p.getGlobalSettings().getMemberClass().stream())
                .map(MemberClassType::getCode)
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<String> getProviderAddress(ClientId clientId) {
        if (clientId == null) {
            return null;
        }

        SharedParametersV2 p = getSharedParameters(clientId.getXRoadInstance());
        return p.getMemberAddresses().get(clientId);
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        if (serverId == null) {
            return null;
        }

        SharedParametersV2 p = getSharedParameters(serverId.getXRoadInstance());
        final SecurityServerType serverType = p.getSecurityServersById().get(serverId);
        if (serverType != null) {
            return serverType.getAddress();
        }

        return null;
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate member) throws Exception {
        return doGetOcspResponderAddressesForCertificate(member, false);
    }

    private List<String> doGetOcspResponderAddressesForCertificate(X509Certificate certificate, boolean certificateIsCA)
            throws Exception {
        List<String> responders = new ArrayList<>();

        for (SharedParametersV2 p : getSharedParameters()) {
            List<OcspInfoType> caOcspData = null;
            X509Certificate caCert;
            try {
                if (!certificateIsCA) {
                    caCert = getCaCert(null, certificate);
                } else {
                    caCert = certificate;
                }
                caOcspData = p.getCaCertsAndOcspData().get(caCert);
            } catch (CodedException e) {
                log.error("Unable to determine OCSP responders: {}", e);
            }
            if (caOcspData == null) {
                continue;
            }
            caOcspData.stream().map(OcspInfoType::getUrl)
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .forEach(responders::add);
        }

        String uri = CertUtils.getOcspResponderUriFromCert(certificate);
        if (uri != null) {
            responders.add(uri.trim());
        }

        return responders;
    }


    @Override
    public List<String> getOcspResponderAddressesForCaCertificate(X509Certificate caCert) throws Exception {
        return doGetOcspResponderAddressesForCertificate(caCert, true);
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        List<X509Certificate> responderCerts = new ArrayList<>();
        try {
            for (SharedParametersV2 p : getSharedParameters()) {
                for (List<OcspInfoType> ocspTypes
                        : p.getCaCertsAndOcspData().values()) {
                    ocspTypes.stream().map(OcspInfoType::getCert)
                            .filter(Objects::nonNull)
                            .map(CryptoUtils::readCertificate)
                            .forEach(responderCerts::add);
                }
            }
        } catch (Exception e) {
            log.error("Error while getting OCSP responder certificates", e);
            return Collections.emptyList();
        }

        return responderCerts;
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate memberCert) throws Exception {
        if (memberCert == null) {
            throw new IllegalArgumentException(
                    "Member certificate must be present to find CA cert!");
        }

        X509CertificateHolder ch = new X509CertificateHolder(
                memberCert.getEncoded());

        String[] instances = instanceIdentifier != null
                ? new String[] {instanceIdentifier} : new String[] {};

        return getSharedParameters(instances)
                .stream()
                .map(p -> p.getSubjectsAndCaCerts().get(ch.getIssuer()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(
                        () -> new CodedException(X_INTERNAL_ERROR,
                                "Certificate is not issued by approved "
                                        + "certification service provider."));
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return getSharedParameters().stream()
                .flatMap(p -> p.getSubjectsAndCaCerts().values().stream())
                .collect(Collectors.toList());
    }

    @Override
    public List<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return new ArrayList<>(getSharedParameters(instanceIdentifier).getSubjectsAndCaCerts().values());
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        if (subject == null) {
            throw new IllegalArgumentException(
                    "Member certificate must be present to find cert chain!");
        }

        List<X509Certificate> chain = new ArrayList<>();
        chain.add(subject);

        SharedParametersV2 p = getSharedParameters(instanceIdentifier);

        X509Certificate ca = p.getCaCertForSubject(subject);
        while (ca != null) {
            chain.add(ca);
            ca = p.getCaCertForSubject(ca);
        }

        if (chain.size() < 2) { // did not found any CA certs
            return null;
        }

        return CertChain.create(instanceIdentifier,
                chain.toArray(new X509Certificate[chain.size()]));
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate ca,
            X509Certificate ocspCert) {
        return getSharedParameters().stream()
                .map(p -> p.getCaCertsAndOcspData().get(ca))
                .filter(Objects::nonNull).flatMap(o -> o.stream())
                .map(OcspInfoType::getCert).filter(Objects::nonNull)
                .map(CryptoUtils::readCertificate)
                .filter(c -> c.equals(ocspCert))
                .findFirst().isPresent();
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        try {
            List<X509Certificate> certs = getAllCaCerts();
            return certs.toArray(new X509Certificate[certs.size()]);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public SecurityServerId.Conf getServerId(X509Certificate cert)
            throws Exception {
        String b64 = encodeBase64(certHash(cert));

        for (SharedParametersV2 p : getSharedParameters()) {
            SecurityServerType serverType = p.getServerByAuthCert().get(b64);
            if (serverType != null) {
                MemberType owner = SharedParametersV2.getOwner(serverType);
                return SecurityServerId.Conf.create(p.getInstanceIdentifier(),
                        owner.getMemberClass().getCode(),
                        owner.getMemberCode(),
                        serverType.getServerCode());
            }
        }

        return null;
    }

    @Override
    public ClientId.Conf getServerOwner(SecurityServerId serverId) {
        for (SharedParametersV2 p : getSharedParameters()) {
            SecurityServerType server = p.getSecurityServersById()
                    .get(serverId);
            if (server != null) {
                return p.createMemberId((MemberType) server.getOwner());
            }
        }

        return null;
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        byte[] inputCertHash = certHash(cert);
        return getSharedParameters().stream()
                .map(p -> p.getMemberAuthCerts().get(memberId))
                .filter(Objects::nonNull).flatMap(Collection::stream)
                .anyMatch(h -> Arrays.equals(inputCertHash, h));
    }

    @Override
    public Collection<ApprovedCAInfo> getApprovedCAs(
            String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedCAs()
            .stream()
            .map(ca -> createApprovedCAInfo(ca))
            .collect(Collectors.toList());
    }

    private ApprovedCAInfo createApprovedCAInfo(ApprovedCATypeV2 ca) {
        return new ApprovedCAInfo(
            ca.getName(),
            ca.isAuthenticationOnly(),
            ca.getCertificateProfileInfo()
        );
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            AuthCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        if (!CertUtils.isAuthCert(cert)) {
            throw new IllegalArgumentException(
                    "Certificate must be authentication certificate");
        }

        return getCertProfile(
            parameters.getServerId().getXRoadInstance(), cert
        ).getAuthCertProfile(parameters);
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        if (!CertUtils.isSigningCert(cert)) {
            throw new IllegalArgumentException(
                    "Certificate must be signing certificate");
        }

        return getCertProfile(
            parameters.getClientId().getXRoadInstance(), cert
        ).getSignCertProfile(parameters);
    }

    @Override
    public List<String> getApprovedTsps(String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().map(ApprovedTSAType::getUrl)
                .collect(Collectors.toList());
    }

    @Override
    public List<ApprovedTSAType> getApprovedTspTypes(String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs();
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().filter(t -> t.getUrl().equals(approvedTspUrl))
                .map(ApprovedTSAType::getName).findFirst().orElse(null);
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws Exception {
        return getSharedParameters().stream()
                .flatMap(p -> p.getApprovedTSAs().stream())
                .map(ApprovedTSAType::getCert)
                .filter(Objects::nonNull)
                .map(CryptoUtils::readCertificate)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getKnownAddresses() {
        return getSharedParameters().stream()
                .flatMap(p -> p.getKnownAddresses().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subjectId,
            GlobalGroupId groupId) {

        SharedParametersV2 p;
        try {
            p = confDir.getShared(groupId.getXRoadInstance());
        } catch (Exception e) {
            p = null;
            log.warn("Got exception while getting shared parameters.", e);
        }

        if (p == null) {
            return false;
        }

        GlobalGroupType group = p.findGlobalGroup(groupId);
        if (group == null) {
            return false;
        }

        return group.getGroupMember().stream().filter(m -> m.equals(subjectId))
                .findFirst().isPresent();
    }

    @Override
    public boolean isSecurityServerClient(ClientId clientId,
            SecurityServerId securityServerId) {
        SharedParametersV2 p = getSharedParameters(securityServerId
                .getXRoadInstance());
        return p.getSecurityServerClients().containsKey(securityServerId)
                && p.getSecurityServerClients().get(securityServerId)
                        .contains(clientId);
    }

    @Override
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        SharedParametersV2 p = getSharedParameters(securityServerId
                .getXRoadInstance());

        return p.getSecurityServersById().containsKey(securityServerId);
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return getSharedParameters().stream()
                .flatMap(p -> p.getVerificationCaCerts().stream())
                .collect(Collectors.toList());
    }

    @Override
    public String getManagementRequestServiceAddress() {
        return getPrivateParameters().getManagementService()
                .getAuthCertRegServiceAddress();
    }

    @Override
    public ClientId getManagementRequestService() {
        return getPrivateParameters().getManagementService()
                .getManagementRequestServiceProviderId();
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() throws Exception {
        byte[] certBytes = getPrivateParameters().getManagementService()
                .getAuthCertRegServiceCert();
        return certBytes != null ? readCertificate(certBytes) : null;
    }

    @Override
    public int getOcspFreshnessSeconds() {
        return getSharedParameters(getInstanceIdentifier())
                .getGlobalSettings().getOcspFreshnessSeconds().intValue();
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return getPrivateParameters().getTimeStampingIntervalSeconds()
                .intValue();
    }

    // ------------------------------------------------------------------------

    protected PrivateParametersV2 getPrivateParameters() {
        PrivateParametersV2 p;
        try {
            p = confDir.getPrivate(getInstanceIdentifier());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        if (p == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Invalid instance identifier: %s",
                    getInstanceIdentifier());
        }

        return p;
    }

    protected SharedParametersV2 getSharedParameters(String instanceIdentifier) {
        SharedParametersV2 p;
        try {
            p = confDir.getShared(instanceIdentifier);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        if (p == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Invalid instance identifier: %s", instanceIdentifier);
        }

        return p;
    }

    protected List<SharedParametersV2> getSharedParameters(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return confDir.getShared();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(this::getSharedParameters)
                .collect(Collectors.toList());
    }

    private CertificateProfileInfoProvider getCertProfile(
            String instanceIdentifier, X509Certificate cert) throws Exception {
        X509Certificate caCert = getCaCert(instanceIdentifier, cert);
        SharedParametersV2 p = getSharedParameters(instanceIdentifier);

        String certProfileProviderClass =
                p.getCaCertsAndCertProfiles().get(caCert);
        if (StringUtils.isBlank(certProfileProviderClass)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find certificate profile info for certificate "
                            + cert.getSubjectX500Principal().getName());
        }

        return new GetCertificateProfile(certProfileProviderClass).instance();
    }

    @Override
    public ApprovedCAInfo getApprovedCA(
            String instanceIdentifier, X509Certificate cert) throws CodedException {
        SharedParametersV2 p = getSharedParameters(instanceIdentifier);

        ApprovedCATypeV2 approvedCAType = p.getCaCertsAndApprovedCAData().get(cert);
        if (approvedCAType == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find approved CA info for certificate "
                            + cert.getSubjectX500Principal().getName());
        }

        return createApprovedCAInfo(approvedCAType);
    }

}
