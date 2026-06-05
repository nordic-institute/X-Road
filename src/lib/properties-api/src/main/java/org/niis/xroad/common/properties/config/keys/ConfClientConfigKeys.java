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

package org.niis.xroad.common.properties.config.keys;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;

import static org.niis.xroad.common.properties.config.Validator.nonEmpty;

/** Configuration-client keys ({@code xroad.configuration-client.*}). */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class ConfClientConfigKeys implements ConfigKeyProvider {

    private static final Scope CONFIGURATION_CLIENT = Scope.of("xroad.configuration-client");

    private static final ConfClientConfigKeys INSTANCE = new ConfClientConfigKeys();

    /** {@code xroad.configuration-client.allowed-federations}. */
    public static final ConfigKey<String> ALLOWED_FEDERATIONS = CONFIGURATION_CLIENT
            .string("allowed-federations").withDefaultValue("NONE").build();

    /** {@code xroad.configuration-client.global-conf-hostname-verification}. */
    public static final ConfigKey<Boolean> GLOBAL_CONF_HOSTNAME_VERIFICATION = CONFIGURATION_CLIENT
            .bool("global-conf-hostname-verification").withDefaultValue(true).build();

    /** {@code xroad.configuration-client.global-conf-tls-cert-verification}. */
    public static final ConfigKey<Boolean> GLOBAL_CONF_TLS_CERT_VERIFICATION = CONFIGURATION_CLIENT
            .bool("global-conf-tls-cert-verification").withDefaultValue(true).build();

    /** {@code xroad.configuration-client.downloader-connect-timeout}. */
    public static final ConfigKey<Integer> DOWNLOADER_CONNECT_TIMEOUT = CONFIGURATION_CLIENT
            .integer("downloader-connect-timeout").withDefaultValue(10000).build();

    /** {@code xroad.configuration-client.downloader-read-timeout}. */
    public static final ConfigKey<Integer> DOWNLOADER_READ_TIMEOUT = CONFIGURATION_CLIENT
            .integer("downloader-read-timeout").withDefaultValue(30000).build();

    /** {@code xroad.configuration-client.global-conf-dir}. */
    public static final ConfigKey<String> GLOBAL_CONF_DIR = CONFIGURATION_CLIENT
            .string("global-conf-dir").withValidator(nonEmpty()).withDefaultValue("/etc/xroad/globalconf").build();

    /** {@code xroad.configuration-client.update-interval}. */
    public static final ConfigKey<Integer> UPDATE_INTERVAL = CONFIGURATION_CLIENT
            .integer("update-interval").withDefaultValue(60).build();

    /** {@code xroad.configuration-client.configuration-anchor-file}. */
    public static final ConfigKey<String> CONFIGURATION_ANCHOR_FILE = CONFIGURATION_CLIENT
            .string("configuration-anchor-file").withValidator(nonEmpty())
            .withDefaultValue("/etc/xroad/configuration-anchor.xml").build();

    /** {@code xroad.configuration-client.configuration-anchor-storage} — {@code FILE} or {@code DB}. */
    public static final ConfigKey<String> CONFIGURATION_ANCHOR_STORAGE = CONFIGURATION_CLIENT
            .string("configuration-anchor-storage").withDefaultValue("DB").build();

    private ConfClientConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static ConfClientConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return CONFIGURATION_CLIENT;
    }
}
