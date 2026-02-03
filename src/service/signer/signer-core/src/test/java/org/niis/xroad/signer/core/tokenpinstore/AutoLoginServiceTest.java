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

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerAutologinProperties;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;
import org.niis.xroad.signer.proto.ActivateTokenReq;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.signer.core.tokenpinstore.AutoLoginService.createRetryInstance;
import static org.niis.xroad.signer.core.tokenpinstore.AutoLoginService.createTimeLimiter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoLoginServiceTest {

    private static final String TOKEN_ID = "softToken-0";
    private static final String TOKEN_PIN = "secret123";

    @Mock
    private SignerAutologinProperties signerAutologinProperties;
    @Mock
    private SignerAutologinProperties.Retry retryProperties;
    @Mock
    private SignerAutologinProperties.TokenConfig tokenConfig;
    @Mock
    private TokenWorkerProvider tokenWorkerProvider;
    @Mock
    private TokenWorker tokenWorker;

    private AutoLoginService autoLoginService;
    private Retry retryInstance;

    @BeforeEach
    void setUp() {
        when(retryProperties.retryDelay()).thenReturn(Duration.ofMillis(10));
        when(retryProperties.retryExponentialBackoffMultiplier()).thenReturn(1.0);
        when(retryProperties.retryMaxAttempts()).thenReturn(2);
        when(retryProperties.retryTimeout()).thenReturn(Duration.ofSeconds(60));
        when(signerAutologinProperties.retry()).thenReturn(retryProperties);

        retryInstance = createRetryInstance(retryProperties);
        TimeLimiter timeLimiter = createTimeLimiter(retryProperties);

        autoLoginService = new AutoLoginService(signerAutologinProperties, tokenWorkerProvider, retryInstance, timeLimiter);
    }

    @Test
    void executeShouldSkipWhenDisabled() {
        when(signerAutologinProperties.enabled()).thenReturn(false);

        autoLoginService.execute();

        verify(tokenWorkerProvider, never()).getTokenWorker(any());
    }

    @Test
    void executeShouldSkipWhenNoTokensConfigured() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of());

        autoLoginService.execute();

        verify(tokenWorkerProvider, never()).getTokenWorker(any());
    }

    @Test
    void executeShouldSkipTokenWithEmptyPin() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn("");
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));

        autoLoginService.execute();

        verify(tokenWorkerProvider, never()).getTokenWorker(any());
    }

    @Test
    void executeShouldSucceedOnFirstAttempt() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));
        when(tokenWorkerProvider.getTokenWorker(TOKEN_ID)).thenReturn(tokenWorker);
        doNothing().when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        autoLoginService.execute();

        verify(tokenWorker, times(1)).handleActivateToken(any(ActivateTokenReq.class));
        assertThat(retryInstance.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isOne();
    }

    @Test
    void executeShouldSucceedOnRetry() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));
        when(tokenWorkerProvider.getTokenWorker(TOKEN_ID)).thenReturn(tokenWorker);

        // Fail first time, succeed second time
        doThrow(new RuntimeException("Temporary failure"))
                .doNothing()
                .when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        autoLoginService.execute();

        verify(tokenWorker, times(2)).handleActivateToken(any(ActivateTokenReq.class));
        assertThat(retryInstance.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isOne();
    }

    @Test
    void executeShouldFailAfterExhaustingRetries() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));
        when(tokenWorkerProvider.getTokenWorker(TOKEN_ID)).thenReturn(tokenWorker);

        // Fail all attempts
        doThrow(new RuntimeException("Persistent failure"))
                .when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        assertThatThrownBy(() -> autoLoginService.execute())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Autologin failed for token");

        // Initial + 2 retries = 3 attempts
        int expectedAttempts = retryProperties.retryMaxAttempts() + 1;
        verify(tokenWorker, times(expectedAttempts)).handleActivateToken(any(ActivateTokenReq.class));
        assertThat(retryInstance.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isOne();
    }

    @Test
    void executeShouldFailImmediatelyOnFatalError() {
        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));
        when(tokenWorkerProvider.getTokenWorker(TOKEN_ID)).thenReturn(tokenWorker);

        // Throw fatal error (TOKEN_PIN_INCORRECT)
        doThrow(XrdRuntimeException.systemException(ErrorCode.TOKEN_PIN_INCORRECT, "Incorrect PIN"))
                .when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        assertThatThrownBy(() -> autoLoginService.execute())
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).isCausedBy(ErrorCode.TOKEN_PIN_INCORRECT)).isTrue());

        // Should only be called once - no retries for fatal errors
        verify(tokenWorker, times(1)).handleActivateToken(any(ActivateTokenReq.class));
    }

    @Test
    void executeShouldFailOnTimeout() {
        // Arrange - create a TimeLimiter with a very short timeout
        TimeLimiterConfig shortTimeoutConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100))
                .cancelRunningFuture(true)
                .build();
        var shortTimeLimiter = TimeLimiter.of("shortTimeoutLimiter", shortTimeoutConfig);

        // Create a new AutoLoginService with the short timeout
        var serviceWithShortTimeout = new AutoLoginService(signerAutologinProperties, tokenWorkerProvider,
                retryInstance, shortTimeLimiter);

        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(TOKEN_ID, tokenConfig));
        when(tokenWorkerProvider.getTokenWorker(TOKEN_ID)).thenReturn(tokenWorker);

        // Mock handleActivateToken to take longer than the timeout
        doAnswer(invocation -> {
            Thread.sleep(500); // Sleep longer than the timeout
            return null;
        }).when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        // Act & Assert - should fail due to timeout
        assertThatThrownBy(() -> serviceWithShortTimeout.execute())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Autologin failed for token");
    }

    @Test
    void executeShouldLoginMultipleTokens() {
        SignerAutologinProperties.TokenConfig tokenConfig2 = () -> "secret456";

        when(signerAutologinProperties.enabled()).thenReturn(true);
        when(tokenConfig.pin()).thenReturn(TOKEN_PIN);
        when(signerAutologinProperties.tokens()).thenReturn(Map.of(
                TOKEN_ID, tokenConfig,
                "softToken-1", tokenConfig2
        ));
        when(tokenWorkerProvider.getTokenWorker(any())).thenReturn(tokenWorker);
        doNothing().when(tokenWorker).handleActivateToken(any(ActivateTokenReq.class));

        autoLoginService.execute();

        verify(tokenWorker, times(2)).handleActivateToken(any(ActivateTokenReq.class));
    }

}
