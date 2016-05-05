/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import lombok.Value;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for configuration downloader
 */
public class ConfigurationDownloaderTest {
    private static final int MAX_ATTEMPTS = 5;
    private static final String LOCATION_URL_SUCCESS = "http://www.example.com/SUCCESS";

    /**
     * For better HA, the order of sources to be tried to download configuration
     * from, must be random.
     */
    @Test
    public void downloadConfigurationFilesInRandomOrder() {
        // We need multiple attempts as shuffling may give original order
        // sometimes.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                executeDownload();

                break;
            } catch (AssertionError e) {
                if (haveMoreAttempts(i)) {
                    continue;
                }

                throw e;
            }
        }
    }

    /**
     * Checks if last successful download location is remembered correctly so
     * that configuration per location is downloaded from where the last
     * successful download was done.
     */
    @Test
    public void rememberLastSuccessfulDownloadLocation() {
        // We loop in order to make failing due to wrong URL more certain.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // Given
            ConfigurationDownloader downloader =
                    getDownloader(LOCATION_URL_SUCCESS);
            List<String> locationUrls = getMixedLocationUrls();

            // When
            downloader.download(getSource(locationUrls));
            resetParser(downloader);
            downloader.download(getSource(locationUrls));

            // Then
            verifyLastSuccessfulLocationUsedSecondTime(downloader);
        }
    }

    /**
     * Checks that ConfigurationDownloader uses connections that timeout
     * after a period of time.
     * @throws IOException
     */
    @Test
    public void downloaderConnectionsTimeout() throws IOException {
        URLConnection connection = ConfigurationDownloader.getDownloadURLConnection(
                new URL("http://test.download.com"));
        assertEquals(connection.getReadTimeout(), ConfigurationDownloader.READ_TIMEOUT);
        assertTrue(connection.getReadTimeout() > 0);
    }

    private void resetParser(ConfigurationDownloader downloader) {
        getParser(downloader).reset();
    }

    private void verifyLastSuccessfulLocationUsedSecondTime(
            ConfigurationDownloader downloader) {
        List<String> successfulDownloadUrls =
                getParser(downloader).getConfigurationUrls();

        assertThat(successfulDownloadUrls, hasOnlyOneSuccessfulUrl());
    }

    private Matcher<List<String>> hasOnlyOneSuccessfulUrl() {
        return new TypeSafeMatcher<List<String>>() {
            @Override
            protected boolean matchesSafely(List<String> parsedUrls) {
                return  parsedUrls.size() == 1
                        && parsedUrls.contains(LOCATION_URL_SUCCESS);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Only one successful URL contained");
            }
        };
    }

    private void executeDownload() {
        // Given
        ConfigurationDownloader downloader = getDownloader();
        List<String> locationUrls = getAllFailedLocationUrls();

        // When
        downloader.download(getSource(locationUrls));

        // Then
        verifyLocationsRandomized(downloader, locationUrls);
    }

    private void verifyLocationsRandomized(
            ConfigurationDownloader downloader, List<String> locationUrls) {
        List<String> urlsParsedInOrder =
                getParser(downloader).getConfigurationUrls();

        assertThat(urlsParsedInOrder, sameUrlsAreContained(locationUrls));
        assertThat(urlsParsedInOrder, urlsAreInDifferentOrder(locationUrls));
    }

    private TestConfigurationParser getParser(ConfigurationDownloader downloader) {
        return (TestConfigurationParser) downloader.getParser();
    }

    private boolean haveMoreAttempts(int attemptNo) {
        return attemptNo < MAX_ATTEMPTS - 1;
    }

    private Matcher<List<String>> sameUrlsAreContained(
            final List<String> locationUrls) {
        return new TypeSafeMatcher<List<String>>() {
            @Override
            protected boolean matchesSafely(List<String> parsedUrls) {
                return  locationUrls.size() == parsedUrls.size()
                        && locationUrls.containsAll(parsedUrls);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Same size and same items contained");
            }
        };
    }

    private Matcher<List<String>> urlsAreInDifferentOrder(
            final List<String> locationUrls) {
        return new TypeSafeMatcher<List<String>>() {
            @Override
            protected boolean matchesSafely(List<String> parsedUrls) {
                return !locationUrls.equals(parsedUrls);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "Location URLs in different order than parsed.");
            }
        };
    }

    private List<String> getAllFailedLocationUrls() {
        List<String> result = new ArrayList<>();

        result.add("http://www.example.com/loc1");
        result.add("http://www.example.com/loc2");
        result.add("http://www.example.com/loc3");

        return result;
    }

    private List<String> getMixedLocationUrls() {
        List<String> result = new ArrayList<>();

        result.add("http://www.example.com/failure1");
        result.add(LOCATION_URL_SUCCESS);
        result.add("http://www.example.com/failure2");

        return result;
    }

    private ConfigurationSource getSource(final List<String> locationUrls) {
        return new TestConfigurationSource(locationUrls);
    }


    private ConfigurationDownloader getDownloader(
            String ... successfulLocationUrls) {
        FileNameProvider fileNameProvider = file -> new File("f").toPath();

        return new ConfigurationDownloader(fileNameProvider) {

            ConfigurationParser parser =
                    new TestConfigurationParser(successfulLocationUrls);

            @Override
            ConfigurationParser getParser() {
                return parser;
            }
        };
    }

    @Value
    private static class TestConfigurationSource implements ConfigurationSource {

        private final List<String> locationUrls;

        @Override
        public String getInstanceIdentifier() {
            return "EE";
        }

        @Override
        public List<ConfigurationLocation> getLocations() {
            List<ConfigurationLocation> result =
                    new ArrayList<>(locationUrls.size());

            locationUrls.forEach(url -> result.add(getLocation(url)));

            return result;
        }

        private ConfigurationLocation getLocation(String url) {
            return new ConfigurationLocation(this, url, new ArrayList<>());
        }
    }

    private static class TestConfigurationParser extends ConfigurationParser {

        @Getter
        private List<String> configurationUrls = new ArrayList<>();
        private final List<String> successfulDownloadUrls;

        TestConfigurationParser(String ... successfulDownloadUrls) {
            this.successfulDownloadUrls = Arrays.asList(successfulDownloadUrls);
        }

        @Override
        public Configuration parse(ConfigurationLocation location,
                               String... contentIdentifiersToBeHandled) {
            // For checking the order later.
            String downloadUrl = location.getDownloadURL();
            configurationUrls.add(downloadUrl);

            if (!successfulDownloadUrls.contains(downloadUrl)) {
                throw new RuntimeException("Do not let it download actually");
            }

            return new Configuration(location);
        }

        void reset() {
            configurationUrls = new ArrayList<>();
        }
    }
}
