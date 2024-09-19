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
package org.niis.xroad.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.util.io.Ports;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Paths;
import java.util.Map;

@SuppressWarnings("checkstyle:linelength")
@EndToEndTest
class JsonLdExpansionTest {

    private final ObjectMapper jsonLdMapper = JacksonJsonLd.createObjectMapper();

    private static final String LRN_VC = """
            {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/suites/jws-2020/v1",
                "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"
              ],
              "type": [
                "VerifiableCredential"
              ],
              "id": "https://xroad-8-ss0.s3.eu-west-1.amazonaws.com/lrn.json",
              "issuer": "did:web:gx-notary.i.x-road.rocks:main",
              "issuanceDate": "2024-05-08T10:17:30.813+00:00",
              "credentialSubject": {
                "id": "https://xroad-8-ss0.s3.eu-west-1.amazonaws.com/lrn.json#cs",
                "type": "gx:legalRegistrationNumber",
                "gx:vatID": "EE100610662",
                "gx:vatID-countryCode": "EE"
              },
              "evidence": [
                {
                  "gx:evidenceOf": "VAT_ID",
                  "gx:evidenceURL": "http://ec.europa.eu/taxation_customs/vies/services/checkVatService",
                  "gx:executionDate": "2024-05-08T10:17:30.813+00:00"
                }
              ],
              "proof": {
                "type": "JsonWebSignature2020",
                "created": "2024-05-08T10:17:30.814+00:00",
                "proofPurpose": "assertionMethod",
                "verificationMethod": "did:web:gx-notary.i.x-road.rocks:main#X509-JWK2020",
                "@context": [
                  "https://www.w3.org/2018/credentials/v1",
                  "https://w3id.org/security/suites/jws-2020/v1"
                ],
                "jws": "eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..XS-t1BsnN24VvP7oFANmG-Cb95DLvob0CxNPD4o_EaCVLV8wlX-pSAlAoegNqkANsJumAZYACuCxK_VF5nsuHEudgApgXEu0tCPPMkHh0SMFoC2gJZdSZeYHOMU_coJRYNp4I6P98YbK8h-3l60yyJ-ioT4jh4ShCF2mLS5btiUwEnMy5-kD3wcXloE-CqP1xkAE_YP1nqmVqUtvUenNme2aQUP10FaXubz8f0IjVYj-LjH3ILywkwyv2q1YaMp6YctaHYyd_sgklBV0zYRQgd4J7Y6t_DKToAiubsYSPgZZXGnU_iEOO6hqBPvho3O0aCynZgm_gQVtUOm4g8WYOA"
              }
            }
            """;

    static {
        System.setProperty("xroad.signer.grpc-tls-enabled", "false");

        String xrdSrcDir = Paths.get("./src/test/resources/files/").toAbsolutePath().normalize().toString();
        System.setProperty("xroad.signer.key-configuration-file", xrdSrcDir + "/signer/keyconf.xml");
    }

    @RegisterExtension
    private static final RuntimeExtension RUNTIME = new RuntimePerClassExtension()
            .setConfiguration(Map.ofEntries(
                    Map.entry("edc.vault.hashicorp.url", "http://url"),
                    Map.entry("edc.vault.hashicorp.token", "token"),
                    Map.entry("edc.iam.issuer.id", "did:web:localhost"),
                    Map.entry("edc.participant.id", "did:web:localhost"),
                    Map.entry("edc.iam.trusted-issuer.localhost.id", "did:web:localhost"),
                    Map.entry("web.http.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.path", "/api"),
                    Map.entry("web.http.resolution.path", "/resolution"),
                    Map.entry("web.http.resolution.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.management.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.management.path", "/management"),
                    Map.entry("web.http.control.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.control.path", "/control"),
                    Map.entry("edc.jsonld.https.enabled", "true"),
                    Map.entry("edc.jsonld.http.enabled", "true")
            ));

    @Test
    void shouldExpandJsonLd() {
        var timer = StopWatch.createStarted();

        var jsonObj = parseJson(LRN_VC);
        System.out.println("====================================");
        System.out.println("Parsing took: " + timer.getTime() + "ms");
        System.out.println("====================================");
        for (int i = 0; i < 10; i++) {
            timer = StopWatch.createStarted();
            var result = RUNTIME.getService(JsonLd.class).expand(jsonObj);
            Assertions.assertNotNull(result.getContent());
            System.out.println("Expanding took: " + timer.getTime() + "ms");
            System.out.println("====================================");

        }
    }

    @SneakyThrows
    private JsonObject parseJson(String json) {
        return jsonLdMapper.readValue(json, JsonObject.class);
    }
}
