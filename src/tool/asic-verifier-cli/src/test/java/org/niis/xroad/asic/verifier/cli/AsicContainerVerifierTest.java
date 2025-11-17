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
package org.niis.xroad.asic.verifier.cli;

import ee.ria.xroad.common.ExpectedXrdRuntimeException;
import ee.ria.xroad.common.asic.AsicContainerVerifier;
import ee.ria.xroad.common.asic.AsicUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.util.Arrays;
import java.util.Collection;

import static org.niis.xroad.common.core.exception.ErrorCode.HASHCHAIN_UNUSED_INPUTS;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_HASH_CHAIN_REF;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SIGNATURE_VALUE;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SIGNATURE;

/**
 * Tests to verify correct ASiC container verifier behavior.
 */
@Slf4j
@RunWith(Parameterized.class)
@RequiredArgsConstructor
public class AsicContainerVerifierTest {

    private static GlobalConfProvider globalConfProvider;
    private static final OcspVerifierFactory OCSP_VERIFIER_FACTORY = new OcspVerifierFactory();

    private final String containerFile;
    private final String errorCode;
    @Rule
    public ExpectedXrdRuntimeException thrown = ExpectedXrdRuntimeException.none();

    /**
     * Set up configuration.
     */
    @BeforeClass
    public static void setUpConf() {
        globalConfProvider = TestGlobalConfFactory.create("../../lib/globalconf-core/src/test/resources/globalconf_good2_v3");
    }

    /**
     * @return test input data
     */
    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"valid-signed-message.asice", null},
                {"valid-non-batch-rest.asice", null},
                {"valid-non-batch-soap-attachments.asice", null},
                {"valid-signed-hashchain.asice", null},
                {"valid-batch-ts.asice", null},
                {"wrong-message.asice", INVALID_SIGNATURE_VALUE.code()},
                {"invalid-digest.asice", INVALID_SIGNATURE_VALUE.code()},
                {"invalid-signed-hashchain.asice", MALFORMED_SIGNATURE.code() + "." + INVALID_HASH_CHAIN_REF.code()},
                {"invalid-hashchain-modified-message.asice", MALFORMED_SIGNATURE.code() + "." + HASHCHAIN_UNUSED_INPUTS.code()},
                // This verification actually passes, since the hash chain
                // is not verified and the signature is correct otherwise
                {"invalid-not-signed-hashchain.asice", null},
                {"invalid-incorrect-references.asice", MALFORMED_SIGNATURE.code()},
                {"invalid-ts-hashchainresult.asice", MALFORMED_SIGNATURE.code()}
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

        verify(containerFile);
    }

    private static void verify(String fileName) throws Exception {
        log.info("Verifying ASiC container \"" + fileName + "\" ...");

        try {
            AsicContainerVerifier verifier = new AsicContainerVerifier(globalConfProvider, OCSP_VERIFIER_FACTORY,
                    "src/test/resources/" + fileName);
            verifier.verify();

            log.info(AsicUtils.buildSuccessOutput(verifier));
        } catch (Exception e) {
            log.error(AsicUtils.buildFailureOutput(e));
            throw e;
        }
    }
}
