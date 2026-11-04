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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Minted;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Reason;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Rejected;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

class DataspaceDidAuthorityTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final SecurityServerId SERVER_ID = SecurityServerId.Conf.create(MEMBER, "SS1");
    private static final int MAX_DID_LENGTH = ParticipantIdentifierScheme.MAX_DID_LENGTH;

    @Test
    void mintsTheCanonicalDidOfAMixedCaseAddressAtPort443() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "SS0.Example.ORG"), 443);

        assertThat(authority.mintMemberDid(MEMBER)).isEqualTo(new Minted(
                ParticipantIdentifierScheme.memberDid(MEMBER, "ss0.example.org")));
        assertThat(((Minted) authority.mintSystemDid()).value()).hasToString("did:web:ss0.example.org:v1:system");
    }

    @Test
    void mintsTheNonDefaultDidPortIntoTheDid() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "ss0.example.org"), 7183);

        var minted = (Minted) authority.mintMemberDid(MEMBER);

        assertThat(minted.value().toString()).startsWith("did:web:ss0.example.org%3A7183:v1:");
    }

    @Test
    void rejectsANonAsciiHost() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "bücher.example"), 443);

        assertThat(authority.mintMemberDid(MEMBER)).isInstanceOfSatisfying(Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(Reason.NON_ASCII_HOST));
        assertThat(authority.mintSystemDid()).isInstanceOfSatisfying(Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(Reason.NON_ASCII_HOST));
    }

    @Test
    void rejectsAnAsciiHostThatIsNotAValidName() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "bad_host.example"), 443);

        assertThat(authority.mintMemberDid(MEMBER)).isInstanceOfSatisfying(Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(Reason.INVALID_HOST));
    }

    @Test
    void mintsADidOfExactlyTheMaximumLength() {
        var host = DidLengthTestHosts.hostForMemberDidLength(MEMBER, MAX_DID_LENGTH);
        var authority = authority(new OwnAddress.Registered(SERVER_ID, host), 443);

        var minted = (Minted) authority.mintMemberDid(MEMBER);

        assertThat(minted.value().toString()).hasSize(MAX_DID_LENGTH);
    }

    @Test
    void rejectsADidOneCharacterOverTheMaximumLength() {
        var host = DidLengthTestHosts.hostForMemberDidLength(MEMBER, MAX_DID_LENGTH + 1);
        var authority = authority(new OwnAddress.Registered(SERVER_ID, host), 443);

        assertThat(authority.mintMemberDid(MEMBER)).isInstanceOfSatisfying(Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(Reason.DID_TOO_LONG));
    }

    @Test
    void theTooLongRejectionNamesTheHostAndTheLengths() {
        var host = DidLengthTestHosts.hostForMemberDidLength(MEMBER, MAX_DID_LENGTH + 1);
        var authority = authority(new OwnAddress.Registered(SERVER_ID, host), 443);

        var rejected = (Rejected) authority.mintMemberDid(MEMBER);

        assertThat(rejected.detail()).contains(host)
                .contains(String.valueOf(MAX_DID_LENGTH + 1))
                .contains(String.valueOf(MAX_DID_LENGTH));
    }

    @Test
    void theInvalidHostRejectionCarriesTheBareDetailAndTheOriginalCause() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "bad_host.example"), 443);

        var rejected = (Rejected) authority.mintMemberDid(MEMBER);

        assertThat(rejected.detail()).contains("bad_host.example").doesNotContain("[").doesNotContain("VALIDATION_ERROR");
        assertThat(rejected.cause()).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void aRejectionBecomesOneValidationErrorWithoutDoubleWrappingAndKeepsTheCause() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "bad_host.example"), 443);
        var rejected = (Rejected) authority.mintMemberDid(MEMBER);

        assertThat(rejected.toException())
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(VALIDATION_ERROR.code());
                    assertThat(e.getDetails()).isEqualTo(rejected.detail());
                    assertThat(e.getMessage()).containsOnlyOnce(VALIDATION_ERROR.code());
                    assertThat(e.getCause()).isInstanceOf(XrdRuntimeException.class);
                });
    }

    @Test
    void didIsPresentOnlyForAMintedResult() {
        var valid = authority(new OwnAddress.Registered(SERVER_ID, "ss0.example.org"), 443);
        var invalid = authority(new OwnAddress.Registered(SERVER_ID, "bad_host.example"), 443);

        assertThat(valid.mintMemberDid(MEMBER).did()).isPresent();
        assertThat(invalid.mintMemberDid(MEMBER).did()).isEmpty();
    }

    @Test
    void dspBaseUrlAgreesWithTheMintOnAnAddressThatCannotCarryADid() {
        for (var host : new String[] {"b\u00fccher.example", "bad_host.example"}) {
            var authority = authority(new OwnAddress.Registered(SERVER_ID, host), 443);

            assertThat(authority.mintMemberDid(MEMBER)).isInstanceOf(Rejected.class);
            assertThatThrownBy(authority::dspBaseUrl).isInstanceOfSatisfying(XrdRuntimeException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(VALIDATION_ERROR.code()));
        }
    }

    @Test
    void theMintUsesTheCanonicalAuthorityOfTheRegisteredAddress() {
        var authority = authority(new OwnAddress.Registered(SERVER_ID, "SS0.Example.ORG"), 7183);

        assertThat(authority.mintSystemDid().did().orElseThrow()).hasToString("did:web:ss0.example.org%3A7183:v1:system");
    }

    @Test
    void throwsWhileTheRegisteredAddressIsUnknown() {
        var authority = authority(new OwnAddress.NotRegistered(SERVER_ID), 443);

        assertThatThrownBy(() -> authority.mintMemberDid(MEMBER)).isInstanceOfSatisfying(XrdRuntimeException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(DSP_PROVISIONING_FAILED.code()));
        assertThatThrownBy(authority::mintSystemDid).isInstanceOf(XrdRuntimeException.class);
    }

    private static DataspaceDidAuthority authority(OwnAddress address, int didPort) {
        var resolver = mock(OwnSecurityServerResolver.class);
        when(resolver.address()).thenReturn(address);
        var dataspace = mock(Dataspace.class);
        when(dataspace.getDidPort()).thenReturn(didPort);
        var properties = mock(AdminServiceProperties.class);
        when(properties.getDataspace()).thenReturn(dataspace);
        return new DataspaceDidAuthority(resolver, properties);
    }
}
