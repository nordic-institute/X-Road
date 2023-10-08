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
package org.niis.xroad.restapi.auth;

import ee.ria.xroad.common.SystemProperties;

import com.google.common.base.Splitter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;

/**
 * Object that knows an IP whitelist and can validate authentication requests against the whitelist.
 * Whitelist can contain individial IP addresses such as 192.168.1.1, or CIDR notation representations
 * such as 192.168.1.0/24.
 * Whitelist can contain ipv4 or ipv6 items, for example 127.0.0.0/8 and ::1
 **/
@Slf4j
@Configuration
public class AuthenticationIpWhitelist {

    public static final String KEY_MANAGEMENT_API_WHITELIST = "keyManagementWhitelist";
    public static final String REGULAR_API_WHITELIST = "regularWhitelist";
    private static final String VALID_IP_ADDRESS = "127.0.0.1";

    @Getter
    private Iterable<String> whitelistEntries;

    /**
     * Constructor. Whitelist is initially empty, so it will block all ip addresses.
     * Use {@link AuthenticationIpWhitelist#setWhitelistEntries(Iterable)} or
     * Use {@link AuthenticationIpWhitelist#setWhitelistEntriesProperty(String)} to set whitelist items.
     */
    public AuthenticationIpWhitelist() {
        whitelistEntries = new ArrayList<>();
    }

    /**
     * Sets whitelisted ips from a comma-separated String.
     * Entries are trimmed for whitespace.
     * @param entriesProperty
     * @throws IllegalArgumentException if entriesProperty contains invalid entries
     */
    public AuthenticationIpWhitelist setWhitelistEntriesProperty(String entriesProperty) {
        Iterable<String> entries = parseWhitelist(entriesProperty);
        setWhitelistEntries(entries);
        return this;
    }

    /**
     * Sets whitelisted ips from an interable containing whitelist entries.
     * Entries are not trimmed for whitespace.
     * @param entries
     * @throws IllegalArgumentException if entries contains invalid entries
     */
    public AuthenticationIpWhitelist setWhitelistEntries(Iterable<String> entries) {
        validateWhitelistEntries(entries);
        this.whitelistEntries = entries;
        return this;
    }

    @Bean(KEY_MANAGEMENT_API_WHITELIST)
    public AuthenticationIpWhitelist keyManagementWhitelist() {
        AuthenticationIpWhitelist authenticationIpWhitelist = new AuthenticationIpWhitelist();
        authenticationIpWhitelist.setWhitelistEntriesProperty(SystemProperties.getKeyManagementApiWhitelist());
        return authenticationIpWhitelist;
    }

    @Bean(REGULAR_API_WHITELIST)
    public AuthenticationIpWhitelist regularWhitelist() {
        AuthenticationIpWhitelist authenticationIpWhitelist = new AuthenticationIpWhitelist();
        authenticationIpWhitelist.setWhitelistEntriesProperty(SystemProperties.getRegularApiWhitelist());
        return authenticationIpWhitelist;
    }

    /**
     * Validator for failing fast object creation
     * @throws IllegalArgumentException if whitelist contains invalid entries
     */
    private void validateWhitelistEntries(Iterable<String> entries) {
        entries.forEach(entry -> new IpAddressMatcher(entry).matches(VALID_IP_ADDRESS));
        for (String entry: entries) {
            new IpAddressMatcher(entry).matches(VALID_IP_ADDRESS);
        }
    }

    /**
     * Validates given authentication object against the IP whitelist.
     * Caller's remote address is compared to IP whitelist.
     * If whitelist blocks access, {@link BadRemoteAddressException} is thrown
     * @param authentication
     * @throws BadRemoteAddressException if caller ip was not allowed in this whitelist
     */
    public void validateIpAddress(Authentication authentication) {
        WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
        String userIp = details.getRemoteAddress();
        validateIpAddress(userIp);
    }

    /**
     * Validates given IP address against the IP whitelist.
     * For testability, use {@link AuthenticationIpWhitelist#validateIpAddress(Authentication)}
     * otherwise
     * @param ipAddress
     * @throws BadRemoteAddressException if caller ip was not allowed in this whitelist
     */
    void validateIpAddress(String ipAddress) {
        for (String whitelistEntry : whitelistEntries) {
            if (new IpAddressMatcher(whitelistEntry).matches(ipAddress)) {
                return;
            }
        }
        throw new BadRemoteAddressException("Invalid IP Address " + ipAddress);
    }

    public static class BadRemoteAddressException extends AuthenticationException {
        public BadRemoteAddressException(String msg) {
            super(msg);
        }
    }

    private static Iterable<String> parseWhitelist(String whitelist) {
        return Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(whitelist);
    }

}
