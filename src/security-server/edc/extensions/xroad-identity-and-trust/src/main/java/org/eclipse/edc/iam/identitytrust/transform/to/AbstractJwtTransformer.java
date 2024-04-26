/*
 *  Copyright (c) 2024 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.iam.identitytrust.transform.to;

import org.eclipse.edc.transform.spi.TypeTransformer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static java.util.Collections.emptyList;

abstract class AbstractJwtTransformer<OUTPUT> implements TypeTransformer<String, OUTPUT> {


    protected static final String TYPE_PROPERTY = "type";
    

    private final Class<OUTPUT> output;


    protected AbstractJwtTransformer(Class<OUTPUT> output) {
        this.output = output;
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    public Class<OUTPUT> getOutputType() {
        return output;
    }

    /**
     * If provided object is a {@link Collection} then apply the transformation
     * method on every object composing the collection. Otherwise, apply the mapping
     * function on the input object directly.
     */
    protected <T> List<T> listOrReturn(Object o, Function<Object, T> mapping) {
        if (o == null) {
            return emptyList();
        } else if (o instanceof Collection<?>) {
            return ((Collection<?>) o).stream()
                    .map(mapping)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.of(mapping.apply(o));
    }

}
