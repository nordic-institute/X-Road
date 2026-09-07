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
package org.niis.xroad.common.acme;

import java.util.List;

/**
 * Everything {@link AcmeClient} needs to act as a given ACME account. Carries no reference to any globalconf
 * CA-info type: the caller (e.g. {@link AcmeService} for member certs, or DS TLS's own service) resolves its own
 * CA-info shape into this one before calling in, so {@link AcmeClient} never needs to know that shape exists.
 *
 * @param accountAlias           the vault/EAB alias this ACME account is stored and resolved under — for a
 *                               member cert this is the member id, for DS TLS a fixed non-member alias
 * @param caName                 the CA's name, used to look up per-CA EAB credential configuration
 * @param acmeServerDirectoryUrl the CA's ACME directory URL
 * @param certificateProfileId   CA-side certificate profile identifier to send on new orders, or {@code null} if
 *                               the CA needs none
 * @param keyUsage               which EAB credential sub-fields ({@code auth-kid}/{@code sign-kid}) to prefer,
 *                               if the CA's EAB configuration splits them; callers with no such split (e.g. DS
 *                               TLS) pass a fixed value — it is otherwise inert for them
 * @param contacts               ACME account contact URIs (e.g. {@code mailto:...}), or {@code null}/empty for none
 */
public record AcmeAccountContext(
        String accountAlias,
        String caName,
        String acmeServerDirectoryUrl,
        String certificateProfileId,
        AcmeKeyPurpose keyUsage,
        List<String> contacts) {
}
