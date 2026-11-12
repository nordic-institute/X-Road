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
package org.niis.xroad.ds.issuance.application;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.dbsource.NodeAwareProducerFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssuanceServiceRpcConfigNodeAwareTest {

    private static final NodeAwareProducerFixture FIXTURE = new NodeAwareProducerFixture("issuernodeaware",
            () -> new IssuanceServiceRpcConfig().xRoadConfig("ds-issuer-service"));

    @Test
    void aConfiguredNodeNameResolvesTheNodeValue() throws Exception {
        assertEquals(NodeAwareProducerFixture.NODE_VALUE, FIXTURE.resolveWithNodeName());
    }

    @Test
    void noNodeNameResolvesTheNodeZeroValue() throws Exception {
        assertEquals(NodeAwareProducerFixture.DEFAULT_NODE_VALUE, FIXTURE.resolveWithoutNodeName());
    }
}
