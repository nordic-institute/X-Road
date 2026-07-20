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

package org.niis.xroad.common.properties.config;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.config.Validator.oneOf;

class ConfigCatalogueTest {

    @Test
    void projectsDeclaredKeysWithCategoryTypeDefaultAndSummary() {
        var prefix = Prefix.of(Category.SIGNER, "xroad.signer");
        prefix.integer("key-length").withValidator(oneOf(2048, 3072)).withDefaultValue(2048).build();
        ConfigKeyProvider provider = () -> prefix;

        var entries = ConfigCatalogue.from(List.of(provider));

        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.key()).isEqualTo("xroad.signer.key-length");
        assertThat(entry.category()).isEqualTo(Category.SIGNER);
        assertThat(entry.type()).isEqualTo(Integer.class);
        assertThat(entry.defaultValue()).isEqualTo("2048");
        assertThat(entry.validationSummary().orElseThrow()).startsWith("one of").contains("2048", "3072");
    }

    @Test
    void entryForUnvalidatedKeyHasNoSummary() {
        var prefix = Prefix.of(Category.SIGNER, "xroad.signer");
        prefix.bool("enforce-token-pin-policy").withDefaultValue(false).build();
        ConfigKeyProvider provider = () -> prefix;

        var entry = ConfigCatalogue.from(List.of(provider)).getFirst();

        assertThat(entry.validationSummary()).isEmpty();
    }

    @Test
    void singleArgPrefixEnumeratesAsCommonCategory() {
        var prefix = Prefix.of("xroad");
        prefix.string("instance-country").build();
        ConfigKeyProvider provider = () -> prefix;

        var entry = ConfigCatalogue.from(List.of(provider)).getFirst();

        assertThat(entry.key()).isEqualTo("xroad.instance-country");
        assertThat(entry.category()).isEqualTo(Category.COMMON);
    }

    @Test
    void aggregatesKeysAcrossMultipleProviders() {
        var signer = Prefix.of(Category.SIGNER, "xroad.signer");
        signer.integer("key-length").withDefaultValue(2048).build();
        var proxy = Prefix.of(Category.PROXY, "xroad.proxy");
        proxy.bool("verify-client-cert").withDefaultValue(true).build();

        var entries = ConfigCatalogue.from(List.of(() -> signer, () -> proxy));

        assertThat(entries).extracting(ConfigCatalogue.Entry::key)
                .containsExactlyInAnyOrder("xroad.signer.key-length", "xroad.proxy.verify-client-cert");
    }

    @Test
    void shippedCatalogueIsNonEmptyAndEveryEntryHasAKey() {
        var entries = ConfigCatalogue.from(ConfigKeyProviders.allProviders());

        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(entry -> assertThat(entry.key()).isNotBlank());
    }
}
