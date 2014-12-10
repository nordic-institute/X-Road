package ee.cyber.sdsb.common.conf.globalconf;

import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.X509CertificateHolder;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.CentralServiceType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.GlobalGroupType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.IdentifierDecoderType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.MemberType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.OcspInfoType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.SecurityServerType;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.SubsystemType;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.CertUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.SystemProperties.getConfigurationPath;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

@Slf4j
public class GlobalConfImpl implements GlobalConfProvider {

    private final ConfigurationDirectory configurationDirectory;

    public GlobalConfImpl(boolean reloadIfChanged) {
        try {
            configurationDirectory =
                    new ConfigurationDirectory(getConfigurationPath(),
                            reloadIfChanged);
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    // ------------------------------------------------------------------------

    @Override
    public String getInstanceIdentifier() {
        return configurationDirectory.getInstanceIdentifier();
    }

    @Override
    public List<String> getInstanceIdentifiers() {
        return getSharedParameters().stream()
                .map(p -> p.getInstanceIdentifier())
                .collect(Collectors.toList());
    }

    @Override
    public ServiceId getServiceId(CentralServiceId serviceId) {
        SharedParameters p = getSharedParameters(serviceId.getSdsbInstance());

        for (CentralServiceType centralServiceType : p.getCentralServices()) {
            if (centralServiceType.getImplementingService() == null) {
                continue;
            }

            if (serviceId.getServiceCode().equals(
                    centralServiceType.getServiceCode())) {
                return centralServiceType.getImplementingService();
            }
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Cannot find implementing service for central service '%s'",
                serviceId);
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        List<MemberInfo> clients = new ArrayList<>();

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (MemberType member : p.getMembers()) {
                clients.add(new MemberInfo(p.createMemberId(member),
                        member.getName()));

                for (SubsystemType subsystem : member.getSubsystem()) {
                    clients.add(new MemberInfo(
                            p.createSubsystemId(member, subsystem),
                            member.getName()));
                }
            }
        }

        return clients;
    }

    @Override
    public String getMemberName(ClientId clientId) {
        SharedParameters p = getSharedParameters(clientId.getSdsbInstance());
        return p.getMembers().stream()
                .filter(m -> p.createMemberId(m).memberEquals(clientId))
                .map(m -> m.getName())
                .findFirst().orElse(null);
    }

    @Override
    public List<CentralServiceId> getCentralServices(
            String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getCentralServices()
                .stream()
                .map(c -> CentralServiceId.create(instanceIdentifier,
                        c.getServiceCode()))
                .collect(Collectors.toList());
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(
            String... instanceIdentifiers) {
        List<GlobalGroupInfo> globalGroups = new ArrayList<>();

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (GlobalGroupType globalGroup : p.getGlobalGroups()) {
                globalGroups.add(new GlobalGroupInfo(
                        p.createGlobalGroupId(globalGroup),
                        globalGroup.getDescription()));
            }
        }

        return globalGroups;
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        return getSharedParameters(globalGroupId.getSdsbInstance())
                .getGlobalGroups().stream()
                .filter(g -> g.getGroupCode().equals(
                        globalGroupId.getGroupCode()))
                .map(g -> g.getDescription())
                .findFirst().orElse(null);
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifiers) {
        return getSharedParameters(instanceIdentifiers).stream()
                .flatMap(p -> p.getGlobalSettings().getMemberClass().stream())
                .map(m -> m.getCode())
                .collect(Collectors.toSet());
    }

    @Override
    public String getProviderAddress(X509Certificate authCert)
            throws Exception {
        if (authCert == null) {
            return null;
        }

        byte[] inputCertHash = certHash(authCert);

        for (SharedParameters p : getSharedParameters()) {
            for (SecurityServerType securityServer : p.getSecurityServers()) {
                for (byte[] hash : securityServer.getAuthCertHash()) {
                    if (Arrays.equals(inputCertHash, hash)) {
                        return securityServer.getAddress();
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Collection<String> getProviderAddress(ClientId clientId) {
        if (clientId == null) {
            return null;
        }

        SharedParameters p = getSharedParameters(clientId.getSdsbInstance());
        return p.getMemberAddresses().get(clientId);
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate member)
            throws Exception {
        List<String> responders = new ArrayList<>();

        for (SharedParameters p : getSharedParameters()) {
            List<OcspInfoType> caOcspData =
                    p.getCaCertsAndOcspData().get(getCaCert(null, member));
            if (caOcspData == null) {
                continue;
            }

            caOcspData.stream().map(c -> c.getUrl())
                .filter(StringUtils::isNotBlank)
                .forEach(url -> responders.add(url.trim()));
        }

        String uri = CertUtils.getOcspResponderUriFromCert(member);
        if (uri != null) {
            responders.add(uri.trim());
        }

        return responders;
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        List<X509Certificate> responderCerts = new ArrayList<>();

        for (SharedParameters p : getSharedParameters()) {
            try {
                for (List<OcspInfoType> ocspTypes
                        : p.getCaCertsAndOcspData().values()) {
                    ocspTypes.stream().filter(t -> t.getCert() != null)
                        .forEach(ocspType ->
                                responderCerts.add(
                                        readCertificate(ocspType.getCert())));
                }
            } catch (Exception e) {
                log.error("Error while getting OCSP responder certificates: ",
                        e);
                return Collections.emptyList();
            }
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

        X509CertificateHolder ch =
                new X509CertificateHolder(memberCert.getEncoded());

        String[] instances = instanceIdentifier != null
                ? new String[] { instanceIdentifier } : new String[] {};

        return getSharedParameters(instances).stream()
                .map(p -> p.getSubjectsAndCaCerts().get(ch.getIssuer()))
                .filter(Objects::nonNull).findFirst().orElseThrow(
                        () -> new CodedException(X_INTERNAL_ERROR,
                                "Unable to find CA certificate for member %s "
                                        + "(issuer = %s)",
                                        ch.getSubject(), ch.getIssuer()));
    }

    @Override
    public List<X509Certificate> getAllCaCerts() throws CertificateException {
        return getSharedParameters().stream()
                .flatMap(p -> p.getSubjectsAndCaCerts().values().stream())
                .collect(Collectors.toList());
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

        SharedParameters p = getSharedParameters(instanceIdentifier);

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
                .map(o -> o.getCert()).filter(Objects::nonNull)
                .map(c -> readCertificate(c)).filter(c -> c.equals(ocspCert))
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
    public SecurityServerId getServerId(X509Certificate cert) throws Exception {
        String base64 = encodeBase64(certHash(cert));

        for (SharedParameters p : getSharedParameters()) {
            SecurityServerType serverType =
                    p.getServerByAuthCert().get(base64);
            if (serverType != null) {
                MemberType owner = SharedParameters.getOwner(serverType);
                return SecurityServerId.create(p.getInstanceIdentifier(),
                        owner.getMemberClass().getCode(),
                        owner.getMemberCode(),
                        serverType.getServerCode());

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
                .filter(Objects::nonNull).flatMap(h -> h.stream())
                .filter(h -> Arrays.equals(inputCertHash, h))
                .findFirst().isPresent();
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) throws Exception {
        // TODO: TBD; Currently returning an empty set.
        return new HashSet<>();
    }

    @Override
    public ClientId getSubjectName(String instanceIdentifier,
            X509Certificate cert) throws Exception {
        X509Certificate caCert = getCaCert(instanceIdentifier, cert);

        SharedParameters p = getSharedParameters(instanceIdentifier);

        IdentifierDecoderType decoder =
                p.getCaCertsAndIdentifierDecoders().get(caCert);
        if (decoder != null) {
            return p.getSubjectName(cert, decoder);
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Could not find name extractor for certificate "
                        + cert.getSubjectX500Principal().getName());
    }

    @Override
    public List<String> getApprovedTsps(String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().map(tsa -> tsa.getUrl())
                .collect(Collectors.toList());
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        return getSharedParameters(instanceIdentifier).getApprovedTSAs()
                .stream().filter(t -> t.getUrl().equals(approvedTspUrl))
                .map(t -> t.getName()).findFirst().orElse(null);
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws Exception {
        return getSharedParameters().stream()
                .flatMap(p -> p.getApprovedTSAs().stream())
                .map(t -> t.getCert())
                .filter(Objects::nonNull)
                .map(c -> readCertificate(c))
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
        SharedParameters p = getSharedParameters(groupId.getSdsbInstance());

        GlobalGroupType group = p.findGlobalGroup(groupId);
        if (group == null) {
            return false;
        }

        return group.getGroupMember().stream()
                .filter(m -> m.equals(subjectId))
                .findFirst().isPresent();
    }

    @Override
    public boolean isSecurityServerClient(ClientId clientId,
            SecurityServerId securityServerId) {
        SharedParameters p =
                getSharedParameters(securityServerId.getSdsbInstance());
        return p.getSecurityServerClients().containsKey(securityServerId)
                && p.getSecurityServerClients().get(securityServerId).contains(
                        clientId);
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
    public int getOcspFreshnessSeconds(boolean smallestValue) {
        if (smallestValue) {
            return getSharedParameters().stream()
                    .map(p -> p.getGlobalSettings().getOcspFreshnessSeconds())
                    .filter(Objects::nonNull).map(BigInteger::intValue)
                    .min(Integer::compare).orElse(60);
        } else {
            return getSharedParameters(getInstanceIdentifier())
                    .getGlobalSettings().getOcspFreshnessSeconds().intValue();
        }
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return getPrivateParameters()
                .getTimeStampingIntervalSeconds().intValue();
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void load(String fileName) throws Exception {
        configurationDirectory.reload();
    }

    @Override
    public void save() throws Exception {
        // do nothing
    }

    @Override
    public void save(OutputStream out) throws Exception {
        // do nothing
    }

    // ------------------------------------------------------------------------

    protected PrivateParameters getPrivateParameters() {
        PrivateParameters p;
        try {
            p = configurationDirectory.getPrivate(getInstanceIdentifier());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        if (p == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Invalid instance identifier: %s", getInstanceIdentifier());
        }

        return p;
    }

    protected SharedParameters getSharedParameters(String instanceIdentifier) {
        SharedParameters p;
        try {
            p = configurationDirectory.getShared(instanceIdentifier);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        if (p == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Invalid instance identifier: %s", instanceIdentifier);
        }

        return p;
    }

    protected List<SharedParameters> getSharedParameters(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return configurationDirectory.getShared();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(instance -> getSharedParameters(instance))
                .collect(Collectors.toList());
    }
}
