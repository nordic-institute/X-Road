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
package org.niis.xroad.cs.admin.api.domain;

/**
 * Why a Security Server's published value is refused. Each validation rule and each storage rule has its
 * own reason.
 */
public enum ParticipantValueRejection {
    /** The DID is not a {@code did:web} DID of scheme version {@code v1}. */
    WRONG_SCHEME,
    /** The DID is longer than 4000 characters. */
    DID_TOO_LONG,
    /** The authority in the DID is not in its canonical form. */
    NOT_CANONICAL,
    /** The host in the DID or the DSP base URL is not the registered address. */
    HOST_DIFFERS,
    /** The registered address cannot be read as a host. */
    UNPARSEABLE_REGISTERED_ADDRESS,
    /** The host in the DID or the DSP base URL, or the registered address, is not ASCII. */
    NON_ASCII_HOST,
    /** The DSP base URL does not use {@code https}. */
    DSP_BASE_URL_NOT_HTTPS,
    /** The DSP base URL is not a URL, or has an invalid port, user info, a query or a fragment. */
    MALFORMED_DSP_BASE_URL,
    /** The member DID has the shape of a DID of scheme version {@code v1} but cannot be decoded. */
    UNDECODABLE_DID,
    /** The member DID decodes to a participant other than the claimed member: another member or a SYSTEM participant. */
    DID_OF_OTHER_MEMBER,
    /** The claimed member is neither the owner nor a registered client of the server. */
    MEMBER_NOT_SERVED,
    /** The SYSTEM DID is not the server's own {@code v1:system} DID. */
    FOREIGN_SYSTEM_DID,
    /** The stored value is current for the registered address and differs from the published one. */
    VALUE_DIFFERS,
    /** The DID is stored for another Security Server. */
    DID_HELD_BY_ANOTHER_SERVER
}
