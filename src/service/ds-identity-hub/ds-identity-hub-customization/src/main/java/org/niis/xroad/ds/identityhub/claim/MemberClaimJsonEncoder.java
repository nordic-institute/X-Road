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

package org.niis.xroad.ds.identityhub.claim;

/**
 * Hand-built JSON encoder for {@link MemberClaim}.
 *
 * <p>Output shape matches the issuer-side {@code MemberIdClaimParser} (Map of String→Object,
 * payload as a nested object). Hand-built rather than via Jackson to keep this module free
 * of binding to a specific JSON impl; the shape is small and fixed.
 *
 * <p>The {@code ParticipantSecureTokenService} claim API is {@code Map<String, String>},
 * so the whole claim is collapsed to a single JSON string which the issuer parser will
 * accept (also handling the {@code Map} variant if a future EDC version expands the type).
 */
final class MemberClaimJsonEncoder {

    private MemberClaimJsonEncoder() { }

    static String encode(MemberClaim claim) {
        var p = claim.payload();
        return "{\"payload\":{"
                + "\"holderDid\":" + jsonString(p.holderDid()) + ","
                + "\"memberId\":" + jsonString(p.memberId()) + ","
                + "\"nonce\":" + jsonString(p.nonce()) + ","
                + "\"issuedAt\":" + p.issuedAt()
                + "},\"signature\":" + jsonString(claim.signature())
                + ",\"certificate\":" + jsonString(claim.certificate())
                + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        var sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}