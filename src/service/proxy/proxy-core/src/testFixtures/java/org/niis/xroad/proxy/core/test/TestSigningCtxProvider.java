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
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TestSigningCtxProvider implements SigningCtxProvider {
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;

    private final Map<String, SigningCtx> signingCtx = new HashMap<>();

    @Override
    public SigningCtx createSigningCtx(ClientId clientId) {

        String orgName = clientId.getMemberCode();
        if (!signingCtx.containsKey(orgName)) {
            signingCtx.put(orgName, TestSigningCtxUtil.getSigningCtx(globalConfProvider, keyConfProvider, orgName));
        }
        return signingCtx.get(orgName);
    }


}
