/*
 *  Copyright (c) 2023 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.identityhub.did;

import org.eclipse.edc.identithub.spi.did.DidDocumentPublisher;
import org.eclipse.edc.identithub.spi.did.DidDocumentPublisherRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * In-mem variant of the publisher registry.
 */
public class DidDocumentPublisherRegistryImpl implements DidDocumentPublisherRegistry {
    public static final String DID_PREFIX = "did";
    public static final String DID_METHOD_SEPARATOR = ":";
    private static final int DID_PREFIX_INDEX = 4;
    private final Map<String, DidDocumentPublisher> publishers = new HashMap<>();

    @Override
    public void addPublisher(String didMethodNameIncludingPrefix, DidDocumentPublisher publisher) {
        publishers.put(didMethodNameIncludingPrefix, publisher);
    }

    @Override
    public DidDocumentPublisher getPublisher(String did) {

        if (!did.startsWith(DID_PREFIX)) {
            throw new IllegalArgumentException("A DID must include the 'did' prefix.");
        }
        var endIndex = did.indexOf(DID_METHOD_SEPARATOR, DID_PREFIX_INDEX);
        if (endIndex >= 0) {
            var method = did.substring(0, endIndex);
            return publishers.get(method);
        } else {
            return publishers.get(did); // endIndex can be -1 when only the method was passed, e.g. "did:web"
        }
    }

}
