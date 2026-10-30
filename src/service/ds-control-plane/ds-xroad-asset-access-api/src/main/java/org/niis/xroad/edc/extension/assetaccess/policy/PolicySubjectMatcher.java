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

package org.niis.xroad.edc.extension.assetaccess.policy;

import org.eclipse.edc.policy.model.Policy;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyReader;

/**
 * Matches an ODRL {@link Policy} against the X-Road subject it was published for. Shared by offer selection
 * (which also falls back to a member or group subject) and agreement reuse (which matches a client id or an
 * unrestricted policy, with no member or group fallback).
 */
public final class PolicySubjectMatcher {

    private PolicySubjectMatcher() {
    }

    /**
     * Whether any permission constraint of {@code policy} is an EQ constraint that names
     * {@code encodedClientId} as the client id subject.
     */
    public static boolean namesClient(Policy policy, String encodedClientId) {
        return XRoadPolicyReader.namesClient(policy, encodedClientId);
    }

    /**
     * Whether any permission constraint of {@code policy} is a local or global group subject.
     */
    public static boolean namesGroup(Policy policy) {
        return XRoadPolicyReader.namesGroup(policy);
    }

    /**
     * Whether {@code policy} carries no permission at all, as the provider publishes for builtin and
     * unrestricted-SYSTEM services. Such a policy applies to any caller.
     */
    public static boolean isUnrestricted(Policy policy) {
        return XRoadPolicyReader.isUnrestricted(policy);
    }
}
