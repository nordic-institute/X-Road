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
package org.niis.xroad.cs.admin.api.service;

import java.time.Duration;
import java.util.Optional;

/**
 * The single-flight lease that keeps Issuer Service set-up to one run at a time across a Central
 * Server cluster: one row in the shared database, naming the node that holds it and when that claim
 * expires on its own. No heartbeat: a node that crashed mid-run is found out only once its lease's TTL
 * passes, never renewed early.
 */
public interface IssuerSetUpLeaseService {

    /**
     * Takes the lease for {@code node}, unless another node already holds an unexpired one.
     *
     * @param node the node claiming the lease
     * @param ttl  how long the claim lasts before it expires on its own
     * @return true if the lease was taken; false if another node already holds it
     */
    boolean tryAcquire(String node, Duration ttl);

    /**
     * Frees the lease, if {@code node} is still its current holder.
     *
     * @param node the node releasing the lease
     */
    void release(String node);

    /**
     * @return the node currently holding an unexpired lease, or empty if none does
     */
    Optional<String> currentHolder();
}
