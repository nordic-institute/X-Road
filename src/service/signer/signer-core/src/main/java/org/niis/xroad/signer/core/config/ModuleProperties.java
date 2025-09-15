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

    @WithName("library_cant_create_os_threads")
    @WithDefault("false")
    Optional<Boolean> libraryCantCreateOsThreads();

    @WithName("os_locking_ok")
    @WithDefault("false")
    Optional<Boolean> osLockingOk();

    @WithName("sign_verify_pin")
    @WithDefault("false")
    boolean signVerifyPin();

    @WithName("token_id_format")
    @WithDefault("{moduleType}{slotIndex}{serialNumber}{label}")
    String tokenIdFormat();

    @WithName("sign_mechanism")
    @WithDefault("CKM_RSA_PKCS")
    String signMechanism();

    @WithName("rsa_sign_mechanism")
    Optional<String> rsaSignMechanism();

    @WithName("ec_sign_mechanism")
    @WithDefault("CKM_ECDSA")
    String ecSignMechanism();

    @WithName("pub_key_attribute_encrypt")
    @WithDefault("true")
    boolean pubKeyAttributeEncrypt();

    @WithName("pub_key_attribute_verify")
    @WithDefault("true")
    boolean pubKeyAttributeVerify();

    @WithName("pub_key_attribute_wrap")
    Optional<Boolean> pubKeyAttributeWrap();

    @WithName("pub_key_attribute_allowed_mechanisms")
    Optional<List<String>> pubKeyAttributeAllowedMechanisms();

    @WithName("priv_key_attribute_sensitive")
    @WithDefault("true")
    boolean privKeyAttributeSensitive();

    @WithName("priv_key_attribute_decrypt")
    @WithDefault("true")
    boolean privKeyAttributeDecrypt();

    @WithName("priv_key_attribute_sign")
    @WithDefault("true")
    boolean privKeyAttributeSign();

    @WithName("priv_key_attribute_unwrap")
    Optional<Boolean> privKeyAttributeUnwrap();

    @WithName("priv_key_attribute_allowed_mechanisms")
    Optional<List<String>> privKeyAttributeAllowedMechanisms();

    @WithName("slot_ids")
    Optional<Set<Long>> slotIds();

    // undocumented properties

    @WithName("batch_signing_enabled")
    @WithDefault("true")
    boolean batchSigningEnabled();

    @WithName("read_only")
    @WithDefault("false")
    boolean readOnly();

    @WithName("pub_key_attribute_verify_recover")
    Optional<Boolean> pubKeyAttributeVerifyRecover();

    @WithName("pub_key_attribute_trusted")
    Optional<Boolean> pubKeyAttributeTrusted();

    @WithName("priv_key_attribute_sign_recover")
    Optional<Boolean> privKeyAttributeSignRecover();

    @WithName("priv_key_attribute_extractable")
    Optional<Boolean> privKeyAttributeExtractable();

    @WithName("priv_key_attribute_always_sensitive")
    Optional<Boolean> privKeyAttributeAlwaysSensitive();

    @WithName("priv_key_attribute_never_extractable")
    Optional<Boolean> privKeyAttributeNeverExtractable();

    @WithName("priv_key_attribute_wrap_with_trusted")
    Optional<Boolean> privKeyAttributeWrapWithTrusted();

}
