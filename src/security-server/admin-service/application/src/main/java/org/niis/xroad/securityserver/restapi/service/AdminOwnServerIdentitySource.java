/*
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;
import org.niis.xroad.serverconf.impl.ownserver.OwnServerIdentitySource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * Reads the own identity through the server configuration repository, in the caller's session, so
 * uncommitted changes are visible. Joins the caller's transaction, or opens a short read-only one
 * when there is none, because the shared entity manager is unusable outside a transaction.
 *
 * <p>Does not go through {@link org.niis.xroad.serverconf.ServerConfProvider}: that reads in its own
 * session, cached, and cannot see uncommitted writes. Never reports global configuration as
 * unavailable, because it does not read it.
 */
@Component
@RequiredArgsConstructor
public class AdminOwnServerIdentitySource implements OwnServerIdentitySource {

    private final ServerConfRepository serverConfRepository;

    @Override
    @Transactional(readOnly = true)
    public OwnIdentity read() {
        try {
            var serverConf = serverConfRepository.getServerConf();
            var owner = serverConf.getOwner();
            if (owner == null) {
                return new OwnIdentity.OwnerNotInitialised();
            }
            return new OwnIdentity.Known(
                    SecurityServerId.Conf.create(owner.getIdentifier(), serverConf.getServerCode()));
        } catch (XrdRuntimeException e) {
            if (MALFORMED_SERVERCONF.code().equals(e.getErrorCode())) {
                return new OwnIdentity.OwnerNotInitialised();
            }
            throw e;
        }
    }
}
