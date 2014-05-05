package ee.cyber.sdsb.proxy.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.HttpClient;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;

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
    }

    /**
     * Saves the current configurations in thread local storage, to protect
     * against configuration reloads during message processing.
     */
    protected void cacheConfigurationForCurrentThread() {
        GlobalConf.initForCurrentThread();
        ServerConf.initForCurrentThread();
        KeyConf.initForCurrentThread();
    }

    /** Returns a new instance of http sender. */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient);
    }

    /** Called when request message has been successfully processed. */
    protected void onSuccess(SoapMessageImpl message) {
    }

    /** Processes the incoming message. */
    protected abstract void process() throws Exception;
}
