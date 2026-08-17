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

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets the account-keystore-password field on the stored ACME configuration document, leaving every
 * other field untouched. Operates on the raw document string, with no database or Spring dependency.
 */
public final class AcmeConfigDocumentCodec {

    public static final String ACCOUNT_KEYSTORE_PASSWORD_FIELD = "account-keystore-password";

    private AcmeConfigDocumentCodec() {
    }

    public static String setAccountKeystorePassword(String acmeConfigurationDocument, String accountKeystorePassword) {
        Map<String, Object> document = parse(acmeConfigurationDocument);
        document.put(ACCOUNT_KEYSTORE_PASSWORD_FIELD, accountKeystorePassword);
        return dump(document);
    }

    private static Map<String, Object> parse(String acmeConfigurationDocument) {
        if (StringUtils.isBlank(acmeConfigurationDocument)) {
            return new LinkedHashMap<>();
        }
        Object loaded = createYaml().load(acmeConfigurationDocument);
        if (loaded instanceof Map<?, ?> map) {
            Map<String, Object> document = new LinkedHashMap<>();
            map.forEach((key, value) -> document.put(String.valueOf(key), value));
            return document;
        }
        return new LinkedHashMap<>();
    }

    private static String dump(Map<String, Object> document) {
        return createYaml().dump(document);
    }

    private static Yaml createYaml() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(dumperOptions), dumperOptions);
    }
}
