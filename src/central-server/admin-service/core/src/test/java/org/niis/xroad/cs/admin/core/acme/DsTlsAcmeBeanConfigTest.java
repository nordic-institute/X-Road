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
package org.niis.xroad.cs.admin.core.acme;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the dataspace TLS ACME renewal kill-switch - the Central Server's independent capability the Security
 * Server deliberately does not have.
 */
class DsTlsAcmeBeanConfigTest {

    private final DsTlsAcmeBeanConfig.IsDsTlsAcmeEnrollmentActive condition = new DsTlsAcmeBeanConfig.IsDsTlsAcmeEnrollmentActive();

    @Test
    void doesNotMatchWhenRenewalInactive() {
        assertFalse(matches("false"));
    }

    @Test
    void matchesWhenRenewalActive() {
        assertTrue(matches("true"));
    }

    @Test
    void matchesByDefaultWhenPropertyIsAbsent() {
        ConditionContext context = mock(ConditionContext.class);
        AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
        Environment environment = mock(Environment.class);

        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("xroad.admin-service.ds-tls-acme.renewal-active", "true")).thenReturn("true");

        assertTrue(condition.matches(context, metadata));
    }

    private boolean matches(String renewalActive) {
        ConditionContext context = mock(ConditionContext.class);
        AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
        Environment environment = mock(Environment.class);

        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("xroad.admin-service.ds-tls-acme.renewal-active", "true"))
                .thenReturn(renewalActive);

        return condition.matches(context, metadata);
    }
}
