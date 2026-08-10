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
package org.niis.xroad.signer.common.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PKCS#11 hardware-module descriptor, parsed from the JSON/YAML document stored in
 * {@code xroad.signer.modules}.
 *
 * <p>Jackson maps kebab-case keys from the stored document to camelCase fields via
 * {@code PropertyNamingStrategies.KEBAB_CASE}. Fields are initialised to the defaults that the
 * previous SmallRye {@code @WithDefault} annotations carried, so absent keys retain their defaults.
 *
 * <p>Getter method names and return types are identical to those of the former
 * {@code ModuleProperties} SmallRye interface so that existing call sites in {@code ModuleConf}
 * require no changes beyond the import.
 */
public class SignerModuleConfig {

    private boolean enabled = true;
    private String library;
    private Boolean libraryCantCreateOsThreads;
    private Boolean osLockingOk;
    private boolean signVerifyPin = false;
    private String tokenIdFormat = "{moduleType}{slotIndex}{serialNumber}{label}";
    private String signMechanism = "CKM_RSA_PKCS";
    private String rsaSignMechanism;
    private String ecSignMechanism = "CKM_ECDSA";
    private boolean pubKeyAttributeEncrypt = true;
    private boolean pubKeyAttributeVerify = true;
    private Boolean pubKeyAttributeWrap;
    private Boolean pubKeyAttributeVerifyRecover;
    private Boolean pubKeyAttributeTrusted;
    private List<String> pubKeyAttributeAllowedMechanisms;
    private boolean privKeyAttributeSensitive = true;
    private boolean privKeyAttributeDecrypt = true;
    private boolean privKeyAttributeSign = true;
    private Boolean privKeyAttributeUnwrap;
    private Boolean privKeyAttributeSignRecover;
    private Boolean privKeyAttributeExtractable;
    private Boolean privKeyAttributeAlwaysSensitive;
    private Boolean privKeyAttributeNeverExtractable;
    private Boolean privKeyAttributeWrapWithTrusted;
    private List<String> privKeyAttributeAllowedMechanisms;
    private Set<Long> slotIds;
    private boolean batchSigningEnabled = true;
    private boolean readOnly = false;

    /** @return whether this module is active */
    public boolean enabled() {
        return enabled;
    }

    /** @return path to the PKCS#11 shared library */
    public String library() {
        return library;
    }

    /** @return whether the library cannot create OS threads */
    public Optional<Boolean> libraryCantCreateOsThreads() {
        return Optional.ofNullable(libraryCantCreateOsThreads);
    }

    /** @return whether OS-level locking is acceptable */
    public Optional<Boolean> osLockingOk() {
        return Optional.ofNullable(osLockingOk);
    }

    /** @return whether PIN verification is required per signing operation */
    public boolean signVerifyPin() {
        return signVerifyPin;
    }

    /** @return token ID format string */
    public String tokenIdFormat() {
        return tokenIdFormat;
    }

    /** @return default sign mechanism name */
    public String signMechanism() {
        return signMechanism;
    }

    /** @return RSA-specific sign mechanism name (overrides sign-mechanism when present) */
    public Optional<String> rsaSignMechanism() {
        return Optional.ofNullable(rsaSignMechanism);
    }

    /** @return EC sign mechanism name */
    public String ecSignMechanism() {
        return ecSignMechanism;
    }

    /** @return whether the public key has the encrypt attribute */
    public boolean pubKeyAttributeEncrypt() {
        return pubKeyAttributeEncrypt;
    }

    /** @return whether the public key has the verify attribute */
    public boolean pubKeyAttributeVerify() {
        return pubKeyAttributeVerify;
    }

    /** @return optional wrap attribute for public keys */
    public Optional<Boolean> pubKeyAttributeWrap() {
        return Optional.ofNullable(pubKeyAttributeWrap);
    }

    /** @return optional verify-recover attribute for public keys */
    public Optional<Boolean> pubKeyAttributeVerifyRecover() {
        return Optional.ofNullable(pubKeyAttributeVerifyRecover);
    }

    /** @return optional trusted attribute for public keys */
    public Optional<Boolean> pubKeyAttributeTrusted() {
        return Optional.ofNullable(pubKeyAttributeTrusted);
    }

    /** @return allowed mechanisms for public key operations */
    public Optional<List<String>> pubKeyAttributeAllowedMechanisms() {
        return Optional.ofNullable(pubKeyAttributeAllowedMechanisms);
    }

    /** @return whether the private key has the sensitive attribute */
    public boolean privKeyAttributeSensitive() {
        return privKeyAttributeSensitive;
    }

    /** @return whether the private key has the decrypt attribute */
    public boolean privKeyAttributeDecrypt() {
        return privKeyAttributeDecrypt;
    }

    /** @return whether the private key has the sign attribute */
    public boolean privKeyAttributeSign() {
        return privKeyAttributeSign;
    }

    /** @return optional unwrap attribute for private keys */
    public Optional<Boolean> privKeyAttributeUnwrap() {
        return Optional.ofNullable(privKeyAttributeUnwrap);
    }

    /** @return optional sign-recover attribute for private keys */
    public Optional<Boolean> privKeyAttributeSignRecover() {
        return Optional.ofNullable(privKeyAttributeSignRecover);
    }

    /** @return optional extractable attribute for private keys */
    public Optional<Boolean> privKeyAttributeExtractable() {
        return Optional.ofNullable(privKeyAttributeExtractable);
    }

    /** @return optional always-sensitive attribute for private keys */
    public Optional<Boolean> privKeyAttributeAlwaysSensitive() {
        return Optional.ofNullable(privKeyAttributeAlwaysSensitive);
    }

    /** @return optional never-extractable attribute for private keys */
    public Optional<Boolean> privKeyAttributeNeverExtractable() {
        return Optional.ofNullable(privKeyAttributeNeverExtractable);
    }

    /** @return optional wrap-with-trusted attribute for private keys */
    public Optional<Boolean> privKeyAttributeWrapWithTrusted() {
        return Optional.ofNullable(privKeyAttributeWrapWithTrusted);
    }

    /** @return allowed mechanisms for private key operations */
    public Optional<List<String>> privKeyAttributeAllowedMechanisms() {
        return Optional.ofNullable(privKeyAttributeAllowedMechanisms);
    }

    /** @return slot IDs to restrict this module to */
    public Optional<Set<Long>> slotIds() {
        return Optional.ofNullable(slotIds);
    }

    /** @return whether batch signing is enabled */
    public boolean batchSigningEnabled() {
        return batchSigningEnabled;
    }

    /** @return whether this module is read-only */
    public boolean readOnly() {
        return readOnly;
    }

    // setters for Jackson deserialization

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public void setLibraryCantCreateOsThreads(Boolean libraryCantCreateOsThreads) {
        this.libraryCantCreateOsThreads = libraryCantCreateOsThreads;
    }

    public void setOsLockingOk(Boolean osLockingOk) {
        this.osLockingOk = osLockingOk;
    }

    public void setSignVerifyPin(boolean signVerifyPin) {
        this.signVerifyPin = signVerifyPin;
    }

    public void setTokenIdFormat(String tokenIdFormat) {
        this.tokenIdFormat = tokenIdFormat;
    }

    public void setSignMechanism(String signMechanism) {
        this.signMechanism = signMechanism;
    }

    public void setRsaSignMechanism(String rsaSignMechanism) {
        this.rsaSignMechanism = rsaSignMechanism;
    }

    public void setEcSignMechanism(String ecSignMechanism) {
        this.ecSignMechanism = ecSignMechanism;
    }

    public void setPubKeyAttributeEncrypt(boolean pubKeyAttributeEncrypt) {
        this.pubKeyAttributeEncrypt = pubKeyAttributeEncrypt;
    }

    public void setPubKeyAttributeVerify(boolean pubKeyAttributeVerify) {
        this.pubKeyAttributeVerify = pubKeyAttributeVerify;
    }

    public void setPubKeyAttributeWrap(Boolean pubKeyAttributeWrap) {
        this.pubKeyAttributeWrap = pubKeyAttributeWrap;
    }

    public void setPubKeyAttributeVerifyRecover(Boolean pubKeyAttributeVerifyRecover) {
        this.pubKeyAttributeVerifyRecover = pubKeyAttributeVerifyRecover;
    }

    public void setPubKeyAttributeTrusted(Boolean pubKeyAttributeTrusted) {
        this.pubKeyAttributeTrusted = pubKeyAttributeTrusted;
    }

    public void setPubKeyAttributeAllowedMechanisms(List<String> pubKeyAttributeAllowedMechanisms) {
        this.pubKeyAttributeAllowedMechanisms = pubKeyAttributeAllowedMechanisms;
    }

    public void setPrivKeyAttributeSensitive(boolean privKeyAttributeSensitive) {
        this.privKeyAttributeSensitive = privKeyAttributeSensitive;
    }

    public void setPrivKeyAttributeDecrypt(boolean privKeyAttributeDecrypt) {
        this.privKeyAttributeDecrypt = privKeyAttributeDecrypt;
    }

    public void setPrivKeyAttributeSign(boolean privKeyAttributeSign) {
        this.privKeyAttributeSign = privKeyAttributeSign;
    }

    public void setPrivKeyAttributeUnwrap(Boolean privKeyAttributeUnwrap) {
        this.privKeyAttributeUnwrap = privKeyAttributeUnwrap;
    }

    public void setPrivKeyAttributeSignRecover(Boolean privKeyAttributeSignRecover) {
        this.privKeyAttributeSignRecover = privKeyAttributeSignRecover;
    }

    public void setPrivKeyAttributeExtractable(Boolean privKeyAttributeExtractable) {
        this.privKeyAttributeExtractable = privKeyAttributeExtractable;
    }

    public void setPrivKeyAttributeAlwaysSensitive(Boolean privKeyAttributeAlwaysSensitive) {
        this.privKeyAttributeAlwaysSensitive = privKeyAttributeAlwaysSensitive;
    }

    public void setPrivKeyAttributeNeverExtractable(Boolean privKeyAttributeNeverExtractable) {
        this.privKeyAttributeNeverExtractable = privKeyAttributeNeverExtractable;
    }

    public void setPrivKeyAttributeWrapWithTrusted(Boolean privKeyAttributeWrapWithTrusted) {
        this.privKeyAttributeWrapWithTrusted = privKeyAttributeWrapWithTrusted;
    }

    public void setPrivKeyAttributeAllowedMechanisms(List<String> privKeyAttributeAllowedMechanisms) {
        this.privKeyAttributeAllowedMechanisms = privKeyAttributeAllowedMechanisms;
    }

    public void setSlotIds(Set<Long> slotIds) {
        this.slotIds = slotIds;
    }

    public void setBatchSigningEnabled(boolean batchSigningEnabled) {
        this.batchSigningEnabled = batchSigningEnabled;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
