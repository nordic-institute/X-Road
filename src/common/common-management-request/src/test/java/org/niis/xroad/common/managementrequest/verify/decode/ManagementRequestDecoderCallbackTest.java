/**
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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.CodedException;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

class ManagementRequestDecoderCallbackTest {
    private static final String MESSAGE = "message";

    private final ManagementRequestDecoderCallback managementRequestDecoderCallback = new ManagementRequestDecoderCallback() {
        @Override
        public void attachment(InputStream content, Map<String, String> additionalHeaders) {
            //do nothing
        }

        @Override
        public void onCompleted() {
            //do nothing
        }

        @Override
        public Object getRequest() {
            return null;
        }
    };

    @Test
    void shouldVerifyMessagePartPass() {
        assertThatNoException()
                .isThrownBy(() -> managementRequestDecoderCallback.verifyMessagePart("VALUE", MESSAGE));
    }

    @Test
    void shouldVerifyMessagePartThrowExceptionOnNull() {
        assertThatExceptionOfType(CodedException.class)
                .isThrownBy(() -> managementRequestDecoderCallback.verifyMessagePart(null, MESSAGE))
                .withMessageContaining(MESSAGE);
    }

    @Test
    void shouldVerifyMessagePartThrowExceptionOnBlank() {
        assertThatExceptionOfType(CodedException.class)
                .isThrownBy(() -> managementRequestDecoderCallback.verifyMessagePart("", MESSAGE))
                .withMessageContaining(MESSAGE);
    }
}
