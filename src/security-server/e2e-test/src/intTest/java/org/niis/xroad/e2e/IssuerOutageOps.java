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

import java.util.regex.Pattern;

/**
 * Operations a scenario needs to take the Issuer Service away from a running environment and to shorten the
 * Central Server's maximum age for issuer data. Implemented only by the compose adapter: the Issuer Service
 * runs as a supervisord program inside the Central Server container there, and only compose can restart
 * ss0's dataspace containers one by one.
 */
public interface IssuerOutageOps {

    /** Whether ss0 runs its control plane and Identity Hub as containers of their own that can be restarted. */
    boolean ss0RunsSeparateDataspaceServices();

    /** Stops the Issuer Service, and does nothing when it is stopped already. */
    void stopIssuerService();

    /** Starts the Issuer Service and returns once it runs again, and does nothing when it runs already. */
    void startIssuerService();

    /** Restarts ss0's control plane and Identity Hub, which drops their in-memory copies of issuer data. */
    void restartSs0DataspaceServices();

    /**
     * Counts the lines of ss0's control plane container log, from the start of the container's life and across
     * restarts, in which the pattern is found.
     */
    long countSs0ControlPlaneLogLines(Pattern pattern);

    /** Stores the maximum age in the Central Server and returns at once, before any Security Server has it. */
    void setIssuerDataFreshnessSeconds(int seconds);

    /** Whether the configuration client of ss0 has downloaded the global configuration that carries the maximum age. */
    boolean ss0HasDownloadedIssuerDataFreshnessSeconds(int seconds);

    /** Removes the stored maximum age, so the Central Server's default applies again. */
    void resetIssuerDataFreshnessSeconds();
}
