package org.niis.xroad.signer.test.hook;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.signer.protocol.RpcSignerClient;

import com.nortal.test.core.services.TestableApplicationInfoProvider;
import com.nortal.test.core.services.hooks.BeforeSuiteHook;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.SystemProperties.GRPC_INTERNAL_KEYSTORE;
import static ee.ria.xroad.common.SystemProperties.GRPC_INTERNAL_KEYSTORE_PASSWORD;
import static ee.ria.xroad.common.SystemProperties.GRPC_INTERNAL_TRUSTSTORE;
import static ee.ria.xroad.common.SystemProperties.GRPC_INTERNAL_TRUSTSTORE_PASSWORD;
import static ee.ria.xroad.common.SystemProperties.GRPC_SIGNER_HOST;
import static ee.ria.xroad.common.SystemProperties.GRPC_SIGNER_PORT;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignerProxyInitHook implements BeforeSuiteHook {
    private final TestableApplicationInfoProvider testableApplicationInfoProvider;

    @Override
    @SneakyThrows
    public void beforeSuite() {
        var host = testableApplicationInfoProvider.getHost();
        var port = testableApplicationInfoProvider.getMappedPort(SystemProperties.getGrpcSignerPort());
        log.info("Will use {}:{}  for signer RPC connection..", host, port);

        System.setProperty(GRPC_SIGNER_HOST, host);
        System.setProperty(GRPC_SIGNER_PORT, String.valueOf(port));

        System.setProperty(GRPC_SIGNER_HOST, host);

        System.setProperty(GRPC_INTERNAL_KEYSTORE,
                "src/intTest/resources/container-files/etc/xroad/transport-keystore/grpc-internal-keystore.jks");
        System.setProperty(GRPC_INTERNAL_KEYSTORE_PASSWORD, "111111");
        System.setProperty(GRPC_INTERNAL_TRUSTSTORE,
                "src/intTest/resources/container-files/etc/xroad/transport-keystore/grpc-internal-keystore.jks");
        System.setProperty(GRPC_INTERNAL_TRUSTSTORE_PASSWORD, "111111");

        System.setProperty("xroad.internal.passwordstore-provider", "file");
        System.setProperty("xroad.internal.passwordstore-file-path", "build/container-passwordstore/");

        RpcSignerClient.init();
    }

}
