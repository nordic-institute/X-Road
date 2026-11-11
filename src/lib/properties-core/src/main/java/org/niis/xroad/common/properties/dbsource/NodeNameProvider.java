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

package org.niis.xroad.common.properties.dbsource;

import java.util.Optional;

/**
 * Supplies the HA node name that decides which {@code configuration_properties} rows an application loads:
 * the shared rows (no node) plus the rows stored for this node. A node row wins over the shared row of the
 * same key. The name is read once when the loading step is built.
 */
@FunctionalInterface
public interface NodeNameProvider {

    /** @return the node name, or empty when only shared rows apply */
    Optional<String> nodeName();

    /** @return a provider without a node, so only shared rows load */
    static NodeNameProvider shared() {
        return Optional::empty;
    }

    /** @return a provider of the node name in the environment, see {@link NodeName#fromEnvironment()} */
    static NodeNameProvider fromEnvironment() {
        var name = NodeName.fromEnvironment().name();
        return () -> Optional.of(name);
    }

    /**
     * Applies the {@link NodeName} rule to the configured node name.
     * @param configuredName the configured node name, may be {@code null} or blank
     * @return a provider of the name, or of {@link NodeName#DEFAULT_NODE_NAME} when the name is blank or missing
     * @throws InvalidNodeNameException if the name breaks the rule; the name is never rewritten
     */
    static NodeNameProvider of(String configuredName) {
        var name = NodeName.resolve(configuredName).name();
        return () -> Optional.of(name);
    }
}
