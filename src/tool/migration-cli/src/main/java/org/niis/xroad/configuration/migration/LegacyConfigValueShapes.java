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

package org.niis.xroad.configuration.migration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes, per legacy configuration key, the shape X-Road 8 needs the migrated value stored in.
 * <p>
 * Complements {@link LegacyConfigPathMapping} (key renames, without inspecting values) and
 * {@link LegacyConfigValueRules} (deriving additional keys from a value): this table says what
 * shape a single migrated key's own value must take.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyConfigValueShapes {

    private static final Map<String, ValueShape> SHAPES = new HashMap<>();

    static {
        SHAPES.put("xroad.message-log-encryption.archive.grouping-strategy",
                new EnumShape(List.of("NONE", "MEMBER", "SUBSYSTEM")));
    }

    static ValueShape shapeOf(String key) {
        return SHAPES.get(key);
    }

    /**
     * A shape a legacy configuration value is converted into.
     */
    sealed interface ValueShape permits EnumShape {
    }

    /**
     * An enum-valued key, stored as the exact name of one of a fixed set of constants.
     */
    record EnumShape(List<String> allowedConstants) implements ValueShape {
        EnumShape {
            allowedConstants = List.copyOf(allowedConstants);
        }
    }

}
