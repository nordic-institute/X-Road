package ee.ria.xroad.proxy.messagelog;

import org.bouncycastle.tsp.TimeStampResponse;

import ee.ria.xroad.common.hashchain.HashChainBuilder;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

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
    Object result(TimeStampResponse tsResponse) throws Exception {
        byte[] timestampDer = getTimestampDer(tsResponse);
        return new Timestamper.TimestampSucceeded(logRecords, timestampDer,
                hashChainResult, hashChains);
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
