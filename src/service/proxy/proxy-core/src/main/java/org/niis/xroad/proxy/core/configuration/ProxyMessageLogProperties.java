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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.ServicePrioritizationStrategy;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "xroad.proxy.message-log")
public interface ProxyMessageLogProperties {
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    @WithName("timestamper")
    TimestamperProperties timestamper();

    @WithName("message-body-logging")
    @WithDefault("true")
    boolean messageBodyLogging();

    @WithName("max-loggable-message-body-size")
    @WithDefault("10485760")
    long maxLoggableMessageBodySize();

    @WithName("truncated-body-allowed")
    @WithDefault("false")
    boolean truncatedBodyAllowed();

    @WithName("hash-algo-id")
    @WithDefault("SHA-512")
    String hashAlgoIdStr();

    @WithName("timestamping-prioritization-strategy")
    @WithDefault("NONE")
    ServicePrioritizationStrategy timestampingPrioritizationStrategy();

    @WithName("enabled-body-logging-local-producer-subsystems")
    @WithDefault("")
    Optional<String> enabledBodyLoggingLocalProducerSubsystems();

    @WithName("enabled-body-logging-remote-producer-subsystems")
    @WithDefault("")
    Optional<String> enabledBodyLoggingRemoteProducerSubsystems();

    @WithName("disabled-body-logging-local-producer-subsystems")
    @WithDefault("")
    Optional<String> disabledBodyLoggingLocalProducerSubsystems();

    @WithName("disabled-body-logging-remote-producer-subsystems")
    @WithDefault("")
    Optional<String> disabledBodyLoggingRemoteProducerSubsystems();

    default DigestAlgorithm hashAlg() {
        return Optional.ofNullable(hashAlgoIdStr())
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    interface TimestamperProperties {
        @WithName("client-connect-timeout")
        @WithDefault("20000")
        int clientConnectTimeout();

        @WithName("client-read-timeout")
        @WithDefault("60000")
        int clientReadTimeout();

        @WithName("timestamp-immediately")
        @WithDefault("false")
        boolean timestampImmediately();

        @WithName("records-limit")
        @WithDefault("10000")
        int recordsLimit();

        @WithName("retry-delay")
        @WithDefault("60")
        int retryDelay();

        @WithName("acceptable-timestamp-failure-period")
        @WithDefault("14400")
        int acceptableTimestampFailurePeriod();

    }
}
