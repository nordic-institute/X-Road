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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Getter
public class SharedParameters {
    private final String instanceIdentifier;
    private final List<ConfigurationSource> sources;
    private final List<ApprovedCA> approvedCAs;
    private final List<ApprovedTSA> approvedTSAs;
    private final List<Member> members;
    private final List<SecurityServer> securityServers;
    private final List<GlobalGroup> globalGroups;
    private final GlobalSettings globalSettings;

    // Utility maps of existing data to speed up searches, filled at conf initialization
    private final Map<X500Name, X509Certificate> subjectsAndCaCerts = new HashMap<>();
    private final Map<X509Certificate, String> caCertsAndCertProfiles = new HashMap<>();
    private final Map<X509Certificate, ApprovedCA> caCertsAndApprovedCAData = new HashMap<>();
    private final Map<X509Certificate, List<OcspInfo>> caCertsAndOcspData = new HashMap<>();
    private final List<X509Certificate> verificationCaCerts = new ArrayList<>();
    private final Map<ClientId, Set<String>> memberAddresses = new HashMap<>();
    private final Map<ClientId, Set<byte[]>> memberAuthCerts = new HashMap<>();
    private final Map<String, SecurityServer> serverByAuthCert = new HashMap<>();
    private final Map<SecurityServerId, Set<ClientId>> securityServerClients = new HashMap<>();
    private final Set<String> knownAddresses = new HashSet<>();
    private final Map<SecurityServerId, SecurityServer> securityServersById = new HashMap<>();

    public SharedParameters(String instanceIdentifier, List<ConfigurationSource> sources, List<ApprovedCA> approvedCAs,
                            List<ApprovedTSA> approvedTSAs, List<Member> members, List<SecurityServer> securityServers,
                            List<GlobalGroup> globalGroups, GlobalSettings globalSettings)
            throws CertificateEncodingException, IOException {
        this.instanceIdentifier = instanceIdentifier;
        this.sources = sources;
        this.approvedCAs = approvedCAs;
        this.approvedTSAs = approvedTSAs;
        this.members = members;
        this.securityServers = securityServers;
        this.globalGroups = globalGroups;
        this.globalSettings = globalSettings;

        cacheCaCerts();
        cacheKnownAddresses();
        cacheSecurityServers();
    }

    private void cacheCaCerts() throws CertificateEncodingException, IOException {
        List<X509Certificate> allCaCerts = new ArrayList<>();

        for (ApprovedCA ca : approvedCAs) {
            List<SharedParameters.CaInfo> topCAs = List.of(ca.getTopCA());
            List<SharedParameters.CaInfo> intermediateCAs = ca.getIntermediateCas();

            cacheOcspData(topCAs);
            cacheOcspData(intermediateCAs);

            List<X509Certificate> pkiCaCerts = new ArrayList<>();

            pkiCaCerts.addAll(getTopOrIntermediateCaCerts(topCAs));
            pkiCaCerts.addAll(getTopOrIntermediateCaCerts(intermediateCAs));

            Boolean authenticationOnly = ca.getAuthenticationOnly();
            if (authenticationOnly == null || !authenticationOnly) {
                verificationCaCerts.addAll(pkiCaCerts);
            }

            for (X509Certificate pkiCaCert : pkiCaCerts) {
                caCertsAndCertProfiles.put(pkiCaCert, ca.getCertificateProfileInfo());
                caCertsAndApprovedCAData.put(pkiCaCert, ca);
            }
            allCaCerts.addAll(pkiCaCerts);

            for (X509Certificate cert : allCaCerts) {
                X509CertificateHolder certHolder =
                        new X509CertificateHolder(cert.getEncoded());
                subjectsAndCaCerts.put(certHolder.getSubject(), cert);
            }
        }
    }

    private void cacheOcspData(List<CaInfo> typesUnderCA) {
        for (CaInfo caInfo : typesUnderCA) {
            X509Certificate cert = readCertificate(caInfo.getCert());
            List<OcspInfo> caOcspTypes = caInfo.getOcsp();
            caCertsAndOcspData.put(cert, caOcspTypes);
        }
    }

    private static List<X509Certificate> getTopOrIntermediateCaCerts(List<CaInfo> typesUnderCA) {
        return typesUnderCA.stream()
                .map(c -> readCertificate(c.getCert()))
                .collect(Collectors.toList());
    }

    private void cacheKnownAddresses() {
        securityServers.stream().map(SecurityServer::getAddress)
                .filter(StringUtils::isNotBlank)
                .forEach(knownAddresses::add);
    }

    private void cacheSecurityServers() {
        for (SecurityServer securityServer : securityServers) {
            for (byte[] certHash: securityServer.getAuthCertHashes()) {
                serverByAuthCert.put(encodeBase64(certHash), securityServer);
            }

            // Add owner of the security server
            addServerClient(securityServer.getOwner(), securityServer);

            // cache security server information by serverId
            SecurityServerId securityServerId = SecurityServerId.Conf.create(
                    instanceIdentifier,  securityServer.getOwner().getMemberClass(),
                    securityServer.getOwner().getMemberCode(), securityServer.getServerCode()
            );
            securityServersById.put(securityServerId, securityServer);

            securityServer.getClients().forEach(client -> addServerClient(client, securityServer));
        }
    }

    private void addServerClient(ClientId client, SecurityServer server) {
        // Add the mapping from client to security server address.
        if (isNotBlank(server.getAddress())) {
            addToMap(memberAddresses, client, server.getAddress());
        }

        // Add the mapping from client to authentication certificate.
        for (byte[] authCert : server.getAuthCertHashes()) {
            addToMap(memberAuthCerts, client, authCert);
        }

        SecurityServerId securityServerId = SecurityServerId.Conf.create(
                instanceIdentifier, server.getOwner().getMemberClass(),
                server.getOwner().getMemberCode(), server.getServerCode()
        );

        addToMap(securityServerClients, securityServerId, client);
    }

    private static <K, V> void addToMap(Map<K, Set<V>> map, K key, V value) {
        Set<V> coll = map.computeIfAbsent(key, k -> new HashSet<>());
        coll.add(value);
    }

    @Data
    public static class ConfigurationSource {
        private String address;
        private List<byte[]> internalVerificationCerts;
        private List<byte[]> externalVerificationCerts;
    }

    @Data
    public static class Member {
        private MemberClass memberClass;
        private String memberCode;
        private String name;
        private List<Subsystem> subsystems;
    }

    @Data
    public static class MemberClass {
        private String code;
        private String description;
    }

    @Data
    public static class Subsystem {
        private String subsystemCode;
    }

    @Data
    public static class ApprovedCA {
        private String name;
        private Boolean authenticationOnly;
        private CaInfo topCA;
        private List<CaInfo> intermediateCas;
        private String certificateProfileInfo;
        private AcmeServer acmeServer;
    }

    @Data
    public static class CaInfo {
        private byte[] cert;
        private List<OcspInfo> ocsp;
    }

    @Data
    public static class AcmeServer {
        private String directoryURL;
        private String ipAddress;
    }

    @Data
    public static class OcspInfo {
        private String url;
        private byte[] cert;
    }

    @Data
    public static class ApprovedTSA {
        private String name;
        private String url;
        private byte[] cert;
    }

    @Data
    public static class SecurityServer {
        private ClientId owner;
        private String serverCode;
        private String address;
        private List<byte[]> authCertHashes;
        private List<ClientId> clients;
    }

    @Data
    public static class GlobalGroup {
        private String groupCode;
        private String description;
        private List<ClientId> groupMembers;
    }

    @Data
    public static class GlobalSettings {
        private List<MemberClass> memberClasses;
        private BigInteger ocspFreshnessSeconds;
    }
}
