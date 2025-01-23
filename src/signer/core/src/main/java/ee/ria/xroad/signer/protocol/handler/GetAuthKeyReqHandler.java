/*
 * The MIT License
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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenUtil;
import ee.ria.xroad.signer.util.passwordstore.PasswordStore;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.AuthKeyProto;

import java.io.File;
import java.io.FileInputStream;

import static java.util.Optional.ofNullable;

/**
 * Handles authentication key retrieval requests.
 */
@Slf4j
@ApplicationScoped
public class GetAuthKeyReqHandler extends AbstractAuthKeyReqHandler<AuthKeyProto> {

    @Override
    protected AuthKeyProto resolveResponse(KeyInfo keyInfo, CertificateInfo certInfo) throws Exception {
        final var alias = keyInfo.getId();
        final var keyStoreFileName = SoftwareTokenUtil.getKeyStoreFileName(alias);
        final char[] password = PasswordStore.getPassword(SoftwareTokenType.ID);

        final var builder = AuthKeyProto.newBuilder()
                .setAlias(alias)
                .setKeyStore(getKeyStore(keyStoreFileName))
                .setCert(certInfo.asMessage());

        ofNullable(password).ifPresent(passwd -> builder.setPassword(new String(passwd)));
        return builder.build();
    }

    private ByteString getKeyStore(String keyStoreFileName) throws Exception {
        final var keyStoreFile = new File(keyStoreFileName);
        log.trace("Loading authentication key from key store '{}'", keyStoreFile);

        try (var fis = new FileInputStream(keyStoreFile)) {
            return ByteString.readFrom(fis);
        }
    }

}
