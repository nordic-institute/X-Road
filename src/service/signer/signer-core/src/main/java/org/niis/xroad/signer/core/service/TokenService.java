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
package org.niis.xroad.signer.core.service;

import org.niis.xroad.signer.core.model.BasicCertInfo;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.BasicTokenInfo;
import org.niis.xroad.signer.core.model.CertRequestData;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TokenService {
    /**
     * Loads all tokens, keys, certificates and certificate requests from the source.
     *
     * @return
     * @throws Exception
     */
    LoadedTokens loadAllTokens() throws Exception;

    boolean delete(Long tokenId) throws Exception;

    Long save(String externalId, String type, String friendlyName, String label, String serialNo) throws Exception;

    boolean setInitialTokenPin(Long tokenId, byte[] pinHash) throws Exception;

    boolean updateTokenPin(Long tokenId, Map<Long, byte[]> updatedKeys, byte[] pinHash) throws Exception;

    boolean updateFriendlyName(Long id, String friendlyName) throws Exception;

    record LoadedTokens(Set<BasicTokenInfo> tokens,
                        Map<Long, List<BasicKeyInfo>> keys,
                        Map<Long, List<BasicCertInfo>> certs,
                        Map<Long, List<CertRequestData>> certRequests) {

    }
}
