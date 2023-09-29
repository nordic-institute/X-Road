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
package org.niis.xroad.cs.registrationservice.testutil;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.identifier.ClientId;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

public class TestGlobalConf extends EmptyGlobalConf {

    private final ClientId.Conf managementService = ClientId.Conf.create("TEST", "CLASS", "MGMT", "MANAGEMENT");

    @Override
    public String getInstanceIdentifier() {
        return "TEST";
    }

    @Override
    public int getOcspFreshnessSeconds() {
        return Integer.MAX_VALUE / 2;
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier, X509Certificate orgCert) {
        if (getInstanceIdentifier().equals(instanceIdentifier)) {
            var ca = TestCertUtil.getCaCert();
            if (ca.getSubjectX500Principal().equals(orgCert.getIssuerX500Principal())) {
                return ca;
            }
        }
        throw new CodedException(X_INTERNAL_ERROR, "Certificate is not issued by approved "
                + "certification service provider.");
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
        return new EjbcaSignCertificateProfileInfo(parameters);
    }

    @Override
    public ClientId getManagementRequestService() {
        return managementService;
    }

}
