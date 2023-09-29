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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServer;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * test SecurityServerConverter
 */
public class SecurityServerConverterTest extends AbstractConverterTestContext {

    @Autowired
    SecurityServerConverter securityServerConverter;

    private static final String SERVER_ADDRESS = "foo.bar.baz";

    @Before
    public void setup() {
        when(globalConfFacade.getSecurityServerAddress(any())).thenReturn(SERVER_ADDRESS);
    }

    @Test
    public void convertSecurityServerObject() {
        SecurityServerId.Conf securityServerId = SecurityServerId.Conf.create(
                "XRD2", "GOV", "M4", "server1");
        SecurityServer converted = securityServerConverter.convert(securityServerId);
        assertEquals("XRD2:GOV:M4:server1", converted.getId());
        assertEquals("XRD2", converted.getInstanceId());
        assertEquals("GOV", converted.getMemberClass());
        assertEquals("M4", converted.getMemberCode());
        assertEquals("server1", converted.getServerCode());
        assertEquals(SERVER_ADDRESS, converted.getServerAddress());
    }

}
