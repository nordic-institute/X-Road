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
package org.niis.xroad.opmonitor.test;

import org.niis.xroad.common.rpc.test.PemFiles;
import org.niis.xroad.common.rpc.test.TestCa;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Key material of the file-identity stack. The op-monitor CA and server identity come from the packaged
 * {@code generate_opmonitor_rpc_identity.sh}; the Security Server RPC CA, the client certificates and the broken
 * variants are made for the test. Each variant is a directory laid out as the op-monitor container reads it.
 */
final class FileIdentityMaterial {

    static final String SCRIPT_PROPERTY = "xroad.opmonitor.identityScript";

    private static final String HOST = "localhost";
    private static final String LOOPBACK = "127.0.0.1";
    private static final String SERVER_KEY = "server.key";
    private static final String SERVER_CERTIFICATE = "server.crt";
    private static final String TRUSTED_CA = "trusted-ca.crt";
    private static final String SCRIPT_VALIDITY_DAYS = "30";
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DIRECTORY_PERMISSIONS = "rwxr-xr-x";
    private static final String FILE_PERMISSIONS = "rw-r--r--";

    private final TestCa securityServerRpcCa;
    private final TestCa.TestLeaf trustedClient;
    private final TestCa.TestLeaf foreignClient;
    private final X509Certificate opMonitorCa;
    private final Path validDirectory;
    private final Path mismatchedKeyDirectory;
    private final Path missingCertificateDirectory;
    private final Path twoTrustedCasDirectory;

    private FileIdentityMaterial(Path root) throws Exception {
        securityServerRpcCa = TestCa.selfSigned("Security Server RPC CA " + System.nanoTime());
        var foreignCa = TestCa.selfSigned("Foreign CA " + System.nanoTime());
        trustedClient = securityServerRpcCa.issueLeaf("proxy", List.of(HOST), List.of());
        foreignClient = foreignCa.issueLeaf("proxy", List.of(HOST), List.of());

        var generated = generateOpMonitorIdentity(directory(root, "generated"));
        opMonitorCa = readCertificate(generated.resolve("ca.crt"));
        var serverKey = generated.resolve(SERVER_KEY);
        var serverCertificate = generated.resolve(SERVER_CERTIFICATE);

        validDirectory = identity(directory(root, "valid"), serverKey, serverCertificate,
                securityServerRpcCa.certificate());
        mismatchedKeyDirectory = identity(directory(root, "mismatched-key"), null, serverCertificate,
                securityServerRpcCa.certificate());
        PemFiles.writePrivateKey(mismatchedKeyDirectory.resolve(SERVER_KEY), foreignClient.privateKey());
        makeReadable(mismatchedKeyDirectory.resolve(SERVER_KEY));
        missingCertificateDirectory = identity(directory(root, "missing-certificate"), serverKey, null,
                securityServerRpcCa.certificate());
        twoTrustedCasDirectory = identity(directory(root, "two-trusted-cas"), serverKey, serverCertificate,
                securityServerRpcCa.certificate(), foreignCa.certificate());
    }

    static FileIdentityMaterial create(Path root) throws Exception {
        return new FileIdentityMaterial(root);
    }

    /** The environment variables of the compose file, naming the directory each op-monitor service mounts. */
    Map<String, String> composeEnvironment() {
        return Map.of(
                "IDENTITY_DIR", validDirectory.toString(),
                "MISMATCHED_KEY_IDENTITY_DIR", mismatchedKeyDirectory.toString(),
                "MISSING_CERTIFICATE_IDENTITY_DIR", missingCertificateDirectory.toString(),
                "TWO_TRUSTED_CAS_IDENTITY_DIR", twoTrustedCasDirectory.toString());
    }

    TestCa.TestLeaf trustedClient() {
        return trustedClient;
    }

    TestCa.TestLeaf foreignClient() {
        return foreignClient;
    }

    X509Certificate securityServerRpcCa() {
        return securityServerRpcCa.certificate();
    }

    X509Certificate opMonitorCa() {
        return opMonitorCa;
    }

    private static Path generateOpMonitorIdentity(Path directory) throws Exception {
        var script = System.getProperty(SCRIPT_PROPERTY);
        if (script == null) {
            throw new IllegalStateException("System property " + SCRIPT_PROPERTY + " names the identity script; "
                    + "it is set by the intTest Gradle task");
        }
        var command = new ArrayList<>(List.of("bash", script, "--days", SCRIPT_VALIDITY_DAYS, HOST, LOOPBACK));
        var dockerHost = DockerClientFactory.instance().dockerHostIpAddress();
        if (!HOST.equals(dockerHost) && !LOOPBACK.equals(dockerHost)) {
            command.add(dockerHost);
        }
        var outputFile = Files.createTempFile(directory, "identity-script", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile());
        builder.environment().put("XROAD_OPMONITOR_IDENTITY_DIR", directory.toString());
        var process = builder.start();
        if (!process.waitFor(SCRIPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("The identity script did not finish in " + SCRIPT_TIMEOUT);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("The identity script failed: " + Files.readString(outputFile));
        }
        return directory;
    }

    private static Path identity(Path directory, Path serverKey, Path serverCertificate,
                                 X509Certificate... trustedCas) throws IOException {
        if (serverKey != null) {
            copyReadable(serverKey, directory.resolve(SERVER_KEY));
        }
        if (serverCertificate != null) {
            copyReadable(serverCertificate, directory.resolve(SERVER_CERTIFICATE));
        }
        makeReadable(PemFiles.writeCertificates(directory.resolve(TRUSTED_CA), trustedCas));
        return directory;
    }

    private static Path directory(Path root, String name) throws IOException {
        return Files.createDirectory(root.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(DIRECTORY_PERMISSIONS)));
    }

    private static void copyReadable(Path source, Path target) throws IOException {
        makeReadable(Files.copy(source, target));
    }

    private static void makeReadable(Path file) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(FILE_PERMISSIONS));
    }

    private static X509Certificate readCertificate(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }
}
