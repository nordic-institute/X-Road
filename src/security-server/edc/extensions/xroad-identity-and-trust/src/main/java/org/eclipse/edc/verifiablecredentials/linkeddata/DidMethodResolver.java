/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.verifiablecredentials.linkeddata;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.SignatureSuite;
import com.apicatalog.ld.signature.method.MethodResolver;
import com.apicatalog.ld.signature.method.VerificationMethod;
import com.apicatalog.vc.VcTag;
import com.apicatalog.vc.integrity.DataIntegrityKeyPair;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.spi.EdcException;

import java.net.URI;

/**
 * This class implements the MethodResolver interface and is responsible for resolving verification methods for a given DID by
 * delegating to the {@link DidResolverRegistry}.
 */
public class DidMethodResolver implements MethodResolver {
    private final DidResolverRegistry resolverRegistry;

    public DidMethodResolver(DidResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public VerificationMethod resolve(URI id, DocumentLoader loader, SignatureSuite suite) throws DocumentError {
        var didDocument = resolverRegistry.resolve(id.toString())
                .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));

        return didDocument.getVerificationMethod().stream()
                .map(verificationMethod -> DataIntegrityKeyPair.createVerificationKey(
                        URI.create(verificationMethod.getId()),
                        URI.create(verificationMethod.getType()),
                        URI.create(verificationMethod.getController()),
                        verificationMethod.serializePublicKey())
                )
                .findFirst()
                .orElseThrow(() -> new DocumentError(DocumentError.ErrorType.Unknown, suite.getSchema().tagged(VcTag.VerificationMethod.name()).term()));
    }

    /**
     * Determines whether the given ID is accepted by checking if it is supported by the resolverRegistry.
     *
     * @param id The ID to check.
     * @return {@code true} if the ID is supported, {@code false} otherwise.
     */
    @Override
    public boolean isAccepted(URI id) {
        return resolverRegistry.isSupported(id.toString());
    }
}
