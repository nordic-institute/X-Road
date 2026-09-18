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
package org.niis.xroad.ds.identity;

import ee.ria.xroad.common.identifier.ClientId;

import com.apicatalog.did.Did;
import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CLIENT_IDENTIFIER;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_ENCODED_ID;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Derives and parses v1 dataspace participant identifiers (ctx-id and did:web DID) from an X-Road member
 * identifier and a Security Server's public address, per XRDADR-41.
 *
 * <p>Encoding is colon-canonical percent-encoding: every character outside {@code A-Z a-z 0-9 . - _} is
 * percent-encoded, case-preserving, UTF-8. The mapping is one-to-one and reversible, so decoding always
 * recovers the original member identifier. Decoding accepts only the canonical form — uppercase hex
 * escapes, and escapes only for characters that require encoding — so no two accepted encoded forms
 * decode to the same participant.
 *
 * <p>DIDs are handed out and taken in as {@link Did} values: the type guarantees DID syntax
 * (method name, {@code idchar} set, well-formed percent-escapes); this class adds the
 * {@code did:web} host escaping and the v1 segment layout on top.
 */
@UtilityClass
public class ParticipantIdentifierScheme {

    /**
     * The scheme version segment in the DID path, and the value recorded alongside a bound identifier.
     */
    public static final String SCHEME_VERSION = "v1";

    /**
     * The reserved ctx-id/DID segment for the per-Security-Server SYSTEM participant. A member DID always
     * has three segments after {@link #SCHEME_VERSION}; SYSTEM has exactly one, so no member code can
     * collide with it.
     */
    public static final String SYSTEM_SEGMENT = "system";

    /** The DID method every participant DID uses. */
    public static final String DID_METHOD = "web";

    private static final String SEGMENT_SEPARATOR = ":";

    /** The DID segment distinguishing the management context's DID from the host's. */
    private static final String MANAGEMENT_SEGMENT = "mgmt";

    /** did:web's fixed escapes for the authority's port separator and IPv6 literal brackets. */
    private static final Map<Character, String> HOST_ESCAPES_BY_CHAR = Map.of(':', "3A", '[', "5B", ']', "5D");
    private static final Map<String, Character> HOST_CHARS_BY_ESCAPE = Map.of("3A", ':', "5B", '[', "5D", ']');
    private static final int HOST_ESCAPE_HEX_LENGTH = 2;

    /** Number of segments in a member ctx-id, and in a member DID's payload after {@link #SCHEME_VERSION}. */
    private static final int MEMBER_SEGMENT_COUNT = 3;

    // method-specific-id layout: {host} : v1 : {payload...}
    private static final int DID_HOST_INDEX = 0;
    private static final int DID_VERSION_INDEX = 1;
    private static final int DID_PAYLOAD_START_INDEX = 2;
    private static final int MIN_DID_SEGMENT_COUNT = DID_PAYLOAD_START_INDEX + 1;

    private static final int PERCENT_ESCAPE_HEX_LENGTH = 2;
    private static final int HEX_RADIX = 16;
    private static final int HEX_LETTER_A_VALUE = 10;

    /**
     * Derives the member ctx-id: {@code {enc(instance)}:{enc(class)}:{enc(code)}}.
     *
     * @param member the X-Road member identifier; must not carry a subsystem code
     * @return the member's ctx-id
     */
    public static String memberCtxId(ClientId member) {
        requireMemberIdentifier(member);

        return encodeSegment(member.getXRoadInstance())
                + SEGMENT_SEPARATOR + encodeSegment(member.getMemberClass())
                + SEGMENT_SEPARATOR + encodeSegment(member.getMemberCode());
    }

    /**
     * Derives the member DID: {@code did:web:{ss-host}:v1:{enc(instance)}:{enc(class)}:{enc(code)}}.
     *
     * @param member the X-Road member identifier; must not carry a subsystem code
     * @param ssHost the Security Server's public address, as {@code host} or {@code host:port}
     * @return the member's per-server DID
     */
    public static Did memberDid(ClientId member, String ssHost) {
        requireMemberIdentifier(member);

        return didOf(ssHost, memberCtxId(member));
    }

    /**
     * Derives the per-server SYSTEM DID: {@code did:web:{ss-host}:v1:system}.
     *
     * @param ssHost the Security Server's public address, as {@code host} or {@code host:port}
     * @return the server's SYSTEM DID
     */
    public static Did systemDid(String ssHost) {
        return didOf(ssHost, SYSTEM_SEGMENT);
    }

    /**
     * Derives the HOST participant context's DID: {@code did:web:{ss-host}}. Unversioned — it names
     * the serving address itself, so it is not decodable with {@link #decodeDid(String)}.
     *
     * @param ssHost the Security Server's public address, as {@code host} or {@code host:port}
     * @return the host context's DID
     */
    public static Did hostDid(String ssHost) {
        return Did.of(DID_METHOD, didWebHost(ssHost));
    }

    /**
     * Derives the MANAGEMENT participant context's DID: {@code did:web:{ss-host}:mgmt}. Unversioned,
     * like {@link #hostDid(String)}.
     *
     * @param ssHost the Security Server's public address, as {@code host} or {@code host:port}
     * @return the management context's DID
     */
    public static Did managementDid(String ssHost) {
        return Did.of(DID_METHOD, hostDid(ssHost).methodSpecificId() + SEGMENT_SEPARATOR + MANAGEMENT_SEGMENT);
    }

    /**
     * Parses a member ctx-id back into an X-Road member identifier.
     *
     * @param ctxId a ctx-id previously derived with {@link #memberCtxId(ClientId)}
     * @return the decoded member identifier
     * @throws XrdRuntimeException if the ctx-id is malformed
     */
    public static ClientId decodeMemberCtxId(String ctxId) {
        requireNonBlank(ctxId, "ctx-id");

        String[] segments = ctxId.split(SEGMENT_SEPARATOR, -1);
        if (segments.length != MEMBER_SEGMENT_COUNT) {
            throw malformed("ctx-id '%s' must have exactly %d colon-separated segments, got %d",
                    ctxId, MEMBER_SEGMENT_COUNT, segments.length);
        }

        return toClientId(segments, 0);
    }

    /**
     * Parses a DID string into a {@link Did}, rejecting anything that is not syntactically a DID.
     *
     * @param did the DID in wire form
     * @return the parsed DID
     * @throws XrdRuntimeException if the string is blank or not a valid DID
     */
    public static Did parseDid(String did) {
        requireNonBlank(did, "DID");
        try {
            return Did.parse(did);
        } catch (IllegalArgumentException e) {
            throw malformed("'%s' is not a valid DID: %s", did, e.getMessage());
        }
    }

    /**
     * {@link #decodeDid(Did)} for a DID still in wire form.
     *
     * @param did a DID string previously derived with {@link #memberDid(ClientId, String)} or {@link #systemDid(String)}
     * @return the decoded participant
     * @throws XrdRuntimeException if the string is not a DID or the DID is malformed
     */
    public static DecodedParticipant decodeDid(String did) {
        return decodeDid(parseDid(did));
    }

    /**
     * Parses a DID back into either a member identifier or the SYSTEM marker, plus the hosting server's
     * address.
     *
     * @param did a DID previously derived with {@link #memberDid(ClientId, String)} or {@link #systemDid(String)}
     * @return the decoded participant
     * @throws XrdRuntimeException if the DID is malformed
     */
    public static DecodedParticipant decodeDid(Did did) {
        if (!DID_METHOD.equals(did.method())) {
            throw malformed("DID '%s' must use the '%s' method, got '%s'", did, DID_METHOD, did.method());
        }

        String[] segments = did.methodSpecificId().split(SEGMENT_SEPARATOR, -1);
        if (segments.length < MIN_DID_SEGMENT_COUNT) {
            throw malformed("DID '%s' must have at least %d colon-separated segments after the method, got %d",
                    did, MIN_DID_SEGMENT_COUNT, segments.length);
        }
        if (!SCHEME_VERSION.equals(segments[DID_VERSION_INDEX])) {
            throw malformed("DID '%s' has unsupported scheme version '%s', expected '%s'",
                    did, segments[DID_VERSION_INDEX], SCHEME_VERSION);
        }

        String ssHost = decodeHost(segments[DID_HOST_INDEX]);
        String[] payload = Arrays.copyOfRange(segments, DID_PAYLOAD_START_INDEX, segments.length);

        if (payload.length == 1 && SYSTEM_SEGMENT.equals(payload[0])) {
            return new SystemParticipant(ssHost);
        }
        if (payload.length == MEMBER_SEGMENT_COUNT) {
            return new MemberParticipant(toClientId(payload, 0), ssHost);
        }
        throw malformed("DID '%s' payload after '%s' must have exactly 1 (SYSTEM) or %d (member) segments, got %d",
                did, SCHEME_VERSION, MEMBER_SEGMENT_COUNT, payload.length);
    }

    private static Did didOf(String ssHost, String payload) {
        return Did.of(DID_METHOD,
                hostDid(ssHost).methodSpecificId() + SEGMENT_SEPARATOR + SCHEME_VERSION + SEGMENT_SEPARATOR + payload);
    }

    private static ClientId toClientId(String[] segments, int offset) {
        String instance = decodeSegment(segments[offset]);
        String memberClass = decodeSegment(segments[offset + 1]);
        String memberCode = decodeSegment(segments[offset + 2]);
        try {
            return ClientId.Conf.create(instance, memberClass, memberCode);
        } catch (IllegalArgumentException e) {
            throw malformed("segments '%s:%s:%s' do not form a valid member identifier: %s",
                    segments[offset], segments[offset + 1], segments[offset + 2], e.getMessage());
        }
    }

    private static void requireMemberIdentifier(ClientId member) {
        if (member == null) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "member identifier must not be null");
        }
        if (member.getSubsystemCode() != null) {
            throw XrdRuntimeException.systemException(INVALID_CLIENT_IDENTIFIER,
                    "participant identity is member-only, but '%s' carries a subsystem code", member.toShortString());
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "%s must not be blank", label);
        }
    }

    private static XrdRuntimeException malformed(String message, Object... args) {
        return XrdRuntimeException.systemException(INVALID_ENCODED_ID, message, args);
    }

    // -- did:web host encoding: the authority's port separator ':' and IPv6 literal brackets become
    //    percent-escapes ('%3A', '%5B', '%5D' — the only escapes a DID's idchar set allows for
    //    them), reversed on decode. --

    /**
     * Encodes a {@code host} or {@code host:port} authority as a {@code did:web} host segment:
     * the port separator and IPv6 literal brackets become percent-escapes, everything else must
     * already be a legal host character.
     *
     * @param ssHost the authority to encode
     * @return the encoded {@code did:web} host segment, e.g. {@code %5B2001%3Adb8%3A%3A8%5D%3A7183}
     * @throws XrdRuntimeException if the authority contains a character that is neither a host
     *                             character nor escapable
     */
    public static String didWebHost(String ssHost) {
        requireNonBlank(ssHost, "ss-host");
        for (int i = 0; i < ssHost.length(); i++) {
            char c = ssHost.charAt(i);
            if (!isHostChar(c) && HOST_ESCAPES_BY_CHAR.get(c) == null) {
                throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                        "ss-host '%s' has an invalid character '%s' at index %d", ssHost, c, i);
            }
        }
        var encoded = ssHost;
        for (var escape : HOST_ESCAPES_BY_CHAR.entrySet()) {
            encoded = encoded.replace(String.valueOf(escape.getKey()), "%" + escape.getValue());
        }
        return encoded;
    }

    private static boolean isHostChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_';
    }

    private static String decodeHost(String encodedHost) {
        if (encodedHost.isEmpty()) {
            throw malformed("DID host segment must not be empty");
        }

        var decoded = new StringBuilder(encodedHost.length());
        int i = 0;
        while (i < encodedHost.length()) {
            char c = encodedHost.charAt(i);
            if (c == '%') {
                int escapeEnd = i + 1 + HOST_ESCAPE_HEX_LENGTH;
                if (escapeEnd > encodedHost.length()) {
                    throw malformed("DID host segment '%s' has a truncated percent-escape at index %d", encodedHost, i);
                }
                String escape = encodedHost.substring(i + 1, escapeEnd);
                Character escapedChar = HOST_CHARS_BY_ESCAPE.get(escape);
                if (escapedChar == null) {
                    throw malformed("DID host segment '%s' has an unsupported percent-escape '%%%s' at index %d",
                            encodedHost, escape, i);
                }
                decoded.append(escapedChar.charValue());
                i = escapeEnd;
            } else if (isHostChar(c)) {
                decoded.append(c);
                i++;
            } else {
                throw malformed("DID host segment '%s' has an invalid character '%s' at index %d", encodedHost, c, i);
            }
        }
        return decoded.toString();
    }

    // -- enc()/dec(): percent-encode every character outside A-Z a-z 0-9 . - _, case-preserving, UTF-8. --

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_';
    }

    private static int upperHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + HEX_LETTER_A_VALUE;
        }
        return -1;
    }

    private static String encodeSegment(String value) {
        var encoded = new StringBuilder();
        for (byte b : strictUtf8Bytes(value)) {
            int c = Byte.toUnsignedInt(b);
            if (isUnreserved(c)) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append("%02X".formatted(c));
            }
        }
        return encoded.toString();
    }

    private static String decodeSegment(String value) {
        var decoded = new ByteArrayOutputStream(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%') {
                int escapeEnd = i + 1 + PERCENT_ESCAPE_HEX_LENGTH;
                if (escapeEnd > value.length()) {
                    throw malformed("segment '%s' has a truncated percent-escape at index %d", value, i);
                }
                String hex = value.substring(i + 1, escapeEnd);
                int hi = upperHexDigit(hex.charAt(0));
                int lo = upperHexDigit(hex.charAt(1));
                if (hi < 0 || lo < 0) {
                    throw malformed("segment '%s' has a non-canonical percent-escape '%%%s' at index %d;"
                            + " only uppercase hex is accepted", value, hex, i);
                }
                int decodedByte = hi * HEX_RADIX + lo;
                if (isUnreserved(decodedByte)) {
                    throw malformed("segment '%s' has a non-canonical percent-escape '%%%s' at index %d"
                            + " for a character that must stay unencoded", value, hex, i);
                }
                decoded.write(decodedByte);
                i = escapeEnd;
            } else if (isUnreserved(c)) {
                decoded.write(c);
                i++;
            } else {
                throw malformed("segment '%s' has an unencoded character '%s' at index %d that requires percent-encoding",
                        value, c, i);
            }
        }
        return strictUtf8(decoded.toByteArray(), value);
    }

    // getBytes(UTF_8) would silently replace an unpaired surrogate with U+FFFD, colliding with a
    // distinct identifier that genuinely contains U+FFFD
    private static byte[] strictUtf8Bytes(String value) {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "identifier component '%s' is not a valid UTF-16 string and cannot be encoded", value);
        }
    }

    private static String strictUtf8(byte[] bytes, String value) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw malformed("segment '%s' percent-decodes to an invalid UTF-8 byte sequence", value);
        }
    }
}
