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
package org.niis.xroad.signer.test;

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;
import static org.niis.xroad.signer.test.container.SignerIntTestContainerSetup.SIGNER;

/**
 * 0300 - Signer: Parallel scenarios. Exercises the signer under real concurrent sign calls against the
 * hardware-token key {@code SignKey from CA} on {@code xrd-softhsm-0} (created by the hardware-token EC
 * class, 0220), before and after a container restart.
 */
@Slf4j
@Order(300)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:MagicNumber")
class SignerParallelActionsIntTest extends AbstractSignerIntTest {

    private static final String TOKEN_HSM_0 = "xrd-softhsm-0";
    private static final String KEY_NAME = "SignKey from CA";

    @BeforeEach
    void backgroundSetup() {
        Step.given("tokens are listed", this::listTokens);
        Step.and("HSM is operational", () -> assertThat(client().isHSMOperational()).isTrue());
    }

    @Test
    @Order(1)
    @DisplayName("Data sign is properly handled in parallel execution")
    void dataSignIsProperlyHandledInParallelExecution() {
        Step.when("digest can be signed in using key 'SignKey from CA' from token 'xrd-softhsm-0'. "
                        + "25 threads each sign 50 times in parallel (1250 signs total).",
                () -> doConcurrentSign(50, 25));
    }

    @Test
    @Order(2)
    @DisplayName("Data sign is properly handled in parallel execution (post restart)")
    void dataSignIsProperlyHandledInParallelExecutionPostRestart() {
        Step.when("signer service is restarted", () -> containerSetup.restartContainer(SIGNER));
        Step.and("tokens are listed", this::listTokens);
        Step.and("digest can be signed in using key 'SignKey from CA' from token 'xrd-softhsm-0'. "
                        + "5 threads each sign 25 times in parallel (125 signs total).",
                () -> doConcurrentSign(25, 5));
    }

    @SneakyThrows
    private void doConcurrentSign(int loops, int threads) {
        final KeyInfo key = findKeyInToken(TOKEN_HSM_0, KEY_NAME);
        var signAlgorithm = switch (SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm()) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };

        Callable<byte[]> callable = () -> {
            var digest = "%s-%d".formatted(UUID.randomUUID(), System.currentTimeMillis());
            var stopWatch = StopWatch.createStarted();
            byte[] result = signClient(PRIMARY).sign(key.getId(), signAlgorithm, calculateDigest(SHA256, digest.getBytes(UTF_8)));
            stopWatch.stop();
            log.trace("Executed sign in {} ms.", stopWatch.getDuration().toMillis());
            return result;
        };

        try (ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
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
        }
    }
}
