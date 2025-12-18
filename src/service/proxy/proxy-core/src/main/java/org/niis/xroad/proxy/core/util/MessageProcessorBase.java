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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

/**
 * Base class for message processors.
 */
@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public abstract class MessageProcessorBase {
    protected final ProxyProperties proxyProperties;
    protected final GlobalConfProvider globalConfProvider;
    protected final ServerConfProvider serverConfProvider;
    protected final ClientAuthenticationService clientAuthenticationService;

    protected final OpMonitoringDataHelper opMonitoringDataHelper;

    /**
     * The servlet request.
     */
    protected final RequestWrapper jRequest;

    /**
     * The servlet response.
     */
    protected final ResponseWrapper jResponse;

    /**
     * The http client instance.
     */
    protected final HttpClient httpClient;

    protected MessageProcessorBase(RequestWrapper request, ResponseWrapper response,
                                   ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                   ServerConfProvider serverConfProvider, ClientAuthenticationService clientAuthenticationService,
                                   HttpClient httpClient) {
        this.proxyProperties = proxyProperties;
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.jRequest = request;
        this.jResponse = response;
        this.httpClient = httpClient;
        this.clientAuthenticationService = clientAuthenticationService;

        this.opMonitoringDataHelper = new OpMonitoringDataHelper(globalConfProvider, serverConfProvider);
        this.globalConfProvider.verifyValidity();
    }

    /**
     * Returns a new instance of http sender.
     */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient, proxyProperties.clientProxy().poolEnableConnectionReuse());
    }

    /**
     * Called when processing started.
     */
    protected void preprocess() {
    }

    /**
     * Called when processing successfully completed.
     */
    protected void postprocess() {
    }

    /**
     * Processes the incoming message.
     *
     * @throws Exception in case of any errors
     */
    public abstract void process() throws Exception;

    /**
     * Check that message transfer was successful.
     */
    public boolean verifyMessageExchangeSucceeded() {
        return true;
    }

}
