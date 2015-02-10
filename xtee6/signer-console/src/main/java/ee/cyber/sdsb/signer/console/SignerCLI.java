package ee.cyber.sdsb.signer.console;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.StringUtils;

import akka.actor.ActorSystem;
import asg.cliche.CLIException;
import asg.cliche.Command;
import asg.cliche.InputConverter;
import asg.cliche.Param;
import asg.cliche.Shell;
import asg.cliche.ShellFactory;

import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.*;

import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static ee.cyber.sdsb.signer.console.Utils.*;

public class SignerCLI {

    static boolean verbose;

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_SIGNER)
            .load();
    }

    public static final InputConverter[] CLI_INPUT_CONVERTERS = {
        new InputConverter() {
            @Override
            @SuppressWarnings("rawtypes")
            public Object convertInput(String original, Class toClass)
                    throws Exception {
                if (toClass.equals(ClientId.class)) {
                    return createClientId(original);
                } else {
                    return null;
                }
            }
        },
    };

    @Command(description = "Lists all tokens")
    public void listTokens() throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        tokens.forEach(t -> printTokenInfo(t, verbose));
    }

    @Command(description = "Lists all keys on all tokens")
    public void listKeys() throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        tokens.forEach(t -> {
            printTokenInfo(t, verbose);

            if (verbose) {
                System.out.println("Keys: ");
            }

            t.getKeyInfo().forEach(k -> {
                printKeyInfo(k, verbose, "\t");
            });

            System.out.println();
        });
    }

    @Command(description = "Lists all certs on all keys on all tokens")
    public void listCerts() throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        tokens.forEach(t -> {
            printTokenInfo(t, verbose);

            if (verbose) {
                System.out.println("Keys: ");
            }

            t.getKeyInfo().forEach(k -> {
                printKeyInfo(k, verbose, "\t");

                if (verbose) {
                    System.out.println("\tCerts: ");
                }

                printCertInfo(k, verbose, "\t\t");
            });

            System.out.println();
        });
    }

    @Command(description = "Sets token friendly name")
    public void setTokenFriendlyName(
            @Param(name = "tokenId", description = "Token ID")
                String tokenId,
            @Param(name = "friendlyName", description = "Friendly name")
                String friendlyName) throws Exception {
        SignerClient.execute(new SetTokenFriendlyName(tokenId, friendlyName));
    }

    @Command(description = "Sets key friendly name")
    public void setKeyFriendlyName(
            @Param(name = "keyId", description = "Key ID")
                String keyId,
            @Param(name = "friendlyName", description = "Friendly name")
                String friendlyName) throws Exception {
        SignerClient.execute(new SetKeyFriendlyName(keyId, friendlyName));
    }

    @Command(description = "Returns key ID for certificate hash")
    public void getKeyIdForCertHash(
            @Param(name = "certHash", description = "Certificare hash")
                String certHash) throws Exception {
        GetKeyIdForCertHashResponse response =
                SignerClient.execute(new GetKeyIdForCertHash(certHash));
        System.out.println(response.getKeyId());
    }

    @Command(description = "Returns all certificates of a member")
    public void getMemberCerts(
            @Param(name = "memberId", description = "Member identifier")
                ClientId memberId) throws Exception {
        GetMemberCertsResponse response =
                SignerClient.execute(new GetMemberCerts(memberId));
        System.out.println("Certs of member " + memberId + ":");
        for (CertificateInfo cert : response.getCerts()) {
            System.out.println("\tId:\t" + cert.getId());
            System.out.println("\t\tStatus:\t" + cert.getStatus());
            System.out.println("\t\tActive:\t" + cert.isActive());
        }
    }

    @Command(description = "Activates a certificate")
    public void activateCertificate(
            @Param(name = "certId", description = "Certificate ID")
                String certId) throws Exception {
        SignerClient.execute(new ActivateCert(certId, true));
    }

    @Command(description = "Deactivates a certificate")
    public void deactivateCertificate(
            @Param(name = "certId", description = "Certificate ID")
                String certId) throws Exception {
        SignerClient.execute(new ActivateCert(certId, false));
    }

    @Command(description = "Deletes a key")
    public void deleteKey(
            @Param(name = "keyId", description = "Key ID")
                String keyId) throws Exception {
        SignerClient.execute(new DeleteKey(keyId, true));
    }

    @Command(description = "Deletes a certificate")
    public void deleteCertificate(
            @Param(name = "certId", description = "Certificate ID")
                String certId) throws Exception {
        SignerClient.execute(new DeleteCert(certId));
    }

    @Command(description = "Deletes a certificate request")
    public void deleteCertificateRequest(
            @Param(name = "certReqId", description = "Certificate request ID")
                String certReqId) throws Exception {
        SignerClient.execute(new DeleteCertRequest(certReqId));
    }

    @Command(description = "Returns suitable authentication key for security server")
    public void getAuthenticationKey(
            @Param(name = "clientId", description = "Member identifier")
                ClientId clientId,
            @Param(name = "serverCode", description = "Security server code")
                String serverCode) throws Exception {
        SecurityServerId serverId =
                SecurityServerId.create(clientId, serverCode);
        AuthKeyInfo authKey = SignerClient.execute(new GetAuthKey(serverId));

        System.out.println("Auth key:");
        System.out.println("\tAlias:\t" + authKey.getAlias());
        System.out.println("\tKeyStore:\t" + authKey.getKeyStoreFileName());
        System.out.println("\tCert:   " + authKey.getCert());
    }

    @Command(description = "Returns signing info for member")
    public void getMemberSigningInfo(
            @Param(name = "clientId", description = "Member identifier")
                ClientId clientId) throws Exception {
        MemberSigningInfo response =
                SignerClient.execute(new GetMemberSigningInfo(clientId));
        System.out.println("Signing info for member " + clientId + ":");
        System.out.println("\tKey id: " + response.getKeyId());
        System.out.println("\tCert:   " + response.getCert());
    }

    @Command(description = "Imports a certificate")
    public void importCertificate(
            @Param(name = "file", description = "Certificate file (PEM)")
                String file,
            @Param(name = "clientId", description = "Member identifier")
                ClientId clientId) throws Exception {
        try {
            byte[] certBytes = fileToBytes(file);
            ImportCertResponse response =
                    SignerClient.execute(
                            new ImportCert(certBytes, "SAVED", clientId));
            System.out.println(response.getKeyId());
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
        }
    }

    @Command(description = "Log in token", abbrev = "li")
    public void loginToken(
            @Param(name = "tokenId", description = "Token ID")
                String tokenId) throws Exception {
        char[] pin = System.console().readPassword("PIN: ");

        PasswordStore.storePassword(tokenId, pin);
        SignerClient.execute(new ActivateToken(tokenId, true));
    }

    @Command(description = "Log out token", abbrev = "lo")
    public void logoutToken(
            @Param(name = "tokenId", description = "Token ID")
                String tokenId) throws Exception {
        PasswordStore.storePassword(tokenId, null);
        SignerClient.execute(new ActivateToken(tokenId, false));
    }

    @Command(description = "Initialize software token")
    public void initSoftwareToken() throws Exception {
        char[] pin = System.console().readPassword("PIN: ");
        char[] pin2 = System.console().readPassword("retype PIN: ");

        if (!Arrays.equals(pin, pin2)) {
            System.out.println("ERROR: PINs do not match");
            return;
        }

        SignerClient.execute(new InitSoftwareToken(pin));
    }

    @Command(description = "Sign some data")
    public void sign(
            @Param(name = "keyId", description = "Key ID")
                String keyId,
            @Param(name = "data", description = "Data to sign (<data1> <data2> ...)")
                String... data) throws Exception {
        String algorithm = "SHA512withRSA";
        for (String d : data) {
            byte[] digest = calculateDigest(getDigestAlgorithmId(algorithm),
                    d.getBytes(StandardCharsets.UTF_8));
            SignResponse response = SignerClient.execute(
                    new Sign(keyId, algorithm, digest));
            System.out.println("Signature: "
                    + Arrays.toString(response.getSignature()));
        }
    }

    @Command(description = "Sign a file")
    public void signFile(
            @Param(name = "keyId", description = "Key ID")
                String keyId,
            @Param(name = "fileName", description = "File name")
                String fileName) throws Exception {
        String algorithm = "SHA512withRSA";
        byte[] digest = calculateDigest(
                getDigestAlgorithmId(algorithm), fileToBytes(fileName));

        SignResponse response =
                SignerClient.execute(new Sign(keyId, algorithm, digest));
        System.out.println("Signature: "
                + Arrays.toString(response.getSignature()));
    }

    @Command(description = "Benchmark signing")
    public void signBenchmark(
            @Param(name = "keyId", description = "Key ID")
                String keyId) throws Exception {
        String algorithm = "SHA512withRSA";
        String data = "Hello world!";
        byte[] digest = calculateDigest(
                getDigestAlgorithmId(algorithm),
                data.getBytes(StandardCharsets.UTF_8));

        int iterations = 10;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
             SignerClient.execute(new Sign(keyId, algorithm, digest));
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Signed " + iterations + " times in "
                + duration + " milliseconds");
    }

    @Command(description = "Generate key on token")
    public void generateKey(
            @Param(name = "tokenId", description = "Token ID")
                String tokenId) throws Exception {
        KeyInfo response = SignerClient.execute(new GenerateKey(tokenId));

        System.out.println(response.getId());
    }

    @Command(description = "Generate certificate request")
    public void generateCertRequest(
            @Param(name = "keyId", description = "Key ID")
                String keyId,
            @Param(name = "memberId", description = "Member identifier")
                ClientId memberId,
            @Param(name = "usage", description = "Key usage (a - auth, s - sign)")
                String usage,
            @Param(name = "subjectName", description = "Subject name")
                String subjectName) throws Exception {
        KeyUsageInfo keyUsage = "a".equals(usage)
                ? KeyUsageInfo.AUTHENTICATION : KeyUsageInfo.SIGNING;
        GenerateCertRequest request =
                new GenerateCertRequest(keyId, memberId, keyUsage, subjectName);
        GenerateCertRequestResponse response = SignerClient.execute(request);

        bytesToFile(keyId + ".csr", response.getCertRequest());
    }

    @Command(description = "Create dummy public key certificate")
    public void dummyCert(
            @Param(name = "keyId", description = "Key ID")
                String keyId,
            @Param(name = "cn", description = "Common name")
                String cn) throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        Date notBefore = cal.getTime();
        cal.add(Calendar.YEAR, 2);
        Date notAfter = cal.getTime();

        ClientId memberId = ClientId.create("FOO", "BAR", "BAZ");

        GenerateSelfSignedCert request =
                new GenerateSelfSignedCert(keyId, cn, notBefore, notAfter,
                        KeyUsageInfo.SIGNING, memberId);

        GenerateSelfSignedCertResponse response = SignerClient.execute(request);
        X509Certificate cert = readCertificate(response.getCertificateBytes());

        System.out.println("Certificate base64:");
        System.out.println(encodeBase64(cert.getEncoded()));
        bytesToFile(keyId + ".crt", cert.getEncoded());
        base64ToFile(keyId + ".crt.b64", cert.getEncoded());
    }

    @Command(description = "Check if batch signing is available on token")
    public void batchSigningEnabled(
            @Param(name = "keyId", description = "Key ID")
                String keyId) throws Exception {
        Boolean enabled = SignerClient.execute(
                new GetTokenBatchSigningEnabled(keyId));
        if (enabled) {
            System.out.println("Batch signing is enabled");
        } else {
            System.out.println("Batch signing is NOT enabled");
        }
    }

    @Command(description = "Show certificate")
    public void showCertificate(
            @Param(name = "certId", description = "Certificate ID")
                String certId) throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        for (TokenInfo token : tokens) {
            for (KeyInfo key : token.getKeyInfo()) {
                for (CertificateInfo cert : key.getCerts()) {
                    if (certId.equals(cert.getId())) {
                        X509Certificate x509 =
                                readCertificate(cert.getCertificateBytes());
                        System.out.println(x509);
                        return;
                    }
                }
            }
        }

        System.out.println("Certificate " + certId + " not found");
    }

    // ------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        CommandLine cmd = getCommandLine(args);

        if (cmd.hasOption("verbose")) {
            verbose = true;
        }

        if (cmd.hasOption("help")) {
            processCommandAndExit("?list");
            return;
        }

        ActorSystem actorSystem = ActorSystem.create("SignerConsole",
                ConfigFactory.load().getConfig("signer-console"));
        try {
            SignerClient.init(actorSystem);

            String[] arguments = cmd.getArgs();
            if (arguments.length > 0) {
                processCommandAndExit(StringUtils.join(arguments, " "));
            } else {
                startCommandLoop();
            }
        } finally {
            actorSystem.shutdown();
        }
    }

    private static void startCommandLoop() throws IOException {
        String prompt = "signer@" + SystemProperties.getSignerPort();

        String description = "Enter '?list' to get list of available commands\n";
        description += "Enter '?help <command>' to get command description\n";
        description += "\nNOTE: Member identifier is entered as "
                + "\"<INSTANCE> <CLASS> <CODE>\" (in quotes)\n";

        getShell(prompt, description).commandLoop();
    }

    private static void processCommandAndExit(String command)
            throws CLIException {
        getShell("", "").processLine(command);
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new BasicParser();

        Options options = new Options();

        options.addOption("h", "help", false, "shows available commands");
        options.addOption("v", "verbose", false, "more detailed output");

        return parser.parse(options, args);
    }

    private static Shell getShell(String prompt, String description) {
        return ShellFactory.createConsoleShell(prompt, description,
                new SignerCLI());
    }
}
