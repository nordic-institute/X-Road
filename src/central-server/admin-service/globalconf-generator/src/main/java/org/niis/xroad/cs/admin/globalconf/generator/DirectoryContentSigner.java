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
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.util.HashCalculator;
import ee.ria.xroad.signer.protocol.message.GetSignMechanism;
import ee.ria.xroad.signer.protocol.message.GetSignMechanismResponse;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;

import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;
import static ee.ria.xroad.common.util.CryptoUtils.getSignatureAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.getSignatureAlgorithmURI;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.header;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.partBuilder;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.rawPart;

@RequiredArgsConstructor
@Slf4j
public class DirectoryContentSigner {
    @NonNull
    private final SignerProxyFacade signerProxy;
    @NonNull
    private final String signDigestAlgorithmId;
    @NonNull
    private final String certDigestAlgorithmId;

    @SneakyThrows
    public String createSignedDirectory(DirectoryContentBuilder.DirectoryContentHolder directoryContent, String keyId, byte[] signingCert) {
        var signAlgorithmId = getSignAlgorithmId(keyId, signDigestAlgorithmId);
        var certHashCalculator = new HashCalculator(getDigestAlgorithmURI(certDigestAlgorithmId));
        return MultipartMessage.builder()
                .contentType("multipart/related")
                .part(rawPart(directoryContent.getContent()))
                .part(partBuilder()
                        .header(header("Content-Type", "application/octet-stream"))
                        .header(header("Content-Transfer-Encoding", "base64"))
                        .header(header("Signature-Algorithm-Id", getSignatureAlgorithmURI(signAlgorithmId)))
                        .header(header("Verification-certificate-hash", String.format("%s; hash-algorithm-id=\"%s\"",
                                certHashCalculator.calculateFromBytes(signingCert), certHashCalculator.getAlgoURI())))
                        .content(encodeBase64(sign(keyId, directoryContent.getSignableContent().getBytes(UTF_8))))
                        .build())
                .build().toString();
    }

    @SneakyThrows
    private byte[] sign(String keyId, byte[] data) {
        log.trace("sign(dataBytes)");

        var signatureAlgorithmId = getSignAlgorithmId(keyId, signDigestAlgorithmId);

        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);
        byte[] digest = calculateDigest(digestAlgorithmId, data);

        SignResponse response = signerProxy.execute(new Sign(keyId, signatureAlgorithmId, digest));
        return response.getSignature();
    }

    @SneakyThrows
    private String getSignAlgorithmId(String keyId, String digestAlgorithmId) {
        log.trace("getSignAlgorithmId({}, {})", keyId, digestAlgorithmId);

        GetSignMechanismResponse response = signerProxy.execute(new GetSignMechanism(keyId));

        return getSignatureAlgorithmId(digestAlgorithmId, response.getSignMechanismName());
    }

}
