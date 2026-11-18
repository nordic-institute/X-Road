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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An issuer on a local port that publishes its DID document and one status list credential, and can be switched
 * off to answer 503 with a body.
 */
class FakeIssuer implements AutoCloseable {

    static final String ERROR_BODY = "issuer-internal-error-detail";

    private static final int LIST_SIZE = 16 * 1024;

    private final HttpServer server;
    private final ECKey signingKey;
    private final String did;
    private final String statusListUrl;
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicInteger statusListRequests = new AtomicInteger();
    private final AtomicReference<String> statusListJwt = new AtomicReference<>();
    private final String didDocument;

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
        didDocument = """
                {
                  "@context": ["https://www.w3.org/ns/did/v1"],
                  "id": "%s",
                  "verificationMethod": [{
                    "id": "%s#key-1",
                    "type": "JsonWebKey2020",
                    "controller": "%s",
                    "publicKeyJwk": %s
                  }],
                  "authentication": ["%s#key-1"]
                }
                """.formatted(did, did, did, signingKey.toPublicJWK().toJSONString(), did);
        server.createContext("/issuer/did.json", exchange -> answer(exchange, didDocument, false));
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

    void down(boolean value) {
        down.set(value);
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
        statusListJwt.set(sign(JsonMapper.builder().build().readValue(credential, Map.class)));
    }

    private String sign(Map<?, ?> credential) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer(did).issueTime(new Date()).claim("vc", credential).build();
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
            var unavailable = down.get() || body == null;
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
