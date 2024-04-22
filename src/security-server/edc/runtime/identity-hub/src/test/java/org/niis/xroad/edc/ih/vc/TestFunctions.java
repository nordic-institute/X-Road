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
package org.niis.xroad.edc.ih.vc;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.ld.signature.method.VerificationMethod;
import com.apicatalog.ld.signature.proof.ProofOptions;
import com.apicatalog.vc.Vc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import jakarta.json.JsonObject;
import lombok.experimental.UtilityClass;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.security.signature.jws2020.IssuerCompatibility;
import org.eclipse.edc.security.signature.jws2020.JwkMethod;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.UUID;

@UtilityClass
public class TestFunctions {
    private static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();

    public static String signDocument(String jsonLdContent, JWK proofKey, ProofOptions proofOptions,
                                      @Nullable DocumentLoader testDocLoader) {
        try {
            var jsonLd = MAPPER.readValue(jsonLdContent, JsonObject.class);
            var ldKeypair = TestFunctions.createKeyPair(proofKey);
            var issuer = Vc.sign(jsonLd, ldKeypair, proofOptions).loader(testDocLoader);
            return IssuerCompatibility.compact(issuer).toString();
        } catch (JsonProcessingException | DocumentError | SigningError e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair createKeyPair(JWK jwk) {
        var id = URI.create("https://org.eclipse.edc/keys/" + UUID.randomUUID());
        var type = URI.create("https://w3id.org/security#JsonWebKey2020");
        return new JwkMethod(id, type, null, jwk);
    }

    public static VerificationMethod createKeyPair(JWK jwk, String id) {
        var type = URI.create("https://w3id.org/security#JsonWebKey2020");
        return new JwkMethod(URI.create(id), type, null, jwk);
    }
}
