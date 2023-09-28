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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.SystemProperties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Class for testing {@link Signer}
 */
public class SignerTest {

    @Test
    public void testModuleManagerUpdateIntervalProperty() {
        // Get default value
        int defaultValue = Integer.parseInt(SystemProperties.DEFAULT_SIGNER_MODULE_MANAGER_UPDATE_INTERVAL);

        // Test for default value
        assertEquals(SystemProperties.SIGNER_MODULE_MANAGER_UPDATE_INTERVAL + " should be " + defaultValue,
                defaultValue,
                SystemProperties.getModuleManagerUpdateInterval());

        // Increase default value by one
        int newValue = defaultValue + 1;

        // Set value to default + 1
        System.setProperty(SystemProperties.SIGNER_MODULE_MANAGER_UPDATE_INTERVAL, Integer.toString(newValue));

        // Test for the new value
        assertEquals(SystemProperties.SIGNER_MODULE_MANAGER_UPDATE_INTERVAL + " should be " + newValue,
                newValue,
                SystemProperties.getModuleManagerUpdateInterval());
    }
}
