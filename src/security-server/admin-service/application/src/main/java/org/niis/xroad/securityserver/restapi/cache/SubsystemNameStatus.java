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

import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SubsystemNameStatus {

    private final Cache<ClientId, Change> nameCache = CacheBuilder.newBuilder()
            .build();


    public Optional<String> getRename(ClientId clientId) {
        return Optional.ofNullable(nameCache.getIfPresent(clientId))
                .map(Change::newName);
    }

    public void set(ClientId clientId, String oldName, String newName) {
        if (!Strings.CS.equals(oldName, newName)) {
            nameCache.put(clientId, new Change(oldName, newName, false));
        }
    }

    public void submit(ClientId clientId, String oldName, String newName) {
        if (!Strings.CS.equals(oldName, newName)) {
            nameCache.put(clientId, new Change(oldName, newName, true));
        }
    }

    public void submit(ClientId clientId) {
        Optional.ofNullable(nameCache.getIfPresent(clientId))
                .ifPresent(change -> {
                    nameCache.put(clientId, change.submit());
                });
    }

    public boolean isSubmitted(ClientId clientId) {
        Change change = nameCache.getIfPresent(clientId);
        return change != null && change.submitted();
    }

    public boolean isSet(ClientId clientId) {
        Change change = nameCache.getIfPresent(clientId);
        return change != null && !change.submitted();
    }

    public void clear(ClientId clientId) {
        nameCache.invalidate(clientId);
    }

    public void clearIf(ClientId clientId, Predicate predicate) {
        Optional.of(clientId)
                .map(nameCache::getIfPresent)
                .filter(rename -> predicate.test(rename.oldName, rename.newName))
                .ifPresent(rename -> clear(clientId));
    }

    public record Change(String oldName, String newName, boolean submitted) {
        public Change submit() {
            return new Change(oldName, newName, true);
        }
    }

    public interface Predicate {
        boolean test(String oldName, String newName);
    }

}
