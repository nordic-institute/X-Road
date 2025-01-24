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
package ee.ria.xroad.asicverifier;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.globalconf.TestGlobalConfImpl;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Tests to verify correct ASiC container verifier behavior.
 */
@Slf4j
@RunWith(Parameterized.class)
@RequiredArgsConstructor
public class AsicContainerVerifierTest {

    private static MockedStatic<AsicVerifierMain> asicVerifierMainSpy;

    private static GlobalConfProvider globalConfProvider;

    private final String containerFile;
    private final String errorCode;
    private final Runnable verificationMethodCall;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Set up configuration.
     */
    @BeforeClass
    public static void setUpConf() {
        asicVerifierMainSpy = Mockito.mockStatic(AsicVerifierMain.class, Mockito.CALLS_REAL_METHODS);

        System.setProperty(SystemProperties.CONFIGURATION_PATH, "src/test/resources/globalconf_2024");
//        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
//                "../common/common-globalconf/src/test/resources/configuration-anchor1.xml");

        globalConfProvider = new TestGlobalConfImpl() {
            @Override
            public X509Certificate getCaCert(String instanceIdentifier, X509Certificate memberCert) throws Exception {
                return TestCertUtil.getCaCert();
            }
        };
    }

    /**
     * @return test input data
     */
    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        Runnable legacyContainerVerifierCall = () -> {
            try {
                AsicVerifierMain.verifyLegacyContainer(anyString(), any(GlobalConfProvider.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Runnable containerVerifierCall = () -> {
            try {
                AsicVerifierMain.verifyContainer(anyString(), any(GlobalConfProvider.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };


        return Arrays.asList(new Object[][]{
                {"valid-eidas.asice", null, containerVerifierCall},
                {"valid-batch.asice", null, legacyContainerVerifierCall}
        });
    }

    /**
     * Test to ensure container file verification result is as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void test() throws Exception {
        thrown.expectError(errorCode);

        // answer "Would you like to extract the signed files?" with "n"
        System.setIn(new ByteArrayInputStream("n".getBytes()));

        verify(containerFile);
        asicVerifierMainSpy.verify(verificationMethodCall::run);

    }

    private static void verify(String fileName) {
        AsicVerifierMain.main(new String[]{
                "src/test/resources/globalconf_2024",
                "src/test/resources/" + fileName
        });
    }
}
