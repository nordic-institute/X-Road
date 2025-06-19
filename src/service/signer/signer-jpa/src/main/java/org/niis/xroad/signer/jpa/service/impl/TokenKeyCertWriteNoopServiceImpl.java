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

package org.niis.xroad.signer.jpa.service.impl;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.signer.core.service.TokenKeyCertWriteService;

import java.time.Instant;

import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;

public class TokenKeyCertWriteNoopServiceImpl implements TokenKeyCertWriteService {

    @Override
    public Long save(Long keyId, String externalId, ClientId clientId, String status, byte[] certBytes) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean delete(Long id) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean setActive(Long id, boolean active) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean updateRenewedCertHash(Long id, String renewedCertHash) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean updateRenewalError(Long id, String renewalError) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean updateNextAutomaticRenewalTime(Long id, Instant nextRenewalTime) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    @Override
    public boolean updateOcspVerifyBeforeActivationError(Long certId, String ocspVerifyBeforeActivationError) {
        throw new CodedException(X_ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }
}
