/**
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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.Key;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyValuePair;
import org.niis.xroad.securityserver.restapi.openapi.model.Token;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenType;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

public class TokenConverterTest extends AbstractConverterTestContext {

    @Autowired
    TokenConverter tokenConverter;

    @Before
    public void setup() {
        doReturn(Collections.singletonList(new Key())).when(keyConverter).convert(any(Iterable.class));
    }

    @Test
    public void convert() throws Exception {
        Map<String, String> tokenInfos = new HashMap<>();
        tokenInfos.put("key1", "value1");
        tokenInfos.put("key2", "value2");
        // keyinfo not used, keyConverter mocked
        KeyInfo dummyKeyInfo = new TokenTestUtils.KeyInfoBuilder().build();

        TokenInfo tokenInfo = new TokenInfo(TokenInfo.SOFTWARE_MODULE_TYPE,
                "friendly-name",
                "id",
                false,
                true,
                true,
                "serial-number",
                "label",
                123,
                TokenStatusInfo.OK,
                Collections.singletonList(dummyKeyInfo),
                tokenInfos);

        Token token = tokenConverter.convert(tokenInfo);

        assertEquals(true, token.getLoggedIn());
        assertEquals(true, token.getAvailable());
        assertEquals("id", token.getId());
        assertNotNull(token.getKeys());
        assertEquals(1, token.getKeys().size());
        assertEquals("friendly-name", token.getName());
        assertEquals(false, token.getReadOnly());
        assertEquals(false, token.getSavedToConfiguration());
        assertEquals("serial-number", token.getSerialNumber());
        assertEquals(TokenStatus.OK, token.getStatus());
        assertEquals(TokenType.SOFTWARE, token.getType());
        assertNotNull(token.getTokenInfos());
        assertEquals(2, token.getTokenInfos().size());
        assertTrue(token.getTokenInfos().contains(new KeyValuePair().key("key1").value("value1")));
        assertTrue(token.getTokenInfos().contains(new KeyValuePair().key("key2").value("value2")));

        // hsm
        tokenInfo = new TokenInfo("hsm-uid-1234",
                "friendly-name",
                "id",
                false,
                true,
                true,
                "serial-number",
                "label",
                123,
                TokenStatusInfo.USER_PIN_COUNT_LOW,
                Collections.singletonList(dummyKeyInfo),
                tokenInfos);
        token = tokenConverter.convert(tokenInfo);
        assertEquals(TokenType.HARDWARE, token.getType());
        assertEquals(TokenStatus.USER_PIN_COUNT_LOW, token.getStatus());
    }

    @Test
    public void isSavedToConfiguration() throws Exception {
        // test different combinations of saved and unsaved keys and the logic for isSavedToConfiguration
        KeyInfo savedKey = new TokenTestUtils.KeyInfoBuilder().build();
        KeyInfo unsavedKey = new TokenTestUtils.KeyInfoBuilder().build();

        savedKey.getCerts().clear();
        savedKey.getCertRequests().clear();
        savedKey.getCertRequests().add(KeyConverterTest.createTestCsr());

        unsavedKey.getCerts().clear();
        unsavedKey.getCertRequests().clear();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder().build();

        tokenInfo.getKeyInfo().clear();
        assertEquals(false, tokenConverter.convert(tokenInfo).getSavedToConfiguration());

        tokenInfo.getKeyInfo().clear();
        tokenInfo.getKeyInfo().add(unsavedKey);
        assertEquals(false, tokenConverter.convert(tokenInfo).getSavedToConfiguration());

        tokenInfo.getKeyInfo().clear();
        tokenInfo.getKeyInfo().add(savedKey);
        assertEquals(true, tokenConverter.convert(tokenInfo).getSavedToConfiguration());

        tokenInfo.getKeyInfo().clear();
        tokenInfo.getKeyInfo().add(unsavedKey);
        tokenInfo.getKeyInfo().add(savedKey);
        tokenInfo.getKeyInfo().add(unsavedKey);
        assertEquals(true, tokenConverter.convert(tokenInfo).getSavedToConfiguration());
    }

}
