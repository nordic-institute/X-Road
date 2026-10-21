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

package org.niis.xroad.edc.extension.policy.controlplane.util;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalGroupConstraintValueTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "ORG", "9999", "Prov");
    private static final String GROUP_CODE = "myLocalGroup";

    @Test
    void encodeProducesOwnerSemicolonGroupCodeFormat() {
        var encoded = new LocalGroupConstraintValue(OWNER, GROUP_CODE).encode();

        assertThat(encoded).isEqualTo("DEV:ORG:9999:Prov;myLocalGroup");
    }

    @Test
    void decodeRoundTripsAnEncodedValue() {
        var encoded = new LocalGroupConstraintValue(OWNER, GROUP_CODE).encode();

        var decoded = LocalGroupConstraintValue.decode(encoded);

        assertThat(decoded).contains(new LocalGroupConstraintValue(OWNER, GROUP_CODE));
    }

    @Test
    void decodeOldFormatValueWithNoSeparatorReturnsEmpty() {
        assertThat(LocalGroupConstraintValue.decode(GROUP_CODE)).isEmpty();
    }

    @Test
    void decodeMalformedOwnerReturnsEmpty() {
        assertThat(LocalGroupConstraintValue.decode("not-an-encoded-client-id;" + GROUP_CODE)).isEmpty();
    }

    @Test
    void decodeBlankGroupCodeReturnsEmpty() {
        assertThat(LocalGroupConstraintValue.decode(OWNER.asEncodedId() + ";")).isEmpty();
    }
}
