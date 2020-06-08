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
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.CentralServiceType;
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

import javax.xml.bind.JAXBElement;

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

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Contains shared parameters of a configuration instance.
 */
@Getter(AccessLevel.PACKAGE)
public class SharedParametersV2 extends AbstractXmlConf<SharedParametersTypeV2> {

    // Cached items, filled at conf reload
    private final Map<X500Name, X509Certificate> subjectsAndCaCerts =
            new HashMap<>();
    private final Map<X509Certificate, String> caCertsAndCertProfiles =
            new HashMap<>();
    private final Map<X509Certificate, ApprovedCATypeV2> caCertsAndApprovedCAData =
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
    private final Map<SecurityServerId, SecurityServerType> securityServersById = new HashMap<>();

    SharedParametersV2() {
        super(ObjectFactory.class, SharedParametersSchemaValidatorV2.class);
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

    List<CentralServiceType> getCentralServices() {
        return confType.getCentralService();
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
        caCertsAndCertProfiles.clear();
        caCertsAndApprovedCAData.clear();
        caCertsAndOcspData.clear();
        memberAddresses.clear();
        memberAuthCerts.clear();
        serverByAuthCert.clear();
        securityServerClients.clear();
        verificationCaCerts.clear();
        knownAddresses.clear();
        securityServersById.clear();
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
            SecurityServerId securityServerId = SecurityServerId.create(
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
