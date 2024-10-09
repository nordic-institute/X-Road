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

package org.eclipse.edc.identityhub.did.defaults;

import org.eclipse.edc.identithub.spi.did.store.DidResourceStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;

import static org.eclipse.edc.identityhub.did.defaults.DidDefaultServicesExtension.NAME;

@Extension(value = NAME)
public class DidDefaultServicesExtension implements ServiceExtension {
    public static final String NAME = "DID Default Services Extension";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public DidResourceStore createInMemoryDidResourceStore() {
        return new InMemoryDidResourceStore(criterionOperatorRegistry);
    }
}
