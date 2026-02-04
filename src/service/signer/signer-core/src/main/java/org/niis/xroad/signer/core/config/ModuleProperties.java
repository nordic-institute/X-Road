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

package org.niis.xroad.signer.core.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ModuleProperties {

    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    @WithName("library")
    String library();

    @WithName("library-cant-create-os-threads")
    @WithDefault("false")
    Optional<Boolean> libraryCantCreateOsThreads();

    @WithName("os-locking-ok")
    @WithDefault("false")
    Optional<Boolean> osLockingOk();

    @WithName("sign-verify-pin")
    @WithDefault("false")
    boolean signVerifyPin();

    @WithName("token-id-format")
    @WithDefault("{moduleType}{slotIndex}{serialNumber}{label}")
    String tokenIdFormat();

    @WithName("sign-mechanism")
    @WithDefault("CKM_RSA_PKCS")
    String signMechanism();

    @WithName("rsa-sign-mechanism")
    Optional<String> rsaSignMechanism();

    @WithName("ec-sign-mechanism")
    @WithDefault("CKM_ECDSA")
    String ecSignMechanism();

    @WithName("pub-key-attribute-encrypt")
    @WithDefault("true")
    boolean pubKeyAttributeEncrypt();

    @WithName("pub-key-attribute-verify")
    @WithDefault("true")
    boolean pubKeyAttributeVerify();

    @WithName("pub-key-attribute-wrap")
    Optional<Boolean> pubKeyAttributeWrap();

    @WithName("pub-key-attribute-allowed-mechanisms")
    Optional<List<String>> pubKeyAttributeAllowedMechanisms();

    @WithName("priv-key-attribute-sensitive")
    @WithDefault("true")
    boolean privKeyAttributeSensitive();

    @WithName("priv-key-attribute-decrypt")
    @WithDefault("true")
    boolean privKeyAttributeDecrypt();

    @WithName("priv-key-attribute-sign")
    @WithDefault("true")
    boolean privKeyAttributeSign();

    @WithName("priv-key-attribute-unwrap")
    Optional<Boolean> privKeyAttributeUnwrap();

    @WithName("priv-key-attribute-allowed-mechanisms")
    Optional<List<String>> privKeyAttributeAllowedMechanisms();

    @WithName("slot-ids")
    Optional<Set<Long>> slotIds();

    // undocumented properties

    @WithName("batch-signing-enabled")
    @WithDefault("true")
    boolean batchSigningEnabled();

    @WithName("read-only")
    @WithDefault("false")
    boolean readOnly();

    @WithName("pub-key-attribute-verify-recover")
    Optional<Boolean> pubKeyAttributeVerifyRecover();

    @WithName("pub-key-attribute-trusted")
    Optional<Boolean> pubKeyAttributeTrusted();

    @WithName("priv-key-attribute-sign-recover")
    Optional<Boolean> privKeyAttributeSignRecover();

    @WithName("priv-key-attribute-extractable")
    Optional<Boolean> privKeyAttributeExtractable();

    @WithName("priv-key-attribute-always-sensitive")
    Optional<Boolean> privKeyAttributeAlwaysSensitive();

    @WithName("priv-key-attribute-never-extractable")
    Optional<Boolean> privKeyAttributeNeverExtractable();

    @WithName("priv-key-attribute-wrap-with-trusted")
    Optional<Boolean> privKeyAttributeWrapWithTrusted();

}
