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

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedCATypeV2;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedTSAType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.CaInfoType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.GlobalGroupType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.GlobalSettingsType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.MemberType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.OcspInfoType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.SecurityServerType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.SharedParametersTypeV2;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.SubsystemType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Contains shared parameters of a configuration instance.
 */
@Getter(AccessLevel.PACKAGE)
public class SharedParametersV2 extends AbstractXmlConf<SharedParametersTypeV2> {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    // Cached items, filled at conf reload
    private Map<X500Name, X509Certificate> subjectsAndCaCerts;
    private Map<X509Certificate, String> caCertsAndCertProfiles;
    private Map<X509Certificate, ApprovedCATypeV2> caCertsAndApprovedCAData;
    private Map<X509Certificate, List<OcspInfoType>> caCertsAndOcspData;
    private Map<ClientId, Set<String>> memberAddresses;
    private Map<ClientId, Set<byte[]>> memberAuthCerts;
    private Map<String, SecurityServerType> serverByAuthCert;
    private Map<SecurityServerId, Set<ClientId>> securityServerClients;
    private List<X509Certificate> verificationCaCerts;
    private Set<String> knownAddresses;
    private Map<SecurityServerId, SecurityServerType> securityServersById;

    private final OffsetDateTime expiresOn;

    // variable to prevent using load methods after construction
    private final boolean initCompleted;

    // This constructor is used for simple verifications after configuration download.
    // It does not initialise class fully!
    SharedParametersV2(byte[] content) {
        super(content, SharedParametersSchemaValidatorV2.class);
        initCompleted = true;
        expiresOn = OffsetDateTime.MAX;
    }

    public SharedParametersV2(Path sharedParametersPath, OffsetDateTime expiresOn) {
        super(sharedParametersPath.toString(), SharedParametersSchemaValidatorV2.class);

        this.expiresOn = expiresOn;

        subjectsAndCaCerts = new HashMap<>();
        caCertsAndCertProfiles = new HashMap<>();
        caCertsAndApprovedCAData = new HashMap<>();
        caCertsAndOcspData = new HashMap<>();
        memberAddresses = new HashMap<>();
        memberAuthCerts = new HashMap<>();
        serverByAuthCert = new HashMap<>();
        securityServerClients = new HashMap<>();
        verificationCaCerts = new ArrayList<>();
        knownAddresses = new HashSet<>();
        securityServersById = new HashMap<>();

        try {
            cacheCaCerts();
            cacheKnownAddresses();
            cacheSecurityServers();
        } catch (Exception e) {
            throw translateException(e);
        }

        initCompleted = true;
    }

    public SharedParametersV2(SharedParametersV2 original, OffsetDateTime newExpiresOn) {
        super(original);

        expiresOn = newExpiresOn;

        subjectsAndCaCerts = original.subjectsAndCaCerts;
        caCertsAndCertProfiles = original.caCertsAndCertProfiles;
        caCertsAndApprovedCAData = original.caCertsAndApprovedCAData;
        caCertsAndOcspData = original.caCertsAndOcspData;
        memberAddresses = original.memberAddresses;
        memberAuthCerts = original.memberAuthCerts;
        serverByAuthCert = original.serverByAuthCert;
        securityServerClients = original.securityServerClients;
        verificationCaCerts = original.verificationCaCerts;
        knownAddresses = original.knownAddresses;
        securityServersById = original.securityServersById;

        initCompleted = true;
    }

    @Override
    public void load(String fileName) throws Exception {
        throwIfInitCompleted();
        super.load(fileName);
    }

    @Override
    public void load(byte[] data) throws Exception {
        throwIfInitCompleted();
        super.load(data);
    }

    private void throwIfInitCompleted() {
        if (initCompleted) {
            throw new IllegalStateException("This object can not be reloaded");
        }
    }

    ClientId.Conf createMemberId(MemberType member) {
        return ClientId.Conf.create(confType.getInstanceIdentifier(),
                member.getMemberClass().getCode(), member.getMemberCode());
    }

    ClientId.Conf createSubsystemId(MemberType member, SubsystemType subsystem) {
        return ClientId.Conf.create(confType.getInstanceIdentifier(),
                member.getMemberClass().getCode(), member.getMemberCode(),
                subsystem.getSubsystemCode());
    }

    GlobalGroupId.Conf createGlobalGroupId(GlobalGroupType globalGroup) {
        return GlobalGroupId.Conf.create(confType.getInstanceIdentifier(),
                globalGroup.getGroupCode());
    }

    String getInstanceIdentifier() {
        return confType.getInstanceIdentifier();
    }

    List<MemberType> getMembers() {
        return confType.getMember();
    }

    List<ApprovedCATypeV2> getApprovedCAs() {
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

    GlobalSettingsType getGlobalSettings() {
        return confType.getGlobalSettings();
    }

    GlobalGroupType findGlobalGroup(GlobalGroupId groupId) {
        if (!groupId.getXRoadInstance().equals(
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

    static MemberType getOwner(SecurityServerType serverType) {
        if (!(serverType.getOwner() instanceof MemberType)) {
            throw new RuntimeException("Server owner must be member");
        }

        return (MemberType) serverType.getOwner();
    }

    private void cacheCaCerts() throws CertificateException, IOException {
        List<X509Certificate> allCaCerts = new ArrayList<>();

        for (ApprovedCATypeV2 caType : confType.getApprovedCA()) {
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

            for (X509Certificate pkiCaCert : pkiCaCerts) {
                caCertsAndCertProfiles.put(pkiCaCert,
                        caType.getCertificateProfileInfo());
                caCertsAndApprovedCAData.put(pkiCaCert,
                        caType);
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

            // cache security server information by serverId
            SecurityServerId securityServerId = SecurityServerId.Conf.create(
                    confType.getInstanceIdentifier(),
                    owner.getMemberClass().getCode(),
                    owner.getMemberCode(), securityServer.getServerCode());
            securityServersById.put(securityServerId, securityServer);

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
        SecurityServerId securityServerId = SecurityServerId.Conf.create(
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

    @Override
    protected JAXBContext getJAXBContext() {
        return JAXB_CONTEXT;
    }

    private static JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}
