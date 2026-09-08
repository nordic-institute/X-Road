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

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.util.Set;

/**
 * X-Road owned inputs to EDC settings ({@code xroad.edc.*}).
 *
 * <p>An EDC setting is read by the EDC runtime itself, through {@code QuarkusConfigBridge}. Rather than
 * configuring such a setting per deployment as a file, the packaged {@code application.yaml} of the
 * runtime interpolates the X-Road key declared here — {@code edc.iam.trusted-issuer.issuer.id:
 * ${xroad.edc.iam.trusted-issuer.issuer.id}} — so the value can arrive by any means the DSL supports
 * (a stored override, an env var, {@code conf.d}) while the EDC key itself stays declared by the
 * packaged yaml, which is what makes it visible to the bridge's snapshot of property names.
 */
public final class EdcConfigKeys implements ConfigKeyProvider {

    private static final Prefix EDC = Prefix.of(Category.COMMON, "xroad.edc");
    private static final Prefix TRUSTED_ISSUER = EDC.subPrefix("iam").subPrefix("trusted-issuer").subPrefix("issuer");

    private static final EdcConfigKeys INSTANCE = new EdcConfigKeys();

    /**
     * {@code xroad.edc.iam.trusted-issuer.issuer.id} — DID of the issuer whose membership credentials this
     * Security Server trusts. Deliberately without a default: a wrong DID silently fails every credential
     * check, so an unset value must surface as a startup error naming the property rather than as a
     * trusted issuer nobody configured.
     */
    public static final ConfigKey<String> TRUSTED_ISSUER_ID = TRUSTED_ISSUER
            .string("id")
            .publishedToFramework()
            .build();

    private EdcConfigKeys() {
    }

    /** @return the provider singleton. */
    public static EdcConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return EDC.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return EDC.keys();
    }
}
