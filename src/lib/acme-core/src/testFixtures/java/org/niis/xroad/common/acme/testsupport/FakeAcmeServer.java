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
package org.niis.xroad.common.acme.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.HmacKey;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-process RFC 8555 + ACME Renewal Information (ARI, draft-ietf-acme-ari) server, for exercising
 * {@link org.niis.xroad.common.acme.AcmeClient} without a live CA. It implements only the subset of the protocol the
 * shared ACME core actually drives: directory, account creation (with optional External Account Binding, MAC
 * verified), a single-domain order, HTTP-01 challenge fulfilment (verified by reading the challenge file straight
 * off disk, since this test double and the client share a filesystem) and renewal information.
 * <p>
 * Not a substitute for the CI-level run against acme2certifier: no DNS-01, no wildcard orders, no rate limiting,
 * no real network validation of the HTTP-01 response.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public final class FakeAcmeServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final HttpServer httpServer;
    private final String baseUrl;
    private final Path challengeDirectory;
    private final KeyPair caKeyPair;
    private final X509Certificate caCertificate;

    private final Map<String, byte[]> eabSecretsByKid = new ConcurrentHashMap<>();
    private volatile boolean externalAccountRequired;
    private volatile boolean renewalInfoAvailable = true;
    private volatile Instant renewalWindowStart = Instant.now().plus(60, ChronoUnit.DAYS);
    private volatile Instant renewalWindowEnd = Instant.now().plus(61, ChronoUnit.DAYS);
    private volatile boolean failNextChallenge;

    private final Map<String, PublicKey> accountKeyByLocation = new ConcurrentHashMap<>();
    private final Map<String, String> accountLocationByThumbprint = new ConcurrentHashMap<>();
    private final Map<String, OrderState> orders = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger();
    private final List<String> capturedUserAgents = new CopyOnWriteArrayList<>();

    public FakeAcmeServer(Path challengeDirectory) throws Exception {
        this.challengeDirectory = challengeDirectory;
        Files.createDirectories(challengeDirectory);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        this.caKeyPair = keyPairGenerator.generateKeyPair();
        this.caCertificate = selfSign(caKeyPair, "Fake ACME Test CA");

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/", this::handle);
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    public String directoryUrl() {
        return baseUrl + "/directory";
    }

    public void requireExternalAccountBinding(String kid, byte[] macKeySecret) {
        eabSecretsByKid.put(kid, macKeySecret);
        externalAccountRequired = true;
    }

    public void setRenewalWindow(Instant start, Instant end) {
        this.renewalWindowStart = start;
        this.renewalWindowEnd = end;
    }

    public void disableRenewalInfo() {
        this.renewalInfoAvailable = false;
    }

    public void failNextChallenge() {
        this.failNextChallenge = true;
    }

    public List<String> capturedUserAgents() {
        return List.copyOf(capturedUserAgents);
    }

    public void clearCapturedUserAgents() {
        capturedUserAgents.clear();
    }

    public X509Certificate caCertificate() {
        return caCertificate;
    }

    @Override
    public void close() {
        httpServer.stop(0);
    }

    private void handle(HttpExchange exchange) {
        try {
            captureUserAgent(exchange);
            String path = exchange.getRequestURI().getPath();
            switch (method(exchange, path)) {
                case "GET /directory" -> handleDirectory(exchange);
                case "HEAD /new-nonce", "GET /new-nonce" -> handleNewNonce(exchange);
                case "POST /new-account" -> handleNewAccount(exchange);
                case "POST /new-order" -> handleNewOrder(exchange);
                case "POST /key-change" -> handleKeyChange(exchange);
                default -> {
                    if (path.startsWith("/order/") && path.endsWith("/finalize")) {
                        handleFinalize(exchange, orderIdFrom(path, "/order/", "/finalize"));
                    } else if (path.startsWith("/order/")) {
                        handleOrderStatus(exchange, orderIdFrom(path, "/order/", null));
                    } else if (path.startsWith("/authz/")) {
                        handleAuthorization(exchange, orderIdFrom(path, "/authz/", null));
                    } else if (path.startsWith("/chall/")) {
                        handleChallenge(exchange, orderIdFrom(path, "/chall/", null));
                    } else if (path.startsWith("/certificate/")) {
                        handleCertificateDownload(exchange, orderIdFrom(path, "/certificate/", null));
                    } else if (path.startsWith("/renewal-info/")) {
                        handleRenewalInfo(exchange);
                    } else {
                        sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such resource");
                    }
                }
            }
        } catch (Exception e) {
            try {
                sendProblem(exchange, 500, "urn:ietf:params:acme:error:serverInternal", String.valueOf(e.getMessage()));
            } catch (IOException ignored) {
                exchange.close();
            }
        } finally {
            exchange.close();
        }
    }

    private static String method(HttpExchange exchange, String path) {
        return exchange.getRequestMethod() + " " + path;
    }

    private void captureUserAgent(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders().get("User-Agent");
        if (values != null && !values.isEmpty()) {
            capturedUserAgents.add(values.get(0));
        }
    }

    private void handleDirectory(HttpExchange exchange) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("newNonce", baseUrl + "/new-nonce");
        body.put("newAccount", baseUrl + "/new-account");
        body.put("newOrder", baseUrl + "/new-order");
        body.put("keyChange", baseUrl + "/key-change");
        if (renewalInfoAvailable) {
            body.put("renewalInfo", baseUrl + "/renewal-info");
        }
        ObjectNode meta = body.putObject("meta");
        meta.put("externalAccountRequired", externalAccountRequired);
        sendJson(exchange, 200, body);
    }

    private void handleNewNonce(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Replay-Nonce", randomNonce());
        exchange.sendResponseHeaders(204, -1);
    }

    private void handleNewAccount(HttpExchange exchange) throws Exception {
        JsonWebSignature jws = parseEnvelope(readJson(exchange));
        Map<String, Object> jwk = objectHeader(jws, "jwk");
        PublicKey accountKey = PublicJsonWebKey.Factory.newPublicJwk(jwk).getPublicKey();
        String thumbprint = thumbprintOf(accountKey);

        String payloadJson = jws.getUnverifiedPayload();
        JsonNode payload = JSON.readTree(payloadJson.isBlank() ? "{}" : payloadJson);

        if (externalAccountRequired) {
            if (!payload.has("externalAccountBinding")) {
                sendProblem(exchange, 400, "urn:ietf:params:acme:error:externalAccountRequired", "EAB required");
                return;
            }
            if (!verifyExternalAccountBinding(payload.get("externalAccountBinding"), accountKey)) {
                sendProblem(exchange, 400, "urn:ietf:params:acme:error:unauthorized", "invalid EAB MAC");
                return;
            }
        }

        String location = accountLocationByThumbprint.computeIfAbsent(thumbprint,
                t -> baseUrl + "/accounts/" + idSequence.incrementAndGet());
        accountKeyByLocation.put(location, accountKey);

        ObjectNode body = JSON.createObjectNode();
        body.put("status", "valid");
        exchange.getResponseHeaders().set("Location", location);
        sendJson(exchange, 201, body);
    }

    private boolean verifyExternalAccountBinding(JsonNode eabNode, PublicKey accountKey) throws Exception {
        JsonWebSignature innerJws = parseEnvelope(eabNode);
        String kid = innerJws.getKeyIdHeaderValue();
        byte[] secret = eabSecretsByKid.get(kid);
        if (secret == null) {
            return false;
        }
        innerJws.setKey(new HmacKey(secret));
        if (!innerJws.verifySignature()) {
            return false;
        }
        Map<String, Object> boundJwk = JSON.readValue(innerJws.getUnverifiedPayload(), Map.class);
        PublicKey boundKey = PublicJsonWebKey.Factory.newPublicJwk(boundJwk).getPublicKey();
        return thumbprintOf(boundKey).equals(thumbprintOf(accountKey));
    }

    /**
     * Handles acme4j's key-change request: a doubly-signed JWS where the outer envelope (signed by the
     * account's current key, {@code kid} = account location) carries an inner envelope (signed by the new
     * key, {@code jwk} = new public key) as its payload. Updates the account record so a subsequent login
     * with the new key resolves to the same account, proving the rotation actually took effect server-side.
     */
    private void handleKeyChange(HttpExchange exchange) throws Exception {
        JsonWebSignature outerJws = parseEnvelope(readJson(exchange));
        String accountLocation = outerJws.getKeyIdHeaderValue();
        if (accountLocation == null || !accountKeyByLocation.containsKey(accountLocation)) {
            sendProblem(exchange, 400, "urn:ietf:params:acme:error:accountDoesNotExist", "unknown account");
            return;
        }

        JsonNode innerEnvelope = JSON.readTree(outerJws.getUnverifiedPayload());
        JsonWebSignature innerJws = parseEnvelope(innerEnvelope);
        Map<String, Object> newJwk = objectHeader(innerJws, "jwk");
        PublicKey newAccountKey = PublicJsonWebKey.Factory.newPublicJwk(newJwk).getPublicKey();

        accountKeyByLocation.put(accountLocation, newAccountKey);
        accountLocationByThumbprint.put(thumbprintOf(newAccountKey), accountLocation);

        ObjectNode body = JSON.createObjectNode();
        body.put("status", "valid");
        sendJson(exchange, 200, body);
    }

    private void handleNewOrder(HttpExchange exchange) throws Exception {
        JsonWebSignature jws = parseEnvelope(readJson(exchange));
        PublicKey accountKey = resolveAccountKey(jws);
        JsonNode payload = JSON.readTree(jws.getUnverifiedPayload());

        List<String> domains = new ArrayList<>();
        payload.get("identifiers").forEach(id -> domains.add(id.get("value").asText()));

        String id = String.valueOf(idSequence.incrementAndGet());
        OrderState order = new OrderState(id, domains, accountKey);
        orders.put(id, order);

        ObjectNode body = JSON.createObjectNode();
        body.put("status", "pending");
        putIdentifiers(body, domains);
        body.putArray("authorizations").add(baseUrl + "/authz/" + id);
        body.put("finalize", baseUrl + "/order/" + id + "/finalize");
        exchange.getResponseHeaders().set("Location", baseUrl + "/order/" + id);
        sendJson(exchange, 201, body);
    }

    private static void putIdentifiers(ObjectNode body, List<String> domains) {
        var array = body.putArray("identifiers");
        for (String domain : domains) {
            ObjectNode idNode = JSON.createObjectNode();
            idNode.put("type", "dns");
            idNode.put("value", domain);
            array.add(idNode);
        }
    }

    private void handleOrderStatus(HttpExchange exchange, String id) throws IOException {
        OrderState order = orders.get(id);
        if (order == null) {
            sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such order");
            return;
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("status", order.status);
        putIdentifiers(body, order.domains);
        body.putArray("authorizations").add(baseUrl + "/authz/" + id);
        body.put("finalize", baseUrl + "/order/" + id + "/finalize");
        if (order.certificateUrl != null) {
            body.put("certificate", order.certificateUrl);
        }
        sendJson(exchange, 200, body);
    }

    private void handleAuthorization(HttpExchange exchange, String id) throws IOException {
        OrderState order = orders.get(id);
        if (order == null) {
            sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such authorization");
            return;
        }
        ObjectNode body = JSON.createObjectNode();
        ObjectNode identifier = body.putObject("identifier");
        identifier.put("type", "dns");
        identifier.put("value", order.domains.getFirst());
        body.put("status", order.authorizationStatus());
        ObjectNode challenge = body.putArray("challenges").addObject();
        challenge.put("type", "http-01");
        challenge.put("url", baseUrl + "/chall/" + id);
        challenge.put("token", order.challengeToken);
        challenge.put("status", order.challengeStatus);
        sendJson(exchange, 200, body);
    }

    private void handleChallenge(HttpExchange exchange, String id) throws Exception {
        OrderState order = orders.get(id);
        if (order == null) {
            sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such challenge");
            return;
        }
        if (!order.challengeTriggered) {
            order.challengeTriggered = true;
            validateChallenge(order);
        }
        sendChallengeState(exchange, order);
    }

    private void validateChallenge(OrderState order) {
        boolean valid = !failNextChallenge && challengeContentMatches(order);
        failNextChallenge = false;
        order.challengeStatus = valid ? "valid" : "invalid";
    }

    private boolean challengeContentMatches(OrderState order) {
        try {
            Path challengeFile = challengeDirectory.resolve(order.challengeToken);
            if (!Files.exists(challengeFile)) {
                return false;
            }
            String content = Files.readString(challengeFile, StandardCharsets.UTF_8);
            String expected = order.challengeToken + "." + thumbprintOf(order.accountKey);
            return expected.equals(content);
        } catch (Exception e) {
            return false;
        }
    }

    private void sendChallengeState(HttpExchange exchange, OrderState order) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("type", "http-01");
        body.put("url", baseUrl + "/chall/" + order.id);
        body.put("token", order.challengeToken);
        body.put("status", order.challengeStatus);
        sendJson(exchange, 200, body);
    }

    private void handleFinalize(HttpExchange exchange, String id) throws Exception {
        OrderState order = orders.get(id);
        if (order == null) {
            sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such order");
            return;
        }
        JsonWebSignature jws = parseEnvelope(readJson(exchange));
        JsonNode payload = JSON.readTree(jws.getUnverifiedPayload());
        byte[] csrDer = BASE64_URL_DECODER.decode(pad(payload.get("csr").asText()));
        PublicKey domainPublicKey = new JcaPKCS10CertificationRequest(new PKCS10CertificationRequest(csrDer)).getPublicKey();

        X509Certificate leaf = issueLeaf(order.domains.getFirst(), domainPublicKey);
        order.issuedChain = List.of(leaf, caCertificate);
        order.certificateUrl = baseUrl + "/certificate/" + id;
        order.status = "valid";

        ObjectNode body = JSON.createObjectNode();
        body.put("status", order.status);
        body.put("certificate", order.certificateUrl);
        sendJson(exchange, 200, body);
    }

    private void handleCertificateDownload(HttpExchange exchange, String id) throws Exception {
        OrderState order = orders.get(id);
        if (order == null || order.issuedChain == null) {
            sendProblem(exchange, 404, "urn:ietf:params:acme:error:malformed", "no such certificate");
            return;
        }
        StringBuilder pem = new StringBuilder();
        for (X509Certificate cert : order.issuedChain) {
            pem.append("-----BEGIN CERTIFICATE-----\n")
                    .append(Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(cert.getEncoded()))
                    .append("\n-----END CERTIFICATE-----\n");
        }
        byte[] bytes = pem.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/pem-certificate-chain");
        exchange.getResponseHeaders().set("Replay-Nonce", randomNonce());
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void handleRenewalInfo(HttpExchange exchange) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode window = body.putObject("suggestedWindow");
        window.put("start", renewalWindowStart.toString());
        window.put("end", renewalWindowEnd.toString());
        sendJson(exchange, 200, body);
    }

    private PublicKey resolveAccountKey(JsonWebSignature jws) throws Exception {
        String kid = jws.getKeyIdHeaderValue();
        if (kid == null) {
            Map<String, Object> jwk = objectHeader(jws, "jwk");
            return PublicJsonWebKey.Factory.newPublicJwk(jwk).getPublicKey();
        }
        PublicKey key = accountKeyByLocation.get(kid);
        if (key == null) {
            throw new IllegalStateException("Unknown account: " + kid);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectHeader(JsonWebSignature jws, String name) {
        return (Map<String, Object>) jws.getHeaders().getObjectHeaderValue(name);
    }

    private static JsonWebSignature parseEnvelope(JsonNode envelope) throws Exception {
        String protectedHeader = envelope.get("protected").asText();
        String payload = envelope.has("payload") ? envelope.get("payload").asText() : "";
        String signature = envelope.get("signature").asText();
        JsonWebSignature jws = new JsonWebSignature();
        jws.setCompactSerialization(protectedHeader + "." + payload + "." + signature);
        return jws;
    }

    private static JsonNode readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return JSON.readTree(in);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, ObjectNode body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Replay-Nonce", randomNonce());
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendProblem(HttpExchange exchange, int status, String type, String detail) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("type", type);
        body.put("detail", detail);
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        exchange.getResponseHeaders().set("Replay-Nonce", randomNonce());
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String randomNonce() {
        byte[] value = new byte[16];
        new SecureRandom().nextBytes(value);
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private static String thumbprintOf(PublicKey key) throws Exception {
        byte[] thumbprint = PublicJsonWebKey.Factory.newPublicJwk(key).calculateThumbprint("SHA-256");
        return BASE64_URL_ENCODER.encodeToString(thumbprint);
    }

    private static String pad(String base64Url) {
        int mod = base64Url.length() % 4;
        return switch (mod) {
            case 2 -> base64Url + "==";
            case 3 -> base64Url + "=";
            default -> base64Url;
        };
    }

    private static String orderIdFrom(String path, String prefix, String suffix) {
        String rest = path.substring(prefix.length());
        if (suffix != null) {
            rest = rest.substring(0, rest.length() - suffix.length());
        }
        return rest;
    }

    private X509Certificate issueLeaf(String commonName, PublicKey publicKey) throws Exception {
        X500Principal issuer = new X500Principal("CN=Fake ACME Test CA");
        X500Principal subject = new X500Principal("CN=" + commonName);
        BigInteger serial = BigInteger.valueOf(idSequence.incrementAndGet());
        Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(Instant.now().plus(90, ChronoUnit.DAYS));
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        var builder = new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(caKeyPair.getPublic()));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static X509Certificate selfSign(KeyPair keyPair, String commonName) throws Exception {
        X500Principal subject = new X500Principal("CN=" + commonName);
        BigInteger serial = BigInteger.valueOf(System.nanoTime());
        Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));
        var builder = new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static final class OrderState {
        private final String id;
        private final List<String> domains;
        private final PublicKey accountKey;
        private final String challengeToken;
        private volatile String challengeStatus = "pending";
        private volatile boolean challengeTriggered;
        private volatile String status = "pending";
        private volatile String certificateUrl;
        private volatile List<X509Certificate> issuedChain;

        OrderState(String id, List<String> domains, PublicKey accountKey) {
            this.id = id;
            this.domains = domains;
            this.accountKey = accountKey;
            this.challengeToken = "tok" + id + "abcdefghijklmnop".substring(0, 8);
        }

        String authorizationStatus() {
            return "valid".equals(challengeStatus) ? "valid" : "invalid".equals(challengeStatus) ? "invalid" : "pending";
        }
    }
}
