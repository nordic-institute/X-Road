/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import ee.ria.xroad.common.conf.globalconf.sharedparameters.*;
import ee.ria.xroad.common.identifier.*;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.X509CertificateHolder;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;
import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Global configuration implementation
 */
@Slf4j
public class GlobalConfImpl implements GlobalConfProvider {

    // Default value used when no configurations are available
    private static final int DEFAULT_OCSP_FRESHNESS = 60;

    private ConfigurationDirectory confDir;

    GlobalConfImpl(boolean reloadIfChanged) {
        try {
            confDir = new CachingConfigurationDirectory(getConfigurationPath(),
                reloadIfChanged);
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    public GlobalConfImpl(ConfigurationDirectory confDir) {
        this.confDir = confDir;
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean isValid() {
        try {
            confDir.verifyUpToDate();
            return true;
        } catch (Exception e) {
            log.warn("Global configuration is invalid: {}", e);
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
                .collect(Collectors.toList());
    }

    @Override
    public ServiceId getServiceId(CentralServiceId serviceId) {
        SharedParameters p = getSharedParameters(serviceId.getXRoadInstance());

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
    public List<SecurityServerId> getSecurityServers(
            String... instanceIdentifiers) {
        List<SecurityServerId> serverIds = new ArrayList<SecurityServerId>();

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
            for (SecurityServerType s : p.getSecurityServers()) {
                MemberType owner = SharedParameters.getOwner(s);
                serverIds.add(SecurityServerId.create(
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

        for (SharedParameters p : getSharedParameters(instanceIdentifiers)) {
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
        SharedParameters p;
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
    public List<CentralServiceId> getCentralServices(
            String instanceIdentifier) {
        return getSharedParameters(instanceIdentifier)
                .getCentralServices()
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
                globalGroups.add(
                        new GlobalGroupInfo(p.createGlobalGroupId(globalGroup),
                                globalGroup.getDescription()));
            }
        }

        return globalGroups;
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        SharedParameters p;
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

        SharedParameters p = getSharedParameters(clientId.getXRoadInstance());
        return p.getMemberAddresses().get(clientId);
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        if (serverId == null) {
            return null;
        }

        SharedParameters p = getSharedParameters(serverId.getXRoadInstance());
        final SecurityServerType serverType = p.getSecurityServersById().get(serverId);
        if (serverType != null) {
            return serverType.getAddress();
        }

        return null;
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate member)
            throws Exception {
        List<String> responders = new ArrayList<>();
        for (SharedParameters p : getSharedParameters()) {
            List<OcspInfoType> caOcspData = null;
            X509Certificate caCert = null;
            try {
                caCert = getCaCert(null, member);
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
        String uri = CertUtils.getOcspResponderUriFromCert(member);
        if (uri != null) {
            responders.add(uri.trim());
        }
        return responders;
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        List<X509Certificate> responderCerts = new ArrayList<>();
        try {
            for (SharedParameters p : getSharedParameters()) {
                for (List<OcspInfoType> ocspTypes
                        : p.getCaCertsAndOcspData().values()) {
                    ocspTypes.stream().map(OcspInfoType::getCert)
                            .filter(Objects::nonNull)
                            .map(CryptoUtils::readCertificate)
                            .forEach(responderCerts::add);
                }
            }
        } catch (Exception e) {
            log.error("Error while getting OCSP responder certificates: ", e);
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
    public SecurityServerId getServerId(X509Certificate cert)
            throws Exception {
        String b64 = encodeBase64(certHash(cert));

        for (SharedParameters p : getSharedParameters()) {
            SecurityServerType serverType = p.getServerByAuthCert().get(b64);
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
                .filter(h -> Arrays.equals(inputCertHash, h)).findFirst()
                .isPresent();
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) throws Exception {
        // TODO TBD; Currently returning an empty set.
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
                .stream().map(ApprovedTSAType::getUrl)
                .collect(Collectors.toList());
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
        SharedParameters p = getSharedParameters(groupId.getXRoadInstance());

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
        SharedParameters p = getSharedParameters(securityServerId
                .getXRoadInstance());
        return p.getSecurityServerClients().containsKey(securityServerId)
                && p.getSecurityServerClients().get(securityServerId)
                        .contains(clientId);
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
                    .min(Integer::compare).orElse(DEFAULT_OCSP_FRESHNESS);
        } else {
            return getSharedParameters(getInstanceIdentifier())
                    .getGlobalSettings().getOcspFreshnessSeconds().intValue();
        }
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return getPrivateParameters().getTimeStampingIntervalSeconds()
                .intValue();
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void load(String fileName) throws Exception {
        confDir.setPath(Paths.get(getConfigurationPath()));
        confDir.reload();
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

    protected SharedParameters getSharedParameters(String instanceIdentifier) {
        SharedParameters p;
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

    protected List<SharedParameters> getSharedParameters(
            String... instanceIdentifiers) {
        if (ArrayUtils.isEmpty(instanceIdentifiers)) {
            return confDir.getShared();
        }

        return Arrays.stream(instanceIdentifiers)
                .map(this::getSharedParameters)
                .collect(Collectors.toList());
    }
}
