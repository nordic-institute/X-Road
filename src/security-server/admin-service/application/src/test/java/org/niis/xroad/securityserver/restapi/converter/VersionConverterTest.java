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

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.VersionInfoDto;
import org.niis.xroad.securityserver.restapi.openapi.model.VersionInfo;

import static org.junit.Assert.assertEquals;

/**
 * Test VersionConverter
 */
public class VersionConverterTest extends AbstractConverterTestContext {

    @Test
    public void convertVersion() {
        VersionConverter versionConverter = new VersionConverter();

        VersionInfoDto infoDto = new VersionInfoDto();
        infoDto.setInfo("1.3.33");
        infoDto.setJavaVersion(9);
        infoDto.setMinJavaVersion(8);
        infoDto.setMaxJavaVersion(11);
        infoDto.setUsingSupportedJavaVersion(true);
        infoDto.setJavaVendor("Xroad");
        infoDto.setJavaRuntimeVersion("0.0.1 xroad jdk");

        VersionInfo version = versionConverter.convert(infoDto);

        assertEquals(infoDto.getInfo(), version.getInfo());
        assertEquals(infoDto.getJavaVersion(), (long) version.getJavaVersion());
        assertEquals(infoDto.getMinJavaVersion(), (long) version.getMinJavaVersion());
        assertEquals(infoDto.getMaxJavaVersion(), (long) version.getMaxJavaVersion());
        assertEquals(infoDto.isUsingSupportedJavaVersion(), version.getUsingSupportedJavaVersion());
        assertEquals(infoDto.getJavaVendor(), version.getJavaVendor());
        assertEquals(infoDto.getJavaRuntimeVersion(), version.getJavaRuntimeVersion());
    }
}
