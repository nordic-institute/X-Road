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

import org.niis.xroad.e2e.parity.acl.SubsystemPool.Entry;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Clock;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Waits until every Security Server other than the host of a registered pool subsystem has it in its
 * global configuration. A server reloads global configuration on its own schedule, so a subsystem that
 * is REGISTERED on its host can still be unknown to the other server, and a grant there then fails with
 * 404.
 */
public final class PoolVisibility {

    /** One pool subsystem that {@code server} has to know. */
    public record Target(String server, String clientId) {
    }

    private PoolVisibility() {
    }

    /** The registered {@code entries} paired with every server in {@code servers} except the host. */
    public static List<Target> targets(List<Entry> entries, List<String> servers) {
        return entries.stream()
                .filter(Entry::register)
                .flatMap(entry -> servers.stream()
                        .filter(server -> !server.equals(entry.host()))
                        .map(server -> new Target(server, entry.clientId())))
                .toList();
    }

    /**
     * Polls {@code known} for the targets not yet known until all are, checking every target at the first
     * poll.
     *
     * @throws IllegalStateException naming each subsystem and server still unknown after {@code timeout}
     */
    public static void await(List<Target> targets, Predicate<Target> known, Duration timeout, Duration interval,
                             Clock clock) {
        var start = clock.nowMillis();
        var pending = targets;
        while (true) {
            pending = pending.stream().filter(known.negate()).toList();
            if (pending.isEmpty()) {
                return;
            }
            var elapsedMillis = clock.nowMillis() - start;
            if (elapsedMillis >= timeout.toMillis()) {
                throw new IllegalStateException("Pool subsystems not known to the server's global configuration after "
                        + Duration.ofMillis(elapsedMillis).toSeconds() + " s: " + describe(pending));
            }
            clock.sleepMillis(interval.toMillis());
        }
    }

    private static String describe(List<Target> pending) {
        return String.join(", ", pending.stream()
                .map(target -> target.clientId() + " on " + target.server())
                .toList());
    }
}
