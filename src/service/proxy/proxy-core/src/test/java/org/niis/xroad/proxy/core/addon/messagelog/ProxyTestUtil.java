/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.messagelog;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.message.BasicHeader;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Slf4j
@UtilityClass
final class ProxyTestUtil {

    static final String TSP_CERT =
            "MIICwjCCAaqgAwIBAgIIb+RPNmkfCdYwDQYJKoZIhvcNAQEFBQAwNzERMA8G"
                    + "A1UEAwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UE"
                    + "BhMCU0UwHhcNMTIxMTI5MTE1MzA2WhcNMTQxMTI5MTE1MzA2WjAVMRMwEQYD"
                    + "VQQDDAp0aW1lc3RhbXAxMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCb"
                    + "55NVDtHzs91sflX3fatZWUS69rxkxDMpcGo6doJ7YaKrCMr3BZ3ZlDTfCdEo"
                    + "sWocTcYXdm3CO8BXlZvhkvKyHN/hr0UzD0T8j8mBYoq3fGjTVTJOIG2yTsyT"
                    + "/3z3dpcMyGMWwsiqOd9TTtI8DcR2cOvQzlLiV9hz/kB9iLJeSQIDAQABo3gw"
                    + "djAdBgNVHQ4EFgQUbdmtvKHCe0+vhKP+ZcVUjmf5w/AwDAYDVR0TAQH/BAIw"
                    + "ADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAOBgNVHQ8BAf8E"
                    + "BAMCBkAwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwgwDQYJKoZIhvcNAQEFBQAD"
                    + "ggEBAFJ3AJ4I4RTeMBWhN8RLPQdJzcd0VRp9FUyYhnIkR679nXU+ZbIyaQNx"
                    + "3+hPIbhcOMKxlKGm0LcDnjHL4EuJ6Gb027vF7mSwFbcKPM+L23x2QLvuVcUE"
                    + "jcbP3Kcm93XCSu3RI71JINM+WinjXke/COuFzhMWJcLYj7S5dGR53ya0NnSf"
                    + "7dlua5FLBRiOFA5kRWTft6RcEW0jGZzscL6wZn+hH99IihjqgdxV1GydL+Bg"
                    + "DMfryZzhl+h1WtTwv0Bi5Gs81v8UlNUTnCCfLu9fatHx85/ttFcXEyt9SQze"
                    + "3NGcaR1i3kyZvNijzG3C+jrUnJ/lFs5AcIiPG0Emz6oZEYs=";

    static String message;
    static String signature;

    static GlobalConfProvider getGlobalConf() {
        return new EmptyGlobalConf() {
            @Override
            public List<X509Certificate> getTspCertificates() {
                return List.of(CryptoUtils.readCertificate(TSP_CERT));
            }

            @Override
            public String getInstanceIdentifier() {
                return "XRD";
            }
        };
    }

    static ServerConfProvider getServerConf() {
        return new EmptyServerConf() {
            @Override
            public List<String> getTspUrls() {
                return List.of("http://iks2-ubuntu.cyber.ee:8080/"
                        + "signserver/tsa?workerName=TimeStampSigner");
            }
        };
    }

    static void cleanUpDatabase(DatabaseCtx databaseCtx) {
        databaseCtx.doInTransaction(session -> {
            var q = session.createNativeMutationQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    @SneakyThrows
    static SoapMessageImpl createMessage() {
        return createMessage("123456789");
    }

    @SneakyThrows
    static SoapMessageImpl createMessage(String queryId) {
        if (message == null) {
            try (InputStream in = new FileInputStream("src/test/queries/simple.query")) {
                message = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        }

        String soap = message.replaceAll("<xroad:id>1234567890</xroad:id>",
                "<xroad:id>" + queryId + "</xroad:id>");
        return (SoapMessageImpl) new SoapParserImpl().parse(
                MimeTypes.TEXT_XML_UTF8,
                new ByteArrayInputStream(soap.getBytes(StandardCharsets.UTF_8)));
    }

    static RestRequest createRestRequest(String queryId, String xRequestId) {
        return new RestRequest(
                "POST",
                String.format("/r%d/XRD/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                null,
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "XRD/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", queryId),
                        new BasicHeader("X-Road-ServerId", "XRD/Class/Member/ServerCode")),
                xRequestId
        );
    }

    @SneakyThrows
    static SignatureData createSignature() {
        if (signature == null) {
            try (InputStream in = new FileInputStream("src/test/resources/signature.xml")) {
                signature = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        }
        return new SignatureData(signature, null, null);
    }

    static List<Task> getTaskQueue(DatabaseCtx databaseCtx) {
        return databaseCtx.doInTransaction(session -> session.createQuery(
                TaskQueue.getTaskQueueQuery(), Task.class).list());
    }

    static void assertTaskQueueSize(DatabaseCtx databaseCtx, int expectedSize) {
        List<Task> taskQueue = getTaskQueue(databaseCtx);
        assertNotNull(taskQueue);
        assertEquals(expectedSize, taskQueue.size());
    }


}
