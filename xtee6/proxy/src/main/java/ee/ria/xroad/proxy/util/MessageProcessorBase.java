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
package ee.ria.xroad.proxy.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.HttpClient;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.proxy.conf.KeyConf;

/**
 * Base class for message processors.
 */
public abstract class MessageProcessorBase {

    /** The servlet request. */
    protected final HttpServletRequest servletRequest;

    /** The servlet response. */
    protected final HttpServletResponse servletResponse;

    /** The http client instance. */
    protected final HttpClient httpClient;

    protected MessageProcessorBase(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, HttpClient httpClient) {
        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        this.httpClient = httpClient;

        cacheConfigurationForCurrentThread();
    }

    /**
     * Saves the current configurations in thread local storage, to protect
     * against configuration reloads during message processing.
     */
    private void cacheConfigurationForCurrentThread() {
        GlobalConf.initForCurrentThread();
        GlobalConf.verifyValidity();

        KeyConf.initForCurrentThread();
    }

    /**
     * Returns a new instance of http sender.
     */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient);
    }

    /**
     * Called when processing started.
     */
    protected void preprocess() throws Exception {
    }

    /**
     * Called when processing successfully completed.
     */
    protected void postprocess() throws Exception {
    }

    /**
     * Processes the incoming message.
     * @throws Exception in case of any errors
     */
    public abstract void process() throws Exception;

    /**
     * @return MessageInfo object for the request message being processed
     */
    public abstract MessageInfo createRequestMessageInfo();
}
