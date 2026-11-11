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
import org.niis.xroad.signer.common.config.SignerModuleConfig;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConfigValueConverterTest {

    private static final String GROUPING_KEY = "xroad.message-log-encryption.archive.grouping-strategy";
    private static final String GROUPING_KEYS_KEY = "xroad.message-log-encryption.archive.grouping-keys";
    private static final String SIGNER_MODULES_KEY = "xroad.signer.modules";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final ObjectMapper KEBAB_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .build();
    private static final TypeReference<Map<String, SignerModuleConfig>> SIGNER_MODULES_TYPE = new TypeReference<>() { };

    @Test
    void convertsLowerCaseMemberToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "member"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsMixedCaseMemberToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "Member"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void keepsAlreadyExactCaseMemberConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "MEMBER"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsLowerCaseSubsystemToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "subsystem"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "SUBSYSTEM");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsLowerCaseNoneToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "none"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "NONE");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void unknownValueKeptUnchangedWithOneWarning() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "team"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "team");
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst())
                .contains(GROUPING_KEY)
                .contains("team")
                .contains("NONE", "MEMBER", "SUBSYSTEM");
    }

    @Test
    void keyOutsideTablePassesThroughUnchanged() {
        var result = LegacyConfigValueConverter.convert(Map.of("some.other.key", "value"));

        assertThat(result.properties()).containsEntry("some.other.key", "value");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void lookalikeKeyPassesThroughUnchanged() {
        var lookalikeKey = GROUPING_KEY + "-x";
        var result = LegacyConfigValueConverter.convert(Map.of(lookalikeKey, "member"));

        assertThat(result.properties()).containsEntry(lookalikeKey, "member");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void reconvertingAlreadyConvertedRowsIsNoOp() {
        var firstPass = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "member"));

        var secondPass = LegacyConfigValueConverter.convert(firstPass.properties());

        assertThat(secondPass.properties()).isEqualTo(firstPass.properties());
        assertThat(secondPass.warnings()).isEmpty();
    }

    @Test
    void mergesIndexedGroupingKeysRowsIntoOneJsonRow() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                GROUPING_KEYS_KEY + ".\"DEV/COM/1111\"[0]", "KEY1",
                GROUPING_KEYS_KEY + ".\"DEV/COM/1111\"[1]", "KEY2",
                GROUPING_KEYS_KEY + ".\"DEV/COM/2222/SUB\"[0]", "KEY3"));

        assertThat(result.properties()).containsOnlyKeys(GROUPING_KEYS_KEY);
        assertThat(result.warnings()).isEmpty();

        Map<String, Set<String>> parsed = JSON_MAPPER.readValue(result.properties().get(GROUPING_KEYS_KEY),
                new TypeReference<Map<String, Set<String>>>() { });
        assertThat(parsed).containsOnly(
                Map.entry("DEV/COM/1111", Set.of("KEY1", "KEY2")),
                Map.entry("DEV/COM/2222/SUB", Set.of("KEY3")));
    }

    @Test
    void noIndexedGroupingKeysRowsMeansNoGroupingKeysRowInOutput() {
        var result = LegacyConfigValueConverter.convert(Map.of("some.other.key", "value"));

        assertThat(result.properties()).doesNotContainKey(GROUPING_KEYS_KEY);
    }

    @Test
    void lookalikeGroupingKeysKeyPassesThroughUnchanged() {
        var lookalikeKey = GROUPING_KEYS_KEY + "-extra";
        var result = LegacyConfigValueConverter.convert(Map.of(lookalikeKey, "value"));

        assertThat(result.properties()).containsEntry(lookalikeKey, "value");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void reconvertingAlreadyMergedGroupingKeysRowIsNoOp() {
        var firstPass = LegacyConfigValueConverter.convert(Map.of(
                GROUPING_KEYS_KEY + ".\"DEV/COM/1111\"[0]", "KEY1"));

        var secondPass = LegacyConfigValueConverter.convert(firstPass.properties());

        assertThat(secondPass.properties()).isEqualTo(firstPass.properties());
        assertThat(secondPass.warnings()).isEmpty();
    }

    @Test
    void groupingStrategyAndGroupingKeysRowsConvertIndependentlyInOneCall() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                GROUPING_KEY, "member",
                GROUPING_KEYS_KEY + ".\"DEV/COM/1111\"[0]", "KEY1"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.properties()).containsKey(GROUPING_KEYS_KEY);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void mergesPerFieldSignerModuleRowsIntoOneJsonRowWithTypedValues() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.enabled", "true",
                SIGNER_MODULES_KEY + ".hsm_one.pub-key-attribute-allowed-mechanisms", "CKM_RSA_PKCS,CKM_SHA256_RSA_PKCS",
                SIGNER_MODULES_KEY + ".hsm_one.slot-ids", "1,2,3",
                SIGNER_MODULES_KEY + ".hsm_two.library", "/usr/local/lib/libcs_pkcs11.so",
                SIGNER_MODULES_KEY + ".hsm_two.pub-key-attribute-encrypt", "false"));

        assertThat(result.properties()).containsOnlyKeys(SIGNER_MODULES_KEY);
        assertThat(result.warnings()).isEmpty();

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);

        assertThat(modules).containsOnlyKeys("hsm_one", "hsm_two");
        assertThat(modules.get("hsm_one").enabled()).isTrue();
        assertThat(modules.get("hsm_one").pubKeyAttributeAllowedMechanisms())
                .contains(List.of("CKM_RSA_PKCS", "CKM_SHA256_RSA_PKCS"));
        assertThat(modules.get("hsm_one").slotIds()).contains(Set.of(1L, 2L, 3L));
        assertThat(modules.get("hsm_two").library()).isEqualTo("/usr/local/lib/libcs_pkcs11.so");
        assertThat(modules.get("hsm_two").pubKeyAttributeEncrypt()).isFalse();
    }

    @Test
    void booleanDeviceFieldsAcceptXRoad7TrueFalseSynonymsCaseInsensitively() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.pub-key-attribute-encrypt", "No",
                SIGNER_MODULES_KEY + ".hsm_one.sign-verify-pin", "YES"));

        assertThat(result.warnings()).isEmpty();

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);
        assertThat(modules.get("hsm_one").pubKeyAttributeEncrypt()).isFalse();
        assertThat(modules.get("hsm_one").signVerifyPin()).isTrue();
    }

    @Test
    void unrecognizedBooleanValueLeftOutWithWarningNamingDeviceFieldAndValue() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.library", "/usr/local/lib/libcs_pkcs11.so",
                SIGNER_MODULES_KEY + ".hsm_one.sign-verify-pin", "maybe"));

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("hsm_one").contains("sign-verify-pin").contains("maybe");

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);
        assertThat(modules.get("hsm_one").library()).isEqualTo("/usr/local/lib/libcs_pkcs11.so");
        assertThat(modules.get("hsm_one").signVerifyPin()).isFalse();
    }

    @Test
    void dottedDeviceNameRowKeySplitsAtLastDot() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm.dotted.enabled", "true",
                SIGNER_MODULES_KEY + ".hsm.dotted.library", "/usr/local/lib/libcs_pkcs11.so"));

        assertThat(result.warnings()).isEmpty();

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);
        assertThat(modules).containsOnlyKeys("hsm.dotted");
        assertThat(modules.get("hsm.dotted").enabled()).isTrue();
        assertThat(modules.get("hsm.dotted").library()).isEqualTo("/usr/local/lib/libcs_pkcs11.so");
    }

    @Test
    void unknownDeviceFieldLeftOutWithWarningNamingDeviceAndField() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.enabled", "true",
                SIGNER_MODULES_KEY + ".hsm_one.not-a-real-field", "value"));

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("hsm_one").contains("not-a-real-field");

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);
        assertThat(modules.get("hsm_one").enabled()).isTrue();
    }

    @Test
    void nonNumericSlotIdLeftOutWithWarningNamingDeviceFieldAndValue() {
        var result = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.enabled", "true",
                SIGNER_MODULES_KEY + ".hsm_one.slot-ids", "1,abc,3"));

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("hsm_one").contains("slot-ids").contains("1,abc,3");

        Map<String, SignerModuleConfig> modules = KEBAB_MAPPER.readValue(result.properties().get(SIGNER_MODULES_KEY),
                SIGNER_MODULES_TYPE);
        assertThat(modules.get("hsm_one").slotIds()).isEmpty();
    }

    @Test
    void noDeviceRowsMeansNoSignerModulesRowInOutput() {
        var result = LegacyConfigValueConverter.convert(Map.of("some.other.key", "value"));

        assertThat(result.properties()).doesNotContainKey(SIGNER_MODULES_KEY);
    }

    @Test
    void lookalikeSignerModulesKeyPassesThroughUnchanged() {
        var lookalikeKey = SIGNER_MODULES_KEY + "-extra.x";
        var result = LegacyConfigValueConverter.convert(Map.of(lookalikeKey, "value"));

        assertThat(result.properties()).containsEntry(lookalikeKey, "value");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void reconvertingAlreadyMergedSignerModulesRowIsNoOp() {
        var firstPass = LegacyConfigValueConverter.convert(Map.of(
                SIGNER_MODULES_KEY + ".hsm_one.enabled", "true"));

        var secondPass = LegacyConfigValueConverter.convert(firstPass.properties());

        assertThat(secondPass.properties()).isEqualTo(firstPass.properties());
        assertThat(secondPass.warnings()).isEmpty();
    }

}
