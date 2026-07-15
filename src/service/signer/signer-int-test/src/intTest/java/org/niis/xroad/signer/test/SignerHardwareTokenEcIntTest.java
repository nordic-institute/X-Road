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
package org.niis.xroad.signer.test;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.List;

/**
 * 0220 - Signer: HardwareToken key operations (EC). Same scenario shape as the RSA class (0210), reusing
 * its bodies via {@link AbstractSignerHardwareKeyOpsIntTest}, plus one extra scenario that deletes every
 * key the RSA class left behind before generating fresh EC keys.
 */
@Order(220)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignerHardwareTokenEcIntTest extends AbstractSignerHardwareKeyOpsIntTest {

    @Override
    protected String tokenFriendlyName() {
        return "xrd-softhsm-0";
    }

    @Override
    protected KeyAlgorithm keyAlgorithm() {
        return KeyAlgorithm.EC;
    }

    @Test
    @Order(15)
    @DisplayName("Previous Keys are deleted")
    void previousKeysAreDeleted() {
        Step.when("token has exact keys \"First key,Second key,KeyX,SignKey from CA,BadAuthKey from CA\"",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA", "BadAuthKey from CA")));
        Step.then("key \"First key\" is deleted from token", () -> deleteKey("First key"));
        Step.and("key \"Second key\" is deleted from token", () -> deleteKey("Second key"));
        Step.and("key \"KeyX\" is deleted from token", () -> deleteKey("KeyX"));
        Step.and("key \"BadAuthKey from CA\" is deleted from token", () -> deleteKey("BadAuthKey from CA"));
        Step.and("key \"SignKey from CA\" is deleted from token", () -> deleteKey("SignKey from CA"));
    }
}
