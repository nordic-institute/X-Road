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
package org.niis.xroad.proxy.core.messagelog;

import ee.ria.xroad.common.messagelog.MessageLogProperties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.tsp.TimeStampResp;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.READING_TIMESTAMP_RESPONSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMP_NON_GRANTED_RESPONSE;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMP_RESPONSE_OBJECT_CREATION_FAILED;

@Slf4j
@UtilityClass
final class TimestamperUtil {


    @SuppressWarnings("unchecked")
    static TimeStampToken addSignerCertificate(TimeStampResponse tsResponse,
                                               X509Certificate signerCertificate)
            throws CertificateEncodingException, IOException, CMSException, TSPException {
        CMSSignedData cms = tsResponse.getTimeStampToken().toCMSSignedData();

        List<X509CertificateHolder> collection = new ArrayList<>();
        collection.add(new X509CertificateHolder(signerCertificate.getEncoded()));
        collection.addAll(cms.getCertificates().getMatches(null));

        return new TimeStampToken(CMSSignedData.replaceCertificatesAndCRLs(cms,
                new JcaCertStore(collection), cms.getAttributeCertificates(), cms.getCRLs()));
    }

    static InputStream makeTsRequest(TimeStampRequest req, String tspUrl) throws IOException {
        byte[] request = req.getEncoded();

        URL url =  URI.create(tspUrl).toURL();
        HttpURLConnection con = getHttpURLConnectionToTimestampingProvider(url, request);

        OutputStream out = con.getOutputStream();
        out.write(request);
        out.flush();

        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            con.disconnect();
            throw XrdRuntimeException.systemException(ErrorCode.TIMESTAMPING_NON_OK_RESPONSE)
                    .details("Received HTTP error: %d - %s".formatted(con.getResponseCode(), con.getResponseMessage()))
                    .metadataItems(con.getResponseCode(), con.getResponseMessage())
                    .build();
        } else if (con.getInputStream() == null) {
            con.disconnect();

            throw XrdRuntimeException.systemException(ErrorCode.TIMESTAMP_PROVIDER_CONNECTION_FAILED)
                    .details("Could not get response from TSP")
                    .build();
        }

        return con.getInputStream();
    }

    private static HttpURLConnection getHttpURLConnectionToTimestampingProvider(URL url, byte[] request) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setConnectTimeout(MessageLogProperties.getTimestamperClientConnectTimeout());
        con.setReadTimeout(MessageLogProperties.getTimestamperClientReadTimeout());
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-type", "application/timestamp-query");
        con.setRequestProperty("Content-length", String.valueOf(request.length));
        return con;
    }

    static TimeStampResponse getTimestampResponse(InputStream in) throws TSPException, IOException {
        TimeStampResp response = readTimestampResponse(in);

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

                sb.append("\"").append(statusString.getStringAtUTF8(i)).append("\"");
            }

            log.error("getTimestampDer() - TimeStampResp.status is not "
                    + "\"granted\" nor \"grantedWithMods\": {}, {}", status, sb);

            throw XrdRuntimeException.systemException(TIMESTAMP_NON_GRANTED_RESPONSE)
                    .details("TimeStampResp.status: " + status + ", .statusString: " + sb)
                    .metadataItems(status, sb.toString(), response.getStatus().getFailInfo())
                    .build();
        }

        try {
            return new TimeStampResponse(response);
        } catch (TSPException | IOException e) {
            throw XrdRuntimeException.systemException(TIMESTAMP_RESPONSE_OBJECT_CREATION_FAILED)
                    .details("Could not create RFC 3161 TimeStampResponse object")
                    .cause(e)
                    .build();
        }
    }

    private static TimeStampResp readTimestampResponse(InputStream in) {
        TimeStampResp response;
        try {
            response = TimeStampResp.getInstance(new ASN1InputStream(in).readObject());
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(READING_TIMESTAMP_RESPONSE_FAILED)
                    .details("Could not read time-stamp response")
                    .cause(e)
                    .build();
        }
        if (response == null) {
            throw XrdRuntimeException.systemException(READING_TIMESTAMP_RESPONSE_FAILED)
                    .details("Could not read time-stamp response")
                    .build();
        }
        return response;
    }
}
