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

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.message.GetSignMechanism;
import ee.ria.xroad.signer.protocol.message.GetSignMechanismResponse;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DirectoryContentSignerTest {
    public static final Pattern HEADER_PATTERN = Pattern.compile(
            "^Content-Type: multipart/related; charset=UTF-8; boundary=(\\w+)\r\n.*", Pattern.DOTALL);

    private static final String CONTENT_TYPE = "Content-Type: multipart/mixed; charset=UTF-8; boundary=y7iyKCnxEZLNDClPvlcQ\r\n"
            + "\r\n";
    private static final String SIGNABLE_DIRECTORY_CONTENT =
            "--y7iyKCnxEZLNDClPvlcQ\r\n"
                    + "Expire-date: 2022-12-08T08:05:01Z\r\n"
                    + "Version: 2\r\n"
                    + "\r\n"
                    + "--y7iyKCnxEZLNDClPvlcQ\r\n"
                    + "Content-type: application/octet-stream\r\n"
                    + "Content-transfer-encoding: base64\r\n"
                    + "Content-identifier: CONTENT-ID; instance='CS-INSTANCE'\r\n"
                    + "Content-location: /V2/some/path/config-file.txt\r\n"
                    + "Hash-algorithm-id: http://www.w3.org/2000/09/xmldsig#sha1\r\n"
                    + "\r\n"
                    + "e4VJC7nd16bg9QniBVyvjcaeTwE=\r\n"
                    + "--y7iyKCnxEZLNDClPvlcQ--";

    private DirectoryContentBuilder.DirectoryContentHolder directoryContentHolder =
            new DirectoryContentBuilder.DirectoryContentHolder(DIRECTORY_CONTENT, SIGNABLE_DIRECTORY_CONTENT);

    private static final String DIRECTORY_CONTENT = CONTENT_TYPE + SIGNABLE_DIRECTORY_CONTENT;
    public static final String KEY_ID = "KEY-ID";
    public static final byte[] SIGNING_CERT = "SIGNING-CERT".getBytes(UTF_8);
    public static final byte[] SIGNATURE = "<signature>".getBytes(UTF_8);
    public static final String SIGNATURE_BASE64 = CryptoUtils.encodeBase64(SIGNATURE);


    @SneakyThrows
    @Test
    void createSignedDirectory() {
        var signerProxyFacade = Mockito.mock(SignerProxyFacade.class);
        when(signerProxyFacade.execute(new GetSignMechanism(KEY_ID))).thenReturn(
                new GetSignMechanismResponse(CryptoUtils.CKM_RSA_PKCS_NAME));
        var digest = CryptoUtils.calculateDigest(CryptoUtils.SHA512_ID, SIGNABLE_DIRECTORY_CONTENT.getBytes());
        when(signerProxyFacade.execute(new Sign(KEY_ID, CryptoUtils.SHA512WITHRSA_ID, digest)))
                .thenReturn(new SignResponse(SIGNATURE));

        var signedDirectory = new DirectoryContentSigner(signerProxyFacade, CryptoUtils.SHA512_ID, CryptoUtils.SHA512_ID)
                .createSignedDirectory(directoryContentHolder, KEY_ID, SIGNING_CERT);

        var headerMatcher = HEADER_PATTERN.matcher(signedDirectory);
        assertThat(headerMatcher).as("Expecting header to match pattern").matches(Matcher::matches);
        var boundary = headerMatcher.group(1);

        assertThat(signedDirectory).isEqualTo("Content-Type: multipart/related; charset=UTF-8; boundary=%s\r\n"
                + "\r\n"
                + "--%s\r\n"
                + "%s\r\n"
                + "--%s\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "Signature-Algorithm-Id: http://www.w3.org/2001/04/xmldsig-more#rsa-sha512\r\n"
                + "Verification-certificate-hash:"
                + " BRWad0j23C27HEofaHQZpBI8DqwjTT8wkucCTGIB9v6kQFAn7AIPevuPWn6SkdZSnru0YCbI9mxzv7DwyNG7dg==;"
                + " hash-algorithm-id=\"http://www.w3.org/2001/04/xmlenc#sha512\"\r\n"
                + "\r\n"
                + "%s\r\n"
                + "--%s--\r\n", boundary, boundary, DIRECTORY_CONTENT, boundary, SIGNATURE_BASE64, boundary);
    }

}
