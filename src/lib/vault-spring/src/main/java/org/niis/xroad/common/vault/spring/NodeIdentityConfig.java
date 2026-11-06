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
package org.niis.xroad.common.vault.spring;

import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.properties.dbsource.NodeName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Provides the node identity of a Central Server service. Imported only by services that are cluster nodes;
 * {@link SpringVaultClientConfig} uses node-independent secret paths in services that do not import it.
 * <p>
 * The name is read from the Spring {@code Environment} rather than the {@code XRoadConfig} DSL: it is
 * per-node install-time identity written to {@code /etc/xroad/conf.d/local.yaml} or set through the
 * {@code XROAD_ADMIN_SERVICE_HA_NODE_NAME} environment variable, and the packaged backup and restore scripts
 * read the same key from that file.
 */
@Configuration
public class NodeIdentityConfig {

    @Bean
    NodeIdentity nodeIdentity(Environment environment) {
        return NodeIdentity.resolve(environment.getProperty(NodeName.PROPERTY_KEY));
    }
}
