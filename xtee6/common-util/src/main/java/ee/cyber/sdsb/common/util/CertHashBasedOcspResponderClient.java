package ee.cyber.sdsb.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.HttpSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.PortNumbers;

public class CertHashBasedOcspResponderClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(CertHashBasedOcspResponderClient.class);

    private static final String METHOD = "GET";
    private static final String CERT_PARAM = "cert";

    public static List<OCSPResp> getOcspResponsesFromServer(
            String providerAddress, List<String> hashes)
                    throws IOException, OCSPException {
        URL url = createUrl(providerAddress, hashes);

        LOG.debug("Getting OCSP responses for hashes ({}) from: {}",
                hashes.toString(), url.getHost());

        return getOcspResponsesFromServer(url);
    }

    /**
     * Creates an GET request to the internal cert hash based OCSP responder
     * and expects an OCSP responses.
     */
    public static List<OCSPResp> getOcspResponsesFromServer(URL destination)
            throws IOException, OCSPException {
        HttpURLConnection connection =
                (HttpURLConnection) destination.openConnection();
        connection.setRequestProperty("Accept", MimeTypes.MULTIPART_RELATED);
        connection.setDoOutput(true);
        connection.setConnectTimeout(20000);
        connection.setRequestMethod(METHOD);
        connection.connect();

        if (connection.getResponseCode() / 100 != 2) {
            LOG.error("Invalid HTTP response ({}) from responder: {}",
                    connection.getResponseCode(),
                    connection.getResponseMessage());
            throw new IOException(connection.getResponseMessage());
        }

        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(connection.getContentType());

        final List<OCSPResp> responses = new ArrayList<>();
        final MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentHandler(new AbstractContentHandler() {
            @Override
            public void startMultipart(BodyDescriptor bd) {
                parser.setFlat();
            }
            @Override
            public void body(BodyDescriptor bd, InputStream is)
                    throws MimeException, IOException {
                if (bd.getMimeType().equalsIgnoreCase(
                        MimeTypes.OCSP_RESPONSE)) {
                    responses.add(new OCSPResp(IOUtils.toByteArray(is)));
                }
            }
        });

        try {
            parser.parse(connection.getInputStream());
        } catch (MimeException e) {
            throw new OCSPException("Error parsing response", e);
        }

        return responses;
    }

    public static URL createUrl(String providerAddress, List<String> hashes)
            throws MalformedURLException {
        return new URL(HttpSchemes.HTTP, providerAddress,
                PortNumbers.PROXY_OCSP_PORT, "/?" + CERT_PARAM + "="
                        + StringUtils.join(hashes, "&" + CERT_PARAM + "="));
    }
}
