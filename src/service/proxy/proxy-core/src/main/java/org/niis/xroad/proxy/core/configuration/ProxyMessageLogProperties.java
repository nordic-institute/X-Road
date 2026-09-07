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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_DISABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_DISABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_ENABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_ENABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_HASH_ALGO_ID;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_MAX_LOGGABLE_MESSAGE_BODY_SIZE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_MESSAGE_BODY_LOGGING;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_CLIENT_READ_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_RECORDS_LIMIT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_RETRY_DELAY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPER_TIMESTAMP_IMMEDIATELY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TIMESTAMPING_PRIORITIZATION_STRATEGY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_LOG_TRUNCATED_BODY_ALLOWED;

/** Proxy message-log configuration ({@code xroad.proxy.message-log.*}). */
@RequiredArgsConstructor
public class ProxyMessageLogProperties {

    private final XRoadConfig xRoadConfig;

    /** @return whether message logging is enabled */
    public boolean enabled() {
        return xRoadConfig.value(MESSAGE_LOG_ENABLED);
    }

    /** @return timestamper sub-group */
    public TimestamperProperties timestamper() {
        return new TimestamperProperties(xRoadConfig);
    }

    /** @return whether message body logging is enabled */
    public boolean messageBodyLogging() {
        return xRoadConfig.value(MESSAGE_LOG_MESSAGE_BODY_LOGGING);
    }

    /** @return maximum loggable message body size in bytes */
    public long maxLoggableMessageBodySize() {
        return xRoadConfig.value(MESSAGE_LOG_MAX_LOGGABLE_MESSAGE_BODY_SIZE);
    }

    /** @return whether truncated body is allowed */
    public boolean truncatedBodyAllowed() {
        return xRoadConfig.value(MESSAGE_LOG_TRUNCATED_BODY_ALLOWED);
    }

    /** @return hash algorithm identifier string */
    public String hashAlgoIdStr() {
        return xRoadConfig.value(MESSAGE_LOG_HASH_ALGO_ID);
    }

    /** @return timestamping prioritization strategy */
    public ServicePrioritizationStrategy timestampingPrioritizationStrategy() {
        return ServicePrioritizationStrategy.valueOf(xRoadConfig.value(MESSAGE_LOG_TIMESTAMPING_PRIORITIZATION_STRATEGY));
    }

    /** @return optional filter for enabled body logging on local producer subsystems */
    public Optional<String> enabledBodyLoggingLocalProducerSubsystems() {
        return Optional.ofNullable(xRoadConfig.value(MESSAGE_LOG_ENABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS));
    }

    /** @return optional filter for enabled body logging on remote producer subsystems */
    public Optional<String> enabledBodyLoggingRemoteProducerSubsystems() {
        return Optional.ofNullable(xRoadConfig.value(MESSAGE_LOG_ENABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS));
    }

    /** @return optional filter for disabled body logging on local producer subsystems */
    public Optional<String> disabledBodyLoggingLocalProducerSubsystems() {
        return Optional.ofNullable(xRoadConfig.value(MESSAGE_LOG_DISABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS));
    }

    /** @return optional filter for disabled body logging on remote producer subsystems */
    public Optional<String> disabledBodyLoggingRemoteProducerSubsystems() {
        return Optional.ofNullable(xRoadConfig.value(MESSAGE_LOG_DISABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS));
    }

    /** @return digest algorithm derived from the hash algo ID string */
    public DigestAlgorithm hashAlg() {
        return Optional.ofNullable(hashAlgoIdStr())
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    /** Timestamper sub-configuration ({@code xroad.proxy.message-log.timestamper.*}). */
    @RequiredArgsConstructor
    public static class TimestamperProperties {

        private final XRoadConfig xRoadConfig;

        /** @return timestamper client connection timeout in milliseconds */
        public int clientConnectTimeout() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT);
        }

        /** @return timestamper client read timeout in milliseconds */
        public int clientReadTimeout() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_CLIENT_READ_TIMEOUT);
        }

        /** @return whether to timestamp immediately */
        public boolean timestampImmediately() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_TIMESTAMP_IMMEDIATELY);
        }

        /** @return maximum number of records per timestamping batch */
        public int recordsLimit() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_RECORDS_LIMIT);
        }

        /** @return delay in seconds before retrying a failed timestamp request */
        public int retryDelay() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_RETRY_DELAY);
        }

        /** @return acceptable timestamp failure period in seconds */
        public int acceptableTimestampFailurePeriod() {
            return xRoadConfig.value(MESSAGE_LOG_TIMESTAMPER_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD);
        }
    }
}
