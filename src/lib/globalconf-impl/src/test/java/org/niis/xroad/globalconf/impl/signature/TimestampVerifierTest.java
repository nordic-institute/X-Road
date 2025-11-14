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
package org.niis.xroad.globalconf.impl.signature;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedXrdRuntimeException;
import ee.ria.xroad.common.TestSecurityUtil;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.tsp.TimeStampToken;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * Tests timestamp verifier.
 */
public class TimestampVerifierTest {

    @Rule
    public ExpectedXrdRuntimeException thrown = ExpectedXrdRuntimeException.none();

    private static GlobalConfProvider globalConfProvider;

    /**
     * Sets up test data
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        TestSecurityUtil.initSecurity();
        globalConfProvider = TestGlobalConfFactory.create("../globalconf-core/src/test/resources/globalconf_good_v2");
    }

    /**
     * Tests valid timestamp.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void validTimestamp() throws Exception {
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts = globalConfProvider.getTspCertificates();
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    /**
     * Tests that verification fails if timestamp hashes mismatch.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void hashMismatch() throws Exception {
        thrown.expectError(ErrorCodes.X_MALFORMED_SIGNATURE);
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        stampedData[42] = 0x01; // change a byte
        TimestampVerifier.verify(token, stampedData, null);
    }

    /**
     * Tests that verification fails if wrong certificate is used.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void wrongCertificate() throws Exception {
        thrown.expectError(ErrorCode.TSP_CERTIFICATE_NOT_FOUND.code());
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts =
                globalConfProvider.getOcspResponderCertificates(); // use ocsp certs
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    /**
     * Tests that verification fails if timestamp signature is invalid.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void invalidSignature() throws Exception {
        thrown.expectError(ErrorCode.TIMESTAMP_SIGNER_VERIFICATION_FAILED.code());
        TimeStampToken token = getTimestampFromFile("invalid-signature");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts = globalConfProvider.getTspCertificates();
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    private static TimeStampToken getTimestampFromFile(String fileName)
            throws Exception {
        byte[] data = getBytesFromFile(fileName);
        TimeStampToken token = new TimeStampToken(ContentInfo.getInstance(
                ASN1Primitive.fromByteArray(data)));
        assertNotNull(token);
        return token;
    }

    private static byte[] getBytesFromFile(String fileName) throws Exception {
        File file = new File("src/test/timestamps/" + fileName);
        try (FileInputStream in = new FileInputStream(file)) {
            return IOUtils.toByteArray(in);
        }
    }
}
