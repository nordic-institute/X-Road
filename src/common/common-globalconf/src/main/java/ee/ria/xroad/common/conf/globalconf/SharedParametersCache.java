/*
 * The MIT License
 *
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

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;

import java.io.IOException;
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
public class SharedParametersCache {
    private final SharedParameters sharedParameters;

    // Utility maps of existing data to speed up searches, filled at conf initialization
    private final Map<X500Name, X509Certificate> subjectsAndCaCerts = new HashMap<>();
    private final Map<X509Certificate, String> caCertsAndCertProfiles = new HashMap<>();
    private final Map<X509Certificate, SharedParameters.ApprovedCA> caCertsAndApprovedCAData = new HashMap<>();
    private final Map<X509Certificate, List<SharedParameters.OcspInfo>> caCertsAndOcspData = new HashMap<>();
    private final List<X509Certificate> verificationCaCerts = new ArrayList<>();
    private final Map<ClientId, Set<String>> memberAddresses = new HashMap<>();
    private final Map<ClientId, Set<byte[]>> memberAuthCerts = new HashMap<>();
    private final Map<String, SharedParameters.SecurityServer> serverByAuthCert = new HashMap<>();
    private final Map<SecurityServerId, Set<ClientId>> securityServerClients = new HashMap<>();
    private final Set<String> knownAddresses = new HashSet<>();
    private final Map<SecurityServerId, SharedParameters.SecurityServer> securityServersById = new HashMap<>();

    public String getInstanceIdentifier() {
        return sharedParameters.getInstanceIdentifier();
    }

    @SneakyThrows
    SharedParametersCache(@NonNull SharedParameters sharedParameters) {
        this.sharedParameters = sharedParameters;

        cacheCaCerts();
        cacheKnownAddresses();
        cacheSecurityServers();
    }


    private void cacheCaCerts() throws CertificateEncodingException, IOException {
        List<X509Certificate> allCaCerts = new ArrayList<>();

        for (SharedParameters.ApprovedCA ca : sharedParameters.getApprovedCAs()) {
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

    private void cacheOcspData(List<SharedParameters.CaInfo> typesUnderCA) {
        for (SharedParameters.CaInfo caInfo : typesUnderCA) {
            X509Certificate cert = readCertificate(caInfo.getCert());
            List<SharedParameters.OcspInfo> caOcspTypes = caInfo.getOcsp();
            caCertsAndOcspData.put(cert, caOcspTypes);
        }
    }

    private static List<X509Certificate> getTopOrIntermediateCaCerts(List<SharedParameters.CaInfo> typesUnderCA) {
        return typesUnderCA.stream()
                .map(c -> readCertificate(c.getCert()))
                .collect(Collectors.toList());
    }

    private void cacheKnownAddresses() {
        sharedParameters.getSecurityServers().stream().map(SharedParameters.SecurityServer::getAddress)
                .filter(StringUtils::isNotBlank)
                .forEach(knownAddresses::add);
    }

    private void cacheSecurityServers() {
        for (SharedParameters.SecurityServer securityServer : sharedParameters.getSecurityServers()) {
            for (CertHash certHash : securityServer.getAuthCertHashes()) {
                serverByAuthCert.put(encodeBase64(certHash.getHash()), securityServer);
            }

            // Add owner of the security server
            addServerClient(securityServer.getOwner(), securityServer);

            // cache security server information by serverId
            SecurityServerId securityServerId = SecurityServerId.Conf.create(
                    sharedParameters.getInstanceIdentifier(), securityServer.getOwner().getMemberClass(),
                    securityServer.getOwner().getMemberCode(), securityServer.getServerCode()
            );
            securityServersById.put(securityServerId, securityServer);

            securityServer.getClients().forEach(client -> addServerClient(client, securityServer));
        }
    }

    private void addServerClient(ClientId client, SharedParameters.SecurityServer server) {
        // Add the mapping from client to security server address.
        if (isNotBlank(server.getAddress())) {
            addToMap(memberAddresses, client, server.getAddress());
        }

        // Add the mapping from client to authentication certificate.
        for (CertHash authCert : server.getAuthCertHashes()) {
            addToMap(memberAuthCerts, client, authCert.getHash());
        }

        SecurityServerId securityServerId = SecurityServerId.Conf.create(
                sharedParameters.getInstanceIdentifier(), server.getOwner().getMemberClass(),
                server.getOwner().getMemberCode(), server.getServerCode()
        );

        addToMap(securityServerClients, securityServerId, client);
    }

    private static <K, V> void addToMap(Map<K, Set<V>> map, K key, V value) {
        Set<V> coll = map.computeIfAbsent(key, k -> new HashSet<>());
        coll.add(value);
    }
}
