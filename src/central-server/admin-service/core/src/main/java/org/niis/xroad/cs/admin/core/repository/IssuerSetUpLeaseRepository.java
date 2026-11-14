/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * All times come from the database clock, so nodes with skewed clocks or different time zones agree on
 * whether the lease has expired.
 */
public interface IssuerSetUpLeaseRepository {

    /**
     * Atomically takes the lease for {@code node} for {@code ttl}, but only if it is currently free (no
     * row yet) or expired. A second caller racing the same moment gets zero rows taken.
     *
     * @return true if the lease was taken
     */
    boolean tryAcquire(String node, Duration ttl);

    /**
     * Frees the lease, but only while {@code node} is still its current holder: a node whose lease has
     * already expired and been taken by another must not release that other node's claim.
     */
    void release(String node);

    /**
     * @return the node holding an unexpired lease, or empty if none does
     */
    Optional<String> findHolder();
}
