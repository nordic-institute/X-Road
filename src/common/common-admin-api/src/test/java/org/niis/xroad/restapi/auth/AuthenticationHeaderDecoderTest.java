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
package org.niis.xroad.restapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * test AuthenticationHeaderDecoder
 */
public class AuthenticationHeaderDecoderTest {

    @Test
    public void decode() throws Exception {
        AuthenticationHeaderDecoder decoder = new AuthenticationHeaderDecoder();

        String encoded = "X-Road-ApiKEy toKen=123";
        assertEquals("123", decoder.decodeApiKey(encoded));

        encoded = "X-Road-ApiKEy toKen=  123  \n ";
        assertEquals("123", decoder.decodeApiKey(encoded));

        String badEncoded = "Bearer 123";
        try {
            decoder.decodeApiKey(badEncoded);
            fail("should have thrown exception");
        } catch (AuthenticationException expected) {
        }

        badEncoded = "X-Road-ApiKEy token=         ";
        try {
            decoder.decodeApiKey(badEncoded);
            fail("should have thrown exception");
        } catch (AuthenticationException expected) {
        }

        badEncoded = "dsadsadasdasadsX-Road-ApiKEy token=123";
        try {
            decoder.decodeApiKey(badEncoded);
            fail("should have thrown exception");
        } catch (AuthenticationException expected) {
        }
    }
}
