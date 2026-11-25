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

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantType;

import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The rule that decides whether the DIDs of bound participants can be created at the registered address: each
 * row is minted by its participant type, and a rejection or an identifier that cannot be encoded counts as a DID
 * that cannot be created.
 */
@UtilityClass
class DataspaceDidMintRule {

    /**
     * A participant whose DID cannot be created.
     *
     * @param row    the binding row of the participant
     * @param reason why the DID cannot be created
     */
    record Unmintable(DsParticipantEntity row, String reason) {
    }

    /**
     * @param didAuthority the authority that mints the DIDs
     * @param rows         the binding rows to check
     * @return the first row whose DID cannot be created, empty when every DID can be created
     * @throws XrdRuntimeException with {@code DSP_PROVISIONING_FAILED} while the registered address is unknown, and
     *                             with any other error the mint raises besides {@code VALIDATION_ERROR}
     */
    Optional<Unmintable> firstUnmintable(DataspaceDidAuthority didAuthority, List<DsParticipantEntity> rows) {
        for (var row : rows) {
            var unmintable = unmintable(didAuthority, row);
            if (unmintable.isPresent()) {
                return unmintable;
            }
        }
        return Optional.empty();
    }

    private Optional<Unmintable> unmintable(DataspaceDidAuthority didAuthority, DsParticipantEntity row) {
        try {
            return mint(didAuthority, row) instanceof DidMintResult.Rejected(var reason, var detail, _)
                    ? Optional.of(new Unmintable(row, reason + " (" + detail + ")"))
                    : Optional.empty();
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(VALIDATION_ERROR)) {
                return Optional.of(new Unmintable(row, e.getMessage()));
            }
            throw e;
        }
    }

    private DidMintResult mint(DataspaceDidAuthority didAuthority, DsParticipantEntity row) {
        return row.getParticipantType() == ParticipantType.SYSTEM
                ? didAuthority.mintSystemDid()
                : didAuthority.mintMemberDid(ClientId.Conf.ensure(row.getMemberIdentifier()));
    }
}
