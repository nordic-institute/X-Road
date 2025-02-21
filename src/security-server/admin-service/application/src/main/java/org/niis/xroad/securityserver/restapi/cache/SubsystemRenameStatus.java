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

package org.niis.xroad.securityserver.restapi.cache;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@RequiredArgsConstructor
public class SubsystemRenameStatus {

    private static final long WAIT_MULTIPLIER = 3;

    private final Cache<ClientId, Rename> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(WAIT_MULTIPLIER * SystemProperties.getConfigurationClientUpdateIntervalSeconds(), SECONDS)
            .build();

    private final GlobalConfProvider globalConfProvider;

    public Optional<String> getNewName(ClientId clientId) {
        return Optional.ofNullable(cache.getIfPresent(clientId))
                .map(Rename::newName);
    }

    public void putNewName(ClientId clientId, String clientName) {
        cache.put(clientId, new Rename(globalConfProvider.getSubsystemName(clientId), clientName));
    }

    public void clear(ClientId clientId) {
        cache.invalidate(clientId);
    }

    public void clearIf(ClientId clientId, Predicate predicate) {
        Optional.of(clientId)
                .map(cache::getIfPresent)
                .filter(rename -> predicate.test(rename.oldName, rename.newName))
                .ifPresent(rename -> clear(clientId));
    }

    public record Rename(String oldName, String newName) {
    }

    public interface Predicate {
        boolean test(String oldName, String newName);
    }

}
