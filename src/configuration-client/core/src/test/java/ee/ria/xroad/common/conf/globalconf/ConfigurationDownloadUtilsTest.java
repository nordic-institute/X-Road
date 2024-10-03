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

import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationDownloadTestDataGenerator.getSourceWithCerts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationDownloadUtilsTest {

    @Test
    void shuffleLocationsPreferHttps() {
        List<String> locationUrlsInOrder = new ArrayList<>();
        locationUrlsInOrder.add("http://node1/inertnalconf");
        locationUrlsInOrder.add("http://node2/inertnalconf");
        locationUrlsInOrder.add("http://node3/inertnalconf");
        locationUrlsInOrder.add("http://node4/inertnalconf");
        locationUrlsInOrder.add("https://node1/inertnalconf");
        locationUrlsInOrder.add("https://node2/inertnalconf");
        locationUrlsInOrder.add("https://node3/inertnalconf");
        locationUrlsInOrder.add("https://node4/inertnalconf");
        var orderedLocations = getSourceWithCerts(locationUrlsInOrder).getLocations();

        var shuffledLocations = ConfigurationDownloadUtils.shuffleLocationsPreferHttps(orderedLocations);

        assertEquals(orderedLocations.size(), shuffledLocations.size());
        assertTrue(orderedLocations.containsAll(shuffledLocations));
        assertNotEquals(orderedLocations, shuffledLocations);
        assertTrue(shuffledLocations.get(0).getDownloadURL().startsWith("https"));
        assertTrue(shuffledLocations.get(1).getDownloadURL().startsWith("https"));
        assertTrue(shuffledLocations.get(2).getDownloadURL().startsWith("https"));
        assertTrue(shuffledLocations.get(3).getDownloadURL().startsWith("https"));
        assertTrue(shuffledLocations.get(0).getDownloadURL().startsWith("http"));
        assertTrue(shuffledLocations.get(1).getDownloadURL().startsWith("http"));
        assertTrue(shuffledLocations.get(2).getDownloadURL().startsWith("http"));
        assertTrue(shuffledLocations.get(3).getDownloadURL().startsWith("http"));
    }

    @Test
    void withHttpsReturnStartWithHttpAndNotWithHttpsFalse() {
        assertFalse(ConfigurationDownloadUtils.startWithHttpAndNotWithHttps("https://"));
    }
}
