package ee.cyber.sdsb.signer.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import akka.actor.ActorRef;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.hashchain.HashChainBuilder;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.util.CryptoUtils.getDigestAlgorithmId;

public final class SigningCtx {

    private final String signatureAlgorithmId;
    @Getter private final List<ActorRef> clients = new ArrayList<>();
    @Getter private final List<byte[][]> hashes = new ArrayList<>();

    private String hashChainResult;
    private String[] hashChains;

    public SigningCtx(String signatureAlgorithmId) {
        this.signatureAlgorithmId = signatureAlgorithmId;
    }

    public void add(ActorRef client, byte[][] hash) {
        clients.add(client);
        hashes.add(hash);
    }

    public String getHashChainResult() {
        return hashChainResult;
    }

    public String getHashChain(int idx) {
        return hashChains != null ? hashChains[idx] : null;
    }

    public byte[] getDataToBeSigned() throws Exception {
        if (hashes.size() == 0) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No hashes in signing context");
        }

        // TODO: Also handle one hash (with attachments) using the hash chain
        if (hashes.size() == 1) {
            return hashes.get(0)[0];
        }

        HashChainBuilder hashChainBuilder = new HashChainBuilder(
                        getDigestAlgorithmId(signatureAlgorithmId));

        for (byte[][] hash : hashes) {
            hashChainBuilder.addInputHash(hash);
        }

        hashChainBuilder.finishBuilding();

        hashChainResult = hashChainBuilder.getHashChainResult();
        hashChains = hashChainBuilder.getHashChains();

        return hashChainResult.getBytes(StandardCharsets.UTF_8);
    }
}
