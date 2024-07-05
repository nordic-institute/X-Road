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

import java.util.ArrayList;
import java.util.List;

public final class ConfigurationDownloadTestDataGenerator {

    private ConfigurationDownloadTestDataGenerator() {
    }

    public static ConfigurationSource getSource(final List<String> locationUrls) {
        return new TestConfigurationSource(locationUrls, new ArrayList<>());
    }

    public static ConfigurationSource getSourceWithCerts(final List<String> locationUrls) {
        return new TestConfigurationSource(
                locationUrls,
                List.of(("MIICqTCCAZGgAwIBAgIBATANBgkqhkiG9w0BAQ0FADAOMQwwCgYDVQQDDANOL0EwHhcNNzAwMTAxMDAwMDAwWhcNMzgwMTAxMDAwMDAwWjAOMQwwC"
                        + "gYDVQQDDANOL0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDe10ai/VApuFjG+cgVLaV18qOEor6il83v0ErKBVznkSEzhzfplPJbf"
                        + "bXVHCezUsZOtVypvS4VCKg3S9wDBJVHmLrTl8z43xHowSE2KhvBxP+boH+9wt4dWdkiM4vohzuDuqz2k7ly50LlaokY/hQfLZKqRAB4PyiA9H1dk"
                        + "9hri9kIckGZpKHxVmXD19pd1hbvRmEcxF0kEAP4oyQ8EaQpgoy4KiN60x6hV8+xZPUqf03eQgh5LXvpmXDtGT58zN43Om8L+dPCtKyK6dTTjNakv"
                        + "6tJxoZJ2yUnVWPZKH9B3nM79bToxA9SiCjg3i6rU8HJ8ih163Dh9upPwqpmdEqDAgMBAAGjEjAQMA4GA1UdDwEB/wQEAwIGQDANBgkqhkiG9w0B"
                        + "AQ0FAAOCAQEAdgVRzoNWG7shsBV+r9I4dB/ghfnxZAcgag9qevG4YrzLEDHupGB7a0Rx4dKp7gxzSV+E82oFOTZMhQdcVinRrqZT1U8DeT1tgvR"
                        + "H6V24g8HCplDp3AuRWKlPFAWIpsyZ6n6oMIrS9uVCaTF4A8uFZ7GxgcByMP+9BwCNQAigFGJOZzwln60idlR8YmtVCn4oVfBVrH+JRNSfgosVqq"
                        + "Ze3q/XHNJP4iVqPt7taYzwwdz2XW02p8lgYZ1MwhMbcZ7xqNjYA0U9yQbUc6/oZ54R5FIgNldINCAJaNRRjXVS+1nt6bRM9GtZoeC4vhFqxIpdu"
                        + "QRYpMD2MaJqbwDgal2pmA==</internalVerificationCert>")
                        .getBytes()));
    }

    private record TestConfigurationSource(List<String> locationUrls, List<byte[]> verificationCerts) implements ConfigurationSource {

        @Override
        public String getInstanceIdentifier() {
            return "EE";
        }

        @Override
        public List<ConfigurationLocation> getLocations() {
            List<ConfigurationLocation> result =
                    new ArrayList<>(locationUrls.size());

            locationUrls.forEach(url -> result.add(getLocation(url, verificationCerts)));

            return result;
        }

        private ConfigurationLocation getLocation(String url, List<byte[]> certs) {
            return new ConfigurationLocation(this.getInstanceIdentifier(), url, certs);
        }
    }
}
