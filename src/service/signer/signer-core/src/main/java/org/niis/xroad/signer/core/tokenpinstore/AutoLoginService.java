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
package org.niis.xroad.signer.core.tokenpinstore;

import com.google.protobuf.ByteString;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerAutologinProperties;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;
import org.niis.xroad.signer.proto.ActivateTokenReq;

import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.util.SignerProtoUtils.charToByte;
import static io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AutoLoginService {
    private static final String RETRY_INSTANCE_NAME = "autologinRetry";

    private static final Set<ErrorCode> FATAL_ERRORS = Set.of(
            ErrorCode.TOKEN_PIN_INCORRECT
    );

    private final SignerAutologinProperties signerAutologinProperties;
    private final TokenWorkerProvider tokenWorkerProvider;

    private Retry retryInstance;

    @PostConstruct
    void init() {
        this.retryInstance = createRetryInstance(signerAutologinProperties.retry());
        retryInstance.getEventPublisher().onRetry(event ->
                log.warn("Retrying autologin. Event: {}", event));
    }

    public void execute() {
        if (!signerAutologinProperties.enabled()) {
            log.info("Autologin disabled");
            return;
        }

        Map<String, SignerAutologinProperties.TokenConfig> tokens = signerAutologinProperties.tokens();
        if (tokens.isEmpty()) {
            log.warn("Autologin enabled but no tokens configured in xroad.signer.autologin.tokens.*");
            return;
        }

        log.info("Starting autologin process for {} tokens", tokens.size());

        performAutologin(tokens);
    }

    private void performAutologin(Map<String, SignerAutologinProperties.TokenConfig> tokens) {
        for (Map.Entry<String, SignerAutologinProperties.TokenConfig> entry : tokens.entrySet()) {
            String tokenId = entry.getKey();
            String pin = entry.getValue().pin();

            if (pin == null || pin.isBlank()) {
                log.warn("Skipping token {} - PIN is empty", tokenId);
                continue;
            }

            try {
                loginTokenWithRetry(tokenId, pin.toCharArray());
                log.info("Autologin successful for token {}", tokenId);
            } catch (Exception e) {
                if (isFatalError(e)) {
                    log.error("Fatal error during autologin for token {}, aborting", tokenId);
                    throw XrdRuntimeException.systemInternalError(
                            "Autologin failed with fatal error for token: " + tokenId, e);
                }
                log.error("Autologin failed for token {} after all retries: {}", tokenId, e.getMessage());
                throw XrdRuntimeException.systemInternalError(
                        "Autologin failed for token: " + tokenId, e);
            }
        }
    }

    private void loginTokenWithRetry(String tokenId, char[] pin) {
        try {
            Retry.decorateCheckedRunnable(retryInstance, () -> attemptLogin(tokenId, pin)).run();
        } catch (Throwable e) {
            log.error("Error while performing autologin", e);
        }
    }

    private void attemptLogin(String tokenId, char[] pin) {
        log.info("Attempting to login to token {}", tokenId);

        var activateTokenReq = ActivateTokenReq.newBuilder()
                .setTokenId(tokenId)
                .setActivate(true)
                .setPin(ByteString.copyFrom(charToByte(pin)))
                .build();

        tokenWorkerProvider.getTokenWorker(tokenId).handleActivateToken(activateTokenReq);
        log.info("Successfully logged in to token {}", tokenId);
    }

    private static Retry createRetryInstance(SignerAutologinProperties.Retry retryProperties) {
        var retryInterval = ofExponentialBackoff(
                retryProperties.retryDelay(),
                retryProperties.retryExponentialBackoffMultiplier());

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(retryProperties.retryMaxAttempts() + 1)
                .intervalFunction(retryInterval)
                .retryOnException(AutoLoginService::isRetryableError)
                .failAfterMaxAttempts(true)
                .build();

        var retryRegistry = RetryRegistry.of(retryConfig);
        return retryRegistry.retry(RETRY_INSTANCE_NAME, retryConfig);
    }

    private static boolean isRetryableError(Throwable throwable) {
        return !isFatalError(throwable);
    }

    private static boolean isFatalError(Throwable throwable) {
        if (throwable instanceof XrdRuntimeException xrdException) {
            ErrorCode errorCode = ErrorCode.fromCode(xrdException.getErrorCode());
            return errorCode != null && FATAL_ERRORS.contains(errorCode);
        }
        return false;
    }
}
