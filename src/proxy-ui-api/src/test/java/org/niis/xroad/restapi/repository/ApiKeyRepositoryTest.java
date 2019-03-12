/**
 * The MIT License
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
package org.niis.xroad.restapi.repository;

import org.junit.Test;
import org.niis.xroad.restapi.domain.ApiKey;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;

import java.util.ArrayList;
import java.util.Arrays;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

/**
 * Test api key repository
 */
public class ApiKeyRepositoryTest {
    @Test
    public void test() {
        ApiKeyRepository apiKeyRepository = new ApiKeyRepository();
        try {
            ApiKey key = apiKeyRepository.create(new ArrayList<>());
            fail("should fail due to missing roles");
        } catch (InvalidParametersException expected) { }

        try {
            ApiKey key = apiKeyRepository.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                    "FOOBAR"));
            fail("should fail due to bad role");
        } catch (InvalidParametersException expected) { }

        ApiKey key = apiKeyRepository.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                "XROAD_REGISTRATION_OFFICER"));
        assertEquals(2, key.getRoles().size());
        assertTrue(key.getRoles().contains("XROAD_SECURITY_OFFICER"));
        assertTrue(key.getRoles().contains("XROAD_REGISTRATION_OFFICER"));
    }
}
