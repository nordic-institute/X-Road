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

package org.eclipse.edc.iam.verifiablecredentials.revocation;

import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class RevocationServiceRegistryImpl implements RevocationServiceRegistry {
    private final Map<String, RevocationListService> entries = new HashMap<>();
    private final Monitor monitor;

    public RevocationServiceRegistryImpl(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void addService(String statusListType, RevocationListService service) {
        entries.put(statusListType, service);
    }

    @Override
    public Result<Void> checkValidity(VerifiableCredential credential) {
        return credential.getCredentialStatus()
                .stream()
                .map(this::checkRevocation)
                .reduce(Result::merge)
                .orElse(Result.success());
    }

    @Override
    public Result<String> getRevocationStatus(VerifiableCredential credential) {
        return credential.getCredentialStatus()
                .stream()
                .map(credentialStatus -> getRevocationStatusInternal(credentialStatus, credential))
                .reduce((r1, r2) -> {
                    if (r1.succeeded() && r2.succeeded()) {
                        return Result.success(Stream.of(r1.getContent(), r2.getContent()).filter(Objects::nonNull).collect(Collectors.joining(", ")));
                    }
                    return r1.merge(r2);
                })
                .orElse(Result.success(null));
    }

    private Result<String> getRevocationStatusInternal(CredentialStatus credentialStatus, VerifiableCredential credential) {
        return ofNullable(entries.get(credentialStatus.type()))
                .map(service -> service.getStatusPurpose(credential))
                .orElse(Result.success(null));
    }

    private Result<Void> checkRevocation(CredentialStatus credentialStatus) {
        var service = entries.get(credentialStatus.type());
        if (service == null) {
            monitor.warning("No revocation service registered for type '%s', will not check revocation.".formatted(credentialStatus.type()));
            return Result.success();
        }
        return service.checkValidity(credentialStatus);
    }
}
