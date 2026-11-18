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
package org.niis.xroad.ds.issuance.identity;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.niis.xroad.ds.issuance.identity.XRoadIssuerHostnameExtension.NAME;

/**
 * Overrides EDC's {@code edc.hostname}-backed {@link Hostname} with the host bound at issuer set-up, read from
 * the Central Server database through a {@link BoundIssuerIdentityHolder}. EDC's own {@code
 * RuntimeCoreServicesExtension} reads {@code edc.hostname} once at boot into an immutable field; registering a
 * replacement {@link Hostname} here is the only way a binding that lands after boot takes effect without a
 * restart. While unbound, every caller of {@link Hostname#get()} gets a refusal instead of a wrong host.
 *
 * <p>The {@code @Inject CriterionOperatorRegistry} field forces EDC's dependency resolver to run this
 * extension after {@code RuntimeCoreServicesExtension}, whose {@code @Provider Hostname} would otherwise be
 * the last registered instance.
 *
 * <p>This extension also provides the {@link BoundIssuerIdentityHolder} it builds, so every other EDC override
 * that needs the bound identity reads it from the one holder this extension owns, instead of building a second
 * one that polls the Central Server database independently.
 */
@Extension(NAME)
public class XRoadIssuerHostnameExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer Hostname";

    static final String IDENTITY_DB_URL_SETTING = "xroad.db.ds-issuer-service-identity.url";
    static final String IDENTITY_DB_USERNAME_SETTING = "xroad.db.ds-issuer-service-identity.username";
    static final String IDENTITY_DB_PASSWORD_SETTING = "xroad.db.ds-issuer-service-identity.password";
    static final String IDENTITY_DB_SCHEMA_SETTING = "xroad.db.ds-issuer-service-identity.schema";

    @Inject
    @SuppressWarnings("unused")
    private CriterionOperatorRegistry criterionOperatorRegistry;

    private BoundIssuerIdentityHolder holder;

    public XRoadIssuerHostnameExtension() {
    }

    XRoadIssuerHostnameExtension(BoundIssuerIdentityHolder holder) {
        this.holder = holder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        if (holder == null) {
            holder = new BoundIssuerIdentityHolder(() -> newReader(context), context.getMonitor());
        }
    }

    @Override
    public void start() {
        holder.start();
    }

    @Override
    public void shutdown() {
        if (holder != null) {
            holder.close();
        }
    }

    @Provider
    public Hostname hostname() {
        return () -> holder.identity()
                .map(BoundIssuerIdentity::host)
                .orElseThrow(() -> new EdcException(NAME + ": the issuer identity is not bound"));
    }

    /**
     * Exposes the one {@link BoundIssuerIdentityHolder} this extension owns as an injectable EDC service, so
     * {@code XRoadStatusListCredentialPublisherExtension} and {@code XRoadIssuanceGateExtension} read the same
     * holder instead of each building its own.
     */
    @Provider
    public BoundIssuerIdentityHolder boundIssuerIdentityHolder() {
        return holder;
    }

    private static BoundIssuerIdentityReader newReader(ServiceExtensionContext context) {
        return new JdbcBoundIssuerIdentityReader(
                context.getSetting(IDENTITY_DB_URL_SETTING, null),
                context.getSetting(IDENTITY_DB_USERNAME_SETTING, null),
                context.getSetting(IDENTITY_DB_PASSWORD_SETTING, null),
                context.getSetting(IDENTITY_DB_SCHEMA_SETTING, null),
                context.getMonitor());
    }
}
