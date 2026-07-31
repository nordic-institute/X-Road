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

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.util.List;

@SuppressWarnings("checkstyle:MagicNumber")
public final class SignerKeyConfigKeys implements ConfigKeyProvider {

    private static final Prefix SIGNER = Prefix.of(Category.SIGNER, "xroad.signer");
    private static final SignerKeyConfigKeys INSTANCE = new SignerKeyConfigKeys();

    public static final ConfigKey<Integer> KEY_LENGTH = SIGNER
            .integer("key-length")
            .withDefaultValue(2048)
            .exposedInUi()
            .build();

    public static final ConfigKey<String> KEY_NAMED_CURVE = SIGNER
            .string("key-named-curve")
            .withDefaultValue("secp256r1")
            .exposedInUi()
            .build();

    private SignerKeyConfigKeys() {
    }

    public static SignerKeyConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Prefix scope() {
        return SIGNER;
    }

    @Override
    public List<ConfigKey<?>> keys() {
        return List.of(KEY_LENGTH, KEY_NAMED_CURVE);
    }
}
