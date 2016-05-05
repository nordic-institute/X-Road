/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.asic;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import ee.ria.xroad.common.ExpectedCodedException;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Tests to verify correct ASiC container loading behavior.
 */
@RunWith(Parameterized.class)
public class AsicContainerTest {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * @return test input data
     */
    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"valid-signed-message.asice", null},
                {"no-mimetype.asice", X_ASIC_MIME_TYPE_NOT_FOUND},
                {"no-message.asice", X_ASIC_MESSAGE_NOT_FOUND},
                {"no-signature.asice", X_ASIC_SIGNATURE_NOT_FOUND},
                {"not-asic.asice", X_ASIC_MIME_TYPE_NOT_FOUND}
        });
    }

    private String containerFile;
    private String errorCode;

    /**
     * Create test with given input data.
     * @param containerFile the container file to load
     * @param errorCode the expected error code
     */
    public AsicContainerTest(String containerFile, String errorCode) {
        this.containerFile = containerFile;
        this.errorCode = errorCode;
    }

    /**
     * Test to ensure ASiC container loading result is as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void test() throws Exception {
        thrown.expectError(errorCode);

        try (FileInputStream in =
                new FileInputStream("src/test/resources/" + containerFile)) {
            // just try to create the container -- the contents are verified
            // in the constructor
            AsicContainer.read(in);
        }
    }
}
