/**
 * The MIT License
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Object that knows an IP whitelist and can validate authentication requests against the whitelist
 */
@Slf4j
@Configuration
public class AuthenticationIpWhitelist {

    public static final String KEY_MANAGEMENT_API_WHITELIST = "keyManagementWhitelist";
    public static final String REGULAR_API_WHITELIST = "regularWhitelist";
    private static final String VALID_IP_ADDRESS = "127.0.0.1";

    private Iterable<String> whitelistEntries;

    public AuthenticationIpWhitelist() {
    }

    public AuthenticationIpWhitelist setWhitelistEntries(Iterable<String> entries) {
        validateWhitelistEntries(entries);
        this.whitelistEntries = entries;
        return this;
    }

    @Bean(KEY_MANAGEMENT_API_WHITELIST)
    public AuthenticationIpWhitelist keyManagementWhitelist() {
        AuthenticationIpWhitelist authenticationIpWhitelist = new AuthenticationIpWhitelist();
        authenticationIpWhitelist.setWhitelistEntries(readKeyManagementWhitelistProperties());
        return authenticationIpWhitelist;
    }

    @Bean(REGULAR_API_WHITELIST)
    public AuthenticationIpWhitelist regularWhitelist() {
        AuthenticationIpWhitelist authenticationIpWhitelist = new AuthenticationIpWhitelist();
        authenticationIpWhitelist.setWhitelistEntries(readRegularWhitelistProperties());
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

    // TO DO: test
    /**
     * TO DO: proper comments
     * If ipLimits = true, go through the whitelisted ips and check that one of them matches
     * caller remote address. If not, throw BadRemoteAddressException
     * @param authentication
     * @throws BadRemoteAddressException if caller ip was not allowed for this authentication provider
     */
    public void validateIpAddress(Authentication authentication) {
        WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
        String userIp = details.getRemoteAddress();
        for (String whitelistEntry : whitelistEntries) {
            if (new IpAddressMatcher(whitelistEntry).matches(userIp)) {
                return;
            }
        }
        throw new BadRemoteAddressException("Invalid IP Address " + userIp);
    }

    public static class BadRemoteAddressException extends AuthenticationException {
        public BadRemoteAddressException(String msg) {
            super(msg);
        }
    }

    private static Iterable<String> readKeyManagementWhitelistProperties() {
        String whitelist = SystemProperties.getKeyManagementApiWhitelist();
        return parseWhitelist(whitelist);
    }

    // TO DO: test
    private static Iterable<String> parseWhitelist(String whitelist) {
        return Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(whitelist);
    }

    private static Iterable<String> readRegularWhitelistProperties() {
        String whitelist = SystemProperties.getRegularApiWhitelist();
        return parseWhitelist(whitelist);
    }
}
