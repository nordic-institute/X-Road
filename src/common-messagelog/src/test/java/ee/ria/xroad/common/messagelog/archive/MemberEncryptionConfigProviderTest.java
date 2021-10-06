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

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void shouldParseMappings() throws IOException {
        final Map<String, Set<String>> mappings = MemberEncryptionConfigProvider.readKeyMappings(
                Paths.get("build/gpg/keys.ini"));
        assertEquals(expected, mappings);
    }

    private static Set<String> setOf(String... elem) {
        return elem.length == 1 ? Collections.singleton(elem[0]) : new HashSet<>(Arrays.asList(elem));
    }
}
