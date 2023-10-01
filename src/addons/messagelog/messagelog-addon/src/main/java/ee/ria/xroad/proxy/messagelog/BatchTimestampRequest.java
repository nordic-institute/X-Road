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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.hashchain.HashChainBuilder;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import org.bouncycastle.tsp.TimeStampResponse;

import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.MessageFileNames.SIGNATURE;
import static ee.ria.xroad.common.util.MessageFileNames.TS_HASH_CHAIN;
import static java.nio.charset.StandardCharsets.UTF_8;


class BatchTimestampRequest extends AbstractTimestampRequest {

    private final String[] signatureHashes;

    private String hashChainResult = null;
    private String[] hashChains = null;


    BatchTimestampRequest(Long[] logRecords, String[] signatureHashes) {
        super(logRecords);

        this.signatureHashes = signatureHashes;
    }

    @Override
    byte[] getRequestData() throws Exception {
        HashChainBuilder hcBuilder = buildHashChain(signatureHashes);
        hashChainResult = hcBuilder.getHashChainResult(TS_HASH_CHAIN);
        hashChains = hcBuilder.getHashChains(SIGNATURE);
        return hashChainResult.getBytes(UTF_8.name());
    }

    @Override
    Timestamper.TimestampResult result(TimeStampResponse tsResponse, String url) throws Exception {
        byte[] timestampDer = getTimestampDer(tsResponse);
        return new Timestamper.TimestampSucceeded(logRecords, timestampDer,
                hashChainResult, hashChains, url);
    }

    private HashChainBuilder buildHashChain(String[] hashes) throws Exception {
        HashChainBuilder hcBuilder =
                new HashChainBuilder(MessageLogProperties.getHashAlg());

        for (String signatureHashBase64 : hashes) {
            hcBuilder.addInputHash(decodeBase64(signatureHashBase64));
        }

        hcBuilder.finishBuilding();
        return hcBuilder;
    }

}
