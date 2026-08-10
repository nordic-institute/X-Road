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
package org.niis.xroad.cs.admin.core.acme;

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.exception.NotFoundException;

import java.util.Map;
import java.util.Optional;

/**
 * The {@code xroad.acme} configuration: EAB credentials keyed by CA name and account alias, and the shared ACME
 * account keystore password. Same top-level configuration key and EAB map shape as the Security Server's
 * equivalent - only the DB property scope differs (this product's {@code spring.application.name} is
 * {@code admin-service}, resolved automatically by the DB properties source). The dataspace TLS account uses the
 * fixed alias {@link DsTlsAcmeService#ACCOUNT_ALIAS} in the {@code members} map instead of a member id, since the
 * Central Server has no member certificates of its own.
 */
@Getter
@Setter
public class AcmeProperties {

    public static final int ACCOUNT_KEYSTORE_PASSWORD_LENGTH = 24;

    private EabCredentials eabCredentials;
    private String accountKeystorePassword;

    @Getter
    @Setter
    public static class EabCredentials {
        Map<String, CA> certificateAuthorities;
    }

    @Getter
    @Setter
    public static class CA {

        boolean isMacKeyBase64Encoded;
        Map<String, Credentials> members;
    }

    @Getter
    @Setter
    public static class Credentials {

        private String kid;
        private String macKey;
    }

    public AcmeProperties.Credentials getEabCredentials(String caName, String accountAlias) {
        return getEabCredentialsOptional(caName, accountAlias)
                .orElseThrow(() -> new NotFoundException(AcmeDeviationMessage.EAB_CREDENTIALS_MISSING.build()));
    }

    public boolean hasEabCredentials(String caName, String accountAlias) {
        return getEabCredentialsOptional(caName, accountAlias).isPresent();
    }

    private Optional<Credentials> getEabCredentialsOptional(String caName, String accountAlias) {
        return Optional.ofNullable(eabCredentials)
                .map(EabCredentials::getCertificateAuthorities)
                .map(certAuthorities -> certAuthorities.get(caName))
                .map(CA::getMembers)
                .map(members -> members.get(accountAlias));
    }

    public Boolean isEabMacKeyBase64Encoded(String caName) {
        return Optional.ofNullable(eabCredentials)
                .map(EabCredentials::getCertificateAuthorities)
                .map(certAuthorities -> certAuthorities.get(caName))
                .map(CA::isMacKeyBase64Encoded).orElse(false);
    }

    public char[] getAccountKeystorePassword() {
        return Optional.ofNullable(accountKeystorePassword)
                .or(() -> Optional.ofNullable(System.getenv().get("ACCOUNT_KEYSTORE_PASSWORD")))
                .map(String::toCharArray)
                .orElse(null);
    }

    /**
     * A fresh account keystore password is not auto-generated and persisted back to configuration; the operator
     * must configure one under {@code xroad.acme.account-keystore-password} before dataspace TLS ACME enrollment
     * can create its account keystore.
     */
    public char[] createNewAccountKeystorePassword() {
        throw new AcmeServiceException(AcmeDeviationMessage.ACCOUNT_KEYSTORE_PASSWORD_MISSING.build());
    }
}
