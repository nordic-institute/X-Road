/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.TimeBasedObjectCache;
import ee.ria.xroad.common.identifier.ClientId;

import java.util.List;

/**
 * Caching implementation for ServerConf
 * The long lasting and frequently used operations
 * getTspUrl, getMemberStatus and getIsAuthentication are cached
 * Performance improvent was measured to be significant.
 */
public class CachingServerConfImpl extends ServerConfImpl {

    public static final String TSP_URL = "tsp_url";
    public static final String MEMBERS = "members";
    public static final String MEMBER_STATUS = "member_status";
    public static final String AUTHENTICATION = "authentication";
    public static final String IDENTIFIER = "identifier";


    private final int expireSeconds;
    private final TimeBasedObjectCache cache;

    /**
     * Constructor, creates time based object cache with expireSeconds paramter
     */
    public CachingServerConfImpl() {
        super();
        expireSeconds = SystemProperties.getServerConfCachePeriod();
        cache = new TimeBasedObjectCache(expireSeconds);
    }

    @Override
    public synchronized List<String> getTspUrl() {
        if (!cache.isValid(TSP_URL)) {
            cache.setValue(TSP_URL, super.getTspUrl());
        }

        return (List<String>)cache.getValue(TSP_URL);
    }

    @Override
    public synchronized String getMemberStatus(ClientId clientId) {
        String key = MEMBER_STATUS + clientId;
        if (!cache.isValid(key)) {
            cache.setValue(key, super.getMemberStatus(clientId));
        }
        return (String)cache.getValue(key);
    }

    @Override
    public synchronized IsAuthentication getIsAuthentication(ClientId clientId) {
        String key = AUTHENTICATION + clientId;
        if (!cache.isValid(key)) {
            cache.setValue(key, super.getIsAuthentication(clientId));
        }
        return (IsAuthentication)cache.getValue(key);
    }
}
