package org.niis.xroad.edc.ih;

import ee.ria.xroad.signer.SignerRpcClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.security.token.jwt.CryptoConverter;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class XRoadSelfDescriptionGenerator {
    private final SignerRpcClient signerRpcClient;

    public JWSObject generate(String xroadMemberIdentifier, JWSSigner signer, String did, String keyId) throws Exception {
        var payload = getPayload(xroadMemberIdentifier);
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(true)
                .customParam("iss", did)
                .keyID(did + "#" + keyId)
                .x509CertChain(List.of(Base64.encode(getActiveCertificate(keyId))))
                .customParam("iat", Instant.now().toString())
                .build();
        var detachedPayload = new Payload(payload);
        var jwsObject = new JWSObject(header, detachedPayload);
        jwsObject.sign(signer);
        return jwsObject;
    }

    private byte[] getActiveCertificate(String keyId) throws Exception {
        var token = signerRpcClient.getTokenForKeyId(keyId);
        var certificates = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getCerts)
                .orElseThrow();
        return certificates.stream()
                .filter(CertificateInfo::isActive)
                .map(CertificateInfo::getCertificateBytes)
                .findFirst()
                .orElseThrow();
    }

    private static String getPayload(String xroadMemberIdentifier) {
        return "{\n" +
                "  \"xroadMemberIdentifier\": \"" + xroadMemberIdentifier + "\"\n" +
                "}";
    }

}
