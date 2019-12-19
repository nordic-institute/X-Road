/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertRequestInfoBuilder;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils.TokenInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class StateChangeActionHelperTest {

    @Autowired
    private StateChangeActionHelper helper;

    @Test
    public void getPossibleCertificateActionUnregister() {
        TokenInfo tokenInfo = new TokenInfoBuilder().build();

        assertTrue(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(StateChangeActionEnum.UNREGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(StateChangeActionEnum.UNREGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(StateChangeActionEnum.UNREGISTER));
    }

    @Test
    public void getPossibleCertificateActionRegister() {
        TokenInfo tokenInfo = new TokenInfoBuilder().build();

        assertTrue(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(StateChangeActionEnum.REGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG).build())
                .contains(StateChangeActionEnum.REGISTER));
        assertFalse(helper.getPossibleCertificateActions(tokenInfo,
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.SIGNING).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED).build())
                .contains(StateChangeActionEnum.REGISTER));
    }

    @Test
    public void getPossibleCertificateActionDelete() {
        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(true).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(null).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.DELETE));

        // unreg enabled -> delete not possible
        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_REGINPROG)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.DELETE));

        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().readOnly(false).active(false).build(),
                new KeyInfoBuilder().keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build(),
                new CertificateInfoBuilder().certificateStatus(CertificateInfo.STATUS_SAVED)
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.DELETE));
    }

    @Test
    public void getPossibleCertificateActionDisable() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.DISABLE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.DISABLE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.DISABLE));
    }

    @Test
    public void getPossibleCertificateActionActivate() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.ACTIVATE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(true)
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.ACTIVATE));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .active(false)
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.ACTIVATE));
    }

    @Test
    public void getPossibleCertificateActionImportFromToken() {
        assertTrue(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .savedToConfiguration(false).build())
                .contains(StateChangeActionEnum.IMPORT_FROM_TOKEN));

        assertFalse(helper.getPossibleCertificateActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertificateInfoBuilder()
                        .savedToConfiguration(true).build())
                .contains(StateChangeActionEnum.IMPORT_FROM_TOKEN));
    }

    @Test
    public void getPossibleCsrActionDelete() {
        EnumSet<StateChangeActionEnum> actions = helper.getPossibleCsrActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().build(),
                new CertRequestInfoBuilder().build());
        assertTrue(actions.contains(StateChangeActionEnum.DELETE));
        assertEquals(1, actions.size()); // no other actions

        assertTrue(helper.getPossibleCsrActions(
                new TokenInfoBuilder().build(),
                new KeyInfoBuilder().keyUsageInfo(null).build(),
                new CertRequestInfoBuilder().build())
                .contains(StateChangeActionEnum.DELETE));
    }
    @Test
    public void requirePossibleAction() throws Exception {
        EnumSet<StateChangeActionEnum> actions = EnumSet.of(StateChangeActionEnum.ACTIVATE);
        helper.requirePossibleAction(StateChangeActionEnum.ACTIVATE, actions);
        try {
            helper.requirePossibleAction(StateChangeActionEnum.DELETE, actions);
            fail("should throw exception");
        } catch (TokenCertificateService.ActionNotPossibleException expected) {
        }
    }
}
