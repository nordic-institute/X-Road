/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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


import com.apicatalog.ld.signature.VerificationMethod;
import com.apicatalog.ld.signature.key.VerificationKey;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/**
 * Generic adapter object for a {@link VerificationMethod}
 */
class DataIntegrityKeyPair implements VerificationKey {
    private final URI id;
    private final URI type;
    private final URI controller;
    private final byte[] privateKey;
    private final byte[] publicKey;

    DataIntegrityKeyPair(URI id, URI type, URI controller, byte[] publicKey, byte[] privateKey) {
        super();
        this.id = id;
        this.type = type;
        this.controller = controller;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    DataIntegrityKeyPair(URI id, URI type, URI controller, byte[] publicKey) {
        this(id, type, controller, publicKey, null);
    }

    @Override
    public URI id() {
        return id;
    }

    @Override
    public URI type() {
        return type;
    }

    @Override
    public URI controller() {
        return controller;
    }

    public byte[] privateKey() {
        return privateKey;
    }

    @Override
    public String algorithm() {
        return type.toString();
    }

    public byte[] publicKey() {
        return publicKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, controller, Arrays.hashCode(privateKey), Arrays.hashCode(publicKey));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DataIntegrityKeyPair) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.type, that.type) &&
                Objects.equals(this.controller, that.controller) &&
                Arrays.equals(this.privateKey, that.privateKey) &&
                Arrays.equals(this.publicKey, that.publicKey);
    }

    @Override
    public String toString() {
        return "DataIntegrityKeyPair[" +
                "id=" + id + ", " +
                "type=" + type + ", " +
                "controller=" + controller + ", " +
                "privateKey=" + Arrays.toString(privateKey) + ", " +
                "publicKey=" + Arrays.toString(publicKey) + ']';
    }

}
