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

import lombok.Getter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationDownloadTestDataGenerator.getSource;
import static org.junit.Assert.assertEquals;

public class ConfigurationDownloaderFromAnchorTest {
    private static final String SUCCESS_URL = "x-road.global/?version=3";
    private static final String SUCCESS_HTTP_URL = "http://x-road.global/?version=3";
    private static final String SUCCESS_HTTPS_URL = "https://x-road.global/?version=3";

    @Test
    public void downloadFromAnchorFirstSuccessUrlIsCached() {
        var configurationDownloader = getDownloader();
        configurationDownloader.downloadFromAnchor(getSource(List.of(SUCCESS_URL)));
        configurationDownloader.downloadFromAnchor(getSource(List.of("other")));
        configurationDownloader.downloadFromAnchor(getSource(List.of("other2")));
        List<String> successfulDownloadUrls = getParser(configurationDownloader).getConfigurationUrls();
        assertEquals(3, successfulDownloadUrls.size());
        assertEquals(SUCCESS_URL, successfulDownloadUrls.get(0));
        assertEquals(SUCCESS_URL, successfulDownloadUrls.get(1));
        assertEquals(SUCCESS_URL, successfulDownloadUrls.get(2));
    }

    @Test
    public void downloadFromAnchorFirstSuccessHttpsUrlIsCached() {
        var configurationDownloader = getDownloader();
        configurationDownloader.downloadFromAnchor(getSource(List.of(SUCCESS_HTTP_URL)));
        configurationDownloader.downloadFromAnchor(getSource(List.of("other")));
        configurationDownloader.downloadFromAnchor(getSource(List.of("other2")));
        List<String> successfulDownloadUrls = getParser(configurationDownloader).getConfigurationUrls();
        assertEquals(3, successfulDownloadUrls.size());
        assertEquals(SUCCESS_HTTPS_URL, successfulDownloadUrls.get(0));
        assertEquals(SUCCESS_HTTPS_URL, successfulDownloadUrls.get(1));
        assertEquals(SUCCESS_HTTPS_URL, successfulDownloadUrls.get(2));
    }

    private TestConfigurationParser getParser(ConfigurationDownloader downloader) {
        return (TestConfigurationParser) downloader.getParser();
    }


    private ConfigurationDownloader getDownloader() {
        return new ConfigurationDownloader("f") {

            final ConfigurationParser parser =
                    new TestConfigurationParser();

            @Override
            ConfigurationParser getParser() {
                return parser;
            }
        };
    }

    @Getter
    private static final class TestConfigurationParser extends ConfigurationParser {

        private final List<String> configurationUrls = new ArrayList<>();

        @Override
        public Configuration parse(ConfigurationLocation location,
                                   String... contentIdentifiersToBeHandled) {
            String downloadUrl = location.getDownloadURL();
            configurationUrls.add(downloadUrl);

            return new Configuration(location);
        }
    }
}
