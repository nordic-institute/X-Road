/*
 *  The MIT License
 *  Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.common.api.throttle;

public interface IpThrottlingFilterConfig {

    /**
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send second
     * one management request, so this value can be low.
     */
    int getRateLimitRequestsPerSecond();

    /**
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send just
     * one management request, so this value can be low.
     * To disable this feature, set this value to -1.
     */
    int getRateLimitRequestsPerMinute();

    /**
     * Controls how many IP addresses can be remembered in the rate-limit cache
     * Tradeoff between memory usage and protection from a large attack.
     * To disable this feature, set this value to -1.
     */
    int getRateLimitCacheSize();

    int getRateLimitExpireAfterAccessMinutes();
}
