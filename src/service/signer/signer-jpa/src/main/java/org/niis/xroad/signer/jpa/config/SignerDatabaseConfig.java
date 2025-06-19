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
package org.niis.xroad.signer.jpa.config;

import ee.ria.xroad.common.db.DatabaseCtx;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Server conf database context.
 */
public class SignerDatabaseConfig {
    public static final String DB_CTX = "signerDbCtx";

    private static final Map<String, String> SPRING_TO_DB_PROP_MAPPING = Map.of(
            "url", "connection.url",
            "username", "connection.username",
            "password", "connection.password",
            "driver-class-name", "connection.driver_class",
            "dialect", "dialect",
            "hikari.data-source-properties.currentSchema", "connection.defaultSchemaName",
            "hbm2ddl.auto", "hbm2ddl.auto"
    );

    @Produces
    @Named(DB_CTX)
    @Singleton
    DatabaseCtx databaseCtx(@ConfigProperty(name = "xroad.db.serverconf.hibernate") Optional<Map<String, String>> serverconfProps,
                            @ConfigProperty(name = "spring.datasource") Optional<Map<String, String>> centerUiProps) {
        if (serverconfProps.isPresent()) {
            return createServerConfDbCtx(serverconfProps.get());
        } else if (centerUiProps.isPresent()) {
            var mappedProps = centerUiProps.get().entrySet().stream()
                    .filter(entry -> SPRING_TO_DB_PROP_MAPPING.containsKey(entry.getKey()))
                    .map(entry -> {
                        String dbPropKey = SPRING_TO_DB_PROP_MAPPING.get(entry.getKey());
                        return dbPropKey != null ? Map.entry(dbPropKey, entry.getValue()) : entry;
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return createServerConfDbCtx(mappedProps);
        } else {
            throw new IllegalStateException("No database configuration found for signer");
        }
    }

    public static DatabaseCtx createServerConfDbCtx(Map<String, String> dbProperties) {
        return new DatabaseCtx("signer", dbProperties);
    }

    public void cleanup(@Named(DB_CTX) @Disposes DatabaseCtx databaseCtx) {
        databaseCtx.destroy();
    }
}
