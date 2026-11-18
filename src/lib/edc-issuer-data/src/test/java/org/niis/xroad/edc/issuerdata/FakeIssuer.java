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

package org.niis.xroad.edc.issuerdata;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.BitString;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An issuer on a local port that publishes its DID document and one status list credential, and can be switched
 * off to answer 503 with a body or to refuse the status list with 404.
 */
class FakeIssuer implements AutoCloseable {

    static final String ERROR_BODY = "issuer-internal-error-detail";

    private static final int LIST_SIZE = 16 * 1024;

    private final HttpServer server;
    private final ECKey signingKey;
    private final String did;
    private final String statusListUrl;
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicBoolean statusListDown = new AtomicBoolean();
    private final AtomicBoolean statusListRefused = new AtomicBoolean();
    private final AtomicInteger statusListRequests = new AtomicInteger();
    private final AtomicReference<String> statusListJwt = new AtomicReference<>();
    private final AtomicReference<String> didDocument = new AtomicReference<>();
    private final AtomicInteger didDocumentRequests = new AtomicInteger();
    private final AtomicBoolean didDocumentRefused = new AtomicBoolean();

    FakeIssuer() {
        try {
            signingKey = new ECKeyGenerator(Curve.P_256).keyID("key-1").generate();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException("Fake issuer could not start", e);
        }
        var port = server.getAddress().getPort();
        did = "did:web:localhost%3A" + port + ":issuer";
        statusListUrl = "http://localhost:" + port + "/issuer/statuslist/1";
        didDocument.set(didDocumentWithKey("key-1", signingKey));
        server.createContext("/issuer/did.json", exchange -> {
            didDocumentRequests.incrementAndGet();
            if (didDocumentRefused.get()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            answer(exchange, didDocument.get(), false);
        });
        server.createContext("/issuer/statuslist/1", exchange -> answer(exchange, statusListJwt.get(), true));
        server.start();
    }

    String did() {
        return did;
    }

    String statusListUrl() {
        return statusListUrl;
    }

    int statusListRequests() {
        return statusListRequests.get();
    }

    int didDocumentRequests() {
        return didDocumentRequests.get();
    }

    /**
     * The DID document exactly as the issuer serves it.
     */
    String didDocumentJson() {
        return didDocument.get();
    }

    /**
     * The public key of the signing key pair, as the DID document of this issuer lists it at the start.
     */
    Map<String, Object> signingPublicJwk() {
        return signingKey.toPublicJWK().toJSONObject();
    }

    /**
     * Makes the DID document answer 404.
     */
    void didDocumentRefused(boolean value) {
        didDocumentRefused.set(value);
    }

    /**
     * Replaces the published DID document with one that lists only the given key id, backed by a new key pair.
     */
    void publishDidDocumentWithOnlyKey(String keyId) {
        try {
            didDocument.set(didDocumentWithKey(keyId, new ECKeyGenerator(Curve.P_256).keyID(keyId).generate()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Fake issuer could not create a key", e);
        }
    }

    void down(boolean value) {
        down.set(value);
    }

    /**
     * Takes down only the status list; the DID document stays reachable.
     */
    void statusListDown(boolean value) {
        statusListDown.set(value);
    }

    /**
     * Makes the status list answer 404; the DID document stays reachable.
     */
    void statusListRefused(boolean value) {
        statusListRefused.set(value);
    }

    /**
     * Issues a credential JWT for the subject whose status entry points at this issuer's status list.
     */
    String issueCredential(String subjectDid, int statusIndex) {
        var credential = Map.of(
                "@context", List.of("https://www.w3.org/2018/credentials/v1"),
                "type", List.of("VerifiableCredential", "MembershipCredential"),
                "issuer", did,
                "credentialSubject", Map.of("id", subjectDid),
                "credentialStatus", Map.of(
                        "id", statusListUrl + "#" + statusIndex,
                        "type", "BitstringStatusListEntry",
                        "statusPurpose", "revocation",
                        "statusListIndex", String.valueOf(statusIndex),
                        "statusListCredential", statusListUrl));
        return sign(credential, subjectDid);
    }

    /**
     * Publishes a signed status list with the given positions set, optionally stating when it stops being valid.
     */
    void publishStatusList(Set<Integer> revokedIndexes, Instant validUntil) {
        var list = BitString.Builder.newInstance().size(LIST_SIZE).build();
        revokedIndexes.forEach(index -> list.set(index, true));
        var encoded = BitString.Writer.newInstance().encoder(Base64.getUrlEncoder().withoutPadding())
                .writeMultibase(list).getContent();
        var validUntilField = validUntil == null ? "" : "\"validUntil\": \"%s\",".formatted(validUntil);
        var credential = """
                {
                  "@context": ["https://www.w3.org/ns/credentials/v2"],
                  "id": "%s",
                  "type": ["VerifiableCredential", "BitstringStatusListCredential"],
                  "issuer": "%s",
                  "validFrom": "2026-11-01T00:00:00Z",
                  %s
                  "credentialSubject": {
                    "id": "%s#list",
                    "type": "BitstringStatusList",
                    "statusPurpose": "revocation",
                    "encodedList": "%s"
                  }
                }
                """.formatted(statusListUrl, did, validUntilField, statusListUrl, encoded);
        statusListJwt.set(sign(JsonMapper.builder().build().readValue(credential, Map.class), null));
    }

    private String didDocumentWithKey(String keyId, ECKey key) {
        return """
                {
                  "@context": ["https://www.w3.org/ns/did/v1"],
                  "id": "%s",
                  "verificationMethod": [{
                    "id": "%s#%s",
                    "type": "JsonWebKey2020",
                    "controller": "%s",
                    "publicKeyJwk": %s
                  }],
                  "authentication": ["%s#%s"]
                }
                """.formatted(did, did, keyId, did, key.toPublicJWK().toJSONString(), did, keyId);
    }

    private String sign(Map<?, ?> credential, String subject) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer(did).subject(subject).issueTime(new Date())
                    .claim("vc", credential).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(did + "#key-1").build(), claims);
            jwt.sign(new ECDSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Status list could not be signed", e);
        }
    }

    private void answer(HttpExchange exchange, String body, boolean countsAsStatusListRequest) throws IOException {
        try (exchange) {
            if (countsAsStatusListRequest) {
                statusListRequests.incrementAndGet();
            }
            if (countsAsStatusListRequest && statusListRefused.get()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            var unavailable = down.get() || (countsAsStatusListRequest && statusListDown.get()) || body == null;
            var bytes = (unavailable ? ERROR_BODY : body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(unavailable ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
