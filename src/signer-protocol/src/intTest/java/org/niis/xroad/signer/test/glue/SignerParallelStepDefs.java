package org.niis.xroad.signer.test.glue;

import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ee.ria.xroad.common.util.CryptoUtils.SHA256WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class SignerParallelStepDefs extends BaseSignerStepDefs {
    @Step("digest can be signed in using key {string} from token {string}. Called {} times with {} threads in parallel.")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String friendlyName, int loops, int threads) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        doConcurrentSign(() -> {
            var digest = String.format("%s-%d", UUID.randomUUID(), System.currentTimeMillis());

            var stopWatch = StopWatch.createStarted();
            byte[] result = SignerProxy.sign(key.getId(), SHA256WITHRSA_ID, calculateDigest(SHA256_ID, digest.getBytes(UTF_8)));
            stopWatch.stop();
            log.info("Executed sign in {} ms.", stopWatch.getTime());
            return result;
        }, threads, loops);

    }

    private void doConcurrentSign(Callable<byte[]> callable,
                                  int threads,
                                  int loops) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        List<Callable<byte[]>> callables = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            for (int j = 0; j < loops; j++) {
                callables.add(callable);
            }
        }

        List<Future<byte[]>> results = executorService.invokeAll(callables);
        for (Future<byte[]> result : results) {
            assertThat(result.get()).isNotEmpty();
        }
        executorService.shutdown();
    }
}
