package ee.cyber.xroad.proxy.securelog;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.tsp.TimeStampResp;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.XmlUtils;
import ee.cyber.xroad.common.signature.SignatureManifest;
import ee.cyber.xroad.common.signature.TimestampVerifier;

/** Requests timestamp for the specified records and logs them. */
class Timestamper implements Runnable {

    private static final Logger LOG =
            LoggerFactory.getLogger(Timestamper.class);

    private List<TodoRecord> todoList;
    private List<String> tspUrls;

    Timestamper(List<TodoRecord> todoList, List<String> tspUrls) {
        this.todoList = todoList;
        this.tspUrls = tspUrls;
    }

    @Override
    public void run() {
        LOG.trace("Timestamper started");

        List<Long> numbers = new ArrayList<>();
        for (TodoRecord todo : todoList) {
            numbers.add(todo.getNr());
        }

        LOG.debug("Asking timestamps for records {}", numbers);

        try {
            Element tsRootManElement = getTsRootManifestElement();
            String tsRootManXml = XmlUtils.toXml(tsRootManElement);
            LOG.trace("timestamp root manifest XML:\n{}", tsRootManXml);
            String hashAlg = LogManager.getTsManifestHashAlg();
            byte[] tsRequestData = XmlUtils.canonicalize(
                    LogManager.getC14nMethodUri(),
                    tsRootManElement);
            LOG.trace("timestamp request XML input is calculated");
            TimeStampRequest tsRequest = createTimestampRequest(tsRequestData);
            LOG.trace("timestamp request created");
            InputStream tsIn = makeTsRequest(tsRequest);
            LOG.trace("timestamp request executed");
            byte[] timestampDER = getTimestampDer(tsRequest, tsIn);

            LogManager.log(new TimestampRecord(numbers, tsRootManXml, hashAlg,
                    timestampDER));
        } catch (Exception e) {
            LOG.error("Error in Timestamper", e);
            try {
                LogManager.queue(new LogManager.TimestampFailed(todoList));
            } catch (InterruptedException e1) {
                LOG.error("Error queueing task to mark records " + numbers
                        + " not in process", e1);
            }
        }
        LOG.trace("Timestamper finished");
    }

    private Element getTsRootManifestElement() throws Exception {
        SignatureManifest tsMan = new SignatureManifest();

        for (TodoRecord todo : todoList) {
            tsMan.addReference("#" + todo.getTsManifestId(),
                    todo.getTsManifestDigestMethod(),
                    todo.getTsManifestDigest());
        }

        return tsMan.createXmlElement("ts-root-manifest");
    }

    /*
     * Example code of getting timestamp:
     * http://bouncy-castle.1462172.n4.nabble.com/Timestamp-request-and-response-td1558231.html
     */

    /**
     * Creates and returns a time stamp request for the specified data.
     */
    private TimeStampRequest createTimestampRequest(byte[] tsRequestData)
            throws Exception {
        LOG.trace("createTimestampRequest({})", new String(tsRequestData));
        TimeStampRequestGenerator reqgen = new TimeStampRequestGenerator();
        String tsaHashAlg = LogManager.getTsaHashAlg();
        byte[] digest = CryptoUtils.calculateDigest(tsaHashAlg, tsRequestData);

        return reqgen.generate(CryptoUtils.getAlgorithmIdentifier(tsaHashAlg)
                .getAlgorithm(), digest, getTsRequestNonce());
    }

    private BigInteger getTsRequestNonce() {
        return BigInteger.valueOf((long) (Math.random() * 100000000000000000L));
    }

    private InputStream makeTsRequest(TimeStampRequest req) throws Exception {
        for (String url: tspUrls) {
            try {
                LOG.debug("Sending time-stamp request to {}", url);
                return makeTsRequest(req, url);
            } catch (Exception ex) {
                LOG.error("Failed to get time stamp from {}", url, ex);
                // We try again with the next URL.
            }
        }

        // All the URLs failed. Throw exception.
        throw new RuntimeException(
                "Failed to get time stamp from any time-stamping providers");
    }

    private InputStream makeTsRequest(TimeStampRequest req, String tspUrl)
            throws Exception {
        byte[] request = req.getEncoded();
        URL url = new URL(tspUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setDoOutput(true);
        con.setDoInput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-type", "application/timestamp-query");

        con.setRequestProperty("Content-length", String.valueOf(request.length));
        OutputStream out = con.getOutputStream();
        out.write(request);
        out.flush();

        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Received HTTP error: "
                    + con.getResponseCode() + " - " + con.getResponseMessage());
        }

        return con.getInputStream();
    }

    private byte[] getTimestampDer(TimeStampRequest req, InputStream in)
            throws Exception {
        TimeStampResp resp = TimeStampResp.getInstance(new ASN1InputStream(in)
                .readObject());
        BigInteger status = resp.getStatus().getStatus();
        LOG.trace("getTimestampDer() - TimeStampResp.status: {}", status);
        if (!PKIStatus.granted.getValue().equals(status)
                && !PKIStatus.grantedWithMods.getValue().equals(status)) {
            PKIFreeText statusString = resp.getStatus().getStatusString();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < statusString.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"" + statusString.getStringAt(i) + "\"");
            }
            LOG.error("getTimestampDer() - TimeStampResp.status is not "
                    + "\"granted\" neither \"grantedWithMods\": {}, {}",
                    status, sb);
            throw new RuntimeException("TimeStampResp.status: " + status
                    + ", .statusString: " + sb);
        }
        TimeStampResponse response = new TimeStampResponse(resp);
        TimeStampToken token = response.getTimeStampToken();

        response.validate(req);
        TimestampVerifier.verify(token, GlobalConf.getTspCertificates());

        return token.getEncoded();
    }
}
