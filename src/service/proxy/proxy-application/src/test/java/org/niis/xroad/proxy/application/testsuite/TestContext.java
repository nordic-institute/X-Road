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

package org.niis.xroad.proxy.application.testsuite;

import ee.ria.xroad.common.util.JobManager;

import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.serverconf.TestServerConfWrapper;

import static org.mockito.Mockito.mock;

public class TestContext {
    TestGlobalConfWrapper globalConfProvider = new TestGlobalConfWrapper(new TestSuiteGlobalConf());
    KeyConfProvider keyConfProvider = new TestSuiteKeyConf(globalConfProvider);
    TestServerConfWrapper serverConfProvider = new TestServerConfWrapper(new TestSuiteServerConf());

    public ServerProxy serverProxy;
    ClientProxy clientProxy;

    public TestContext() {
        try {
            org.apache.xml.security.Init.init();
            SigningCtxProvider signingCtxProvider = new TestSuiteSigningCtxProvider(globalConfProvider, keyConfProvider);

            CertHelper certHelper = new CertHelper(globalConfProvider);
            CertChainFactory certChainFactory = new CertChainFactory(globalConfProvider);
            AuthTrustVerifier authTrustVerifier = new AuthTrustVerifier(keyConfProvider, certHelper, certChainFactory);

            CommonBeanProxy commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider, signingCtxProvider, certChainFactory,
                    certHelper);

            clientProxy = new ClientProxy(commonBeanProxy, globalConfProvider, keyConfProvider, serverConfProvider, authTrustVerifier);
            serverProxy = new ServerProxy(commonBeanProxy);

            clientProxy.afterPropertiesSet();
            serverProxy.afterPropertiesSet();

            OpMonitoring.init(serverConfProvider);
            MessageLog.init(mock(JobManager.class), globalConfProvider, serverConfProvider);
        } catch (Exception e) {
            throw new RuntimeException("Init failed", e);
        }
    }

    public void destroy() {
        try {
            serverProxy.destroy();
        } catch (Exception e) {
        }

        try {
            clientProxy.destroy();
        } catch (Exception e) {
        }
    }
}
