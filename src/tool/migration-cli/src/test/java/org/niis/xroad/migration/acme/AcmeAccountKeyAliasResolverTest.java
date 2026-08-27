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
package org.niis.xroad.migration.acme;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcmeAccountKeyAliasResolverTest {

    @Test
    void identityResolverNeverResolvesAnything() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.identity();

        assertThat(resolver.resolveOriginalCaseAliasCandidates("dev:com:1234")).isEmpty();
        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234")).isEmpty();
    }

    @Test
    void resolvesBareMemberIdAlias() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("dev:com:1234")).containsExactly("DEV:COM:1234");
    }

    @Test
    void resolvesAuthPrefixedAlias() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("auth_dev:com:1234")).containsExactly("auth_DEV:COM:1234");
    }

    @Test
    void resolvesSignPrefixedAlias() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234")).containsExactly("sign_DEV:COM:1234");
    }

    @Test
    void resolvesAliasWithMixedCaseWithinASingleSegment() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DeV:com:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234")).containsExactly("sign_DeV:com:1234");
    }

    @Test
    void resolvesSubsystemAlias() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234:SUB"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234:sub")).containsExactly("sign_DEV:COM:1234:SUB");
    }

    @Test
    void returnsEmptyWhenNoClientMatches() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:9999")).isEmpty();
    }

    @Test
    void doesNotMatchAnUnknownPrefix() {
        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("other_dev:com:1234")).isEmpty();
    }

    @Test
    void returnsEveryCandidateWhenClientIdentifiersDifferOnlyByCase() {
        AcmeAccountKeyAliasResolver resolver =
                AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234", "dev:com:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("dev:com:1234"))
                .containsExactlyInAnyOrder("DEV:COM:1234", "dev:com:1234");
        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234"))
                .containsExactlyInAnyOrder("sign_DEV:COM:1234", "sign_dev:com:1234");
    }

    @Test
    void aCaseCollisionDoesNotAffectResolutionOfUnrelatedClients() {
        AcmeAccountKeyAliasResolver resolver =
                AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234", "dev:com:1234", "DEV:COM:5678"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("dev:com:1234"))
                .containsExactlyInAnyOrder("DEV:COM:1234", "dev:com:1234");
        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:5678")).containsExactly("sign_DEV:COM:5678");
    }

    @Test
    void aRepeatedIdenticalClientIdentifierIsNotTreatedAsACollision() {
        AcmeAccountKeyAliasResolver resolver =
                AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234", "DEV:COM:1234"));

        assertThat(resolver.resolveOriginalCaseAliasCandidates("sign_dev:com:1234")).containsExactly("sign_DEV:COM:1234");
    }
}
