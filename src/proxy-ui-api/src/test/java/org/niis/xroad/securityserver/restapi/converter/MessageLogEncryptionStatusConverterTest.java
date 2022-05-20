/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@link MessageLogEncryptionStatusConverter}
 */
public class MessageLogEncryptionStatusConverterTest {

    private static final boolean MESSAGELOG_ARCHIVE_ENCRYPTION_STATUS = true;
    private static final boolean MESSAGELOG_DATABASE_ENCRYPTION_STATUS = true;
    private static final String MEMBER_ID = "MemberId";
    private static final String GROUPING_RULE = "GroupingRule";
    private static final String KEY_1 = "Key1";
    private static final String KEY_2 = "Key2";

    private MessageLogEncryptionStatusConverter messageLogEncryptionStatusConverter;

    @Before
    public void setup() {
        messageLogEncryptionStatusConverter = new MessageLogEncryptionStatusConverter();
    }


    @Test
    public void shouldConvertToMessageLogEncryptionStatus() {
        MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics =
                new MessageLogEncryptionStatusDiagnostics(MESSAGELOG_ARCHIVE_ENCRYPTION_STATUS,
                        MESSAGELOG_DATABASE_ENCRYPTION_STATUS,
                        GROUPING_RULE,
                        createMessageLogArchiveEncryptionMember());

        MessageLogEncryptionStatus result = messageLogEncryptionStatusConverter
                .convert(messageLogEncryptionStatusDiagnostics);

        assertTrue(result.getMessageLogArchiveEncryptionStatus());
        assertTrue(result.getMessageLogDatabaseEncryptionStatus());
        assertEquals(GROUPING_RULE, result.getMessageLogGroupingRule());
        assertEquals(1, result.getMembers().size());
        org.niis.xroad.securityserver.restapi.openapi.model.MessageLogArchiveEncryptionMember convertedMember
                = result.getMembers().get(0);
        assertEquals(MEMBER_ID, convertedMember.getMemberId());
        assertTrue(convertedMember.getKeys().contains(KEY_1));
        assertTrue(convertedMember.getKeys().contains(KEY_2));
        assertEquals(false, convertedMember.getDefaultKeyUsed());
    }

    private List<MessageLogArchiveEncryptionMember> createMessageLogArchiveEncryptionMember() {
        return Collections.singletonList(new MessageLogArchiveEncryptionMember(
                MEMBER_ID, setOf(KEY_1, KEY_2), false));
    }

    private static Set<String> setOf(String... elem) {
        return elem.length == 1 ? Collections.singleton(elem[0]) : new HashSet<>(Arrays.asList(elem));
    }
}
