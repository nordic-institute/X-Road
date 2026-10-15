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

package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpMonitorIniMigrationTest {

    private static final String LOCAL_NAME = "ss1.example";
    private static final String LOCAL_IPV4 = "192.0.2.10";
    private static final String LOCAL_IPV6 = "2001:db8::10";
    private static final String REMOTE_IPV4 = "198.51.100.7";
    private static final String DNS_SAN_KEY = "xroad.common-rpc.certificate-provisioning.alt-names";
    private static final String IP_SAN_KEY = "xroad.common-rpc.certificate-provisioning.ip-subject-alt-names";

    private static final String FULL_OP_MONITOR_INI = """
            [proxy]
            xroad-tls-ciphers=TLS_AES_128_GCM_SHA256
            client-timeout=30000

            [op-monitor]
            host=ss1.example
            port=2080
            scheme=https
            tls-certificate=/etc/xroad/ssl/opmonitor.crt
            client-tls-certificate=/etc/xroad/ssl/opmonitor-client.crt
            xroad-tls-ciphers=TLS_AES_128_GCM_SHA256
            grpc-port=2181
            grpc-client-timeout=45000
            keep-records-for-days=7
            clean-interval=3600
            max-records-in-payload=100
            records-available-timestamp-offset-seconds=5
            health-statistics-period-seconds=300

            [op-monitor-service]
            socket-timeout-seconds=60
            connection-timeout-seconds=30

            [op-monitor-buffer]
            size=100
            max-records-in-message=10
            sending-interval-seconds=2
            socket-timeout-seconds=60
            connection-timeout-seconds=30
            """;

    @TempDir
    Path dir;

    private final IniUtil iniUtil = new IniUtil(new OpMonitorHostRuleTest.FakeResolver());

    @Test
    void everyOpMonitorRowIsMappedKeptOrDropped() throws IOException {
        var file = write("local.ini", FULL_OP_MONITOR_INI);

        assertThat(new TreeMap<>(iniUtil.loadToFlatMap(file.toString(), "xroad"))).isEqualTo(new TreeMap<>(Map.ofEntries(
                Map.entry("xroad.proxy.xroad-tls-ciphers", "TLS_AES_128_GCM_SHA256"),
                Map.entry("xroad.proxy.client-proxy.client-timeout", "30000"),
                Map.entry("xroad.common-rpc.channel.op-monitor.host", LOCAL_NAME),
                Map.entry("xroad.common-rpc.channel.op-monitor.port", "2181"),
                Map.entry("xroad.common-rpc.channel.op-monitor.deadline-after", "45000"),
                Map.entry("xroad.op-monitor.rpc.listen-address", LOCAL_NAME),
                Map.entry("xroad.op-monitor.rpc.port", "2181"),
                Map.entry(DNS_SAN_KEY, LOCAL_NAME),
                Map.entry("xroad.op-monitor.keep-records-for-days", "7"),
                Map.entry("xroad.op-monitor.clean-interval", "3600"),
                Map.entry("xroad.op-monitor.max-records-in-payload", "100"),
                Map.entry("xroad.op-monitor.records-available-timestamp-offset-seconds", "5"),
                Map.entry("xroad.op-monitor.health-statistics-period-seconds", "300"),
                Map.entry("xroad.proxy.addon.op-monitor.buffer.size", "100"),
                Map.entry("xroad.proxy.addon.op-monitor.buffer.max-records-in-message", "10"),
                Map.entry("xroad.proxy.addon.op-monitor.buffer.sending-interval-seconds", "2"))));
    }

    @Test
    void nestedOutputCarriesTheSameOpMonitorRows() throws IOException {
        var file = write("local.ini", FULL_OP_MONITOR_INI);

        var flat = new TreeMap<String, Object>();
        flatten("", iniUtil.loadToNestedMap(file.toString(), "xroad"), flat);

        assertThat(flat)
                .containsEntry("xroad.common-rpc.channel.op-monitor.host", LOCAL_NAME)
                .containsEntry("xroad.common-rpc.channel.op-monitor.port", 2181)
                .containsEntry("xroad.common-rpc.channel.op-monitor.deadline-after", 45000)
                .containsEntry("xroad.op-monitor.rpc.listen-address", LOCAL_NAME)
                .containsEntry("xroad.op-monitor.rpc.port", 2181)
                .containsEntry(DNS_SAN_KEY, LOCAL_NAME)
                .containsEntry("xroad.proxy.addon.op-monitor.buffer.size", 100)
                .doesNotContainKeys("xroad.op-monitor.port", "xroad.op-monitor.scheme",
                        "xroad.op-monitor.tls-certificate", "xroad.op-monitor.client-tls-certificate",
                        "xroad.op-monitor.xroad-tls-ciphers");
        assertThat(flat.keySet()).noneMatch(key -> key.contains("timeout-seconds"));
    }

    @Test
    void fileWithoutOpMonitorKeysYieldsNoOpMonitorRows() throws IOException {
        var file = write("local.ini", """
                [proxy]
                client-timeout=30000
                health-check-port=0
                """);

        assertThat(iniUtil.loadToFlatMap(file.toString(), "xroad")).isEqualTo(Map.of(
                "xroad.proxy.client-proxy.client-timeout", "30000",
                "xroad.proxy.health-check-port", "0"));
    }

    @Test
    void hostKeyOutsideOpMonitorSectionIsIgnored() throws IOException {
        var file = write("local.ini", """
                [proxy]
                host=ss1.example

                [op-monitor-buffer]
                host=ss1.example

                [op-monitor-extra]
                host=ss1.example
                """);

        assertThat(iniUtil.loadToFlatMap(file.toString(), "xroad")).isEqualTo(Map.of(
                "xroad.proxy.host", LOCAL_NAME,
                "xroad.op-monitor-buffer.host", LOCAL_NAME,
                "xroad.op-monitor-extra.host", LOCAL_NAME));
    }

    @Test
    void localIniHostOverridesOverrideIniHost() throws IOException {
        write("override-10-opmon.ini", "[op-monitor]\nhost=" + LOCAL_IPV4 + "\n");
        var local = write("local.ini", "[op-monitor]\nhost=" + REMOTE_IPV4 + "\n");

        assertThat(iniUtil.loadToFlatMap(local.toString(), "xroad"))
                .isEqualTo(Map.of("xroad.common-rpc.channel.op-monitor.host", REMOTE_IPV4));
        assertThat(iniUtil.loadToFlatMap(dir.resolve("override-10-opmon.ini").toString(), "xroad"))
                .isEqualTo(Map.of("xroad.common-rpc.channel.op-monitor.host", REMOTE_IPV4));
    }

    @Test
    void overrideIniHostApplies() throws IOException {
        var override = write("override-10-opmon.ini", "[op-monitor]\nhost=" + LOCAL_IPV4 + "\n");
        write("local.ini", "[proxy]\nclient-timeout=1\n");

        assertThat(iniUtil.loadToFlatMap(override.toString(), "xroad")).containsOnly(
                Map.entry("xroad.common-rpc.channel.op-monitor.host", LOCAL_IPV4),
                Map.entry("xroad.op-monitor.rpc.listen-address", LOCAL_IPV4),
                Map.entry(IP_SAN_KEY, "127.0.0.1," + LOCAL_IPV4));
        assertThat(iniUtil.loadToFlatMap(dir.resolve("local.ini").toString(), "xroad"))
                .isEqualTo(Map.of("xroad.proxy.client-proxy.client-timeout", "1"));
    }

    @Test
    void laterOverrideFileWinsAlphabetically() throws IOException {
        var first = write("override-a.ini", "[op-monitor]\nhost=" + LOCAL_IPV4 + "\n");
        write("override-b.ini", "[op-monitor]\nhost=" + REMOTE_IPV4 + "\n");

        assertThat(iniUtil.loadToFlatMap(first.toString(), "xroad"))
                .isEqualTo(Map.of("xroad.common-rpc.channel.op-monitor.host", REMOTE_IPV4));
    }

    @Test
    void bracketedIpv6LoopbackHostIsMigratedWithoutBrackets() throws IOException {
        var file = write("local.ini", "[op-monitor]\nhost=[::1]\n");

        assertThat(iniUtil.loadToFlatMap(file.toString(), "xroad")).isEqualTo(Map.of(
                "xroad.common-rpc.channel.op-monitor.host", "::1",
                "xroad.op-monitor.rpc.listen-address", "::1",
                IP_SAN_KEY, "127.0.0.1,::1"));
    }

    @Test
    void bracketedLocalIpv6HostInOverrideIniIsMigratedWithoutBrackets() throws IOException {
        var override = write("override-10-opmon.ini", "[op-monitor]\nhost=[" + LOCAL_IPV6 + "]\n");
        write("local.ini", "[proxy]\nclient-timeout=1\n");

        assertThat(iniUtil.loadToFlatMap(override.toString(), "xroad")).isEqualTo(Map.of(
                "xroad.common-rpc.channel.op-monitor.host", LOCAL_IPV6,
                "xroad.op-monitor.rpc.listen-address", LOCAL_IPV6,
                IP_SAN_KEY, "127.0.0.1," + LOCAL_IPV6));
    }

    @Test
    void bracketedIpv6WildcardHostIsMigratedWithoutBrackets() throws IOException {
        var file = write("local.ini", "[op-monitor]\nhost=[::]\n");

        assertThat(iniUtil.loadToFlatMap(file.toString(), "xroad"))
                .isEqualTo(Map.of("xroad.op-monitor.rpc.listen-address", "::"));
    }

    @Test
    void nestedOutputCarriesBracketedIpv6HostWithoutBrackets() throws IOException {
        var file = write("local.ini", "[op-monitor]\nhost=[::1]\n");

        var flat = new TreeMap<String, Object>();
        flatten("", iniUtil.loadToNestedMap(file.toString(), "xroad"), flat);

        assertThat(flat)
                .containsEntry("xroad.common-rpc.channel.op-monitor.host", "::1")
                .containsEntry("xroad.op-monitor.rpc.listen-address", "::1")
                .containsEntry(IP_SAN_KEY, "127.0.0.1,::1");
    }

    @Test
    void iniToDbSavesBracketedIpv6IpSanWithoutBrackets() throws IOException {
        var file = write("local.ini", "[op-monitor]\nhost=[::1]\n");

        assertThat(migrateToDb(file)).isEqualTo(Map.of(
                "xroad.common-rpc.channel.op-monitor.host", "::1",
                "xroad.op-monitor.rpc.listen-address", "::1",
                IP_SAN_KEY, "127.0.0.1,::1"));
    }

    @Test
    void iniToDbWritesCertificateRowsOfTheEffectiveHostOnly() throws IOException {
        var override = write("override-a.ini", "[op-monitor]\nhost=" + LOCAL_IPV4 + "\n");
        write("local.ini", "[op-monitor]\nhost=" + LOCAL_NAME + "\n");

        assertThat(migrateToDb(override)).isEqualTo(Map.of(
                "xroad.common-rpc.channel.op-monitor.host", LOCAL_NAME,
                "xroad.op-monitor.rpc.listen-address", LOCAL_NAME,
                DNS_SAN_KEY, LOCAL_NAME));
    }

    private static Map<String, String> migrateToDb(Path file) {
        var migrator = new IniToDbMigrator(new IniUtil(new OpMonitorHostRuleTest.FakeResolver())) {
            @Override
            String readEnv(String name) {
                return "true";
            }
        };

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate(file.toString(), "db.properties");

            var invocations = mocked.constructed().stream()
                    .flatMap(repo -> Mockito.mockingDetails(repo).getInvocations().stream())
                    .toList();
            assertThat(invocations.stream().map(invocation -> invocation.getMethod().getName()).toList())
                    .containsOnly("saveProperty", "close");
            return invocations.stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("saveProperty"))
                    .collect(Collectors.toMap(
                            invocation -> invocation.getArgument(0, String.class),
                            invocation -> invocation.getArgument(1, String.class)));
        }
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
        map.forEach((key, value) -> {
            var path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, out);
            } else {
                out.put(path, value);
            }
        });
    }
}
