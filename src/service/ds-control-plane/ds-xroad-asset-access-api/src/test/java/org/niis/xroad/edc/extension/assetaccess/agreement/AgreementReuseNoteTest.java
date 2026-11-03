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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.assetaccess.agreement.AgreementReuseNote.Key;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgreementReuseNoteTest {

    private static Key key(String clientId) {
        return new Key("ctx", "did:own", "did:provider", "asset", clientId);
    }

    @Test
    void remembersTheAgreementPerKey() {
        var note = new AgreementReuseNote(10);

        note.remember(key("DEV:COM:222:A"), "agreement-a");

        assertThat(note.find(key("DEV:COM:222:A"))).contains("agreement-a");
        assertThat(note.find(key("DEV:COM:222:B"))).isEmpty();
        assertThat(note.find(key(null))).isEmpty();
    }

    @Test
    void anotherOwnIdentityProviderOrAssetMisses() {
        var note = new AgreementReuseNote(10);
        note.remember(key("DEV:COM:222:A"), "agreement-a");

        assertThat(note.find(new Key("ctx", "did:renamed", "did:provider", "asset", "DEV:COM:222:A"))).isEmpty();
        assertThat(note.find(new Key("ctx", "did:own", "did:other", "asset", "DEV:COM:222:A"))).isEmpty();
        assertThat(note.find(new Key("ctx", "did:own", "did:provider", "other", "DEV:COM:222:A"))).isEmpty();
        assertThat(note.find(new Key("other", "did:own", "did:provider", "asset", "DEV:COM:222:A"))).isEmpty();
    }

    @Test
    void evictingTheHeldAgreementRemovesTheEntry() {
        var note = new AgreementReuseNote(10);
        note.remember(key("DEV:COM:222:A"), "agreement-a");

        note.evict(key("DEV:COM:222:A"), "agreement-a");

        assertThat(note.find(key("DEV:COM:222:A"))).isEmpty();
    }

    @Test
    void evictingAStaleIdKeepsTheNewerEntry() {
        var note = new AgreementReuseNote(10);
        note.remember(key("DEV:COM:222:A"), "agreement-old");
        note.remember(key("DEV:COM:222:A"), "agreement-new");

        note.evict(key("DEV:COM:222:A"), "agreement-old");

        assertThat(note.find(key("DEV:COM:222:A"))).contains("agreement-new");
    }

    @Test
    void neverGrowsPastItsCapacityAndDropsTheLeastRecentlyUsed() {
        var note = new AgreementReuseNote(3);

        for (int i = 0; i < 10; i++) {
            note.remember(key("client-" + i), "agreement-" + i);
        }

        var remembered = IntStream.range(0, 10)
                .filter(i -> note.find(key("client-" + i)).isPresent())
                .boxed()
                .toList();
        assertThat(remembered).containsExactly(7, 8, 9);
    }

    @Test
    void rejectsACapacityBelowOne() {
        assertThatThrownBy(() -> new AgreementReuseNote(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
