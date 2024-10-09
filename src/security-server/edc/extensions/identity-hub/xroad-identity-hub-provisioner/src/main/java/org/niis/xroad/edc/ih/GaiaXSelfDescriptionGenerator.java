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
package org.niis.xroad.edc.ih;

import com.apicatalog.jsonld.json.JsonUtils;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.CryptoSuite;
import com.apicatalog.ld.signature.LinkedDataSuiteError;
import com.apicatalog.vc.ModelVersion;
import com.apicatalog.vc.Verifiable;
import com.apicatalog.vc.issuer.ProofDraft;
import com.apicatalog.vc.processor.ExpandedVerifiable;
import com.apicatalog.vc.proof.EmbeddedProof;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.security.signature.jws2020.Jws2020ProofDraft;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.verifiablecredentials.signature.ExtJWSObject;
import org.eclipse.edc.verifiablecredentials.signature.ExtJws2020CryptoSuite;
import org.eclipse.edc.verifiablecredentials.signature.ExtJws2020ProofDraft;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public final class GaiaXSelfDescriptionGenerator {

    private static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();

    private final CryptoSuite suite = new ExtJws2020CryptoSuite();

    private final DocumentLoader loader = SchemeRouter.defaultInstance();

    private final JsonLd jsonLdService;

    public GaiaXSelfDescriptionGenerator(JsonLd jsonLd) {
        this.jsonLdService = jsonLd;
    }

    public ExpandedVerifiable signDocument(JsonObject jsonLdDocument, JWSSigner signer, URI verificationUrl)
            throws JOSEException, LinkedDataSuiteError, DocumentError {
        var expanded = jsonLdService.expand(jsonLdDocument).getContent();

        var proofDraft = ExtJws2020ProofDraft.Builder.newInstance()
                .mapper(MAPPER)
                .created(Instant.now())
                .verificationUrl(verificationUrl)
                .proofPurpose(URI.create("https://w3id.org/security#assertionMethod"))
                .build();
        var unsignedDraft = proofDraft.unsigned();

        // Gaia-X specific hash
        byte[] documentHash = suite.digest(suite.canonicalize(expanded));
        byte[] hashCode = Hex.encode(documentHash);

        // Create and sign JWS
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(false)
                .criticalParams(Collections.singleton("b64"))
                .build();

        var detachedPayload = new Payload(hashCode);
        var jwsObject = new ExtJWSObject(header, detachedPayload);
        jwsObject.sign(signer);

        var isDetached = true;
        var jws = jwsObject.serialize(isDetached);
        byte[] signature = jws.getBytes();
        var proofValue = Json.createValue(new String(signature));
        var signedProof = Jws2020ProofDraft.signed(unsignedDraft, proofValue);

        var version = Verifiable.getVersion(jsonLdDocument);
        var context = getContext(version, jsonLdDocument, proofDraft);
        return new ExpandedVerifiable(EmbeddedProof.addProof(expanded, signedProof), context, loader);
    }

    private JsonArray getContext(ModelVersion version, JsonObject document, ProofDraft draft) {

        final Collection<String> urls = new HashSet<>();
        final JsonArrayBuilder contexts = Json.createArrayBuilder();

        // extract origin contexts
        if (document != null && document.containsKey(Keywords.CONTEXT)) {
            final JsonValue documentContext = document.get(Keywords.CONTEXT);
            if (JsonUtils.isString(documentContext)) {
                urls.add(((JsonString) documentContext).getString());
                contexts.add(documentContext);

            } else if (JsonUtils.isObject(documentContext)) {
                contexts.add(documentContext);

            } else if (JsonUtils.isArray(documentContext)) {
                for (final JsonValue context : documentContext.asJsonArray()) {
                    if (JsonUtils.isString(context)) {
                        urls.add(((JsonString) context).getString());
                    }
                    contexts.add(context);
                }
            }
        }

        final Collection<String> provided = draft.context(version);

        if (provided != null) {
            // use .stream().filter(Predicate.not(urls::contains))
            for (String url : provided) {
                if (!urls.contains(url)) {
                    urls.add(url);
                    contexts.add(Json.createValue(url));
                }
            }
        }

        return contexts.build();
    }

    public static JsonObject composeGaiaXParticipantDocument(String hostname) throws JsonProcessingException {
        String vc = "{\n" +
                "  \"@context\": [\n" +
                "    \"https://www.w3.org/2018/credentials/v1\",\n" +
                "    \"https://w3id.org/security/suites/jws-2020/v1\",\n" +
                "    \"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"\n" +
                "  ],\n" +
                "  \"type\": [\n" +
                "    \"VerifiableCredential\"\n" +
                "  ],\n" +
                "  \"id\": \"https://" + hostname + ":9396/participant.json\",\n" +
                "  \"issuer\": \"did:web:" + hostname + "%3A9396\",\n" +
                "  \"issuanceDate\": \"" + Instant.now() + "\",\n" +
                "  \"credentialSubject\": {\n" +
                "    \"id\": \"https://" + hostname + ":9396/participant.json#cs\",\n" +
                "    \"type\": \"gx:LegalParticipant\",\n" +
                "    \"gx:legalName\": \"NIIS\",\n" +
                "    \"gx:legalRegistrationNumber\": {\n" +
                "      \"id\": \"https://" + hostname + ":9396/lrn.json#cs\"\n" +
                "    },\n" +
                "    \"gx:headquarterAddress\": {\n" +
                "      \"gx:countrySubdivisionCode\": \"EE-37\"\n" +
                "    },\n" +
                "    \"gx:legalAddress\": {\n" +
                "      \"gx:countrySubdivisionCode\": \"EE-37\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        return MAPPER.readValue(vc, JsonObject.class);
    }

    public static JsonObject composeGaiaXTermsAndConditionsDocument(String hostname) throws JsonProcessingException {
        String vc = "{\n" +
                "  \"@context\": [\n" +
                "    \"https://www.w3.org/2018/credentials/v1\",\n" +
                "    \"https://w3id.org/security/suites/jws-2020/v1\",\n" +
                "    \"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"\n" +
                "  ],\n" +
                "  \"type\": \"VerifiableCredential\",\n" +
                "  \"issuanceDate\": \"" + Instant.now() + "\",\n" +
                "  \"credentialSubject\": {\n" +
                "    \"type\": \"gx:GaiaXTermsAndConditions\",\n" +
                "    \"gx:termsAndConditions\": \"The PARTICIPANT signing the Self-Description agrees as follows:\\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\\n\\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).\",\n" +
                "    \"id\": \"https://" + hostname + ":9396/tsandcs.json#subject\"\n" +
                "  },\n" +
                "  \"issuer\": \"did:web:" + hostname + "%3A9396\",\n" +
                "  \"id\": \"https://" + hostname + ":9396/tsandcs.json\"\n" +
                "}\n";
        return MAPPER.readValue(vc, JsonObject.class);
    }

    public static JsonObject composeXRoadCredentialDocument(String hostname, String xroadIdentifier) throws JsonProcessingException {
        String vc = "{\n" +
                "  \"@context\": [\n" +
                "    \"https://www.w3.org/2018/credentials/v1\",\n" +
                "    \"https://w3id.org/security/suites/jws-2020/v1\",\n" +
                "    \"https://www.w3.org/ns/did/v1\",\n" +
                "    {\n" +
                "      \"xrd\": \"https://w3id.org/xroad/credentials/\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"id\": \"https://" + hostname + ":9396/xroad-credential.json\",\n" +
                "  \"type\": [\n" +
                "    \"VerifiableCredential\",\n" +
                "    \"XroadCredential\"\n" +
                "  ],\n" +
                "  \"issuer\": \"did:web:" + hostname + "%3A9396\",\n" +
                "  \"issuanceDate\": \"" + Instant.now() + "\",\n" +
                "  \"credentialSubject\": {\n" +
                "    \"id\": \"did:web:" + hostname + "%3A9396\",\n" +
                "    \"type\": \"XRoadIdentifier\",\n" +
                "    \"xrd:identifier\": \"" + xroadIdentifier + "\"\n" +
                "  }\n" +
                "}";
        return MAPPER.readValue(vc, JsonObject.class);
    }

}
