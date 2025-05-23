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
package org.niis.xroad.common.acme;

import org.shredzone.acme4j.connector.Connection;
import org.shredzone.acme4j.connector.DefaultConnection;
import org.shredzone.acme4j.connector.HttpConnector;
import org.shredzone.acme4j.connector.NetworkSettings;
import org.shredzone.acme4j.provider.AbstractAcmeProvider;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static org.niis.xroad.common.acme.AcmeCustomSchema.XRD_ACME;

public class AcmeXroadProvider extends AbstractAcmeProvider {

    @Override
    public boolean accepts(URI serverUri) {
        return XRD_ACME.getSchema().equals(serverUri.getScheme()) || (XRD_ACME.getSchema() + "s").equals(serverUri.getScheme());
    }

    @Override
    public URL resolve(URI serverUri) {
        String protocol = XRD_ACME.getSchema().equals(serverUri.getScheme()) ? "http" : "https";
        try {
            return new URL(protocol, serverUri.getHost(), serverUri.getPort(), serverUri.getPath());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Bad server URI", ex);
        }
    }

    @Override
    public Connection connect(URI serverUri, NetworkSettings networkSettings) {
        return new DefaultConnection(createHttpConnector(networkSettings));
    }

    @Override
    protected HttpConnector createHttpConnector(NetworkSettings settings) {
        return new AcmeXroadHttpConnector(settings);
    }

}
