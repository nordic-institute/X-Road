/**
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
package ee.ria.xroad.asicverifier;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.asic.AsicContainerVerifier;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.TestGlobalConfImpl;

import lombok.RequiredArgsConstructor;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

import static ee.ria.xroad.common.ErrorCodes.X_HASHCHAIN_UNUSED_INPUTS;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HASH_CHAIN_REF;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;

/**
 * Tests to verify correct ASiC container verifier behavior.
 */
@RunWith(Parameterized.class)
@RequiredArgsConstructor
@Ignore(value = "Test data must be updated to conform to the latest changes in X-Road message headers")
public class AsicContainerVerifierTest {

    private final String containerFile;
    private final String errorCode;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Set up configuration.
     */
    @BeforeClass
    public static void setUpConf() {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, "../common/common-util/src/test/resources/globalconf_good");
        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                "../common/common-util/src/test/resources/configuration-anchor1.xml");

        GlobalConf.reload(new TestGlobalConfImpl() {
            @Override
            public X509Certificate getCaCert(String instanceIdentifier, X509Certificate memberCert) throws Exception {
                return TestCertUtil.getCaCert();
            }
        });
    }

    /**
     * @return test input data
     */
    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"valid-signed-message.asice", null},
                {"valid-signed-hashchain.asice", null},
                {"valid-batch-ts.asice", null},
                {"wrong-message.asice", X_INVALID_SIGNATURE_VALUE},
                {"invalid-digest.asice", X_INVALID_SIGNATURE_VALUE},
                {"invalid-signed-hashchain.asice", X_MALFORMED_SIGNATURE + "." + X_INVALID_HASH_CHAIN_REF},
                {"invalid-hashchain-modified-message.asice", X_MALFORMED_SIGNATURE + "." + X_HASHCHAIN_UNUSED_INPUTS},
                // This verification actually passes, since the hash chain
                // is not verified and the signature is correct otherwise
                {"invalid-not-signed-hashchain.asice", null},
                {"invalid-incorrect-references.asice", X_MALFORMED_SIGNATURE},
                {"invalid-ts-hashchainresult.asice", X_MALFORMED_SIGNATURE}
        });
    }

    /**
     * Test to ensure container file verification result is as expected.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void test() throws Exception {
        thrown.expectError(errorCode);

        verify(containerFile);
    }

    private static void verify(String fileName) throws Exception {
        AsicContainerVerifier verifier = new AsicContainerVerifier("src/test/resources/" + fileName);
        verifier.verify();
    }
}
