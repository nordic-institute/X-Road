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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.Key;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KeyConverterTest extends AbstractConverterTestContext {

    @Test
    public void convert() throws Exception {
        List<CertificateInfo> certs = new ArrayList<>();
        certs.add(new CertificateTestUtils.CertificateInfoBuilder().build());
        List<CertRequestInfo> csrs = new ArrayList<>();
        csrs.add(new CertRequestInfo("id", ClientId.Conf.create("a", "b", "c"),
                "sujbect-name"));

        KeyInfo info = new KeyInfo(true,
                KeyUsageInfo.SIGNING,
                "friendly-name",
                "id",
                "label",
                "public-key",
                certs,
                csrs,
                "sign-mechanism-name");
        Key key = keyConverter.convert(info);

        assertEquals(true, key.getAvailable());
        assertNotNull(key.getCertificates());
        assertEquals(1, key.getCertificates().size());
        assertNotNull(key.getCertificateSigningRequests());
        assertEquals(1, key.getCertificateSigningRequests().size());
        assertEquals("id", key.getId());
        assertEquals("label", key.getLabel());
        assertEquals("friendly-name", key.getName());
        assertEquals(true, key.getSavedToConfiguration());
        assertEquals(KeyUsageType.SIGNING, key.getUsage());
    }

    @Test
    public void isSavedToConfiguration() throws Exception {
        // test different combinations of keys and certs and the logic for isSavedToConfiguration
        KeyInfo info = new TokenTestUtils.KeyInfoBuilder().build();

        info.getCerts().clear();
        info.getCertRequests().clear();
        info.getCertRequests().add(createTestCsr());
        assertEquals(true, keyConverter.convert(info).getSavedToConfiguration());

        info.getCerts().clear();
        info.getCertRequests().clear();
        assertEquals(false, keyConverter.convert(info).getSavedToConfiguration());

        info.getCerts().clear();
        info.getCertRequests().clear();
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(false).build());
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(false).build());
        assertEquals(false, keyConverter.convert(info).getSavedToConfiguration());

        info.getCerts().clear();
        info.getCertRequests().clear();
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(false).build());
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(true).build());
        assertEquals(true, keyConverter.convert(info).getSavedToConfiguration());

        info.getCerts().clear();
        info.getCertRequests().clear();
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(true).build());
        info.getCerts().add(new CertificateTestUtils.CertificateInfoBuilder().savedToConfiguration(false).build());
        assertEquals(true, keyConverter.convert(info).getSavedToConfiguration());
    }

    public static CertRequestInfo createTestCsr() {
        return new CertRequestInfo("id",
                ClientId.Conf.create("a", "b", "c"),
                "sujbect-name");

    }
}
