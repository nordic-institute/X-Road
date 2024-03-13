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

public class FixedLdpPresentationGenerator implements PresentationGenerator<JsonObject> {

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
                                         SignatureSuiteRegistry signatureSuiteRegistry, String defaultSignatureSuite, LdpIssuer ldpIssuer, ObjectMapper mapper) {
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
    public JsonObject generatePresentation(List<VerifiableCredentialContainer> credentials, String keyId, Map<String, Object> additionalData) {
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
            throw new IllegalArgumentException("One or more VerifiableCredentials cannot be represented in the desired format " + CredentialFormat.JSON_LD);
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

        var jwk = CryptoConverter.createJwk(new KeyPair(getPublicKey(), pk));
        var keypair = new JwkMethod(keyId, type, null, jwk);

        var options = (DataIntegrityProofOptions) suite.createOptions();
        options.purpose(URI.create("https://w3id.org/security#assertionMethod"));
        options.verificationMethod(getVerificationMethod(keyId));
        return ldpIssuer.signDocument(presentationObject, keypair, options)
                .orElseThrow(f -> new EdcException(f.getFailureDetail()));
    }

    public static PublicKey getPublicKey() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return certificateFactory.generateCertificate(new ByteArrayInputStream(("-----BEGIN " +
                    "CERTIFICATE-----\nMIIENTCCAh2gAwIBAgIBDjANBgkqhkiG9w0BAQsFADBbMQswCQYDVQQGEwJGSTEUMBIGA1UECgwLWC1Sb2FkIFRlc3QxGjAYBgNVBAsMEVgtUm9hZCBUZXN0IENBIE9VMRowGAYDVQQDDBFYLVJvYWQgVGVzdCBDQSBDTjAeFw0yMzAyMTUxMDMwNDBaFw00MzAyMTAxMDMwNDBaMEIxCzAJBgNVBAYTAkZJMQ8wDQYDVQQKDAZPUkFOR0UxDDAKBgNVBAMMAzIyMjEUMBIGA1UEBRMLREVWL1NTMS9DT00wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCb8FQOy6Q+S3U5BNlZEhvPKW/VyESWCxJNcAANuNNypSQ5lgX+ROms/CK8y0epvyL1j6UNlIkaq7Kte0xWNzcipDpwjwdtjWwNrvgDq0nZs0e+UwqVjlhzyn50FocgK+IPLYOFj8ctumT/Dr/MlfdNvDqq7m5VGvNM2hgsu8OY/YsRQ3WZJh5tFrlpDeA/QXZBlmytHSa3n1WHppqIaDoqS0Skwxc+LhhOa+ttu8u+TJlsAIJaP8+eAO/M2snHrZvoJ1s/NeNsTzaptn/Mg4tQi5p5zNHCnkSkzJTP/C5FCR+u9FXe4hjGOyhdjxliAkn6InWhOS6+vbjkbjJS/KktAgMBAAGjHTAbMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgZAMA0GCSqGSIb3DQEBCwUAA4ICAQASYHbhw7HFsGCtawnu9TmFt/TxtKbWuEkXCYg98NVs6/6u4J2Pf/krg1JoHpV2xkCYkNxGtWM5NCNm+WaDffkn0MY/sC8IsrliwQ89BDoEbaH4VpjOYDsQkW0EuhqMdwvxWR2PXD67cFzKcPR+ju+HjINeSX7kZxoVSvh7vPWYysrhOLegqo2xIMdTP1QyAGuzD0Chb5LqXQnDe8d9imJXOIAhJYh73SEYw05eHpZeWB0Z217pXh9zE2iJvpSRakbd8SqM02HmpvyQmKUtA3zTaOYoKMMP5wQ4JMYztNcb6Oy3uLEOpK2jOSoHuZFs9FpVWkLI7OSjqpWjSeDp/bRHtKnmUbAkn+IdZ5jkfmO6f84xWjkLsYXCFAkZ2n6X26LotOkuFVtPsmyk+jHbbG0gDJ199+X+fdmw+rDpCV4YHP5Lkmf7XsV8wFnW5J8DleOW0FjPW9q55Yxz2+/TFGI5RZcU/HG2U0zissHKeKSmWSGDsiaqgm8TKxy0Cj6Z+cDl2sCglITJvA5LkzmaEWv/IbMxlwNWv13ca6qj5ltk1oWSTuwngDA/fYCpSzUUEuMPX+tfjY2XhmV8bc8hn0woips8R8suAH0kQ54ub6cFt+pgIZob+XaRUI78cCe8xUYjnaWYOeShf13acubsb/4FSW88X0fA6OPN2joWqGwdAg==\n-----END CERTIFICATE-----").getBytes())).getPublicKey();
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
