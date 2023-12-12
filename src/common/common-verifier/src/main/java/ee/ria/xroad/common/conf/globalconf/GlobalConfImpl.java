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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;
import static ee.ria.xroad.common.util.CryptoUtils.certHash;
import static ee.ria.xroad.common.util.CryptoUtils.certSha1Hash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Global configuration implementation
 */
@Slf4j
public class GlobalConfImpl implements GlobalConfProvider {

    private volatile VersionedConfigurationDirectory confDir;

    GlobalConfImpl() {
        try {
            confDir = new VersionedConfigurationDirectory(getConfigurationPath());
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    @Override
    public void reload() {
        VersionedConfigurationDirectory original = confDir;
        try {
            confDir = new VersionedConfigurationDirectory(getConfigurationPath(), original);
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    // ------------------------------------------------------------------------
    @Override
    public boolean isValid() {
        // it is important to get handle of confDir as this variable is volatile
        VersionedConfigurationDirectory checkDir = confDir;
        try {
            return !checkDir.isExpired();
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
                .map(SharedParameters::getInstanceIdentifier)
                .toList();
    }

    @Override
    public List<SecurityServerId.Conf> getSecurityServers(
            String... instanceIdentifiers) {
        List<SecurityServerId.Conf> serverIds = new ArrayList<>();

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (SharedParameters.SecurityServer s : p.getSecurityServers()) {
                serverIds.add(SecurityServerId.Conf.create(
                        p.getInstanceIdentifier(), s.getOwner().getMemberClass(),
                        s.getOwner().getMemberCode(), s.getServerCode())
                );
            }
        }

        return serverIds;
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        List<MemberInfo> clients = new ArrayList<>();

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (SharedParameters.Member member : p.getMembers()) {
                clients.add(new MemberInfo(createMemberId(p, member), member.getName()));

                for (SharedParameters.Subsystem subsystem : member.getSubsystems()) {
                    clients.add(new MemberInfo(createSubsystemId(p, member, subsystem), member.getName()));
                }
            }
        }

        return clients;
    }

    ClientId.Conf createMemberId(SharedParameters p, SharedParameters.Member member) {
        return ClientId.Conf.create(p.getInstanceIdentifier(), member.getMemberClass().getCode(), member.getMemberCode());
    }

    ClientId.Conf createSubsystemId(SharedParameters p, SharedParameters.Member member, SharedParameters.Subsystem subsystem) {
        return ClientId.Conf.create(
                p.getInstanceIdentifier(), member.getMemberClass().getCode(),
                member.getMemberCode(), subsystem.getSubsystemCode()
        );
    }

    @Override
    public String getMemberName(ClientId clientId) {
        Optional<SharedParameters> p;
        try {
            p = confDir.findShared(clientId.getXRoadInstance());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p.flatMap(params -> params.getMembers().stream()
                .filter(m -> createMemberId(p.get(), m).memberEquals(clientId))
                .map(SharedParameters.Member::getName)
                .findFirst()
        ).orElse(null);
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers) {
        List<GlobalGroupInfo> globalGroups = new ArrayList<>();
        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (SharedParameters.GlobalGroup globalGroup : p.getGlobalGroups()) {
                globalGroups.add(
                        new GlobalGroupInfo(createGlobalGroupId(globalGroup, p), globalGroup.getDescription())
                );
            }
        }
        return globalGroups;
    }

    GlobalGroupId.Conf createGlobalGroupId(SharedParameters.GlobalGroup globalGroup, SharedParameters p) {
        return GlobalGroupId.Conf.create(p.getInstanceIdentifier(), globalGroup.getGroupCode());
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        Optional<SharedParameters> p;
        try {
            p = confDir.findShared(globalGroupId.getXRoadInstance());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p.flatMap(params -> params.getGlobalGroups().stream()
                .filter(g -> g.getGroupCode().equals(globalGroupId.getGroupCode()))
                .map(SharedParameters.GlobalGroup::getDescription)
                .findFirst()
        ).orElse(null);
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifiers) {
        return getSharedParameters(instanceIdentifiers).stream()
                .flatMap(p -> p.getGlobalSettings().getMemberClasses().stream())
                .map(SharedParameters.MemberClass::getCode)
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<String> getProviderAddress(ClientId clientId) {
        if (clientId == null) {
            return Collections.emptySet();
        }

        SharedParameters p = getSharedParameters(clientId.getXRoadInstance());
        return p.getMemberAddresses().get(clientId);
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        if (serverId == null) {
            return null;
        }

        SharedParameters p = getSharedParameters(serverId.getXRoadInstance());
        final SharedParameters.SecurityServer server = p.getSecurityServersById().get(serverId);
        if (server != null) {
            return server.getAddress();
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

        for (SharedParameters p : getSharedParameters()) {
            List<SharedParameters.OcspInfo> caOcspData = null;
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
            caOcspData.stream().map(SharedParameters.OcspInfo::getUrl)
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
            for (SharedParameters p : getSharedParameters()) {
                for (List<SharedParameters.OcspInfo> ocsps : p.getCaCertsAndOcspData().values()) {
                    ocsps.stream()
                            .map(SharedParameters.OcspInfo::getCert)
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
                .toList();
    }

    @Override
    public List<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return new ArrayList<>(getSharedParameters(instanceIdentifier).getSubjectsAndCaCerts().values());
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        if (subject == null) {
            throw new IllegalArgumentException("Member certificate must be present to find cert chain!");
        }

        List<X509Certificate> chain = new ArrayList<>();
        chain.add(subject);

        SharedParameters sharedParams = getSharedParameters(instanceIdentifier);

        X509Certificate ca = getCaCertForSubject(subject, sharedParams);
        while (ca != null) {
            chain.add(ca);
            ca = getCaCertForSubject(ca, sharedParams);
        }

        if (chain.size() < 2) { // did not find any CA certs
            return null;
        }

        return CertChain.create(instanceIdentifier, chain.toArray(new X509Certificate[chain.size()]));
    }

    X509Certificate getCaCertForSubject(X509Certificate subject, SharedParameters sharedParameters)
            throws CertificateEncodingException, IOException {
        X509CertificateHolder certHolder =
                new X509CertificateHolder(subject.getEncoded());
        if (certHolder.getSubject().equals(certHolder.getIssuer())) {
            return null;
        }

        return sharedParameters.getSubjectsAndCaCerts().get(certHolder.getIssuer());
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate ca,
            X509Certificate ocspCert) {
        return getSharedParameters().stream()
                .map(p -> p.getCaCertsAndOcspData().get(ca))
                .filter(Objects::nonNull).flatMap(Collection::stream)
                .map(SharedParameters.OcspInfo::getCert).filter(Objects::nonNull)
                .map(CryptoUtils::readCertificate)
                .anyMatch(c -> c.equals(ocspCert));
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
    public SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception {
        for (SharedParameters p : getSharedParameters()) {
            String b64 = encodeBase64(calculateCertHash(p, cert));
            SharedParameters.SecurityServer server = p.getServerByAuthCert().get(b64);
            if (server != null) {
                return SecurityServerId.Conf.create(
                        p.getInstanceIdentifier(), server.getOwner().getMemberClass(),
                        server.getOwner().getMemberCode(), server.getServerCode()
                );
            }
        }

        return null;
    }

    private byte[] calculateCertHash(SharedParameters p, X509Certificate cert)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        Integer version = VersionedConfigurationDirectory.getVersion(
                Path.of(confDir.getPath().toString(), p.getInstanceIdentifier(), ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS)
        );
        if (version != null && version > 2) {
            return certHash(cert.getEncoded());
        } else {
            return certSha1Hash(cert.getEncoded());
        }
    }

    @Override
    public ClientId getServerOwner(SecurityServerId serverId) {
        for (SharedParameters p : getSharedParameters()) {
            SharedParameters.SecurityServer server = p.getSecurityServersById().get(serverId);
            if (server != null) {
                return server.getOwner();
            }
        }
        return null;
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        for (SharedParameters p : getSharedParameters()) {
            byte[] inputCertHash = calculateCertHash(p, cert);
            boolean match = Optional.ofNullable(p.getMemberAuthCerts().get(memberId)).stream()
                    .flatMap(Collection::stream)
                    .anyMatch(h -> Arrays.equals(inputCertHash, h));
            if (match) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ApprovedCAInfo> getApprovedCAs(
            String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedCAs()
            .stream()
            .map(this::createApprovedCAInfo)
            .toList();
    }

    private ApprovedCAInfo createApprovedCAInfo(SharedParameters.ApprovedCA ca) {
        return new ApprovedCAInfo(
            ca.getName(),
            ca.getAuthenticationOnly(),
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
    public List<String> getApprovedTspUrls(String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().map(SharedParameters.ApprovedTSA::getUrl)
                .toList();
    }

    @Override
    public List<SharedParameters.ApprovedTSA> getApprovedTsps(String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs();
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().filter(t -> t.getUrl().equals(approvedTspUrl))
                .map(SharedParameters.ApprovedTSA::getName).findFirst().orElse(null);
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws Exception {
        return getSharedParameters().stream()
                .flatMap(p -> p.getApprovedTSAs().stream())
                .map(SharedParameters.ApprovedTSA::getCert)
                .filter(Objects::nonNull)
                .map(CryptoUtils::readCertificate)
                .toList();
    }

    @Override
    public Set<String> getKnownAddresses() {
        return getSharedParameters().stream()
                .flatMap(p -> p.getKnownAddresses().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subjectId, GlobalGroupId groupId) {
        return findGlobalGroup(groupId)
                .filter(group -> group.getGroupMembers().stream().anyMatch(m -> m.equals(subjectId)))
                .isPresent();
    }

    Optional<SharedParameters.GlobalGroup> findGlobalGroup(GlobalGroupId groupId) {
        Optional<SharedParameters> sharedParameters = confDir.findShared(groupId.getXRoadInstance());
        return sharedParameters.flatMap(params -> params.getGlobalGroups().stream()
                        .filter(g -> g.getGroupCode().equals(groupId.getGroupCode()))
                        .findFirst());
    }

    @Override
    public boolean isSecurityServerClient(ClientId clientId,
            SecurityServerId securityServerId) {
        SharedParameters p = getSharedParameters(securityServerId
                .getXRoadInstance());
        return p.getSecurityServerClients().containsKey(securityServerId)
                && p.getSecurityServerClients().get(securityServerId)
                        .contains(clientId);
    }

    @Override
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        SharedParameters p = getSharedParameters(securityServerId
                .getXRoadInstance());

        return p.getSecurityServersById().containsKey(securityServerId);
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return getSharedParameters().stream()
                .flatMap(p -> p.getVerificationCaCerts().stream())
                .toList();
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

    protected PrivateParameters getPrivateParameters() {
        Optional<PrivateParameters> p;
        try {
            p = confDir.findPrivate(getInstanceIdentifier());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p.orElseThrow(() ->
                new CodedException(X_INTERNAL_ERROR, "Private params for instance identifier %s not found", getInstanceIdentifier()));
    }

    protected SharedParameters getSharedParameters(String instanceIdentifier) {
        Optional<SharedParameters> p;
        try {
            p = confDir.findShared(instanceIdentifier);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p.orElseThrow(() ->
                new CodedException(X_INTERNAL_ERROR, "Shared params for instance identifier %s not found", instanceIdentifier));
    }

    protected List<SharedParameters> getSharedParameters(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return confDir.getShared();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(this::getSharedParameters)
                .toList();
    }

    private CertificateProfileInfoProvider getCertProfile(
            String instanceIdentifier, X509Certificate cert) throws Exception {
        X509Certificate caCert = getCaCert(instanceIdentifier, cert);
        SharedParameters p = getSharedParameters(instanceIdentifier);

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
        SharedParameters p = getSharedParameters(instanceIdentifier);

        SharedParameters.ApprovedCA approvedCA = p.getCaCertsAndApprovedCAData().get(cert);
        if (approvedCA == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find approved CA info for certificate "
                            + cert.getSubjectX500Principal().getName());
        }

        return createApprovedCAInfo(approvedCA);
    }

}
