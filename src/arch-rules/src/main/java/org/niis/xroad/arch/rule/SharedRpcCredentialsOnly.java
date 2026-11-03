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
package org.niis.xroad.arch.rule;

import com.societegenerale.commons.plugin.model.RootClassFolder;
import com.societegenerale.commons.plugin.rules.ArchRuleTest;
import com.societegenerale.commons.plugin.service.ScopePathProvider;
import com.societegenerale.commons.plugin.utils.ArchUtils;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit rule that keeps every internal gRPC channel and server on the shared credentials, except for op-monitor.
 *
 * <p>The checks, applied to each module's main classes:
 * <ul>
 *   <li>only the op-monitor client may create a channel with its own credentials;</li>
 *   <li>only the op-monitor server may override the server credentials of a managed RPC server;</li>
 *   <li>an RPC server is built only where the shared server credentials are requested;</li>
 *   <li>credential and Netty builder types are used only by the classes that own them.</li>
 * </ul>
 */
@Slf4j
public class SharedRpcCredentialsOnly implements ArchRuleTest {

    private static final String RPC_CHANNEL_FACTORY = "org.niis.xroad.common.rpc.client.RpcChannelFactory";
    private static final String CHANNEL_CREDENTIALS = "io.grpc.ChannelCredentials";
    private static final String RPC_SERVER = "org.niis.xroad.common.rpc.server.RpcServer";
    private static final String MANAGED_RPC_SERVER = "org.niis.xroad.common.rpc.server.ManagedRpcServer";
    private static final String RPC_CREDENTIALS_CONFIGURER = "org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer";
    private static final String CLIENT_NAME_SERVER_CREDENTIALS = "org.niis.xroad.common.rpc.credentials.ClientNameServerCredentials";
    private static final String TLS_CONFIGURER = "org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer";
    private static final String INSECURE_CONFIGURER = "org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer";

    private static final String OP_MONITOR_CLIENT = "org.niis.xroad.opmonitor.client.OpMonitorClient";
    private static final String OP_MONITOR_TRUST_RESOLVER = "org.niis.xroad.opmonitor.client.OpMonitorChannelTrustResolver";
    private static final String OP_MONITOR_RPC_SERVER = "org.niis.xroad.opmonitor.core.OpMonitorRpcServer";
    private static final String OP_MONITOR_SERVER_CREDENTIALS = "org.niis.xroad.opmonitor.core.OpMonitorServerCredentials";
    private static final String EDC_RPC_SERVER_EXTENSION = "org.niis.xroad.edc.extension.rpc.XRoadRpcServerExtension";

    private static final String CREATE_CHANNEL = "createChannel";
    private static final String CREATE_SERVER_CREDENTIALS = "createServerCredentials";

    private static final Set<String> OWN_CREDENTIALS_CHANNEL_CREATORS = Set.of(OP_MONITOR_CLIENT, RPC_CHANNEL_FACTORY);
    private static final Set<String> OWN_CREDENTIALS_SERVERS = Set.of(OP_MONITOR_RPC_SERVER, MANAGED_RPC_SERVER);
    private static final Set<String> RPC_SERVER_BUILDERS = Set.of(MANAGED_RPC_SERVER, EDC_RPC_SERVER_EXTENSION);

    private static final Map<String, Set<String>> RESTRICTED_TYPE_USERS = Map.of(
            CLIENT_NAME_SERVER_CREDENTIALS, Set.of(OP_MONITOR_SERVER_CREDENTIALS),
            "io.grpc.TlsChannelCredentials", Set.of(TLS_CONFIGURER, OP_MONITOR_TRUST_RESOLVER),
            "io.grpc.TlsServerCredentials", Set.of(TLS_CONFIGURER, CLIENT_NAME_SERVER_CREDENTIALS),
            "io.grpc.InsecureChannelCredentials", Set.of(INSECURE_CONFIGURER),
            "io.grpc.InsecureServerCredentials", Set.of(INSECURE_CONFIGURER),
            "io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder", Set.of(RPC_CHANNEL_FACTORY),
            "io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder", Set.of(RPC_SERVER),
            "io.grpc.ManagedChannelBuilder", Set.of(),
            "io.grpc.Grpc", Set.of()
    );

    private static final String REASON = """
            Every internal gRPC channel and server except op-monitor's uses the shared OpenBao-managed credentials. \
            Only the op-monitor client and server may use their own credentials.""";

    @Override
    public void execute(String packagePath, ScopePathProvider scopePathProvider, Collection<String> excludedPaths) {
        RootClassFolder mainClassesFolder = scopePathProvider.getMainClassesPath();
        if (mainClassesFolder == null) {
            log.debug("Skipping {}: module {} has no main classes folder", getClass().getSimpleName(), packagePath);
            return;
        }

        String mainClassesPath = mainClassesFolder.getValue();
        if (mainClassesPath == null || !Files.exists(Path.of(mainClassesPath))) {
            log.debug("Skipping {}: main classes path {} does not exist", getClass().getSimpleName(), mainClassesPath);
            return;
        }

        classes()
                .should(new SharedCredentialsCondition())
                .because(REASON)
                .allowEmptyShould(true)
                .check(ArchUtils.importAllClassesInPackage(mainClassesFolder, packagePath, excludedPaths));
    }

    static class SharedCredentialsCondition extends ArchCondition<JavaClass> {

        SharedCredentialsCondition() {
            super("use the shared RPC credentials unless they belong to op-monitor");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            checkChannelCreation(javaClass, events);
            checkServerCredentialsOverride(javaClass, events);
            checkRpcServerConstruction(javaClass, events);
            checkRestrictedTypes(javaClass, events);
        }

        private void checkChannelCreation(JavaClass javaClass, ConditionEvents events) {
            if (OWN_CREDENTIALS_CHANNEL_CREATORS.contains(javaClass.getName())) {
                return;
            }
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                if (isCreateChannelWithCredentials(call)) {
                    violation(events, javaClass, "creates a channel with its own credentials at line %d"
                            .formatted(call.getLineNumber()));
                }
            }
        }

        private static boolean isCreateChannelWithCredentials(JavaMethodCall call) {
            return call.getTargetOwner().getName().equals(RPC_CHANNEL_FACTORY)
                    && CREATE_CHANNEL.equals(call.getName())
                    && call.getTarget().getRawParameterTypes().stream().anyMatch(type -> type.getName().equals(CHANNEL_CREDENTIALS));
        }

        private void checkServerCredentialsOverride(JavaClass javaClass, ConditionEvents events) {
            if (OWN_CREDENTIALS_SERVERS.contains(javaClass.getName()) || !javaClass.isAssignableTo(MANAGED_RPC_SERVER)) {
                return;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (CREATE_SERVER_CREDENTIALS.equals(method.getName())) {
                    violation(events, javaClass, "overrides the server credentials of an RPC server");
                }
            }
        }

        private void checkRpcServerConstruction(JavaClass javaClass, ConditionEvents events) {
            for (JavaConstructorCall call : javaClass.getConstructorCallsFromSelf()) {
                if (!call.getTargetOwner().getName().equals(RPC_SERVER)) {
                    continue;
                }
                if (!RPC_SERVER_BUILDERS.contains(javaClass.getName())) {
                    violation(events, javaClass, "builds an RPC server at line %d".formatted(call.getLineNumber()));
                } else if (!requestsSharedServerCredentials(javaClass)) {
                    violation(events, javaClass, "builds an RPC server without the shared server credentials");
                }
            }
        }

        private static boolean requestsSharedServerCredentials(JavaClass javaClass) {
            return javaClass.getMethodCallsFromSelf().stream()
                    .anyMatch(call -> call.getTargetOwner().getName().equals(RPC_CREDENTIALS_CONFIGURER)
                            && CREATE_SERVER_CREDENTIALS.equals(call.getName()));
        }

        private void checkRestrictedTypes(JavaClass javaClass, ConditionEvents events) {
            String user = outermostName(javaClass);
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                String used = outermostName(access.getTargetOwner());
                Set<String> allowedUsers = RESTRICTED_TYPE_USERS.get(used);
                if (allowedUsers != null && !used.equals(user) && !allowedUsers.contains(user)) {
                    violation(events, javaClass, "uses %s at line %d".formatted(used, access.getLineNumber()));
                }
            }
        }

        private static String outermostName(JavaClass javaClass) {
            String name = javaClass.getName();
            int nested = name.indexOf('$');
            return nested < 0 ? name : name.substring(0, nested);
        }

        private static void violation(ConditionEvents events, JavaClass javaClass, String detail) {
            events.add(SimpleConditionEvent.violated(javaClass, "Class <%s> %s. %s"
                    .formatted(javaClass.getFullName(), detail, REASON)));
        }
    }
}
