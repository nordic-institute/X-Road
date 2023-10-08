/*
 * The MIT License
 *
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
package ee.ria.xroad.common;

import ee.ria.xroad.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

@Slf4j
public class OcspResponderStatusSerializationTest {

    @Test
    public void serializeAndDeserializeCertificationServiceDiagnostics() throws IOException {
        CertificationServiceDiagnostics certificationServiceDiagnostics = new CertificationServiceDiagnostics();
        String name = "name";
        String url = "url";
        OcspResponderStatus ocspResponderStatus = new OcspResponderStatus(0, url, null, null);
        CertificationServiceStatus certificationServiceStatus = new CertificationServiceStatus(name);
        certificationServiceStatus.getOcspResponderStatusMap().put(url, ocspResponderStatus);
        certificationServiceDiagnostics.getCertificationServiceStatusMap().put(name, certificationServiceStatus);

        byte[] bytesOut = JsonUtils.getObjectWriter().writeValueAsBytes(certificationServiceDiagnostics);

        Assert.assertTrue(bytesOut.length > 0);

        CertificationServiceDiagnostics deserialized = JsonUtils.getObjectReader()
                .readValue(bytesOut, CertificationServiceDiagnostics.class);

        Assert.assertNotNull(deserialized);
    }

    @Test
    public void serializeAndDeserializeDiagnosticsStatus() throws IOException {
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(0, null, null, "desc");

        byte[] bytesOut = JsonUtils.getObjectWriter().writeValueAsBytes(diagnosticsStatus);

        Assert.assertTrue(bytesOut.length > 0);

        DiagnosticsStatus deserialized = JsonUtils.getObjectReader()
                .readValue(bytesOut, DiagnosticsStatus.class);

        Assert.assertNotNull(deserialized);
    }
}
