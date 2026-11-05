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
package org.niis.xroad.common.properties.spring;

import org.niis.xroad.common.properties.dbsource.NodeNameProvider;
import org.springframework.core.env.Environment;

/**
 * Builds the {@link NodeNameProvider} of a Spring application from its {@code Environment}, which holds
 * {@code local.yaml} (via {@code spring.config.import}) and the {@code XROAD_ADMIN_SERVICE_HA_NODE_NAME}
 * environment variable (relaxed binding), following {@link NodeNameProvider#of(String)}. An application
 * without a node name resolves to {@link NodeNameProvider#DEFAULT_NODE_NAME}.
 */
public final class SpringNodeNameProvider {

    private SpringNodeNameProvider() {
    }

    /**
     * @param environment the application's environment
     * @return the provider of the node name configured in the environment
     */
    public static NodeNameProvider forEnvironment(Environment environment) {
        return NodeNameProvider.of(environment.getProperty(NodeNameProvider.HA_NODE_NAME_PROPERTY_KEY));
    }
}
