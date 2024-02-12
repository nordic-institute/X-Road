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
package org.niis.xroad.edc.sig.jades;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.TimestampBinary;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.tsp.TimeStampResp;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;

import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmIdentifier;

@Slf4j
public class DssTspSource implements TSPSource {
    private static final String TSP_URL = "http://cs:8899";
    private static final int DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT = 20000;
    private static final int DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT = 60000;

    @Override
    public TimestampBinary getTimeStampResponse(DigestAlgorithm digestAlgorithm, byte[] bytes) throws DSSException {

        try {
            long execStart = System.currentTimeMillis();

            var request = makeTsRequest(createTimestampRequest(digestAlgorithm.getJavaName(), bytes), TSP_URL);
            var response = getTimestampResponse(request);

            log.info("TSP request took {} ms", System.currentTimeMillis() - execStart);
            return new TimestampBinary(response.getEncoded());

        } catch (Exception e) {
            throw new DSSException(e);
        }
    }

    private TimeStampRequest createTimestampRequest(String tsaHashAlg, byte[] data)
            throws Exception {
        TimeStampRequestGenerator reqgen = new TimeStampRequestGenerator();


        log.trace("Creating time-stamp request (algorithm: {})", tsaHashAlg);

        byte[] digest = calculateDigest(tsaHashAlg, data);

        ASN1ObjectIdentifier algorithm =
                getAlgorithmIdentifier(tsaHashAlg).getAlgorithm();

        return reqgen.generate(algorithm, digest);
    }

    public static InputStream makeTsRequest(TimeStampRequest req, String tspUrl) throws Exception {
        byte[] request = req.getEncoded();

        URL url = new URL(tspUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setDoOutput(true);
        con.setDoInput(true);
        con.setConnectTimeout(DEFAULT_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT);
        con.setReadTimeout(DEFAULT_TIMESTAMPER_CLIENT_READ_TIMEOUT);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-type", "application/timestamp-query");
        con.setRequestProperty("Content-length", String.valueOf(request.length));

        OutputStream out = con.getOutputStream();
        out.write(request);
        out.flush();

        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            con.disconnect();
            throw new RuntimeException("Received HTTP error: " + con.getResponseCode() + " - "
                    + con.getResponseMessage());
        } else if (con.getInputStream() == null) {
            con.disconnect();
            throw new IOException("Could not get response from TSP");
        }

        return con.getInputStream();
    }

    static TimeStampResponse getTimestampResponse(InputStream in) throws Exception {
        TimeStampResp response = TimeStampResp.getInstance(new ASN1InputStream(in).readObject());

        if (response == null) {
            throw new RuntimeException("Could not read time-stamp response");
        }

        BigInteger status = response.getStatus().getStatus();

        log.trace("getTimestampDer() - TimeStampResp.status: {}", status);

        if (!PKIStatus.granted.getValue().equals(status)
                && !PKIStatus.grantedWithMods.getValue().equals(status)) {
            PKIFreeText statusString = response.getStatus().getStatusString();

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < statusString.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append("\"" + statusString.getStringAt(i) + "\"");
            }

            log.error("getTimestampDer() - TimeStampResp.status is not "
                    + "\"granted\" neither \"grantedWithMods\": {}, {}", status, sb);

            throw new RuntimeException("TimeStampResp.status: " + status + ", .statusString: " + sb);
        }

        return new TimeStampResponse(response);
    }
}
