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
package org.niis.xroad.edc.ih;

import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.LinkedDataSuiteError;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.JsonLdConfiguration;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.niis.xroad.edc.ih.GaiaXSelfDescriptionGenerator.composeGaiaXParticipantDocument;

class GaiaXSelfDescriptionGeneratorTest {

    @Test
    void generateVerifiableCredential() throws JOSEException, IOException, LinkedDataSuiteError, DocumentError {
        var rsaKey = new RSAKeyGenerator(2048).keyID("test-foo").generate();
        var jsonLdConf = JsonLdConfiguration.Builder.newInstance().httpEnabled(true).httpsEnabled(true).build();
        var jsonLd = new TitaniumJsonLd(new Monitor() { }, jsonLdConf);
        var vcGenerator = new GaiaXSelfDescriptionGenerator(jsonLd);

        var signer = new RSASSASigner(rsaKey.toPrivateKey());

        JsonObject doc = composeGaiaXParticipantDocument("localhost");
        var vc = vcGenerator.signDocument(doc, signer, URI.create("did:web:localhost#key-id"));
        assertThat(vc.compacted().toString(), notNullValue());
    }

}
