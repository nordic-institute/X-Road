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
package org.niis.xroad.edc.credential;

import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.spi.EdcException;
import org.niis.xroad.restapi.converter.ClientIdConverter;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.niis.xroad.edc.credential.XRoadMemberShipCredentialIssuanceController.MEMBERSHIP_CREDENTIAL_PATH;

@Path(MEMBERSHIP_CREDENTIAL_PATH)
@Consumes("application/jwt")
@Produces("application/vc+jwt")
public class XRoadMemberShipCredentialIssuanceController {

    static final String MEMBERSHIP_CREDENTIAL_PATH = "/membership-credential";

    private final DidPublicKeyResolver didPublicKeyResolver;

    private final GlobalConfProvider globalConfProvider;

    private final String issuingDid;
    private final JWSSigner signer;
    private final String keyId;

    private final ObjectMapper mapper = new ObjectMapper();

    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    public XRoadMemberShipCredentialIssuanceController(DidPublicKeyResolver didPublicKeyResolver, GlobalConfProvider globalConfProvider,
                                                       String issuingDid, JwsSignerProvider jwsSignerProvider, String keyId) {
        this.didPublicKeyResolver = didPublicKeyResolver;
        this.globalConfProvider = globalConfProvider;
        this.issuingDid = issuingDid;
        this.signer = jwsSignerProvider.createJwsSigner(keyId)
                .orElseThrow(f ->
                        new EdcException("JWSSigner cannot be generated for private key '%s': %s".formatted(keyId, f.getFailureDetail())));
        this.keyId = keyId;
    }

    @POST
    public Response issueMembershipCredential(String selfDescriptionJwt) throws Exception {
        var signedJwt = SignedJWT.parse(selfDescriptionJwt);

        // verify cryptographic integrity of JWT
        var publicKeyId = signedJwt.getHeader().getKeyID();
        var publicKey = didPublicKeyResolver.resolveKey(publicKeyId)
                .orElseThrow(f ->
                        new EdcException("Failed to resolve DID public key with ID '%s': %s".formatted(keyId, f.getFailureDetail())));
        var jwtVerifier = CryptoConverter.createVerifierFor(publicKey);
        if (!signedJwt.verify(jwtVerifier)) {
            throw new EdcException("The Cryptographic integrity of self-description is invalid");
        }

        // Ensure the certificate corresponds to the signing public key and is issued by a trusted chain
        var certificate = CryptoUtils.readCertificate(signedJwt.getHeader().getX509CertChain().getFirst().decode());
        if (!Arrays.equals(publicKey.getEncoded(), certificate.getPublicKey().getEncoded())) {
            throw new EdcException("The certificate in self-description does not match with the public key");
        }
        var certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), certificate);
        var verifier = new CertChainVerifier(globalConfProvider, certChain);
        verifier.verifyChainOnly(new Date()); //TODO also verify ocsp

        // Validate the membership claim using the signing certificate
        var xroadMemberIdentifier = signedJwt.getJWTClaimsSet().getStringClaim("xroadMemberIdentifier");
        if (!clientIdConverter.isEncodedMemberId(xroadMemberIdentifier)) {
            throw new IllegalStateException("Invalid member identifier: " + xroadMemberIdentifier);
        }
        var clientId = clientIdConverter.convertId(xroadMemberIdentifier);
        var clientIdFromCert = getClientIdFromCert(certificate, clientId);
        if (!clientId.equals(clientIdFromCert)) {
            throw new EdcException("The 'xroadMemberIdentifier' claim in self-description does not belong to the one in certificate");
        }
        var isMemberRecognized = globalConfProvider.getMembers(globalConfProvider.getInstanceIdentifier()).stream()
                .anyMatch(m -> m.getId().equals(clientIdFromCert));
        if (!isMemberRecognized) {
            throw new EdcException("Member '%s' is not recognized".formatted(clientIdFromCert));
        }

        // Construct the membership credential
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .keyID(issuingDid + "#" + keyId)
                .type(JOSEObjectType.JWT)
                .build();
        var participantDid = signedJwt.getHeader().getCustomParam("iss").toString();
        var memberName = globalConfProvider.getMemberName(clientIdFromCert);
        var credentialPayload = constructCredentialPayload(participantDid, xroadMemberIdentifier, memberName);
        var claims = new JWTClaimsSet.Builder()
                .subject(participantDid)
                .issuer(issuingDid)
                .claim("vc", mapper.readValue(credentialPayload.toString(), Map.class))
                .issueTime(Date.from(Instant.now()))
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return Response.ok().entity(jwt.serialize()).build();
    }

    private ClientId getClientIdFromCert(X509Certificate cert, ClientId clientId) throws Exception {
        return globalConfProvider.getSubjectName(
                new SignCertificateProfileInfoParameters(clientId.getMemberId(), ""), cert
        );
    }

    private JsonObject constructCredentialPayload(String participantDid, String memberIdentifier, String memberName) {
        return createObjectBuilder()
                .add(JsonLdKeywords.CONTEXT, createArrayBuilder()
                        .add("https://www.w3.org/2018/credentials/v1")
                        .add("https://w3id.org/security/suites/jws-2020/v1")
                        .add("https://www.w3.org/ns/did/v1")
                        .add(createObjectBuilder().add("xrd", "https://w3id.org/xroad/credentials/").build())
                        .build())
                .add("id", memberIdentifier)
                .add("type", createArrayBuilder()
                        .add("VerifiableCredential")
                        .add("XRoadCredential")
                        .build())
                .add("issuer", issuingDid)
                .add("issuanceDate", Instant.now().toString())
                .add("credentialSubject", createObjectBuilder()
                        .add("id", participantDid)
                        .add("xrd:memberIdentifier", memberIdentifier)
                        .add("xrd:memberName", memberName)
                        .build())
                .build();
    }

}
