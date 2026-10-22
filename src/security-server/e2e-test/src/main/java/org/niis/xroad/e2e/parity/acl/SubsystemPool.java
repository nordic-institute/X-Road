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

package org.niis.xroad.e2e.parity.acl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The subsystems every parity scenario shares. Registering a subsystem takes over a minute, so the run
 * adds and registers all of them once, in one batch, before the first scenario. Scenarios only use them
 * as callers or providers.
 */
public final class SubsystemPool {

    public static final String SS0 = "ss0";
    public static final String SS1 = "ss1";

    /** Sibling subsystem of the consumer's member, local to and registered on ss1. */
    public static final String SIBLING_OF_CONSUMER = "DEV:COM:4321:PoolSibling";
    /** Subsystem of the provider's member, local to and registered on ss1. */
    public static final String SECOND_CONSUMER = "DEV:COM:1234:PoolConsumer";
    /** Provider-side client on ss0 that is created but never registered, so it stays SAVED. */
    public static final String UNREGISTERED_PROVIDER = "DEV:COM:1234:PoolSaved";
    /** Second provider-side client on ss0; it owns a local group only and is never called. */
    public static final String SECOND_OWNER = "DEV:COM:1234:PoolOwner";

    private static final int CLIENT_ID_PARTS = 4;

    private static final List<Entry> ENTRIES = List.of(
            new Entry(SIBLING_OF_CONSUMER, SS1, true),
            new Entry(SECOND_CONSUMER, SS1, true),
            new Entry(UNREGISTERED_PROVIDER, SS0, false),
            new Entry(SECOND_OWNER, SS0, false));

    private SubsystemPool() {
    }

    /** The pool content, in declaration order. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** The entries grouped by the Security Server that hosts them, keeping the declared order. */
    public static Map<String, List<Entry>> byHost(List<Entry> entries) {
        var grouped = new LinkedHashMap<String, List<Entry>>();
        for (var entry : entries) {
            grouped.computeIfAbsent(entry.host(), host -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    /** The client ids among {@code entries} that are to be registered, not only created. */
    public static List<String> clientIdsToRegister(List<Entry> entries) {
        return entries.stream().filter(Entry::register).map(Entry::clientId).toList();
    }

    /**
     * One pool subsystem: its client id ({@code INSTANCE:CLASS:CODE:SUBSYSTEM}), the environment name of
     * the Security Server hosting it, and whether it is registered after being added.
     */
    public record Entry(String clientId, String host, boolean register) {

        public Entry {
            if (clientId.split(":", -1).length != CLIENT_ID_PARTS || clientId.contains(" ")) {
                throw new IllegalArgumentException("Not a subsystem client id: " + clientId);
            }
            if (host.isBlank()) {
                throw new IllegalArgumentException("Pool entry " + clientId + " has no host");
            }
        }
    }
}
