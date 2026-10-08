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
package org.niis.xroad.serverconf.impl;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;
import org.niis.xroad.serverconf.impl.ownserver.OwnServerIdentitySource;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * Reads the own identity through {@link ServerConfProvider}, in the provider's own cached session.
 * {@link CachingServerConfImpl} cross-checks a cached owner against global configuration and throws
 * {@link GlobalConfInitException} when that cannot be read; this class turns it into a result state.
 *
 * <p>Not a Spring bean: only the proxy uses it, and the admin service supplies its own source.
 */
@RequiredArgsConstructor
public class ProxyOwnServerIdentitySource implements OwnServerIdentitySource {

    private final ServerConfProvider serverConfProvider;

    @Override
    public OwnIdentity read() {
        try {
            return new OwnIdentity.Known(serverConfProvider.getIdentifier());
        } catch (GlobalConfInitException e) {
            return new OwnIdentity.GlobalConfUnavailable(e);
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(MALFORMED_SERVERCONF)) {
                return new OwnIdentity.OwnerNotInitialised();
            }
            throw e;
        }
    }
}
