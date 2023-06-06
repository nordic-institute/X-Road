/**
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
package org.niis.xroad.restapi.auth;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AuthenticationIpWhitelistTest {

    private static final String EXAMPLE_IPV4_ADDRESS = "1.2.3.4";
    private static final String EXAMPLE_IPV6_ADDRESS = "fd42:2e81:e4e3:7e70:216:3eff:fe9d:7ca";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "::1";

    @Test
    public void testAllowAll() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty("0.0.0.0/0");
        whitelist.validateIpAddress(EXAMPLE_IPV4_ADDRESS);
        assertInvalidIp(EXAMPLE_IPV6_ADDRESS, whitelist);
    }

    @Test
    public void testAllowAllIpV6() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty("::/0");
        whitelist.validateIpAddress(EXAMPLE_IPV6_ADDRESS);
        assertInvalidIp(EXAMPLE_IPV4_ADDRESS, whitelist);
    }

    @Test
    public void testAllowLocalhost() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty("127.0.0.1/8");
        whitelist.validateIpAddress(LOCALHOST_IPV4);
        whitelist.validateIpAddress("127.0.0.255");
        assertInvalidIp("128.0.0.0", whitelist);
        assertInvalidIp(LOCALHOST_IPV6, whitelist);
    }

    @Test
    public void testAllowLocalhostIpV6() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty(LOCALHOST_IPV6);
        whitelist.validateIpAddress(LOCALHOST_IPV6);
        assertInvalidIp(LOCALHOST_IPV4, whitelist);
        assertInvalidIp(EXAMPLE_IPV6_ADDRESS, whitelist);
    }

    @Test
    public void testAllowRange() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty("10.0.0.0/24");
        whitelist.validateIpAddress("10.0.0.0");
        whitelist.validateIpAddress("10.0.0.1");
        whitelist.validateIpAddress("10.0.0.254");
        whitelist.validateIpAddress("10.0.0.255");
        assertInvalidIp("10.0.1.0", whitelist);
        assertInvalidIp("11.0.0.0", whitelist);
        assertInvalidIp(EXAMPLE_IPV6_ADDRESS, whitelist);
    }

    @Test
    public void testAllowRangeIpV6() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        whitelist.setWhitelistEntriesProperty("2001:db8::/48");
        whitelist.validateIpAddress("2001:0DB8:0000:0000:0000:0000:0000:0000");
        whitelist.validateIpAddress("2001:0DB8:0000:FFFF:FFFF:FFFF:FFFF:FFFF");
        assertInvalidIp("2001:0DB7:0000:FFFF:FFFF:FFFF:FFFF:FFFF", whitelist);
        assertInvalidIp("2001:0DB9:0000:0000:0000:0000:0000:0000", whitelist);
        assertInvalidIp(EXAMPLE_IPV4_ADDRESS, whitelist);
    }

    @Test
    public void testTrimEntriesProperty() throws Exception {
        AuthenticationIpWhitelist whitelist1 = new AuthenticationIpWhitelist();
        whitelist1.setWhitelistEntriesProperty("2001:db8::/48,,,,  127.0.0.1  ");
        Set<String> entries = new HashSet<>(Arrays.asList("2001:db8::/48", "127.0.0.1"));
        AuthenticationIpWhitelist whitelist2 = new AuthenticationIpWhitelist();
        whitelist2.setWhitelistEntries(entries);
        assertEquals(Sets.newHashSet(whitelist1.getWhitelistEntries()),
                Sets.newHashSet(whitelist2.getWhitelistEntries()));
        assertEquals(entries, Sets.newHashSet(whitelist2.getWhitelistEntries()));
    }

    @Test
    public void testValidateEntriesProperty() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        try {
            whitelist.setWhitelistEntries(Arrays.asList("256.0.0.1"));
            fail("should throw exception");
        } catch (IllegalArgumentException expected) {
        }
        try {
            whitelist.setWhitelistEntriesProperty("127.0.0.1, foobar");
            fail("should throw exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUnitializedWhitelist() throws Exception {
        AuthenticationIpWhitelist whitelist = new AuthenticationIpWhitelist();
        assertTrue(Iterables.size(whitelist.getWhitelistEntries()) == 0);
        assertInvalidIp(LOCALHOST_IPV4, whitelist);
    }

    private void assertInvalidIp(String ip, AuthenticationIpWhitelist whitelist) {
        try {
            whitelist.validateIpAddress(ip);
            fail("should throw exception");
        } catch (AuthenticationIpWhitelist.BadRemoteAddressException expected) {
        }
    }
}
