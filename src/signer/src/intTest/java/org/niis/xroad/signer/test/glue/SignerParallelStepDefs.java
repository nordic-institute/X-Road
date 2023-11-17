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
            log.trace("Executed sign in {} ms.", stopWatch.getTime());
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
