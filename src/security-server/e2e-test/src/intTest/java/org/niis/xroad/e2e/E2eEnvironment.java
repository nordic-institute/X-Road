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

import org.niis.xroad.test.apitest.core.container.BaseComposeSetup.ContainerMapping;

/**
 * Environment abstraction for the e2e test suite. Implemented by both the Compose facade
 * ({@code E2eEnvSetup}) and the LXD adapter ({@code LxdEnvSetup}); tests declare this type (or one
 * of the ops interfaces) as a method parameter and never touch environment-specific operations.
 */
public interface E2eEnvironment {

    /**
     * Resolves a service's reachable host and port from (env, service, port).
     */
    ContainerMapping getContainerMapping(String env, String service, int port);

    /**
     * Returns true when the environment is fully bootstrapped and ready for tests.
     */
    boolean isInitialized();

    /**
     * Returns the security server address registered in the global configuration for the given environment.
     */
    String securityServerAddress(String env);
}
