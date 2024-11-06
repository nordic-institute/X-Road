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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleAction;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificateSigningRequest;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

public class TokenCertificateSigningRequestConverterTest extends AbstractConverterTestContext {

    @Autowired
    TokenCertificateSigningRequestConverter csrConverter;

    @Before
    public void setup() {
        doReturn(EnumSet.of(PossibleActionEnum.DELETE)).when(possibleActionsRuleEngine)
                .getPossibleCsrActions(any());
    }

    @Test
    public void convert() {
        CertRequestInfo certRequestInfo = new CertificateTestUtils.CertRequestInfoBuilder().build();
        TokenCertificateSigningRequest csr = csrConverter.convert(certRequestInfo);
        assertEquals("id", csr.getId());
        assertEquals("a:b:c", csr.getOwnerId());
    }

    @Test
    public void convertWithPossibleActions() {
        CertRequestInfo certRequestInfo = new CertificateTestUtils.CertRequestInfoBuilder().build();
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .csr(certRequestInfo)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .key(keyInfo)
                .build();
        TokenCertificateSigningRequest csr = csrConverter.convert(certRequestInfo, keyInfo, tokenInfo);
        Collection<PossibleAction> actions = csr.getPossibleActions();
        assertTrue(actions.contains(PossibleAction.DELETE));
        assertEquals(1, actions.size());
    }

}
