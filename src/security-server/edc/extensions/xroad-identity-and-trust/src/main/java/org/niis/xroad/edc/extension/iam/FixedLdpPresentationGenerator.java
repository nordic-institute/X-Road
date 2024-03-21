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
package org.niis.xroad.edc.extension.iam;

import com.apicatalog.ld.signature.SignatureSuite;
import com.apicatalog.ld.signature.method.VerificationMethod;
import com.apicatalog.vc.integrity.DataIntegrityProofOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.identityhub.core.creators.LdpPresentationGenerator;
import org.eclipse.edc.identityhub.spi.generator.PresentationGenerator;
import org.eclipse.edc.identitytrust.model.CredentialFormat;
import org.eclipse.edc.identitytrust.model.VerifiableCredentialContainer;
import org.eclipse.edc.identitytrust.verification.SignatureSuiteRegistry;
import org.eclipse.edc.security.signature.jws2020.JwkMethod;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.security.PrivateKeyResolver;
import org.eclipse.edc.verifiablecredentials.linkeddata.LdpIssuer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.eclipse.edc.identityhub.core.creators.LdpPresentationGenerator.TYPE_PROPERTY;
import static org.eclipse.edc.identityhub.core.creators.LdpPresentationGenerator.VERIFIABLE_CREDENTIAL_PROPERTY;
import static org.eclipse.edc.identitytrust.VcConstants.IATP_CONTEXT_URL;
import static org.eclipse.edc.identitytrust.VcConstants.PRESENTATION_EXCHANGE_URL;
import static org.eclipse.edc.identitytrust.VcConstants.W3C_CREDENTIALS_URL;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;

@SuppressWarnings("checkstyle:LineLength")
public class FixedLdpPresentationGenerator implements PresentationGenerator<JsonObject> {

    private static final Map<String, String> ALIAS_CERT_MAP = Map.of(
            "did:web:did-server:ss0", "-----BEGIN CERTIFICATE-----\nMIIEOzCCAiOgAwIBAgIBBjANBgkqhkiG9w0BAQsFADBbMQswCQYDVQQGEwJGSTEUMBIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9VMRowGAYDVQQDDBFYLVJvYWQgVGVzdCBDQSBDTjAeFw0yMTAzMTAwODI2NTdaFw00MTAzMDUwODI2NTdaMEgxCzAJBgNVBAYTAkZJMRAwDgYDVQQKDAdUZXN0T3JnMRIwEAYDVQQDDAkyOTA4NzU4LTQxEzARBgNVBAUTCkNTL1NTMC9PUkcwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDhTvUommNFnntSWkAMX3zQ5F8yvd5re+5mEYaY4OQu54943p+N3WgXK7+90Jwmj2JcS2cDoMT0MU6FLXvdAtrhMk/NeQD481r24cDWvUvMZ935C4DPx8JHtaip4/Y63LtYnCfRcWA4zpcmUnZR1UWgUgpnCdVmWYV9quxj211LxNYf/ChZjUrf+FOPCc5HCH5H1grI+NkQHgpTG17K2UoVU6ho4S9ohrKDBrl3e85O7xS5TXPUT/wfP7hCO+R8DO2aegxmIy5FF/EPRzjpjQ4JVwsOzrAXS09d1cGJL9/ooqSq8oqLc+uTL0a/bte0BHH2dW/m6Kbk0kLAp8rrmlnhAgMBAAGjHTAbMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgZAMA0GCSqGSIb3DQEBCwUAA4ICAQA86e5eogjRWMV/+Qmwe0vNA12a5VELpJ2/WlxooqKyzT4j4BBEuCLJHPWUuG0p9I2fQ6XLUSsG43x0ZT19c1cy3QljXWI7DUA4+y03eZuSXw5DHKOdRoAQrmfJfqf9q+G1b6jqzYLZsrZYnuOzt9KmFjJMNIpcRFcAEhODu6raZJKgc37AHkuDFFQyQicjpH2SKNY/u8nyQXMI/TqKJWb92HDh+tgSeumdaHrTXXlzC0Gdx5qYgXjZVkzfK1b3eZi2N3Tp3HccNrND84GoLrHLsBwZNL0uS6+U3yKPfNGsz6gUFoXRKhVyxxDnUsCm7RCA7x2Wxxj2g7/CSQBcKKrY1ETuzc0ksla5cttQdU7TnGB3Acai3VnfDs+w5kxkUg8HMTH6ygEjPKGcxESKwmWwG0wH0ICYY3D4S3q+gIs8BvjB/6gC0UxJeu4yiR72uco9EFIikRI9w/+s3GGBr2NbJAJn+EEmK32BPoBky4GNI0BpjVUUcukJMXXJPdnq3ifto8CJT8ZF4XCAkhWD12nA+9JdNIBq1/AFqrpZ4J0oKlzhXyETbrLi2AIcJQg5/3QR5Pbvv/N5FdZG8DZPJNFlqfqpBrgCUQWQlJIqB85EPh2Qyq4JW/bTVavqS2igW8QRKXsxjQMC10dH0VRBFG6HVss1SrkCF6Fpm4R32tC5CA==\n-----END CERTIFICATE-----",
            "did:web:did-server:ss1", "-----BEGIN CERTIFICATE-----\nMIIEezCCAmOgAwIBAgIBFDANBgkqhkiG9w0BAQsFADBbMQswCQYDVQQGEwJGSTEUMBIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9VMRowGAYDVQQDDBFYLVJvYWQgVGVzdCBDQSBDTjAeFw0yNDAyMjAxNDM2MDRaFw00NDAyMTUxNDM2MDRaMEgxCzAJBgNVBAYTAkZJMRAwDgYDVQQKDAdUZXN0R292MRIwEAYDVQQDDAkwMjQ1NDM3LTIxEzARBgNVBAUTCkNTL1NTMS9HT1YwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDCmqhLyC57snFixbLNAyN4srSrgVbJEuI/VRAJtslqqENn+NSX/ZSDMd5PJg3J4UkJFL80IC5Z7eEb5FS8oN4XqjV5WWmhVfQxGs3CCCjjrnL1iU437PvJdQZC9uWYkMRsEShhXnUkchIUJMHj+0d3BIGDrM8fPFbgox1A50ecbSS6Kl622UJ2/B/bUsKz1Ngrsfz8aPfRWKVIk7hsF6C2DvkmOAMOKnAT924rWBELAV/HUmZGrbYJTwSnGghehFB+fONQIbZUklh54kxbyIO9OnZJDckrgcl0yJyYH/2Mk9GzU40S6aMLBEXgiC1fmuch49s7AXqC0hLNAbj9YHQDAgMBAAGjXTBbMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgZAMB0GA1UdDgQWBBSe5JAV+CU0PQ1CVtbl4MPTf1/kaDAfBgNVHSMEGDAWgBTOdbt9k88MTU+r8w/+KsgVICpQnDANBgkqhkiG9w0BAQsFAAOCAgEARtcWa0crF7x+JbeS1GyCLRuBaxw12Tro8UcqyzA3nCccZV+3LqobQ8BfV+CF0bNuszpuWOH2nTGwTTNlVAHtuydHNg3LmGhHyhFPG3SZ8C8OeYFvtMG07ilcTejIfc6Ekc6L3y43tyOW12ecSHNdUAtb2il6FVRuoi39/M27W1cWKL8YaKEzct9ZQUZMc3x2DAPb1k4u6QzP5JVH/UrA97Pr6uAmiOR5S9XaBjXXr8vVEMjA+ZpxbDnfAuFc53wexwqvNzRUhStZqXgqtX8LGEFpDWMSNecV09zQDfnQBtATDN1FmlGbYwXar13jX0ctN8SXWwlZCJ7BNMcqLzPPg5lWVNrf9awZjSW63l43xHQS5pWVpy2LfUOhv5w+4UAOK/5uHhfFmdAdq+Hnp3j4PBTrv5ayr9yuaqToZBYBlHqJX7hriL8c7t/UhOaCiNI42QFFwozlg4vpzDmfV1xSV8E2A2SsD4QCgjaU+n95hahxAzm8LMJ3fpib6MOEqMphmV+18UrnEfyS0zgjyhxME7GHUu612DWVzHFSApHc8uD4g0BXJi2CJrsxJ9iYeg/0K1h866NU7l3pneGJuN9AXAdYDVu6lq7/U3b+MvAZbSHlA016fzNn3UYCQL+CLzes6FqDx6gOV9XujiGTSpBsOFpxBbEDSSLPKQFfNIpUmZQ=\n-----END CERTIFICATE-----"
    );

    public static final String ID_PROPERTY = "id";

    public static final String TYPE_ADDITIONAL_DATA = "types";
    public static final String HOLDER_PROPERTY = "holder";
    private final PrivateKeyResolver privateKeyResolver;

    private final String issuerId;
    private final SignatureSuiteRegistry signatureSuiteRegistry;
    private final String defaultSignatureSuite;
    private final LdpIssuer ldpIssuer;
    private final ObjectMapper mapper;

    public FixedLdpPresentationGenerator(PrivateKeyResolver privateKeyResolver, String ownDid,
                                         SignatureSuiteRegistry signatureSuiteRegistry, String defaultSignatureSuite, LdpIssuer ldpIssuer,
                                         ObjectMapper mapper) {
        this.privateKeyResolver = privateKeyResolver;
        this.issuerId = ownDid;
        this.signatureSuiteRegistry = signatureSuiteRegistry;
        this.defaultSignatureSuite = defaultSignatureSuite;
        this.ldpIssuer = ldpIssuer;
        this.mapper = mapper;
    }

    /**
     * Will always throw an {@link UnsupportedOperationException}.
     * Please use {@link LdpPresentationGenerator#generatePresentation(List, String, Map)} instead.
     */
    @Override
    public JsonObject generatePresentation(List<VerifiableCredentialContainer> credentials, String keyId) {
        throw new UnsupportedOperationException("Must provide additional data: 'types'");

    }

    /**
     * Creates a presentation with the given credentials, key ID, and additional data. Note that JWT-VCs cannot be represented in LDP-VPs - while the spec would allow that
     * the JSON schema does not.
     *
     * @param credentials    The list of Verifiable Credential Containers to include in the presentation.
     * @param keyId          The key ID of the private key to be used for generating the presentation. Must be a URI.
     * @param additionalData The additional data to be included in the presentation.
     *                       It must contain a "types" field and optionally, a "suite" field to indicate the desired signature suite.
     *                       If the "suite" parameter is specified, it must be a W3C identifier for signature suites.
     * @return The created presentation as a JsonObject.
     * @throws IllegalArgumentException If the additional data does not contain "types",
     *                                  if no {@link SignatureSuite} is found for the provided suite identifier,
     *                                  if the key ID is not in URI format,
     *                                  or if one or more VerifiableCredentials cannot be represented in the JSON-LD format.
     */
    @Override
    public JsonObject generatePresentation(List<VerifiableCredentialContainer> credentials, String keyId,
                                           Map<String, Object> additionalData) {
        if (!additionalData.containsKey("types")) {
            throw new IllegalArgumentException("Must provide additional data: 'types'");
        }

        var keyIdUri = URI.create(keyId);

        var suiteIdentifier = additionalData.getOrDefault("suite", defaultSignatureSuite).toString();
        var suite = signatureSuiteRegistry.getForId(suiteIdentifier);
        if (suite == null) {
            throw new IllegalArgumentException("No SignatureSuite for identifier '%s' was found.".formatted(suiteIdentifier));
        }

        if (credentials.stream().anyMatch(c -> c.format() != CredentialFormat.JSON_LD)) {
            throw new IllegalArgumentException("One or more VerifiableCredentials cannot be represented in the desired format "
                    + CredentialFormat.JSON_LD);
        }

        // check if private key can be resolved
        var pk = privateKeyResolver.resolvePrivateKey(keyId)
                .orElseThrow(f -> new IllegalArgumentException(f.getFailureDetail()));

        var types = (List) additionalData.get("types");
        var presentationObject = Json.createObjectBuilder()
                .add(CONTEXT, stringArray(List.of(W3C_CREDENTIALS_URL, PRESENTATION_EXCHANGE_URL)))
                .add(ID_PROPERTY, IATP_CONTEXT_URL + "/id/" + UUID.randomUUID())
                .add(TYPE_PROPERTY, stringArray(types))
                .add(HOLDER_PROPERTY, issuerId)
                .add(VERIFIABLE_CREDENTIAL_PROPERTY, toJsonArray(credentials))
                .build();

        return signPresentation(presentationObject, suite, pk, keyIdUri);
    }

    @NotNull
    private JsonArray toJsonArray(List<VerifiableCredentialContainer> credentials) {
        var array = Json.createArrayBuilder();
        credentials.stream()
                .map(VerifiableCredentialContainer::rawVc)
                .map(str -> {
                    try {
                        return mapper.readValue(str, JsonObject.class);
                    } catch (JsonProcessingException e) {
                        throw new EdcException(e);
                    }
                })
                .forEach(array::add);
        return array.build();
    }

    private JsonObject signPresentation(JsonObject presentationObject, SignatureSuite suite, PrivateKey pk, URI keyId) {
        var type = URI.create(suite.getId().toString());

        var jwk = CryptoConverter.createJwk(new KeyPair(getPublicKey(keyId), pk));
        var keypair = new JwkMethod(keyId, type, null, jwk);

        var options = (DataIntegrityProofOptions) suite.createOptions();
        options.purpose(URI.create("https://w3id.org/security#assertionMethod"));
        options.verificationMethod(getVerificationMethod(keyId));
        return ldpIssuer.signDocument(presentationObject, keypair, options)
                .orElseThrow(f -> new EdcException(f.getFailureDetail()));
    }

    public static PublicKey getPublicKey(URI keyId) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return certificateFactory.generateCertificate(new ByteArrayInputStream(ALIAS_CERT_MAP.get(keyId.toString()).getBytes()))
                    .getPublicKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    private VerificationMethod getVerificationMethod(URI keyId) {
        return new JwkMethod(keyId, null, null, null);
    }

    private JsonArrayBuilder stringArray(Collection<?> values) {
        var ja = Json.createArrayBuilder();
        values.forEach(s -> ja.add(s.toString()));
        return ja;
    }
}
