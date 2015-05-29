package ee.ria.xroad.common.util;

import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates the sending and receiving of content via HTTP POST
 * method synchronously.
 */
public class HttpSender extends AbstractHttpSender {

    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);

    private final HttpClient client;

    /**
     * Configures a HTTP sender using the given HTTP client.
     * @param httpClient HTTP client this sender should use
     */
    public HttpSender(HttpClient httpClient) {
        client = httpClient;
    }

    /**
     * Sends data using POST method to some address.
     * Method blocks until response becomes available, after which
     * {@link #getResponseContent()} and {@link #getResponseContentType()}
     * can be used to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, String content, String contentType)
            throws Exception {
        LOG.trace("doPost(address = {}, timeout = {})", address, timeout);

        HttpPost post = new HttpPost(address);
        post.setConfig(getRequestConfig());
        post.setEntity(createStringEntity(content, contentType));

        doRequest(post);
    }

    /**
     * Sends an input stream of data using POST method to some address.
     * Method blocks until response becomes available, after which
     * {@link #getResponseContent()} and {@link #getResponseContentType()}
     * can be used to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentLength length of the content in bytes
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, InputStream content, long contentLength,
            String contentType) throws Exception {
        LOG.trace("doPost(address = {}, timeout = {})", address, timeout);

        HttpPost post = new HttpPost(address);
        post.setConfig(getRequestConfig());
        post.setEntity(createInputStreamEntity(content, contentLength,
                contentType));

        doRequest(post);
    }

    @Override
    public void doGet(URI address) throws Exception {
        LOG.trace("doGet(address = {}, timeout = {})", address, timeout);

        HttpGet get = new HttpGet(address);
        get.setConfig(getRequestConfig());

        doRequest(get);
    }

    private void doRequest(HttpRequestBase request) throws Exception {
        this.request = request;

        addAdditionalHeaders();
        try {
            HttpResponse response = client.execute(request, context);
            handleResponse(response);
        } catch (Exception ex) {
            LOG.debug("Request failed", ex);
            request.abort();
            throw ex;
        }
    }
}
