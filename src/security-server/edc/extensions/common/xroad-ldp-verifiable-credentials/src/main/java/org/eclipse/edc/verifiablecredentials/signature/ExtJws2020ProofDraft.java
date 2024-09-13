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
package org.eclipse.edc.verifiablecredentials.signature;

import com.apicatalog.ld.node.LdNodeBuilder;
import com.apicatalog.ld.signature.CryptoSuite;
import com.apicatalog.ld.signature.VerificationMethod;
import com.apicatalog.vc.ModelVersion;
import com.apicatalog.vc.integrity.DataIntegrityVocab;
import com.apicatalog.vc.issuer.ProofDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.security.signature.jws2020.Jwk2020KeyAdapter;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite.PROOF_VALUE_TERM;

public class ExtJws2020ProofDraft extends ProofDraft {

    private final Instant created;
    private final URI proofPurpose;
    private ObjectMapper mapper;

    private ExtJws2020ProofDraft(CryptoSuite crypto, VerificationMethod method, Instant created, URI proofPurpose) {
        super(crypto, method);
        this.created = created;
        this.proofPurpose = proofPurpose;
    }

    public static JsonObject signed(JsonObject unsigned, JsonValue proofValue) {
        return LdNodeBuilder.of(unsigned).set(PROOF_VALUE_TERM).value(proofValue).build();
    }

    @Override
    public Collection<String> context(ModelVersion model) {
        return List.of(Jws2020SignatureSuite.CONTEXT);

    }

    @Override
    public JsonObject unsigned() {
        var builder = new LdNodeBuilder();
        super.unsigned(builder, new Jwk2020KeyAdapter(mapper));

        builder.type(Jws2020SignatureSuite.ID);
        builder.set(DataIntegrityVocab.PURPOSE).id(proofPurpose);
        builder.set(DataIntegrityVocab.CREATED).xsdDateTime(created != null ? created : Instant.now());

        return builder.build();
    }

    public static final class Builder {
        private Instant created;
        private URI proofPurpose;
        private VerificationMethod method;
        private URI id;
        private ObjectMapper mapper;

        private Builder() {
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder created(Instant created) {
            this.created = created;
            return this;
        }

        public Builder verificationMethod(VerificationMethod verificationMethod) {
            this.method = verificationMethod;
            return this;
        }

        public Builder proofPurpose(URI proofPurpose) {
            this.proofPurpose = proofPurpose;
            return this;
        }

        public Builder method(VerificationMethod method) {
            this.method = method;
            return this;
        }

        public Builder id(URI id) {
            this.id = id;
            return this;
        }

        public Builder mapper(ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public ExtJws2020ProofDraft build() {
            Objects.requireNonNull(mapper, "mapper is required");
            var draft = new ExtJws2020ProofDraft(new ExtJws2020CryptoSuite(), method, created, proofPurpose);
            draft.id = this.id;
            draft.mapper = mapper;
            return draft;
        }
    }
}
