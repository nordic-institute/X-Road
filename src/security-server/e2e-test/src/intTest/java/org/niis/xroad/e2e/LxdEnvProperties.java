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
 * LXD topology configuration. Defaults mirror the Ansible vars.env.
 * Each value is overridable via the environment variable named in parentheses.
 */
@ConfigMapping(prefix = "test-framework.lxd")
public interface LxdEnvProperties {

    /** Central Server host (TEST_FRAMEWORK_LXD_CS_HOST). */
    @WithDefault("xrd-cs.lxd")
    @WithName("cs-host")
    String csHost();

    /** Certificate Authority host (TEST_FRAMEWORK_LXD_CA_HOST). */
    @WithDefault("xrd-ca.lxd")
    @WithName("ca-host")
    String caHost();

    /** Security Server 0 host (TEST_FRAMEWORK_LXD_SS0_HOST). */
    @WithDefault("xrd-ss0.lxd")
    @WithName("ss0-host")
    String ss0Host();

    /** Security Server 1 host (TEST_FRAMEWORK_LXD_SS1_HOST). */
    @WithDefault("xrd-ss1.lxd")
    @WithName("ss1-host")
    String ss1Host();

    /** Information System (IS) mock host (TEST_FRAMEWORK_LXD_IS_HOST). */
    @WithDefault("xrd-is.lxd")
    @WithName("is-host")
    String isHost();

    /** Proxy port — same on all SS hosts (TEST_FRAMEWORK_LXD_PROXY_PORT). */
    @WithDefault("8080")
    @WithName("proxy-port")
    int proxyPort();

    /** Member identifier for ss0 (TEST_FRAMEWORK_LXD_SS0_MEMBER_ID). */
    @WithDefault("DEV:COM:1234")
    @WithName("ss0-member-id")
    String ss0MemberId();

    /** Member identifier for ss1 (TEST_FRAMEWORK_LXD_SS1_MEMBER_ID). */
    @WithDefault("DEV:COM:4321")
    @WithName("ss1-member-id")
    String ss1MemberId();

    /** Local {@code lxc} executable used to shell out to the LXD stack (TEST_FRAMEWORK_LXD_LXC_COMMAND). */
    @WithDefault("lxc")
    @WithName("lxc-command")
    String lxcCommand();
}
