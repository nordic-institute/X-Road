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
package org.niis.xroad.securityserver.restapi.acme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AcmeConfigDocumentCodecTest {

    private static final String EAB_CREDENTIALS_DOCUMENT = """
            eab-credentials:
              certificate-authorities:
                test-ca:
                  mac-key-base64-encoded: true
                  members:
                    memberA:
                      kid: kid-a
                      mac-key: mac-key-a
            """;

    @ParameterizedTest
    @NullAndEmptySource
    void addsPasswordFieldWhenNoDocumentExists(String document) {
        String result = AcmeConfigDocumentCodec.setAccountKeystorePassword(document, "generated-password");

        assertThat(load(result)).containsOnly(entry("account-keystore-password", "generated-password"));
    }

    @Test
    void addsPasswordFieldWithoutDisturbingEabCredentials() {
        String result = AcmeConfigDocumentCodec.setAccountKeystorePassword(EAB_CREDENTIALS_DOCUMENT, "generated-password");

        Map<String, Object> parsed = load(result);
        assertThat(parsed).containsEntry("account-keystore-password", "generated-password");
        assertThat(parsed.get("eab-credentials")).isEqualTo(load(EAB_CREDENTIALS_DOCUMENT).get("eab-credentials"));
    }

    @Test
    void overwritesExistingPasswordOnly() {
        String documentWithOldPassword = AcmeConfigDocumentCodec.setAccountKeystorePassword(EAB_CREDENTIALS_DOCUMENT, "old-password");

        String result = AcmeConfigDocumentCodec.setAccountKeystorePassword(documentWithOldPassword, "new-password");

        Map<String, Object> parsed = load(result);
        assertThat(parsed).containsEntry("account-keystore-password", "new-password");
        assertThat(parsed.get("eab-credentials")).isEqualTo(load(EAB_CREDENTIALS_DOCUMENT).get("eab-credentials"));
    }

    @Test
    void roundTrippingWithSamePasswordPreservesAllFields() {
        String withPassword = AcmeConfigDocumentCodec.setAccountKeystorePassword(EAB_CREDENTIALS_DOCUMENT, "same-password");

        String roundTripped = AcmeConfigDocumentCodec.setAccountKeystorePassword(withPassword, "same-password");

        assertThat(load(roundTripped)).isEqualTo(load(withPassword));
    }

    private static Map<String, Object> load(String document) {
        return new Yaml().load(document);
    }
}
