package org.niis.xroad.edc.ih;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.edc.identityhub.spi.store.CredentialStore;
import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Path("/v1/participants/{participantId}/enveloped-verifiable-credentials")
@Produces("application/vp+ld+json+jwt")
public class EnvelopedVerifiableCredentialsController {

    private final CredentialStore credentialStore;
    private final JWSSigner signer;
    private final String keyId;

    public EnvelopedVerifiableCredentialsController(CredentialStore credentialStore, JwsSignerProvider jwsSignerProvider, String keyId) {
        this.credentialStore = credentialStore;
        this.signer = jwsSignerProvider.createJwsSigner(keyId)
                .orElseThrow(f -> new EdcException("JWSSigner cannot be generated for private key '%s': %s".formatted(keyId, f.getFailureDetail())));
        this.keyId = keyId;
    }

    @GET
    public String getEnvelopedVerifiableCredentials(
            @PathParam("participantId") String participantId, @QueryParam("vcType") String verifiableCredentialType) throws JOSEException {
        var query = buildQuerySpec(participantId, verifiableCredentialType);
        var credentials = credentialStore.query(query)
                .orElseThrow(f -> new EdcException("Error obtaining credentials for participant '%s': %s".formatted(participantId, f.getFailureDetail())));

        var rawVcJwt = credentials.stream()
                .map(c -> c.getVerifiableCredential().rawVc()).toList();
        var vp = toVerifiablePresentation(participantId, rawVcJwt);
        return toVpJwt(participantId, vp);
    }

    private QuerySpec buildQuerySpec(String participantId, String credentialType) {
        var queryBuilder = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantId", "=", participantId));
        if (StringUtils.isNotEmpty(credentialType)) {
            queryBuilder.filter(new Criterion("verifiableCredential.credential.type", "contains", credentialType));
        }
        return queryBuilder.build();
    }

    private String toVpJwt(String did, String vp) throws JOSEException {
        // Create and sign JWS
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(true)
                .customParam("iss", did)
                .keyID(did + "#" + keyId)
                .contentType("vc+ld+json")
                //.type(JOSEObjectType.JWT)
                .build();

        var detachedPayload = new Payload(vp);
        var jwsObject = new JWSObject(header, detachedPayload);
        jwsObject.sign(signer);
        return jwsObject.serialize();
    }

    private String toVerifiablePresentation(String issuer, List<String> rawVcJwts) {
        var envelopedVcs = rawVcJwts.stream()
                .map(this::toEnvelopedVerifiableCredential)
                .collect(Collectors.joining(", "));
        return "{\n" +
                "  \"@context\": [\n" +
                "    \"https://www.w3.org/ns/credentials/v2\",\n" +
                "    \"https://www.w3.org/ns/credentials/examples/v2\"\n" +
                "  ],\n" +
                "  \"type\": \"VerifiablePresentation\",\n" +
                "  \"verifiableCredential\": [" + envelopedVcs +
                "  ],\n" +
                "  \"issuer\": \"" + issuer + "\",\n" +
                "  \"validFrom\": \"" + Instant.now() + "\"\n" +
                "}";
    }



    private String toEnvelopedVerifiableCredential(String rawVcJwt) {
        return "\n" +
                "    {\n" +
                "      \"@context\": \"https://www.w3.org/ns/credentials/v2\",\n" +
                "      \"id\": \"data:application/vc+ld+json+jwt;" + rawVcJwt + "\",\n" +
                "      \"type\": \"EnvelopedVerifiableCredential\"\n" +
                "    }";
    }


}
