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
package org.niis.xroad.globalconf.impl;

import ee.ria.xroad.common.TestCertUtil;

import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class GlobalConfVer7Test {
    private static final String GOOD_CONF_DIR = "../globalconf-core/src/test/resources/globalconf_good_v7";

    private static GlobalConfProvider globalConfProvider;

    @BeforeClass
    public static void setUpBeforeClass() {
        var source = new FileSystemGlobalConfSource(GOOD_CONF_DIR);
        globalConfProvider = new GlobalConfImpl(source, new GlobalConfExtensions(source, new GlobalConfExtensionFactoryImpl()));
    }

    @Test
    public void getApprovedDsTlsCas() {
        Collection<ApprovedDsTlsCaInfo> eeDsTlsCas = globalConfProvider.getApprovedDsTlsCas("EE");
        ApprovedDsTlsCaInfo dsTlsCa = eeDsTlsCas.stream().filter(ca -> ca.getName().equals("Test DS TLS CA")).findFirst().get();

        assertEquals("Test DS TLS CA", dsTlsCa.getName());
        assertEquals(TestCertUtil.getCaCert(), dsTlsCa.getTopCaCert());
        assertEquals(1, dsTlsCa.getIntermediateCaCerts().size());
        assertEquals(TestCertUtil.getTspCert(), dsTlsCa.getIntermediateCaCerts().get(0));
        assertEquals("http://testca.com/acme", dsTlsCa.getAcmeServerDirectoryUrl());
        assertEquals("192.99.88.7", dsTlsCa.getAcmeServerIpAddress());
        assertEquals("ds-tls-profile", dsTlsCa.getDsTlsCertificateProfileId());
    }
}
