/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.test.glue;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import io.cucumber.java.en.Step;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.niis.xroad.common.managemenetrequest.test.TestAuthCertRegRequest;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegRequestBuilder;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.security.KeyPairGenerator;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ManagementRequestStepDefs extends BaseStepDefs {

    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;

    private ResponseEntity<String> managementRequestId;


    @Step("Request is executed")
    public void executeRequest() throws Exception {
        var req = generateRequest();
        var content = IOUtils.toByteArray(req.getRequestContent());
        managementRequestId = managementRequestsApi.addManagementRequest(req.getRequestContentType(), content);
    }

    private TestAuthCertRegRequest generateRequest() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);

        var authKeyPair = keyPairGenerator.generateKeyPair();
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());

        var serverId = SecurityServerId.Conf.create("EE", "CLASS", "MEMBER", "SS1");
        var receiver = ClientId.Conf.create("EE", "BUSINESS", "servicemember2");
        var ownerKeyPair = keyPairGenerator.generateKeyPair();
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());

        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);

        var builder = new TestAuthRegRequestBuilder(serverId.getOwner(), receiver);
        var req = builder.buildAuthCertRegRequest(
                serverId,
                "ss1.example.org",
                authCert);

        return new TestAuthCertRegRequest(authCert,
                ownerCert.getEncoded(),
                ownerOcsp.getEncoded(),
                req,
                authKeyPair.getPrivate(),
                ownerKeyPair.getPrivate());
    }
}
