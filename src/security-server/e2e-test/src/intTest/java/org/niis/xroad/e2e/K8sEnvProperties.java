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
package org.niis.xroad.e2e;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Two-SS k8s topology configuration (PRD .workbench/20260730-k8s-e2e-variant/PRD.md, slice 06:
 * two-ss-message-flow). ss0 and ss1 are two Security Server chart releases in separate namespaces
 * ({@code ss0}/{@code ss1}), reachable from the harness through the {@code kubectl port-forward}
 * listeners started by {@code core/scripts/env-k8s} — hence the {@code localhost} host defaults.
 * Both SS's forward to localhost, so each needs its own local port; ss1's default ports are ss0's
 * + 1. Each value is overridable via the environment variable named in parentheses.
 */
@ConfigMapping(prefix = "test-framework.k8s")
public interface K8sEnvProperties {

    /** Security Server 0 namespace in the kind cluster (TEST_FRAMEWORK_K8S_SS0_NAMESPACE). */
    @WithDefault("ss0")
    @WithName("ss0-namespace")
    String ss0Namespace();

    /** Security Server 0 host, reached via kubectl port-forward (TEST_FRAMEWORK_K8S_SS0_HOST). */
    @WithDefault("localhost")
    @WithName("ss0-host")
    String ss0Host();

    /** Forwarded proxy port on ss0Host (TEST_FRAMEWORK_K8S_SS0_PROXY_PORT). */
    @WithDefault("8080")
    @WithName("ss0-proxy-port")
    int ss0ProxyPort();

    /** Forwarded admin UI port on ss0Host (TEST_FRAMEWORK_K8S_SS0_UI_PORT). */
    @WithDefault("4000")
    @WithName("ss0-ui-port")
    int ss0UiPort();

    /** Member identifier for ss0 (TEST_FRAMEWORK_K8S_SS0_MEMBER_ID). */
    @WithDefault("DEV:COM:1234")
    @WithName("ss0-member-id")
    String ss0MemberId();

    /** Security Server 1 namespace in the kind cluster (TEST_FRAMEWORK_K8S_SS1_NAMESPACE). */
    @WithDefault("ss1")
    @WithName("ss1-namespace")
    String ss1Namespace();

    /** Security Server 1 host, reached via kubectl port-forward (TEST_FRAMEWORK_K8S_SS1_HOST). */
    @WithDefault("localhost")
    @WithName("ss1-host")
    String ss1Host();

    /** Forwarded proxy port on ss1Host (TEST_FRAMEWORK_K8S_SS1_PROXY_PORT). */
    @WithDefault("8081")
    @WithName("ss1-proxy-port")
    int ss1ProxyPort();

    /** Forwarded admin UI port on ss1Host (TEST_FRAMEWORK_K8S_SS1_UI_PORT). */
    @WithDefault("4001")
    @WithName("ss1-ui-port")
    int ss1UiPort();

    /** Member identifier for ss1 (TEST_FRAMEWORK_K8S_SS1_MEMBER_ID). */
    @WithDefault("DEV:COM:4321")
    @WithName("ss1-member-id")
    String ss1MemberId();

    /** Local {@code kubectl} executable used to shell out to the kind cluster (TEST_FRAMEWORK_K8S_KUBECTL_COMMAND). */
    @WithDefault("kubectl")
    @WithName("kubectl-command")
    String kubectlCommand();
}
