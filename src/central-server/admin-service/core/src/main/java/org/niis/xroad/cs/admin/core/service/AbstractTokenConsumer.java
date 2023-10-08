/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.CodedException;

import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.core.exception.SignerProxyException;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SIGNER_PROXY_ERROR;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TOKEN_NOT_FOUND;


public abstract class AbstractTokenConsumer {
    private static final String TOKEN_NOT_FOUND_FAULT_CODE = "Signer.TokenNotFound";

    protected abstract SignerProxyFacade getSignerProxyFacade();

    protected ee.ria.xroad.signer.protocol.dto.TokenInfo getToken(String tokenId) {
        try {
            return getSignerProxyFacade().getToken(tokenId);
        } catch (CodedException codedException) {
            if (causedByNotFound(codedException)) {
                throw new NotFoundException(TOKEN_NOT_FOUND);
            }
            throw new SignerProxyException(SIGNER_PROXY_ERROR, codedException, codedException.getFaultCode());
        } catch (Exception exception) {
            throw new SignerProxyException(SIGNER_PROXY_ERROR, exception);
        }
    }

    private boolean causedByNotFound(CodedException codedException) {
        return TOKEN_NOT_FOUND_FAULT_CODE.equals(codedException.getFaultCode());
    }
}
