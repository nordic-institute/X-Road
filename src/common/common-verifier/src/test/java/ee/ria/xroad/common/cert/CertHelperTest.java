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
package ee.ria.xroad.common.cert;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests the CertHelper utility class.
 */
public class CertHelperTest {

    /**
     * Tests that the name extractor returns correct name from certificate.
     */
    @Test
    public void getSubjectCommonName() {
        X509Certificate cert = TestCertUtil.getProducer().certChain[0];
        String commonName = CertUtils.getSubjectCommonName(cert);
        assertEquals("producer", commonName);
    }

    /**
     * Tests getting the subject serial number from the certificate.
     * @throws Exception if an error occurs
     */
    @Test
    public void getSubjectSerialNumber() throws Exception {
        String base64data = IOUtils.toString(new FileInputStream(
                "../common-test/src/test/certs/test-esteid.txt"), StandardCharsets.UTF_8);
        X509Certificate cert = CryptoUtils.readCertificate(base64data);
        String serialNumber = CertUtils.getSubjectSerialNumber(cert);
        assertEquals("47101010033", serialNumber);
    }

    /**
     * Tests that getting serial number from a certificate which does not have
     * one returns null.
     * @throws Exception if an error occurs
     */
    @Test
    public void subjectSerialNumberNotAvailable() throws Exception {
        X509Certificate cert = TestCertUtil.getProducer().certChain[0];
        String serialNumber = CertUtils.getSubjectSerialNumber(cert);
        assertNull(serialNumber);
    }
}
