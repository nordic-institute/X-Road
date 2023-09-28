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
package ee.ria.xroad.common.certificateprofile.impl;

import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;

/**
 * Tests the implementation of SkEsteidCertificateProfileInfoProvider.
 *
 * @deprecated
 * No longer used to the best of our knowledge, deprecated as of X-Road 7.2.0.
 * Will be removed in a future version.
 */
@Deprecated
public class SkEsteidCertificateProfileInfoProviderTest {

    /**
     * Tests whether getting subject identifier succeeds as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void getSubjectIdentifier() throws Exception {
        assertEquals(
                ClientId.Conf.create("XX", "PERSON", "foobar"),
                id("SERIALNUMBER=foobar")
        );
    }

    /**
     * Tests whether getting subject identifier fails if serial number of
     * the certificate is missing.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = Exception.class)
    public void missingSerialNumber() throws Exception {
        id("C=x");
    }

    private ClientId id(String name) throws Exception {
        X509Certificate mockCert = Mockito.mock(X509Certificate.class);

        Mockito.when(mockCert.getSubjectX500Principal()).thenReturn(
                new X500Principal(name)
        );

        return new SkEsteIdCertificateProfileInfoProvider().getSignCertProfile(
                new SignCertificateProfileInfo.Parameters() {
                    @Override
                    public ClientId getClientId() {
                        return ClientId.Conf.create("XX", "foo", "bar");
                    }

                    @Override
                    public String getMemberName() {
                        return "foo";
                    }

                    @Override
                    public SecurityServerId getServerId() {
                        return null;
                    }
                }
        ).getSubjectIdentifier(mockCert);
    }
}
