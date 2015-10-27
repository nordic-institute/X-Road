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
