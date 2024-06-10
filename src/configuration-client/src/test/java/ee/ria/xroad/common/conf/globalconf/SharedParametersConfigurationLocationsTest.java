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
package ee.ria.xroad.common.conf.globalconf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationDownloadTestDataGenerator.getSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedParametersConfigurationLocationsTest {

    @Test
    void whenLocationDownloadUrlNotMatchFormatThenNoSharedParametersReturned() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("f"));

        assertEquals(0,
                sharedParametersConfigurationLocations.get(getSource(List.of("http://notMatchFormat"))).size());
    }

    @Test
    void getInternalconfLocationsFromSharedParameters() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("src/test/resources/V3"));

        var locations = sharedParametersConfigurationLocations.get(
                getSource(List.of("http://domainAddress/internalconf")));

        assertEquals(2, locations.size());
        assertEquals("https://node1/internalconf", locations.get(0).getDownloadURL());
        assertEquals(1, locations.get(0).getVerificationCerts().size());
        assertEquals("http://node1/internalconf", locations.get(1).getDownloadURL());
        assertEquals(1, locations.get(1).getVerificationCerts().size());
    }

    @Test
    void getExtarnalconfLocationsFromSharedParameters() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("src/test/resources/V3"));

        var locations = sharedParametersConfigurationLocations.get(
                getSource(List.of("http://domainAddress/externalconf")));

        assertEquals(2, locations.size());
        assertEquals("https://node1/externalconf", locations.get(0).getDownloadURL());
        assertEquals(1, locations.get(0).getVerificationCerts().size());
        assertEquals("http://node1/externalconf", locations.get(1).getDownloadURL());
        assertEquals(1, locations.get(1).getVerificationCerts().size());
    }

    @Test
    void getProxyConfLocationsFromSharedParameters() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("src/test/resources/V3"));

        var locations = sharedParametersConfigurationLocations.get(
                getSource(List.of("http://domainAddress/PROXY/conf")));

        assertEquals(2, locations.size());
        assertEquals("https://node1/PROXY/conf", locations.get(0).getDownloadURL());
        assertEquals(2, locations.get(0).getVerificationCerts().size());
        assertEquals("http://node1/PROXY/conf", locations.get(1).getDownloadURL());
        assertEquals(2, locations.get(1).getVerificationCerts().size());
    }

    @Test
    void getManyInternalconfLocationsFromSharedParameters() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("src/test/resources/V3-many-nodes"));

        var locations = sharedParametersConfigurationLocations.get(
                getSource(List.of("http://domainAddress/internalconf")));

        assertEquals(6, locations.size());
        assertTrue(locations.get(0).getDownloadURL().startsWith("https://"));
        assertTrue(locations.get(1).getDownloadURL().startsWith("https://"));
        assertTrue(locations.get(2).getDownloadURL().startsWith("https://"));
        assertFalse(locations.get(3).getDownloadURL().startsWith("https://"));
        assertFalse(locations.get(4).getDownloadURL().startsWith("https://"));
        assertFalse(locations.get(5).getDownloadURL().startsWith("https://"));
    }

    @Test
    void whenGetLocationsFromVersion2SharedParametersThenNothingReturned() {
        var sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(
                new FileNameProviderImpl("src/test/resources/V2"));

        assertEquals(0, sharedParametersConfigurationLocations.get(
                getSource(List.of("http://domainAddress/confDir"))).size());
    }
}
