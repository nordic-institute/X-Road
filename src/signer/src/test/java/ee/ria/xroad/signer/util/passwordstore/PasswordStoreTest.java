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
package ee.ria.xroad.signer.util.passwordstore;

import org.junit.Test;

import static ee.ria.xroad.signer.util.passwordstore.PasswordStore.getPassword;
import static ee.ria.xroad.signer.util.passwordstore.PasswordStore.storePassword;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify
 */
public class PasswordStoreTest {

    /**
     * Run tests.
     *
     * @throws Exception in case of unexpected errors
     */
    @Test
    public void runTest() throws Exception {
        getPassword("foo"); // Just check if get on empty DB works.

        storePassword("foo", null);
        storePassword("bar", null);

        assertNull(getPassword("foo"));

        storePassword("foo", "fooPwd".getBytes(UTF_8));
        storePassword("bar", "barPwd".getBytes(UTF_8));

        assertArrayEquals("fooPwd".toCharArray(), getPassword("foo"));
        assertArrayEquals("barPwd".toCharArray(), getPassword("bar"));

        storePassword("foo", null);
        assertNull(getPassword("foo"));
        assertArrayEquals("barPwd".toCharArray(), getPassword("bar"));
    }
}
