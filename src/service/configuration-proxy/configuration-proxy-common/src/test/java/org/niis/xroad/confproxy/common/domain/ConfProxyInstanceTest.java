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
package org.niis.xroad.confproxy.common.domain;

import org.apache.commons.configuration2.INIConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ACTIVE_SIGNING_KEY_ID;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.SIGNING_KEY_ID_PREFIX;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;

@ExtendWith(MockitoExtension.class)
class ConfProxyInstanceTest {

    private static final String INSTANCE_NAME = "PROXY1";
    private static final String CONFIG_PATH = "/etc/xroad/confproxy";
    private static final String GENERATED_CONF_PATH = "/var/lib/xroad/generated-conf";

    @Mock
    private INIConfiguration config;

    private ConfProxyInstance instance;

    @BeforeEach
    void setUp() {
        instance = new ConfProxyInstance(INSTANCE_NAME, CONFIG_PATH, GENERATED_CONF_PATH, config);
    }

    @Test
    void getInstanceReturnsInstanceName() {
        assertThat(instance.getInstance()).isEqualTo(INSTANCE_NAME);
    }

    @Test
    void getConfigReturnsIniConfiguration() {
        assertThat(instance.getConfig()).isSameAs(config);
    }

    @Test
    void getInstanceConfigurationPathCombinesConfigPathAndInstance() {
        assertThat(instance.getInstanceConfigurationPath())
                .isEqualTo(CONFIG_PATH + "/" + INSTANCE_NAME);
    }

    @Test
    void getConfigurationTargetPathCombinesGeneratedConfPathAndInstance() {
        assertThat(instance.getConfigurationTargetPath())
                .isEqualTo(GENERATED_CONF_PATH + "/" + INSTANCE_NAME);
    }

    @Test
    void getValidityIntervalSecondsReturnsConfiguredValue() {
        when(config.getInteger(VALIDITY_INTERVAL_SECONDS, -1)).thenReturn(600);

        assertThat(instance.getValidityIntervalSeconds()).isEqualTo(600);
    }

    @Test
    void getValidityIntervalSecondsReturnsDefaultWhenNotConfigured() {
        when(config.getInteger(VALIDITY_INTERVAL_SECONDS, -1)).thenReturn(-1);

        assertThat(instance.getValidityIntervalSeconds()).isEqualTo(-1);
    }

    @Test
    void getProxyAnchorPathCombinesInstanceConfigPathAndAnchorXml() {
        assertThat(instance.getProxyAnchorPath())
                .isEqualTo(CONFIG_PATH + "/" + INSTANCE_NAME + "/anchor.xml");
    }

    @Test
    void getKeyListReturnsSigningKeyIds() {
        var keys = List.of(SIGNING_KEY_ID_PREFIX + "1", SIGNING_KEY_ID_PREFIX + "2", "other-key").iterator();
        when(config.getKeys()).thenReturn(keys);
        when(config.getString(SIGNING_KEY_ID_PREFIX + "1")).thenReturn("KEY_ID_1");
        when(config.getString(SIGNING_KEY_ID_PREFIX + "2")).thenReturn("KEY_ID_2");

        List<String> keyList = instance.getKeyList();

        assertThat(keyList).containsExactly("KEY_ID_1", "KEY_ID_2");
    }

    @Test
    void getKeyListReturnsEmptyListWhenNoSigningKeys() {
        when(config.getKeys()).thenReturn(Collections.emptyIterator());

        assertThat(instance.getKeyList()).isEmpty();
    }

    @Test
    void getKeyListFiltersOutNonSigningKeys() {
        var keys = List.of(ACTIVE_SIGNING_KEY_ID, VALIDITY_INTERVAL_SECONDS, "some-other-prop").iterator();
        when(config.getKeys()).thenReturn(keys);

        assertThat(instance.getKeyList()).isEmpty();
    }

    @Test
    void getActiveSigningKeyReturnsConfiguredKey() {
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn("ACTIVE_KEY_123");
        when(config.getString(ACTIVE_SIGNING_KEY_ID)).thenReturn("ACTIVE_KEY_123");

        assertThat(instance.getActiveSigningKey()).isEqualTo("ACTIVE_KEY_123");
    }

    @Test
    void getActiveSigningKeyReturnsNullWhenNotConfigured() {
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn(null);
        when(config.getString(ACTIVE_SIGNING_KEY_ID)).thenReturn(null);

        assertThat(instance.getActiveSigningKey()).isNull();
    }

    @Test
    void getCertPathConstructsCorrectPath() {
        Path certPath = instance.getCertPath("KEY_123");

        assertThat(certPath.toString())
                .isEqualTo(CONFIG_PATH + "/" + INSTANCE_NAME + "/cert_KEY_123.pem");
    }

    @Test
    void isReadyReturnsTrueWhenActiveKeyExistsAndAnchorFileExists(@TempDir Path tempDir) throws IOException {
        Path instanceDir = tempDir.resolve(INSTANCE_NAME);
        Files.createDirectories(instanceDir);
        Files.createFile(instanceDir.resolve("anchor.xml"));

        var readyInstance = new ConfProxyInstance(INSTANCE_NAME, tempDir.toString(), GENERATED_CONF_PATH, config);
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn("ACTIVE_KEY");

        assertThat(readyInstance.isReady()).isTrue();
    }

    @Test
    void isReadyReturnsFalseWhenNoActiveSigningKey(@TempDir Path tempDir) throws IOException {
        Path instanceDir = tempDir.resolve(INSTANCE_NAME);
        Files.createDirectories(instanceDir);
        Files.createFile(instanceDir.resolve("anchor.xml"));

        var readyInstance = new ConfProxyInstance(INSTANCE_NAME, tempDir.toString(), GENERATED_CONF_PATH, config);
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn(null);

        assertThat(readyInstance.isReady()).isFalse();
    }

    @Test
    void isReadyReturnsFalseWhenAnchorFileDoesNotExist(@TempDir Path tempDir) {
        var readyInstance = new ConfProxyInstance(INSTANCE_NAME, tempDir.toString(), GENERATED_CONF_PATH, config);
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn("ACTIVE_KEY");

        assertThat(readyInstance.isReady()).isFalse();
    }

    @Test
    void isReadyReturnsFalseWhenNoActiveKeyAndNoAnchorFile(@TempDir Path tempDir) {
        var readyInstance = new ConfProxyInstance(INSTANCE_NAME, tempDir.toString(), GENERATED_CONF_PATH, config);
        when(config.getProperty(ACTIVE_SIGNING_KEY_ID)).thenReturn(null);

        assertThat(readyInstance.isReady()).isFalse();
    }
}
