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
package org.niis.xroad.serverconf.impl.ownserver;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * Who this Security Server is, or the reason that is not known. A database failure is not a state:
 * it propagates as an exception.
 */
public sealed interface OwnIdentity permits OwnIdentity.Known, OwnerNotInitialised {

    /**
     * @return the server identifier, empty for every other state
     */
    default Optional<SecurityServerId.Conf> serverId() {
        return this instanceof Known(var id) ? Optional.of(id) : Optional.empty();
    }
    /**
     * @return the server identifier
     * @throws XrdRuntimeException with {@code MALFORMED_SERVERCONF} when the owner is not set
     */
    default SecurityServerId.Conf requireServerId() {
        return serverId().orElseThrow(() -> XrdRuntimeException.systemException(MALFORMED_SERVERCONF,
                OwnerNotInitialised.SECURITY_SERVER_OWNER_NOT_SET));
    }

    /**
     * The server's identifier is known.
     */
    record Known(SecurityServerId.Conf id) implements OwnIdentity {
    }
}
