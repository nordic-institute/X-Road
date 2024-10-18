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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.conf.globalconf.GlobalConfPropertiesConfig;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.bootstrap.XrdSpringServiceBuilder;
import org.niis.xroad.common.rpc.RpcClientProperties;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Signer main program.
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({SignerMain.SignerRpcServerProperties.class, SignerMain.ConfClientRpcClientProperties.class})
public class SignerMain {

    private static final String APP_NAME = "xroad-signer";

    public static void main(String[] args) {
        XrdSpringServiceBuilder.newApplicationBuilder(APP_NAME, SignerMain.class, SignerConfig.class, GlobalConfPropertiesConfig.class)
                .build()
                .run(args);
    }

    @ConfigurationProperties(prefix = "xroad.signer")
    static class SignerRpcServerProperties extends RpcServerProperties {
        SignerRpcServerProperties(String grpcListenAddress, int grpcPort, boolean grpcTlsEnabled,
                                         String grpcTlsTrustStore, char[] grpcTlsTrustStorePassword,
                                         String grpcTlsKeyStore, char[] grpcTlsKeyStorePassword) {
            super(grpcListenAddress, grpcPort, grpcTlsEnabled, grpcTlsTrustStore, grpcTlsTrustStorePassword,
                    grpcTlsKeyStore, grpcTlsKeyStorePassword);
        }
    }

    @ConfigurationProperties(prefix = "xroad.configuration-client")
    static class ConfClientRpcClientProperties extends RpcClientProperties {
        ConfClientRpcClientProperties(String grpcHost, int grpcPort, boolean grpcTlsEnabled,
                                             String grpcTlsTrustStore, char[] grpcTlsTrustStorePassword,
                                             String grpcTlsKeyStore, char[] grpcTlsKeyStorePassword) {
            super(grpcHost, grpcPort, grpcTlsEnabled, grpcTlsTrustStore, grpcTlsTrustStorePassword,
                    grpcTlsKeyStore, grpcTlsKeyStorePassword);
        }
    }
}
