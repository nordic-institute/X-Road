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
package org.niis.xroad.common.acme;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.Map;

@Slf4j
@Configuration
public class AcmeCommonConfig {

    @Bean
    public AcmeProperties acmeProperties(AcmeConfig acmeConfig, @Value("${xroad.acme:}") String acmeConfiguration) {
        if (StringUtils.isBlank(acmeConfiguration)) {
            if (acmeConfig.isAcmeChallengePortEnabled()) {
                log.error("Acme challenge port enabled, but configuration is missing.");
            } else {
                log.info("Acme configuration not set, and acme challenge port not enabled. Skipping ACME configuration.");
            }
            return new AcmeProperties();
        }
        try {
            Constructor constructor = createAcmeYamlConstructor();
            Yaml yaml = new Yaml(constructor);
            AcmeProperties properties = yaml.loadAs(acmeConfiguration, AcmeProperties.class);
            if (properties == null) {
                log.error("Failed to load Acme yaml configuration, result is null");
                return new AcmeProperties();
            }
            return properties;
        } catch (Exception e) {
            log.warn("Failed to load Acme yaml configuration", e);
            return new AcmeProperties();
        }
    }

    private static Constructor createAcmeYamlConstructor() {
        Constructor constructor = new Constructor(AcmeProperties.class, new LoaderOptions());

        TypeDescription acmePropertiesDescriptor = new TypeDescription(AcmeProperties.class);
        acmePropertiesDescriptor.substituteProperty("eab-credentials",
                AcmeProperties.EabCredentials.class,
                "getEabCredentials",
                "setEabCredentials");
        acmePropertiesDescriptor.substituteProperty("account-keystore-password",
                String.class,
                "getAccountKeystorePassword",
                "setAccountKeystorePassword");
        constructor.addTypeDescription(acmePropertiesDescriptor);

        TypeDescription eabCredentialsDescriptor = new TypeDescription(AcmeProperties.EabCredentials.class);
        eabCredentialsDescriptor.substituteProperty("certificate-authorities",
                Map.class,
                "getCertificateAuthorities",
                "setCertificateAuthorities",
                String.class,
                AcmeProperties.CA.class);
        constructor.addTypeDescription(eabCredentialsDescriptor);

        TypeDescription caDescriptor = new TypeDescription(AcmeProperties.CA.class);
        caDescriptor.substituteProperty("mac-key-base64-encoded",
                boolean.class,
                "isMacKeyBase64Encoded",
                "setMacKeyBase64Encoded");
        constructor.addTypeDescription(caDescriptor);

        TypeDescription credentialsDescription = new TypeDescription(AcmeProperties.Credentials.class);
        credentialsDescription.substituteProperty("mac-key", String.class, "getMacKey", "setMacKey");
        credentialsDescription.substituteProperty("auth-mac-key", String.class, "getAuthMacKey", "setAuthMacKey");
        credentialsDescription.substituteProperty("sign-mac-key", String.class, "getSignMacKey", "setSignMacKey");
        credentialsDescription.substituteProperty("auth-kid", String.class, "getAuthKid", "setAuthKid");
        credentialsDescription.substituteProperty("sign-kid", String.class, "getSignKid", "setSignKid");
        constructor.addTypeDescription(credentialsDescription);

        return constructor;
    }

}
