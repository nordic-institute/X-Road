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
package org.niis.xroad.globalconf.impl;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.PrivateParameters;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.SharedParametersCache;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.certHash;
import static ee.ria.xroad.common.util.CryptoUtils.certSha1Hash;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static java.util.stream.Collectors.toSet;

/**
 * Global configuration implementation
 */
@Slf4j
@Singleton
@ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
public class GlobalConfImpl implements GlobalConfProvider {

    private final GlobalConfSource globalConfSource;
    private final CertChainFactory certChainFactory;
    private final GlobalConfExtensions globalConfExtensions;

    public GlobalConfImpl(GlobalConfSource globalConfSource) {
        this.globalConfSource = globalConfSource;
        this.certChainFactory = new CertChainFactory(this);
        this.globalConfExtensions = new GlobalConfExtensions(globalConfSource, new GlobalConfExtensionFactoryImpl());
    }

    @Override
    public void reload() {
        globalConfSource.reload();
    }

    // ------------------------------------------------------------------------
    @Override
    public boolean isValid() {
        // it is important to get handle of confDir as this variable is volatile
        try {
            return !globalConfSource.isExpired();
        } catch (Exception e) {
            log.warn("Error checking global configuration validity", e);
            return false;
        }
    }

    /**
     * Verifies that the global configuration is valid. Throws exception
     * with error code ErrorCodes.X_OUTDATED_GLOBALCONF if the it is too old.
     */
    @Override
    public void verifyValidity() {
        if (!isValid()) {
            throw new CodedException(X_OUTDATED_GLOBALCONF,
                    "Global configuration is expired");
        }
    }

    @Override
    public String getInstanceIdentifier() {
        return globalConfSource.getInstanceIdentifier();
    }

    @Override
    public Set<String> getInstanceIdentifiers() {
        return getSharedParameters().stream()
                .map(SharedParameters::getInstanceIdentifier)
                .collect(toSet());
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
                var memberId = createMemberId(p.getInstanceIdentifier(), member);
                clients.add(new MemberInfo(memberId, member.getName(), null));

                for (SharedParameters.Subsystem subsystem : member.getSubsystems()) {
                    var subsystemId = createSubsystemId(p.getInstanceIdentifier(), member, subsystem);
                    clients.add(new MemberInfo(subsystemId, member.getName(), subsystem.getSubsystemName()));
                }
            }
        }

        return clients;
    }

    ClientId.Conf createMemberId(String instanceIdentifier, SharedParameters.Member member) {
        return ClientId.Conf.create(instanceIdentifier, member.getMemberClass().getCode(), member.getMemberCode());
    }

    ClientId.Conf createSubsystemId(String instanceIdentifier, SharedParameters.Member member, SharedParameters.Subsystem subsystem) {
        return ClientId.Conf.create(
                instanceIdentifier, member.getMemberClass().getCode(),
                member.getMemberCode(), subsystem.getSubsystemCode()
        );
    }

    @Override
    public String getMemberName(ClientId clientId) {
        return findShared(clientId.getXRoadInstance()).stream()
                .map(SharedParameters::getMembers)
                .flatMap(List::stream)
                .filter(m -> createMemberId(clientId.getXRoadInstance(), m).memberEquals(clientId))
                .findFirst()
                .map(SharedParameters.Member::getName)
                .orElse(null);
    }

    @Override
    public String getSubsystemName(ClientId clientId) {
        return internalGetSubsystemName(clientId).orElse(null);
    }

    private Optional<String> internalGetSubsystemName(ClientId clientId) {
        return findShared(clientId.getXRoadInstance()).stream()
                .map(SharedParameters::getMembers)
                .flatMap(List::stream)
                .filter(m -> createMemberId(clientId.getXRoadInstance(), m).memberEquals(clientId))
                .map(SharedParameters.Member::getSubsystems)
                .flatMap(List::stream)
                .filter(s -> s.getSubsystemCode().equals(clientId.getSubsystemCode()))
                .findFirst()
                .map(SharedParameters.Subsystem::getSubsystemName);
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
        return findShared(globalGroupId.getXRoadInstance())
                .flatMap(params -> params.getGlobalGroups().stream()
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
                .collect(toSet());
    }

    @Override
    public Collection<String> getProviderAddress(ClientId clientId) {
        if (clientId == null) {
            return Set.of();
        }

        return getSharedParametersCache(clientId.getXRoadInstance()).getMemberAddresses().get(clientId);
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        if (serverId == null) {
            return null;
        }

        final SharedParameters.SecurityServer server = getSharedParametersCache(serverId.getXRoadInstance())
                .getSecurityServersById().get(serverId);
        if (server != null) {
            return server.getAddress();
        }

        return null;
    }

    @Override
    public ClientId.Conf getSubjectName(SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
        log.trace("getSubjectName({})", parameters.getClientId());

        try {
            return getSignCertificateProfileInfo(parameters, cert)
                    .getSubjectIdentifier(cert);
        } catch (CertificateEncodingException | IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate member) throws Exception {
        return doGetOcspResponderAddressesForCertificate(member, false);
    }

    private List<String> doGetOcspResponderAddressesForCertificate(X509Certificate certificate, boolean certificateIsCA)
            throws Exception {
        List<String> responders = new ArrayList<>();

        for (SharedParametersCache p : globalConfSource.getSharedParametersCaches()) {
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
                log.error("Unable to determine OCSP responders", e);
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
            for (SharedParametersCache p : getSharedParametersCaches()) {
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
            return List.of();
        }

        return responderCerts;
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
                                     X509Certificate memberCert) throws CertificateEncodingException, IOException {
        if (memberCert == null) {
            throw new IllegalArgumentException(
                    "Member certificate must be present to find CA cert!");
        }

        X509CertificateHolder ch = new X509CertificateHolder(
                memberCert.getEncoded());

        String[] instances = instanceIdentifier != null
                ? new String[]{instanceIdentifier} : new String[]{};

        return getSharedParametersCaches(instances)
                .stream()
                .map(p -> p.getSubjectsAndCaCerts().get(ch.getIssuer()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(
                        () -> new CodedException(X_INTERNAL_ERROR,
                                "Certificate is not issued by approved certification service provider."));
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return getSharedParametersCaches().stream()
                .flatMap(p -> p.getSubjectsAndCaCerts().values().stream())
                .toList();
    }

    @Override
    public List<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return new ArrayList<>(getSharedParametersCache(instanceIdentifier).getSubjectsAndCaCerts().values());
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
                                  X509Certificate subject) throws Exception {
        if (subject == null) {
            throw new IllegalArgumentException("Member certificate must be present to find cert chain!");
        }

        List<X509Certificate> chain = new ArrayList<>();
        chain.add(subject);

        SharedParametersCache sharedParams = getSharedParametersCache(instanceIdentifier);

        X509Certificate ca = getCaCertForSubject(subject, sharedParams);
        while (ca != null) {
            chain.add(ca);
            ca = getCaCertForSubject(ca, sharedParams);
        }

        if (chain.size() < 2) { // did not find any CA certs
            return null;
        }

        return certChainFactory.create(instanceIdentifier, chain.toArray(new X509Certificate[0]));
    }

    X509Certificate getCaCertForSubject(X509Certificate subject, SharedParametersCache sharedParameters)
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
        return getSharedParametersCaches().stream()
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
            return certs.toArray(new X509Certificate[0]);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception {
        for (SharedParametersCache p : getSharedParametersCaches()) {
            String b64 = encodeBase64(calculateCertHash(p.getInstanceIdentifier(), cert));
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

    private byte[] calculateCertHash(String instanceIdentifier, X509Certificate cert)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        Integer version = globalConfSource.getVersion();
        if (version != null && version > 2) {
            return certHash(cert.getEncoded());
        } else {
            return certSha1Hash(cert.getEncoded());
        }
    }

    @Override
    public ClientId getServerOwner(SecurityServerId serverId) {
        for (SharedParametersCache p : getSharedParametersCaches()) {
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
        for (SharedParametersCache p : getSharedParametersCaches()) {
            byte[] inputCertHash = calculateCertHash(p.getInstanceIdentifier(), cert);
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
                ca.getCertificateProfileInfo(),
                ca.getAcmeServer() != null ? ca.getAcmeServer().getDirectoryURL() : null,
                ca.getAcmeServer() != null ? ca.getAcmeServer().getIpAddress() : null,
                ca.getAcmeServer() != null ? ca.getAcmeServer().getAuthenticationCertificateProfileId() : null,
                ca.getAcmeServer() != null ? ca.getAcmeServer().getSigningCertificateProfileId() : null
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
            X509Certificate cert) throws CertificateEncodingException, IOException {
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
        return getSharedParametersCaches().stream()
                .flatMap(p -> p.getKnownAddresses().stream())
                .collect(toSet());
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subjectId, GlobalGroupId groupId) {
        return findGlobalGroup(groupId)
                .filter(group -> group.getGroupMembers().stream().anyMatch(m -> m.equals(subjectId)))
                .isPresent();
    }

    Optional<SharedParameters.GlobalGroup> findGlobalGroup(GlobalGroupId groupId) {
        return globalConfSource.findShared(groupId.getXRoadInstance())
                .flatMap(params -> params.getGlobalGroups().stream()
                        .filter(g -> g.getGroupCode().equals(groupId.getGroupCode()))
                        .findFirst());
    }

    @Override
    public boolean isSecurityServerClient(ClientId clientId,
                                          SecurityServerId securityServerId) {
        SharedParametersCache p = getSharedParametersCache(securityServerId
                .getXRoadInstance());
        return p.getSecurityServerClients().containsKey(securityServerId)
                && p.getSecurityServerClients().get(securityServerId)
                .contains(clientId);
    }

    @Override
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        return getSharedParametersCache(securityServerId.getXRoadInstance())
                .getSecurityServersById()
                .containsKey(securityServerId);
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return getSharedParametersCaches().stream()
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
        return certBytes != null ? CryptoUtils.readCertificate(certBytes) : null;
    }

    @Override
    public int getOcspFreshnessSeconds() {
        return getSharedParameters(getInstanceIdentifier())
                .getGlobalSettings().getOcspFreshnessSeconds();
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return getPrivateParameters().getTimeStampingIntervalSeconds();
    }

    private Optional<SharedParameters> findShared(String xroadInstance) {
        try {
            return globalConfSource.findShared(xroadInstance);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    // ------------------------------------------------------------------------

    protected PrivateParameters getPrivateParameters() {
        Optional<PrivateParameters> p;
        try {
            p = globalConfSource.findPrivate(getInstanceIdentifier());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        return p.orElseThrow(() ->
                new CodedException(X_INTERNAL_ERROR, "Private params for instance identifier %s not found", getInstanceIdentifier()));
    }

    protected SharedParameters getSharedParameters(String instanceIdentifier) {
        return findShared(instanceIdentifier)
                .orElseThrow(() ->
                        new CodedException(X_INTERNAL_ERROR, "Shared params for instance identifier %s not found", instanceIdentifier));
    }

    protected List<SharedParameters> getSharedParameters(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return globalConfSource.getShared();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(this::getSharedParameters)
                .toList();
    }

    private SharedParametersCache getSharedParametersCache(String instanceIdentifier) {
        try {
            return globalConfSource.findSharedParametersCache(instanceIdentifier).orElseThrow(() ->
                    new CodedException(X_INTERNAL_ERROR, "Shared params for instance identifier %s not found", instanceIdentifier));
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    protected List<SharedParametersCache> getSharedParametersCaches(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return globalConfSource.getSharedParametersCaches();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(this::getSharedParametersCache)
                .toList();
    }

    private CertificateProfileInfoProvider getCertProfile(
            String instanceIdentifier, X509Certificate cert) throws CertificateEncodingException, IOException {
        X509Certificate caCert = getCaCert(instanceIdentifier, cert);
        SharedParametersCache p = getSharedParametersCache(instanceIdentifier);

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
        SharedParametersCache p = getSharedParametersCache(instanceIdentifier);

        SharedParameters.ApprovedCA approvedCA = p.getCaCertsAndApprovedCAData().get(cert);
        if (approvedCA == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find approved CA info for certificate "
                            + cert.getSubjectX500Principal().getName());
        }

        return createApprovedCAInfo(approvedCA);
    }

    @Override
    public GlobalConfExtensions getGlobalConfExtensions() {
        return globalConfExtensions;
    }

    @Override
    public OptionalInt getVersion() {
        try {
            return Optional.ofNullable(globalConfSource.getVersion()).stream()
                    .mapToInt(Integer::intValue)
                    .findFirst();
        } catch (GlobalConfInitException e) {
            log.warn("Error getting global configuration version", e);
            return OptionalInt.empty();
        }
    }

    @Override
    public Optional<SharedParameters.MaintenanceMode> getMaintenanceMode(SecurityServerId serverId) {
        return Optional.ofNullable(serverId)
                .map(id -> getSharedParametersCache(id.getXRoadInstance()).getSecurityServersById().get(id))
                .map(SharedParameters.SecurityServer::getMaintenanceMode);
    }

    @Override
    public Optional<SharedParameters.MaintenanceMode> getMaintenanceMode(String instanceIdentifier, String serverAddress) {
        return Optional.ofNullable(serverAddress)
                .map(addr -> getSharedParametersCache(instanceIdentifier).getSecurityServersByAddress().get(addr))
                .map(SharedParameters.SecurityServer::getMaintenanceMode);
    }

    @Override
    public Set<SecurityServerId> getClientSecurityServers(ClientId clientId) {
        return getSharedParametersCache(clientId.getXRoadInstance())
                .getSecurityServersByClientId().getOrDefault(clientId, Set.of())
                .stream()
                .map(securityServer -> SecurityServerId.Conf.create(securityServer.getOwner(), securityServer.getServerCode()))
                .collect(toSet());
    }
}
