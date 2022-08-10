/**
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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemberEncryptionConfigProviderTest {
    private final Map<String, Set<String>> expected = new HashMap<>();

    {
        expected.put("INSTANCE/memberClass/memberCode", setOf("B23B8E993AC4632A896D39A27BE94D3451C16D33"));
        expected.put("withEquals=", setOf("=Föö <foo@example.org>"));
        expected.put("test", setOf("key#1", "key#2"));
        expected.put("#comment escape#", setOf("#42"));
        expected.put("backslash\\=equals", setOf("1"));
        expected.put("backslash\\#hash", setOf("1"));
    }

    @Before
    public void before() {
        System.setProperty(MessageLogProperties.ARCHIVE_DEFAULT_ENCRYPTION_KEY,
                "B23B8E993AC4632A896D39A27BE94D3451C16D55");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG, "build/gpg/keys.ini");
    }

    @After
    public void after() {
        System.clearProperty(MessageLogProperties.ARCHIVE_DEFAULT_ENCRYPTION_KEY);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG);
    }

    @Test
    public void forDiagnosticsWhenExistsRegisteredMemberAndConfigMappingThenShouldReturnMemberWithMappedKey()
            throws IOException {
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode");
        MemberEncryptionConfigProvider provider = new MemberEncryptionConfigProvider();

        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.singletonList(registeredMember));

        assertEncryptionConfig(encryptionConfig);
        List<EncryptionMember> encryptionMembers = encryptionConfig.getEncryptionMembers();
        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.get(0);
        assertEquals(encryptionMember.getMemberId(), "INSTANCE/memberClass/memberCode");
        assertEquals(encryptionMember.getKeys(), Collections.singleton("B23B8E993AC4632A896D39A27BE94D3451C16D33"));
        assertFalse(encryptionMember.isDefaultKeyUsed());
    }

    @Test
    public void forDiagnosticsWhenExistsRegisteredMemberAndNotExistsConfigMappingThenShouldReturnMemberWithDefaultKey()
            throws IOException {
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode2");
        MemberEncryptionConfigProvider provider = new MemberEncryptionConfigProvider();

        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.singletonList(registeredMember));

        assertEncryptionConfig(encryptionConfig);
        List<EncryptionMember> encryptionMembers = encryptionConfig.getEncryptionMembers();
        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.get(0);
        assertEquals("INSTANCE/memberClass/memberCode2", encryptionMember.getMemberId());
        assertEquals(Collections.singleton("B23B8E993AC4632A896D39A27BE94D3451C16D55"), encryptionMember.getKeys());
        assertTrue(encryptionMember.isDefaultKeyUsed());
    }

    @Test
    public void forDiagnosticsWhenNotExistsRegisteredMembersThenShouldReturnEmptyEncryptionMembers()
            throws IOException {
        MemberEncryptionConfigProvider provider = new MemberEncryptionConfigProvider();

        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.emptyList());

        assertEncryptionConfig(encryptionConfig);
        assertEquals(0, encryptionConfig.getEncryptionMembers().size());
    }

    @Test
    public void forDiagnosticsWhenExistsRegisteredMemberAndSubsystemThenShouldReturnOnlyMemberWithMappedKey()
            throws IOException {
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode");
        ClientId registeredSubsystem = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode", "subsystemCode");
        MemberEncryptionConfigProvider provider = new MemberEncryptionConfigProvider();

        EncryptionConfig encryptionConfig =
                provider.forDiagnostics(Arrays.asList(registeredMember, registeredSubsystem));

        assertEncryptionConfig(encryptionConfig);
        List<EncryptionMember> encryptionMembers = encryptionConfig.getEncryptionMembers();
        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.get(0);
        assertEquals("INSTANCE/memberClass/memberCode", encryptionMember.getMemberId());
        assertEquals(Collections.singleton("B23B8E993AC4632A896D39A27BE94D3451C16D33"), encryptionMember.getKeys());
        assertFalse(encryptionMember.isDefaultKeyUsed());
    }

    @Test
    public void shouldParseMappings() throws IOException {
        final Map<String, Set<String>> mappings = MemberEncryptionConfigProvider.readKeyMappings(
                Paths.get("build/gpg/keys.ini"));
        assertEquals(expected, mappings);
    }

    private static Set<String> setOf(String... elem) {
        return elem.length == 1 ? Collections.singleton(elem[0]) : new HashSet<>(Arrays.asList(elem));
    }

    private void assertEncryptionConfig(EncryptionConfig encryptionConfig) {
        assertTrue(encryptionConfig.isEnabled());
        assertEquals(Paths.get("/etc/xroad/gpghome"), encryptionConfig.getGpgHomeDir());
        assertEquals(Collections.emptySet(), encryptionConfig.getEncryptionKeys());
    }
}
