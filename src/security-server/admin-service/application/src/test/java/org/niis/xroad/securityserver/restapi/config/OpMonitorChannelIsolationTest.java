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
package org.niis.xroad.securityserver.restapi.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.auxiliaryservice.proto.AuxiliaryServiceRpcClient;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.DataspaceConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.rpc.test.TlsTestServer;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsResp;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OperationalDataIntervalProto;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.exceptions.ApplicationExceptionHandler;
import org.niis.xroad.restapi.exceptions.ExceptionTranslator;
import org.niis.xroad.restapi.exceptions.ValidationErrorHelper;
import org.niis.xroad.securityserver.restapi.openapi.DiagnosticsApiController;
import org.niis.xroad.securityserver.restapi.service.DiagnosticService;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpMonitorChannelIsolationTest {
    private static final String HOST = "localhost";
    private static final long FROM_MILLIS = 1_700_000_040_000L;
    private static final long SUCCESS_COUNT = 7;
    private static final String NOT_A_CERTIFICATE = "not a certificate";
    private static final String TRUST_FAILURE_CODE = "core." + ErrorCode.OP_MONITOR_TRUST_FAILURE.code();
    private static final String TRUST_FAILURE_DETAILS = "The op-monitor channel trust could not be established";
    private static final String INTERVALS_URL = "/api/v1/diagnostics/operational-monitoring"
            + "?records_from=2023-11-14T22:14:00Z&records_to=2023-11-14T22:15:00Z&interval=1";

    private TestCa opMonitorCa;
    private TlsTestServer opMonitor;
    private VaultKeyProvider keyProvider;
    private VaultClient vaultClient;
    private AnnotationConfigApplicationContext context;
    private final ListAppender<ILoggingEvent> handlerLog = new ListAppender<>();
    private Level handlerLoggerLevel;

    @BeforeEach
    void setUp() throws Exception {
        TestCa securityServerCa = TestCa.selfSigned("Security Server CA");
        opMonitorCa = TestCa.selfSigned("Op-monitor CA");
        var identity = securityServerCa.issueLeaf("proxy-ui-api", List.of(HOST), List.of());
        keyProvider = new TestVaultKeyProvider(identity, securityServerCa.certificate());
        var serverIdentity = opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        opMonitor = TlsTestServer.start(serverIdentity, securityServerCa.certificate(), new IntervalsService());
        vaultClient = mock(VaultClient.class);
        handlerLog.start();
        handlerLoggerLevel = handlerLogger().getLevel();
        handlerLogger().setLevel(Level.ERROR);
        handlerLogger().addAppender(handlerLog);
    }

    @AfterEach
    void tearDown() throws Exception {
        handlerLogger().detachAppender(handlerLog);
        handlerLogger().setLevel(handlerLoggerLevel);
        if (context != null) {
            context.close();
        }
        opMonitor.close();
    }

    @Test
    void applicationStartsWithoutReadingTheOpMonitorCa() {
        failReadsWithMalformedPem();

        startApplication();

        verify(vaultClient, never()).getOpMonitorCa();
    }

    @Test
    void trafficIntervalsCallFailsWithTheTrustFailureCodeWhenTheStoredCaCannotBeRead() {
        failReadsWith(() -> new IllegalStateException("permission denied"));
        startApplication();

        assertThatThrownBy(this::getTrafficIntervals)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.OP_MONITOR_TRUST_FAILURE.code());
                    assertThat(e.getDetails()).isEqualTo(TRUST_FAILURE_DETAILS);
                })
                .hasStackTraceContaining("permission denied");
        verify(vaultClient, times(1)).getOpMonitorCa();
    }

    @Test
    void trafficIntervalsCallFailsWithTheTrustFailureCodeWhenTheStoredCaIsMalformed() {
        failReadsWithMalformedPem();
        startApplication();

        assertThatThrownBy(this::getTrafficIntervals)
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OP_MONITOR_TRUST_FAILURE.code()))
                .hasStackTraceContaining("Could not parse certificate");
    }

    @Test
    void httpResponseHidesTheCauseWhenTheStoredCaCannotBeRead() throws Exception {
        failReadsWith(() -> new IllegalStateException("permission denied"));
        startApplication();

        assertTrustFailureResponse(trafficIntervalsRequest(), "permission denied");
    }

    @Test
    void httpResponseHidesTheCauseWhenTheStoredCaIsMalformed() throws Exception {
        failReadsWithMalformedPem();
        startApplication();

        assertTrustFailureResponse(trafficIntervalsRequest(), "Could not parse certificate");
    }

    @Test
    void httpResponseHidesTheCauseWhenTheKeyManagerFails() throws Exception {
        var failingKeys = mock(VaultKeyProvider.class, delegatesTo(keyProvider));
        keyProvider = failingKeys;
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(opMonitorCa.certificate()));
        startApplication();
        doThrow(new IllegalStateException("key manager is not ready")).when(failingKeys).getKeyManager();

        assertTrustFailureResponse(trafficIntervalsRequest(), "key manager is not ready");
    }

    @Test
    void trafficIntervalsCallWorksAndCreatesTheChannelOnce() {
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(opMonitorCa.certificate()));
        startApplication();

        assertThat(getTrafficIntervals()).isEqualTo(SUCCESS_COUNT);
        assertThat(getTrafficIntervals()).isEqualTo(SUCCESS_COUNT);

        verify(vaultClient, times(1)).getOpMonitorCa();
    }

    @Test
    void explicitInitCreatesTheChannelOnlyOnce() {
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(opMonitorCa.certificate()));
        startApplication();
        var client = context.getBean(OpMonitorClient.class);

        client.init();
        var channel = client.getChannel();
        client.init();

        assertThat(client.getChannel()).isSameAs(channel);
        verify(vaultClient, times(1)).getOpMonitorCa();
    }

    @Test
    void laterCallSucceedsWithoutRestartAfterTheChannelCreationFailed() {
        failReadsWith(() -> new IllegalStateException("permission denied"));
        startApplication();
        assertThatThrownBy(this::getTrafficIntervals).isInstanceOf(XrdRuntimeException.class);

        reset(vaultClient);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(opMonitorCa.certificate()));

        assertThat(getTrafficIntervals()).isEqualTo(SUCCESS_COUNT);
        verify(vaultClient, times(1)).getOpMonitorCa();
    }

    private void failReadsWith(Supplier<RuntimeException> failure) {
        when(vaultClient.getOpMonitorCa()).thenAnswer(invocation -> {
            throw failure.get();
        });
    }

    private void failReadsWithMalformedPem() {
        doCallRealMethod().when(vaultClient).toOpMonitorCa(any());
        when(vaultClient.getOpMonitorCa())
                .thenAnswer(_ -> vaultClient.toOpMonitorCa(Map.of(VaultClient.CERTIFICATE_KEY, NOT_A_CERTIFICATE)));
    }

    private void assertTrustFailureResponse(RequestBuilder request, String rawCause)
            throws Exception {
        var body = mockMvc().perform(request)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error.code").value(TRUST_FAILURE_CODE))
                .andExpect(jsonPath("$.error.metadata.length()").value(2))
                .andExpect(jsonPath("$.error.metadata[0]").value(TRUST_FAILURE_DETAILS))
                .andExpect(jsonPath("$.error.metadata[1]").value("tr." + ErrorCode.OP_MONITOR_TRUST_FAILURE.code()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(rawCause, "Exception", "Caused by");
        assertThat(handlerLog.list).singleElement().satisfies(event -> {
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy())).contains(rawCause);
        });
    }

    private static RequestBuilder trafficIntervalsRequest() {
        return get(INTERVALS_URL);
    }

    private MockMvc mockMvc() throws Exception {
        var constructor = Arrays.stream(DiagnosticsApiController.class.getConstructors())
                .filter(c -> !Modifier.isPrivate(c.getModifiers()))
                .findFirst().orElseThrow();
        var arguments = Arrays.stream(constructor.getParameterTypes())
                .map(type -> type == DiagnosticService.class ? diagnosticService() : mock(type))
                .toArray();
        var controller = (DiagnosticsApiController) constructor.newInstance(arguments);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(context.getBean(ApplicationExceptionHandler.class))
                .build();
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(ApplicationExceptionHandler.class);
    }

    private DiagnosticService diagnosticService() {
        return new DiagnosticService(context.getBean(ConfClientRpcClient.class),
                context.getBean(SignerRpcClient.class), context.getBean(ProxyRpcClient.class),
                context.getBean(AuxiliaryServiceRpcClient.class), context.getBean(OpMonitorClient.class));
    }

    private void startApplication() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(XRoadConfig.class, this::config);
        context.registerBean(VaultClient.class, () -> vaultClient);
        context.registerBean("reloadableVaultKeyManager", VaultKeyProvider.class, () -> keyProvider);
        context.registerBean(AuditEventLoggingFacade.class, () -> mock(AuditEventLoggingFacade.class));
        context.registerBean(ExceptionTranslator.class, () -> new ExceptionTranslator(new ValidationErrorHelper()));
        context.registerBean(ApplicationExceptionHandler.class);
        context.register(RpcClientsConfig.class);
        context.refresh();
    }

    private long getTrafficIntervals() {
        return diagnosticService().getOperationalDataIntervals(FROM_MILLIS, FROM_MILLIS + 60_000L, 1, null, null, null)
                .getFirst().getSuccessCount();
    }

    private XRoadConfig config() {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(CommonConfigKeys.instance())
                .register(AdminServiceConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .register(GlobalConfConfigKeys.instance())
                .register(ServerConfConfigKeys.instance())
                .register(DataspaceConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.common-rpc.use-tls", "true",
                        "xroad.common-rpc.channel.op-monitor.host", HOST,
                        "xroad.common-rpc.channel.op-monitor.port", String.valueOf(opMonitor.port()),
                        "xroad.common-rpc.channel.op-monitor.deadline-after", "5000"))
                .build();
    }

    private static final class IntervalsService extends OpMonitorServiceGrpc.OpMonitorServiceImplBase {
        @Override
        public void getOperationalDataIntervals(GetOperationalDataIntervalsReq request,
                                                StreamObserver<GetOperationalDataIntervalsResp> responseObserver) {
            responseObserver.onNext(GetOperationalDataIntervalsResp.newBuilder()
                    .addOperationalDataInterval(OperationalDataIntervalProto.newBuilder()
                            .setTimeIntervalStart(Timestamp.newBuilder().setSeconds(FROM_MILLIS / 1000))
                            .setSuccessCount(SUCCESS_COUNT)
                            .build())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
