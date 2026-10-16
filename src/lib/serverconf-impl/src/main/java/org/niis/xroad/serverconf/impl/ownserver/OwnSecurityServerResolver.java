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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;

/**
 * This Security Server as global configuration knows it: its identifier and registered address.
 * Every reason an answer is missing is a distinct result state: owner not initialised, address not
 * registered, global configuration unavailable. Database failures propagate, and so does a
 * {@link GlobalConfInitException} from {@link #identity()} when the identity source needs global
 * configuration and cannot read it; {@link #address()} maps that one to a result state.
 */
@RequiredArgsConstructor
public class OwnSecurityServerResolver {

    private final OwnServerIdentitySource identitySource;
    private final GlobalConfProvider globalConfProvider;

    /**
     * @return the identity, or the reason it is not known
     * @throws GlobalConfInitException if the identity source needs global configuration and it cannot be read
     */
    public OwnIdentity identity() {
        return identitySource.read();
    }

    public OwnAddress address() {
        OwnIdentity identity;
        try {
            identity = identity();
        } catch (GlobalConfInitException e) {
            return new OwnAddress.GlobalConfUnavailable(e);
        }
        return switch (identity) {
            case OwnIdentity.Known(var id) -> address(id);
            case OwnerNotInitialised _ -> new OwnerNotInitialised();
        };
    }

    public GlobalConfAddress address(SecurityServerId id) {
        try {
            var address = globalConfProvider.getSecurityServerAddress(id);
            if (address == null || address.isBlank()) {
                return new OwnAddress.NotRegistered(id);
            }
            return new OwnAddress.Registered(id, address);
        } catch (XrdRuntimeException | GlobalConfInitException e) {
            return new OwnAddress.GlobalConfUnavailable(e);
        }
    }
}
