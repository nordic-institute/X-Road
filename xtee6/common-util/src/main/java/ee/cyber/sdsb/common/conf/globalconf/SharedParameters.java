package ee.cyber.sdsb.common.conf.globalconf;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import lombok.AccessLevel;
import lombok.Getter;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;

import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.*;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Contains shared parameters of a configuration instance.
 */
@Getter(AccessLevel.PACKAGE)
public class SharedParameters extends AbstractXmlConf<SharedParametersType> {

    /**
     * The content identifier for the shared parameters.
     */
    public static final String CONTENT_ID_SHARED_PARAMETERS =
            "SHARED-PARAMETERS";

    /**
     * The default file name of shared parameters.
     */
    public static final String FILE_NAME_SHARED_PARAMETERS =
            "shared-params.xml";

    // Cached items, filled at conf reload
    private final Map<X500Name, X509Certificate> subjectsAndCaCerts =
            new HashMap<>();
    private final Map<X509Certificate, IdentifierDecoderType> caCertsAndIdentifierDecoders =
            new HashMap<>();
    private final Map<X509Certificate, List<OcspInfoType>> caCertsAndOcspData =
            new HashMap<>();
    private final Map<ClientId, Set<String>> memberAddresses = new HashMap<>();
    private final Map<ClientId, Set<byte[]>> memberAuthCerts = new HashMap<>();
    private final Map<String, SecurityServerType> serverByAuthCert =
            new HashMap<>();
    private final Map<SecurityServerId, Set<ClientId>> securityServerClients =
            new HashMap<>();
    private final List<X509Certificate> verificationCaCerts = new ArrayList<>();
    private final Set<String> knownAddresses = new HashSet<>();

    SharedParameters() {
        super(ObjectFactory.class, SharedParametersSchemaValidator.class);
    }

    ClientId createMemberId(MemberType member) {
        return ClientId.create(confType.getInstanceIdentifier(),
                member.getMemberClass().getCode(), member.getMemberCode());
    }

    ClientId createSubsystemId(MemberType member, SubsystemType subsystem) {
        return ClientId.create(confType.getInstanceIdentifier(),
                member.getMemberClass().getCode(), member.getMemberCode(),
                subsystem.getSubsystemCode());
    }

    GlobalGroupId createGlobalGroupId(GlobalGroupType globalGroup) {
        return GlobalGroupId.create(confType.getInstanceIdentifier(),
                globalGroup.getGroupCode());
    }

    String getInstanceIdentifier() {
        return confType.getInstanceIdentifier();
    }

    List<MemberType> getMembers() {
        return confType.getMember();
    }

    List<ApprovedCAType> getApprovedCAs() {
        return confType.getApprovedCA();
    }

    List<ApprovedTSAType> getApprovedTSAs() {
        return confType.getApprovedTSA();
    }

    List<SecurityServerType> getSecurityServers() {
        return confType.getSecurityServer();
    }

    List<GlobalGroupType> getGlobalGroups() {
        return confType.getGlobalGroup();
    }

    List<CentralServiceType> getCentralServices() {
        return confType.getCentralService();
    }

    GlobalSettingsType getGlobalSettings() {
        return confType.getGlobalSettings();
    }

    GlobalGroupType findGlobalGroup(GlobalGroupId groupId) {
        if (!groupId.getSdsbInstance().equals(
                confType.getInstanceIdentifier())) {
            return null;
        }

        return confType.getGlobalGroup().stream()
                .filter(g -> g.getGroupCode().equals(groupId.getGroupCode()))
                .findFirst().orElse(null);
    }

    X509Certificate getCaCertForSubject(X509Certificate subject)
            throws Exception {
        X509CertificateHolder certHolder =
                new X509CertificateHolder(subject.getEncoded());
        if (certHolder.getSubject().equals(certHolder.getIssuer())) {
            return null;
        }

        return subjectsAndCaCerts.get(certHolder.getIssuer());
    }

    ClientId getSubjectName(X509Certificate cert,
            IdentifierDecoderType decoder) throws Exception {
        return IdentifierDecoderHelper.getSubjectName(cert, decoder,
                getInstanceIdentifier());
    }

    @Override
    public void load(String fileName) throws Exception {
        super.load(fileName);

        if (fileName == null) {
            return;
        }

        try {
            clearCache();
            cacheCaCerts();
            cacheKnownAddresses();
            cacheSecurityServers();
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    static MemberType getOwner(SecurityServerType serverType) {
        if (!(serverType.getOwner() instanceof MemberType)) {
            throw new RuntimeException("Server owner must be member");
        }

        return (MemberType) serverType.getOwner();
    }

    // ------------------------------------------------------------------------

    private void clearCache() {
        subjectsAndCaCerts.clear();
        caCertsAndIdentifierDecoders.clear();
        caCertsAndOcspData.clear();
        memberAddresses.clear();
        memberAuthCerts.clear();
        serverByAuthCert.clear();
        securityServerClients.clear();
        verificationCaCerts.clear();
        knownAddresses.clear();
    }

    private void cacheCaCerts() throws CertificateException, IOException {
        List<X509Certificate> allCaCerts = new ArrayList<>();

        for (ApprovedCAType caType : confType.getApprovedCA()) {
            List<CaInfoType> topCAs = Arrays.asList(caType.getTopCA());
            List<CaInfoType> intermediateCAs = caType.getIntermediateCA();

            cacheOcspData(topCAs);
            cacheOcspData(intermediateCAs);

            List<X509Certificate> pkiCaCerts = new ArrayList<>();

            pkiCaCerts.addAll(getTopOrIntermediateCaCerts(topCAs));
            pkiCaCerts.addAll(getTopOrIntermediateCaCerts(intermediateCAs));

            Boolean authenticationOnly = caType.isAuthenticationOnly();
            if (authenticationOnly == null || !authenticationOnly) {
                verificationCaCerts.addAll(pkiCaCerts);
            }

            IdentifierDecoderType identifierDecoder =
                    caType.getIdentifierDecoder();

            for (X509Certificate pkiCaCert : pkiCaCerts) {
                caCertsAndIdentifierDecoders.put(pkiCaCert, identifierDecoder);
            }
            allCaCerts.addAll(pkiCaCerts);
        }

        for (X509Certificate cert : allCaCerts) {
            X509CertificateHolder certHolder =
                    new X509CertificateHolder(cert.getEncoded());
            subjectsAndCaCerts.put(certHolder.getSubject(), cert);
        }
    }

    private void cacheKnownAddresses() {
        confType.getSecurityServer().stream().map(s -> s.getAddress())
            .filter(StringUtils::isNotBlank)
            .forEach(knownAddresses::add);
    }

    private void cacheSecurityServers() {
        // Map of XML ID fields mapped to client IDs
        Map<String, ClientId> clientIds = getClientIds();

        for (SecurityServerType securityServer : confType.getSecurityServer()) {
            // Cache the server.
            for (byte[] certHash: securityServer.getAuthCertHash()) {
                serverByAuthCert.put(encodeBase64(certHash),
                        securityServer);
            }

            // Add owner of the security server.
            MemberType owner = (MemberType) securityServer.getOwner();
            addServerClient(createMemberId(owner), securityServer);

            // Add clients of the security server.
            for (JAXBElement<?> client : securityServer.getClient()) {
                Object val = client.getValue();

                if (val instanceof MemberType) {
                    addServerClient(createMemberId((MemberType) val),
                            securityServer);
                } else if (val instanceof SubsystemType) {
                    addServerClient(
                            clientIds.get(((SubsystemType) val).getId()),
                            securityServer);
                }
            }
        }
    }

    private void addServerClient(ClientId client, SecurityServerType server) {
        // Add the mapping from client to security server address.
        if (isNotBlank(server.getAddress())) {
            addToMap(memberAddresses, client, server.getAddress());
        }

        // Add the mapping from client to authentication certificate.
        for (byte[] authCert : server.getAuthCertHash()) {
            addToMap(memberAuthCerts, client, authCert);
        }

        MemberType owner = getOwner(server);
        SecurityServerId securityServerId = SecurityServerId.create(
                confType.getInstanceIdentifier(),
                owner.getMemberClass().getCode(),
                owner.getMemberCode(), server.getServerCode());

        addToMap(securityServerClients, securityServerId, client);
    }

    private Map<String, ClientId> getClientIds() {
        Map<String, ClientId> ret = new HashMap<>();

        for (MemberType member : confType.getMember()) {
            ret.put(member.getId(), createMemberId(member));

            for (SubsystemType subsystem : member.getSubsystem()) {
                ret.put(subsystem.getId(),
                        createSubsystemId(member, subsystem));
            }
        }

        return ret;
    }

    private void cacheOcspData(List<CaInfoType> typesUnderCA)
            throws CertificateException, IOException {
        for (CaInfoType caType : typesUnderCA) {
            X509Certificate cert = readCertificate(caType.getCert());
            List<OcspInfoType> caOcspTypes = caType.getOcsp();
            caCertsAndOcspData.put(cert, caOcspTypes);
        }
    }

    private static <K, V> void addToMap(Map<K, Set<V>> map, K key, V value) {
        Set<V> coll = map.get(key);
        if (coll == null) {
            coll = new HashSet<>();
            map.put(key, coll);
        }
        coll.add(value);
    }

    private static List<X509Certificate> getTopOrIntermediateCaCerts(
            List<CaInfoType> typesUnderCA) {
        return typesUnderCA.stream()
                .map(c -> readCertificate(c.getCert()))
                .collect(Collectors.toList());
    }
}
