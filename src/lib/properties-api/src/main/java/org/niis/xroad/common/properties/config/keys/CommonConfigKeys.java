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

/** Common keys, registered by every process. */
public final class CommonConfigKeys implements ConfigKeyProvider {

    private static final Scope COMMON = Scope.of("xroad.common");

    private static final CommonConfigKeys INSTANCE = new CommonConfigKeys();

    /** {@code xroad.common.instance-country} — active country overlay flag; no default. */
    public static final ConfigKey<String> INSTANCE_COUNTRY = COMMON
            .string("instance-country")
            .build();

    /** {@code xroad.common.temp-files-path} — base directory for temporary files. */
    public static final ConfigKey<String> TEMP_FILES_PATH = COMMON
            .string("temp-files-path")
            .withValidator(nonEmpty())
            .withDefaultValue("/var/tmp/xroad/")
            .build();

    private CommonConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static CommonConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return COMMON;
    }
}
