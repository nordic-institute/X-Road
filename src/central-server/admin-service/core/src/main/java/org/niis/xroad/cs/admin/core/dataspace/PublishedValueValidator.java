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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.domain.PublishedValueRejection;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.MemberDid;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.SystemPair;
import org.niis.xroad.ds.identity.DecodedParticipant;
import org.niis.xroad.ds.identity.DidAuthority;
import org.niis.xroad.ds.identity.MemberParticipant;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.ds.identity.SystemParticipant;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DSP_BASE_URL_NOT_HTTPS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.MALFORMED_DSP_BASE_URL;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.NON_ASCII_HOST;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNRELATED_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.WRONG_SCHEME;

/**
 * Checks the values a Security Server publishes (its SYSTEM DID with DSP base URL, and its member DIDs)
 * against the server as registered in the Central Server, and tells when a stored value no longer matches
 * the registered address. It reads nothing but its context; whether a value is already held is the
 * caller's concern.
 *
 * <p>Hosts are compared after the canonical host rules of {@link DidAuthority}; ports are not compared.
 * Shape and scheme of a DID are checked before it is decoded, so a decoding failure never hides a more
 * specific reason. A DSP base URL must have the scheme {@code https} in lowercase and a host in the canonical
 * form of {@link DidAuthority#canonicalHost(String)}; its port is taken in plain decimal form, as written, so an explicit
 * {@code :443}, another port and no port are all accepted. Its checks run in the order not HTTPS,
 * non-ASCII host, malformed, not canonical, host differs.
 */
public final class PublishedValueValidator {

    /** The most characters a published DID may have. */
    public static final int MAX_DID_LENGTH = 4000;

    private static final String DID_PREFIX = "did:" + ParticipantIdentifierScheme.DID_METHOD + ":";
    private static final String DSP_BASE_URL_PREFIX = "https://";
    private static final int HOST_SEGMENT_INDEX = 0;
    private static final int VERSION_SEGMENT_INDEX = 1;
    private static final int MIN_DID_SEGMENT_COUNT = 3;

    private final PublishedValueContext context;
    private final String registeredHost;
    private final PublishedValueRejection registeredAddressRejection;

    public PublishedValueValidator(PublishedValueContext context) {
        this.context = context;
        var address = context.registeredAddress();
        if (address == null) {
            registeredHost = null;
            registeredAddressRejection = UNPARSEABLE_REGISTERED_ADDRESS;
        } else if (!DidAuthority.isAscii(address)) {
            registeredHost = null;
            registeredAddressRejection = NON_ASCII_HOST;
        } else {
            var host = canonicalHostOf(address);
            registeredHost = host.orElse(null);
            registeredAddressRejection = host.isPresent() ? null : UNPARSEABLE_REGISTERED_ADDRESS;
        }
    }

    /**
     * @param candidate the value to publish
     * @return the reason it is refused, or empty when it is accepted
     */
    public Optional<PublishedValueRejection> validate(PublishedValueCandidate candidate) {
        if (registeredAddressRejection != null) {
            return Optional.of(registeredAddressRejection);
        }
        return switch (candidate) {
            case SystemPair pair -> validateSystemPair(pair);
            case MemberDid memberDid -> validateMemberDid(memberDid);
        };
    }

    /**
     * @param storedDid a stored member DID
     * @return whether its host is not the registered address, or the registered address cannot be used
     */
    public boolean isMemberDidStale(String storedDid) {
        return !didHostMatches(storedDid);
    }

    /**
     * @param storedDid        the stored SYSTEM DID
     * @param storedDspBaseUrl the stored DSP base URL
     * @return whether the host of either is not the registered address, or the registered address cannot be used
     */
    public boolean isSystemPairStale(String storedDid, String storedDspBaseUrl) {
        return !didHostMatches(storedDid) || !dspBaseUrlHostMatches(storedDspBaseUrl);
    }

    private Optional<PublishedValueRejection> validateSystemPair(SystemPair pair) {
        var shape = checkShape(pair.did());
        if (shape.isPresent()) {
            return shape;
        }
        var participant = decode(pair.did());
        if (participant.isEmpty()) {
            return Optional.of(isDecodableWithCanonicalEncoding(pair.did()) ? NOT_CANONICAL : FOREIGN_SYSTEM_DID);
        }
        var authority = checkAuthority(participant.get().ssHost());
        if (authority.isPresent()) {
            return authority;
        }
        if (!(participant.get() instanceof SystemParticipant)) {
            return Optional.of(FOREIGN_SYSTEM_DID);
        }
        return checkDspBaseUrl(pair.dspBaseUrl());
    }

    private Optional<PublishedValueRejection> validateMemberDid(MemberDid memberDid) {
        var shape = checkShape(memberDid.did());
        if (shape.isPresent()) {
            return shape;
        }
        var participant = decode(memberDid.did());
        if (participant.isEmpty()) {
            return Optional.of(isDecodableWithCanonicalEncoding(memberDid.did()) ? NOT_CANONICAL : UNRELATED_MEMBER);
        }
        var authority = checkAuthority(participant.get().ssHost());
        if (authority.isPresent()) {
            return authority;
        }
        if (!(participant.get() instanceof MemberParticipant decoded)
                || !decoded.member().equals(memberDid.member())
                || !isOwnerOrClient(memberDid.member())) {
            return Optional.of(UNRELATED_MEMBER);
        }
        return Optional.empty();
    }

    private boolean isOwnerOrClient(ClientId member) {
        return member.equals(context.owner()) || context.clientMembers().contains(member);
    }

    private static Optional<PublishedValueRejection> checkShape(String did) {
        if (did == null || !did.startsWith(DID_PREFIX)) {
            return Optional.of(WRONG_SCHEME);
        }
        var segments = did.substring(DID_PREFIX.length()).split(":", -1);
        if (segments.length < MIN_DID_SEGMENT_COUNT
                || !ParticipantIdentifierScheme.SCHEME_VERSION.equals(segments[VERSION_SEGMENT_INDEX])) {
            return Optional.of(WRONG_SCHEME);
        }
        if (did.length() > MAX_DID_LENGTH) {
            return Optional.of(DID_TOO_LONG);
        }
        if (!DidAuthority.isAscii(segments[HOST_SEGMENT_INDEX])) {
            return Optional.of(NON_ASCII_HOST);
        }
        return Optional.empty();
    }

    private Optional<PublishedValueRejection> checkAuthority(String authority) {
        if (!DidAuthority.isCanonical(authority)) {
            return Optional.of(NOT_CANONICAL);
        }
        return hostMatches(authority) ? Optional.empty() : Optional.of(HOST_DIFFERS);
    }

    private Optional<PublishedValueRejection> checkDspBaseUrl(String url) {
        if (url == null || !startsWithHttps(url)) {
            return Optional.of(DSP_BASE_URL_NOT_HTTPS);
        }
        var authority = authorityOf(url);
        if (!DidAuthority.isAscii(authority)) {
            return Optional.of(NON_ASCII_HOST);
        }
        if (authority.indexOf('@') >= 0 || url.indexOf('?') >= 0 || url.indexOf('#') >= 0
                || canonicalHostOf(authority).isEmpty() || !DidAuthority.isUriHost(authority)
                || !isWellFormedUri(url)) {
            return Optional.of(MALFORMED_DSP_BASE_URL);
        }
        if (!url.startsWith(DSP_BASE_URL_PREFIX) || !hasCanonicalHost(authority)) {
            return Optional.of(NOT_CANONICAL);
        }
        return hostMatches(authority) ? Optional.empty() : Optional.of(HOST_DIFFERS);
    }

    private static boolean hasCanonicalHost(String authority) {
        var parsed = DidAuthority.parse(authority);
        var port = parsed.port().isPresent() ? ":" + parsed.port().getAsInt() : "";
        return authority.equals(DidAuthority.canonicalHost(parsed.host()) + port);
    }

    private boolean didHostMatches(String did) {
        return registeredHost != null && did != null && decode(did).map(DecodedParticipant::ssHost)
                .filter(this::hostMatches).isPresent();
    }

    private boolean dspBaseUrlHostMatches(String url) {
        return registeredHost != null && url != null && startsWithHttps(url) && hostMatches(authorityOf(url));
    }

    private boolean hostMatches(String authority) {
        return canonicalHostOf(authority).filter(host -> host.equals(registeredHost)).isPresent();
    }

    private static boolean startsWithHttps(String url) {
        return url.regionMatches(true, 0, DSP_BASE_URL_PREFIX, 0, DSP_BASE_URL_PREFIX.length());
    }

    private static String authorityOf(String url) {
        var afterScheme = url.substring(DSP_BASE_URL_PREFIX.length());
        int end = IntStream.range(0, afterScheme.length())
                .filter(i -> "/?#".indexOf(afterScheme.charAt(i)) >= 0)
                .findFirst()
                .orElse(afterScheme.length());
        return afterScheme.substring(0, end);
    }

    private static boolean isWellFormedUri(String url) {
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException _) {
            return false;
        }
    }

    private static boolean isDecodableWithCanonicalEncoding(String did) {
        var canonical = ParticipantIdentifierScheme.canonicalPercentEncoding(did);
        return !canonical.equals(did) && decode(canonical).isPresent();
    }

    private static Optional<DecodedParticipant> decode(String did) {
        try {
            return Optional.of(ParticipantIdentifierScheme.decodeDid(did));
        } catch (XrdRuntimeException _) {
            return Optional.empty();
        }
    }

    private static Optional<String> canonicalHostOf(String authority) {
        try {
            return Optional.of(DidAuthority.canonicalHost(DidAuthority.parse(authority).host()));
        } catch (XrdRuntimeException _) {
            return Optional.empty();
        }
    }
}
