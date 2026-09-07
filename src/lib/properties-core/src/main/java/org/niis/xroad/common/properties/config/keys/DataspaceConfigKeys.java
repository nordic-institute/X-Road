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

package org.niis.xroad.common.properties.config.keys;

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.util.Set;

/** Dataspace keys ({@code xroad.dataspace.*}). */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class DataspaceConfigKeys implements ConfigKeyProvider {

    private static final Prefix DATASPACE = Prefix.of(Category.ADMIN_SERVICE, "xroad.dataspace");
    private static final Prefix ISSUER = DATASPACE.subPrefix("issuer");
    private static final Prefix ISSUER_PROVISIONING_RPC = DATASPACE.subPrefix("issuer-provisioning").subPrefix("rpc");
    private static final Prefix CONTROL_PLANE_PROVISIONING_RPC = DATASPACE.subPrefix("control-plane-provisioning").subPrefix("rpc");
    private static final Prefix IDENTITY_HUB_PROVISIONING_RPC = DATASPACE.subPrefix("identity-hub-provisioning").subPrefix("rpc");

    private static final String DEADLINE_AFTER = "deadline-after";

    private static final DataspaceConfigKeys INSTANCE = new DataspaceConfigKeys();

    /** {@code xroad.dataspace.issuer.host}. */
    public static final ConfigKey<String> ISSUER_HOST = ISSUER
            .string("host")
            .withDefaultValue("localhost")
            .build();

    /** {@code xroad.dataspace.issuer.did-port}. */
    public static final ConfigKey<Integer> ISSUER_DID_PORT = ISSUER
            .integer("did-port")
            .withDefaultValue(6183)
            .build();

    /** {@code xroad.dataspace.issuer.issuance-port}. */
    public static final ConfigKey<Integer> ISSUER_ISSUANCE_PORT = ISSUER
            .integer("issuance-port")
            .withDefaultValue(6185)
            .build();

    /** {@code xroad.dataspace.issuer.credential-json-schema-url}. */
    public static final ConfigKey<String> ISSUER_CREDENTIAL_JSON_SCHEMA_URL = ISSUER
            .string("credential-json-schema-url")
            .withDefaultValue("https://example.com/schema/XRoadMembershipCredential.json")
            .build();

    /** {@code xroad.dataspace.issuer-provisioning.rpc.host}. */
    public static final ConfigKey<String> ISSUER_PROVISIONING_RPC_HOST = ISSUER_PROVISIONING_RPC
            .string("host")
            .withDefaultValue("127.0.0.1")
            .build();

    /** {@code xroad.dataspace.issuer-provisioning.rpc.port}. */
    public static final ConfigKey<Integer> ISSUER_PROVISIONING_RPC_PORT = ISSUER_PROVISIONING_RPC
            .integer("port")
            .withDefaultValue(5460)
            .build();

    /** {@code xroad.dataspace.issuer-provisioning.rpc.deadline-after}. */
    public static final ConfigKey<Integer> ISSUER_PROVISIONING_RPC_DEADLINE_AFTER = ISSUER_PROVISIONING_RPC
            .integer(DEADLINE_AFTER)
            .withDefaultValue(60000)
            .build();

    /** {@code xroad.dataspace.control-plane-provisioning.rpc.host}. */
    public static final ConfigKey<String> CONTROL_PLANE_PROVISIONING_RPC_HOST = CONTROL_PLANE_PROVISIONING_RPC
            .string("host")
            .withDefaultValue("ds-control-plane")
            .build();

    /** {@code xroad.dataspace.control-plane-provisioning.rpc.port}. */
    public static final ConfigKey<Integer> CONTROL_PLANE_PROVISIONING_RPC_PORT = CONTROL_PLANE_PROVISIONING_RPC
            .integer("port")
            .withDefaultValue(5461)
            .build();

    /** {@code xroad.dataspace.control-plane-provisioning.rpc.deadline-after}. */
    public static final ConfigKey<Integer> CONTROL_PLANE_PROVISIONING_RPC_DEADLINE_AFTER = CONTROL_PLANE_PROVISIONING_RPC
            .integer(DEADLINE_AFTER)
            .withDefaultValue(60000)
            .build();

    /** {@code xroad.dataspace.identity-hub-provisioning.rpc.host}. */
    public static final ConfigKey<String> IDENTITY_HUB_PROVISIONING_RPC_HOST = IDENTITY_HUB_PROVISIONING_RPC
            .string("host")
            .withDefaultValue("ds-identity-hub")
            .build();

    /** {@code xroad.dataspace.identity-hub-provisioning.rpc.port}. */
    public static final ConfigKey<Integer> IDENTITY_HUB_PROVISIONING_RPC_PORT = IDENTITY_HUB_PROVISIONING_RPC
            .integer("port")
            .withDefaultValue(5460)
            .build();

    /** {@code xroad.dataspace.identity-hub-provisioning.rpc.deadline-after}. */
    public static final ConfigKey<Integer> IDENTITY_HUB_PROVISIONING_RPC_DEADLINE_AFTER = IDENTITY_HUB_PROVISIONING_RPC
            .integer(DEADLINE_AFTER)
            .withDefaultValue(60000)
            .build();

    private DataspaceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static DataspaceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return DATASPACE.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return DATASPACE.keys();
    }
}
