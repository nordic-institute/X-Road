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
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;
import org.niis.xroad.signer.proto.ActivateTokenReq;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.util.SignerProtoUtils.charToByte;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AutoLoginService {
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);

    private static final Set<ErrorCode> FATAL_ERRORS = Set.of(
            ErrorCode.TOKEN_PIN_INCORRECT
    );

    private static final Set<ErrorCode> RETRYABLE_ERRORS = Set.of(
            ErrorCode.TOKEN_NOT_FOUND,
            ErrorCode.TOKEN_NOT_INITIALIZED,
            ErrorCode.TOKEN_NOT_AVAILABLE,
            ErrorCode.HTTP_ERROR,
            ErrorCode.NETWORK_ERROR,
            ErrorCode.LOGIN_FAILED
    );

    private final SignerProperties signerProperties;
    private final TokenWorkerProvider tokenWorkerProvider;

    public void execute() {
        if (!signerProperties.autologin()) {
            log.info("Autologin disabled");
            return;
        }

        Map<String, SignerProperties.TokenConfig> tokens = signerProperties.tokens();
        if (tokens.isEmpty()) {
            log.warn("Autologin enabled but no tokens configured in xroad.signer.tokens.*");
            return;
        }

        log.info("Starting autologin process for {} tokens", tokens.size());

        performAutologin(tokens);
    }

    /**
     * Performs automatic login for configured tokens with retry logic.
     * Retries on transient errors (token not available, connection errors).
     * Fails immediately on fatal errors (incorrect PIN).
     */
    private void performAutologin(Map<String, SignerProperties.TokenConfig> tokens) {
        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, SignerProperties.TokenConfig> entry : tokens.entrySet()) {
            String tokenId = entry.getKey();
            String pin = entry.getValue().pin();

            if (pin == null || pin.isBlank()) {
                log.warn("Skipping token {} - PIN is empty", tokenId);
                failureCount++;
                continue;
            }

            LoginResult result = loginTokenWithRetry(tokenId, pin.toCharArray());

            if (LoginResult.FATAL_ERROR == result) {
                log.error("Fatal error during autologin for token {}, aborting", tokenId);
                throw XrdRuntimeException.systemInternalError(
                        "Autologin failed with fatal error for token: " + tokenId);
            }
        }

        log.info("Autologin completed: {} successful, {} failed", successCount, failureCount);
    }

    /**
     * Attempts to login to a token with retry logic.
     *
     * @param tokenId the token ID
     * @param pin the token PIN
     * @return the login result
     */
    private LoginResult loginTokenWithRetry(String tokenId, char[] pin) {
        while (true) {
            log.info("(Re)trying to login to token {}", tokenId);

            try {
                var activateTokenReq = ActivateTokenReq.newBuilder()
                        .setTokenId(tokenId)
                        .setActivate(true)
                        .setPin(ByteString.copyFrom(charToByte(pin)))
                        .build();

                tokenWorkerProvider.getTokenWorker(tokenId).handleActivateToken(activateTokenReq);
                log.info("Successfully logged in to token {}", tokenId);
                return LoginResult.SUCCESS;

            } catch (XrdRuntimeException e) {
                ErrorCode errorCode = ErrorCode.fromCode(e.getErrorCode());

                if (errorCode != null && FATAL_ERRORS.contains(errorCode)) {
                    log.error("FATAL: Incorrect PIN for token {}", tokenId);
                    return LoginResult.FATAL_ERROR;
                }

                if (errorCode != null && RETRYABLE_ERRORS.contains(errorCode)) {
                    log.warn("Failed to login to token {} ({}), retrying...",
                            tokenId, e.getErrorCode());
                    sleep(RETRY_DELAY);
                    continue;
                }

                // Unknown error - treat as retryable but log warning
                log.warn("Unknown error during login to token {}: {}, retrying...",
                        tokenId, e.getMessage());
                sleep(RETRY_DELAY);

            } catch (Exception e) {
                log.warn("Unexpected error during login to token {}: {}, retrying...",
                        tokenId, e.getMessage());
                sleep(RETRY_DELAY);
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XrdRuntimeException.systemInternalError("Autologin interrupted", e);
        }
    }

    private enum LoginResult {
        SUCCESS,
        FATAL_ERROR,
        RETRYABLE_ERROR
    }
}
