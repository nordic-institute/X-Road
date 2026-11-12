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
package org.niis.xroad.cs.admin.core.dataspace;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;
import org.niis.xroad.cs.admin.core.repository.IssuerSetUpLeaseRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Each method call is its own short transaction (the class-level {@link Transactional} wraps every
 * public method individually), so the lease is never held open across the background run's gRPC calls.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class IssuerSetUpLeaseServiceImpl implements IssuerSetUpLeaseService {

    private final IssuerSetUpLeaseRepository issuerSetUpLeaseRepository;

    @Override
    public boolean tryAcquire(String node, Duration ttl) {
        return issuerSetUpLeaseRepository.tryAcquire(node, ttl);
    }

    @Override
    public void release(String node) {
        issuerSetUpLeaseRepository.release(node);
    }

    @Override
    public Optional<String> currentHolder() {
        return issuerSetUpLeaseRepository.findHolder();
    }
}
