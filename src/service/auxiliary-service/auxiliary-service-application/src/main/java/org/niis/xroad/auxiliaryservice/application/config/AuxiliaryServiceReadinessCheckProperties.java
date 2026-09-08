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
package org.niis.xroad.auxiliaryservice.application.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_CA_CERT_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_CONNECT_TIMEOUT_MS;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_READ_TIMEOUT_MS;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_SERVICE_HOST;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_SERVICE_PORT;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.READINESS_CHECK_KUBERNETES_TOKEN_PATH;

/** Auxiliary-service readiness-check configuration ({@code xroad.auxiliary-service.readiness-check.*}). */
@RequiredArgsConstructor
public class AuxiliaryServiceReadinessCheckProperties {

    private final XRoadConfig xRoadConfig;

    /** @return Kubernetes API configuration for the readiness check */
    public KubernetesApiProperties kubernetes() {
        return new KubernetesApiProperties(xRoadConfig);
    }

    /** Kubernetes API sub-configuration ({@code xroad.auxiliary-service.readiness-check.kubernetes.*}). */
    @RequiredArgsConstructor
    public static class KubernetesApiProperties {

        private final XRoadConfig xRoadConfig;

        /** @return Kubernetes API server host, or empty when not configured */
        public Optional<String> serviceHost() {
            return Optional.ofNullable(xRoadConfig.value(READINESS_CHECK_KUBERNETES_SERVICE_HOST));
        }

        /** @return Kubernetes API server port, or empty when not configured */
        public Optional<String> servicePort() {
            return Optional.ofNullable(xRoadConfig.value(READINESS_CHECK_KUBERNETES_SERVICE_PORT));
        }

        /** @return path to the service account token file */
        public String tokenPath() {
            return xRoadConfig.value(READINESS_CHECK_KUBERNETES_TOKEN_PATH);
        }

        /** @return path to the CA certificate file */
        public String caCertPath() {
            return xRoadConfig.value(READINESS_CHECK_KUBERNETES_CA_CERT_PATH);
        }

        /** @return connection timeout in milliseconds */
        public int connectTimeoutMs() {
            return xRoadConfig.value(READINESS_CHECK_KUBERNETES_CONNECT_TIMEOUT_MS);
        }

        /** @return read timeout in milliseconds */
        public int readTimeoutMs() {
            return xRoadConfig.value(READINESS_CHECK_KUBERNETES_READ_TIMEOUT_MS);
        }
    }
}
