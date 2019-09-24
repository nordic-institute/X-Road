/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import com.google.common.collect.Streams;
import org.niis.xroad.restapi.openapi.model.Token;
import org.niis.xroad.restapi.openapi.model.TokenStatus;
import org.niis.xroad.restapi.openapi.model.TokenType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Convert Token related data between openapi and service domain classes
 */
@Component
public class TokenConverter {

    private final KeyConverter keyConverter;

    @Autowired
    public TokenConverter(KeyConverter keyConverter) {
        this.keyConverter = keyConverter;
    }

    /**
     * Convert {@link TokenInfo} to openapi {@link Token} object
     * @param tokenInfo
     * @return
     */
    public Token convert(TokenInfo tokenInfo) {
        Token token = new Token();
        token.setId(tokenInfo.getId());
        token.setName(tokenInfo.getFriendlyName());

        Optional<TokenStatus> status = TokenStatusMapping.map(tokenInfo.getStatus());
        token.setStatus(status.orElse(null));

        // software module has a magic type, hardware modules have device UI as type
        if (TokenInfo.SOFTWARE_MODULE_TYPE.equals(tokenInfo.getType())) {
            token.setType(TokenType.SOFTWARE);
        } else {
            token.setType(TokenType.HARDWARE);
        }

        token.setKeys(keyConverter.convert(tokenInfo.getKeyInfo()));

        // what about these properties?

//        private final boolean readOnly;
//
//        private final boolean available;
//
//        private final boolean active;
//
//        private final String serialNumber;
//
//        private final String label;
//
//        private final int slotIndex;
//
//        /** Contains label-value pairs of information about token. */
//        private final Map<String, String> tokenInfo;

        return token;
    }

    /**
     * Convert a list of {@link TokenInfo tokenInfos} to a list of {@link Token tokens}
     * @param tokenInfos
     * @return List of {@link TokenInfo tokenInfos}
     */
    public List<Token> convert(Iterable<TokenInfo> tokenInfos) {
        return Streams.stream(tokenInfos)
                .map(this::convert)
                .collect(Collectors.toList());
    }
}
