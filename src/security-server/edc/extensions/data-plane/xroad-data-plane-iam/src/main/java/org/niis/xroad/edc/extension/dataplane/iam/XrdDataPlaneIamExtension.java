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

package org.niis.xroad.edc.extension.dataplane.iam;

import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import lombok.SneakyThrows;
import org.eclipse.edc.connector.dataplane.iam.service.DefaultDataPlaneAccessTokenServiceImpl;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessTokenService;
import org.eclipse.edc.connector.dataplane.spi.store.AccessTokenDataStore;
import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.token.spi.TokenValidationService;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.eclipse.edc.connector.dataplane.iam.DataPlaneIamDefaultServicesExtension.TOKEN_SIGNER_PRIVATE_KEY_ALIAS;
import static org.eclipse.edc.connector.dataplane.iam.DataPlaneIamDefaultServicesExtension.TOKEN_VERIFIER_PUBLIC_KEY_ALIAS;

@Extension(value = XrdDataPlaneIamExtension.NAME)
public class XrdDataPlaneIamExtension implements ServiceExtension {

    static final String NAME = "XRD Data Plane IAM Services";

    @Inject
    private JwsSignerProvider jwsSignerProvider;
    @Inject
    private AccessTokenDataStore accessTokenDataStore;
    @Inject
    private TokenValidationService tokenValidationService;

    @Provider
    public DataPlaneAccessTokenService defaultAccessTokenService(ServiceExtensionContext context) {
        var tokenVerifierPublicKeyAlias = context.getConfig().getString(TOKEN_VERIFIER_PUBLIC_KEY_ALIAS);
        var tokenSignerPrivateKeyAlias = context.getConfig().getString(TOKEN_SIGNER_PRIVATE_KEY_ALIAS);
        var monitor = context.getMonitor().withPrefix("DataPlane IAM");
        return new DefaultDataPlaneAccessTokenServiceImpl(new JwtGenerationService(jwsSignerProvider),
                accessTokenDataStore, monitor,
                () -> tokenSignerPrivateKeyAlias,
                () -> tokenVerifierPublicKeyAlias,
                tokenValidationService, keyId -> Result.ofThrowable(() -> getPublicKey(keyId)));
    }

    @SneakyThrows
    private PublicKey getPublicKey(String keyId) {
        var token = SignerProxy.getTokenForKeyId(keyId);
        String base64PublicKey = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getPublicKey)
                .orElseThrow();
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

}
