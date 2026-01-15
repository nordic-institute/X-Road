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

package org.niis.xroad.confclient.core.globalconf;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.confclient.core.config.ConfigurationClientProperties;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class VerificationConfHandlerTest {

    private static final String CONF_DIR = "src/test/resources/verification-globalconf";

    @Test
    void testGetVerificationConf() throws Exception {
        ConfigurationClientProperties configurationClientProperties = ConfigUtils.initConfiguration(ConfigurationClientProperties.class,
                Map.of("xroad.configuration-client.global-conf-dir", CONF_DIR));
        VerificationConfHandler handler = new VerificationConfHandler(configurationClientProperties);


        byte[] zippedResponse = handler.getVerificationConf().getContent().toByteArray();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zippedResponse))) {
            ZipEntry entry = zip.getNextEntry();
            assertEquals("verificationconf/CS/shared-params.xml", entry.getName());
            assertArrayEquals(Files.readAllBytes(Path.of(CONF_DIR, "CS/shared-params.xml")), zip.readAllBytes());

            entry = zip.getNextEntry();
            assertEquals("verificationconf/CS/shared-params.xml.metadata", entry.getName());
            assertArrayEquals("{\"configurationVersion\":\"3\"}".getBytes(), zip.readAllBytes());
        }
    }

    @Test
    void testException() {
        ConfigurationClientProperties configurationClientProperties = ConfigUtils.initConfiguration(ConfigurationClientProperties.class,
                Map.of("xroad.configuration-client.global-conf-dir", "not-existing-dir"));
        VerificationConfHandler handler = new VerificationConfHandler(configurationClientProperties);

        var exception = assertThrows(XrdRuntimeException.class, handler::getVerificationConf);
        assertEquals("internal_error", exception.getCode());
        assertEquals("Could not read instance identifier of this security server", exception.getDetails());
    }

}
