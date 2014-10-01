package ee.cyber.sdsb.signer.console;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import akka.actor.ActorSystem;
import asg.cliche.Command;
import asg.cliche.InputConverter;
import asg.cliche.Param;
import asg.cliche.ShellFactory;

import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.*;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;

public class SignerCLI {

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

    @Command(description="Lists all tokens (compact)")
    public void list() throws Exception {
        listTokens(false);
    }

    @Command(description="Lists all tokens (verbosely)")
    public void listTokens(
            @Param(name="verbose", description="True for more verbose output")
                boolean verbose) throws Exception {

        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());

        System.out.println("Tokens (" + tokens.size() + "):");
        for (TokenInfo token : tokens) {
            System.out.println("============================================");
            System.out.println("Token type:    " + token.getType());
            System.out.println("Token id:      " + token.getId());
            System.out.println("Friendly name: " + token.getFriendlyName());
            System.out.println("Read-Only:     " + token.isReadOnly());
            System.out.println("Available:     " + token.isAvailable());
            System.out.println("Active:        " + token.isActive());
            System.out.println("Status:        " + token.getStatus());
            System.out.println("Serial number: " + token.getSerialNumber());
            System.out.println("Label:         " + token.getLabel());

            if (verbose) {
                System.out.println("TokenInfo:");
                for (Entry<String, String> e :
                        token.getTokenInfo().entrySet()) {
                    System.out.println("\t" + e.getKey() + "\t\t"
                        + e.getValue());
                }
            }

            System.out.println("Keys:");
            for (KeyInfo key : token.getKeyInfo()) {
                System.out.println("\tId:        " + key.getId());
                System.out.println("\tName:      " + key.getFriendlyName());
                System.out.println("\tUsage:     " + key.getUsage());
                System.out.println("\tAvailable: " + key.isAvailable());

                if (verbose) {
                    if (key.getPublicKey() != null) {
                        System.out.println("\tPublic key (Base64):\n"
                                + key.getPublicKey());
                    } else {
                        System.out.println("\t<no public key available>");
                    }
                }

                if (!key.getCerts().isEmpty()) {
                    System.out.println("\t\tCerts:");
                    for (CertificateInfo cert : key.getCerts()) {
                        System.out.println("\t\t\tId:            "
                                + cert.getId());
                        System.out.println("\t\t\tStatus:        "
                                + cert.getStatus());
                        System.out.println("\t\t\tMember:        "
                                + cert.getMemberId());
                        System.out.println("\t\t\tHash:          "
                                + certHash(cert.getCertificateBytes()));
                        System.out.println("\t\t\tOCSP:          "
                                + (cert.getOcspBytes() != null ? "yes" : "no"));
                        System.out.println("\t\t\tSaved to conf: "
                                + cert.isSavedToConfiguration());
                    }
                }
                if (!key.getCertRequests().isEmpty()) {
                    System.out.println("\t\tCert requests:");
                    for (CertRequestInfo certReq : key.getCertRequests()) {
                        System.out.println("\t\t\tId:            "
                                + certReq.getId());
                        System.out.println("\t\t\tMember:        "
                                + certReq.getMemberId());
                        System.out.println("\t\t\tSubject name:  "
                                + certReq.getSubjectName());
                    }
                }
                System.out.println();
                System.out.println("----------------------------------------");
            }
        }
    }

    @Command(description="Sets token friendly name")
    public void setTokenFriendlyName(
            @Param(name="tokenId", description="Token ID")
                String tokenId,
            @Param(name="friendlyName", description="Friendly name")
                String friendlyName) throws Exception {
        SignerClient.execute(new SetTokenFriendlyName(tokenId, friendlyName));
    }

    @Command(description="Sets key friendly name")
    public void setKeyFriendlyName(
            @Param(name="keyId", description="Key ID")
                String keyId,
            @Param(name="friendlyName", description="Friendly name")
                String friendlyName) throws Exception {
        SignerClient.execute(new SetKeyFriendlyName(keyId, friendlyName));
    }

    @Command(description="Returns key ID for certificate hash")
    public void getKeyIdForCertHash(
            @Param(name="certHash", description="Certificare hash")
                String certHash) throws Exception {
        GetKeyIdForCertHashResponse response =
                SignerClient.execute(new GetKeyIdForCertHash(certHash));
        System.out.println("Key ID : " + response.getKeyId());
    }

    @Command(description="Returns all certificates of a member")
    public void getMemberCerts(
            @Param(name="memberId", description="Member identifier")
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

    @Command(description="Activates/deactivates a certificate")
    public void activateCertificate(
            @Param(name="certId", description="Certificate ID")
                String certId,
            @Param(name="active", description="True, if activate")
                boolean active) throws Exception {
        SignerClient.execute(new ActivateCert(certId, active));
    }

    @Command(description="Deletes a key")
    public void deleteKey(
            @Param(name="keyId", description="Key ID")
                String keyId) throws Exception {
        SignerClient.execute(new DeleteKey(keyId));
    }

    @Command(description="Deletes a certificate")
    public void deleteCertificate(
            @Param(name="certId", description="Certificate ID")
                String certId) throws Exception {
        SignerClient.execute(new DeleteCert(certId));
    }

    @Command(description="Deletes a certificate request")
    public void deleteCertificateRequest(
            @Param(name="certReqId", description="Certificate request ID")
                String certReqId) throws Exception {
        SignerClient.execute(new DeleteCertRequest(certReqId));
    }

    @Command(description="Returns suitable authentication key for security server")
    public void getAuthenticationKey(
            @Param(name="clientId", description="Member identifier")
                ClientId clientId,
            @Param(name="serverCode", description="Security server code")
                String serverCode) throws Exception {
        SecurityServerId serverId =
                SecurityServerId.create(clientId, serverCode);
        AuthKeyInfo authKey = SignerClient.execute(new GetAuthKey(serverId));

        System.out.println("Auth key:");
        System.out.println("\tAlias:\t" + authKey.getAlias());
        System.out.println("\tKeyStore:\t" + authKey.getKeyStoreFileName());
        System.out.println("\tCert:   " + authKey.getCert());
    }

    @Command(description="Returns signing info for member")
    public void getMemberSigningInfo(
            @Param(name="clientId", description="Member identifier")
                ClientId clientId) throws Exception {
        MemberSigningInfo response =
                SignerClient.execute(new GetMemberSigningInfo(clientId));
        System.out.println("Signing info for member " + clientId + ":");
        System.out.println("\tKey id: " + response.getKeyId());
        System.out.println("\tCert:   " + response.getCert());
    }

    @Command(description="Imports a certificate")
    public void importCertificate(
            @Param(name="file", description="Certificate file (PEM)")
                String file,
            @Param(name="status", description="Initial status (eg. SAVED)")
                String status,
            @Param(name="clientId", description="Member identifier")
                ClientId clientId) throws Exception {
        try {
            byte[] certBytes = fileToBytes(file);
            ImportCertResponse response =
                    SignerClient.execute(
                            new ImportCert(certBytes, status, clientId));
            System.out.println("Imported certificate to key "
                    + response.getKeyId());
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
        }
    }

    @Command(description="Log in token", abbrev="li")
    public void login(
            @Param(name="tokenId", description="Token ID")
                String tokenId,
            @Param(name="pin", description="PIN")
                String pin) throws Exception {
        PasswordStore.storePassword(tokenId, pin.toCharArray());
        SignerClient.execute(new ActivateToken(tokenId, true));
    }

    @Command(description="Log out token", abbrev="lo")
    public void logout(
            @Param(name="tokenId", description="Token ID")
                String tokenId) throws Exception {
        PasswordStore.storePassword(tokenId, null);
        SignerClient.execute(new ActivateToken(tokenId, false));
    }

    @Command(description="Initialize software token")
    public void initializeSoftToken(
            @Param(name="pin", description="PIN")
                String pin) throws Exception {
        SignerClient.execute(new InitSoftwareToken(pin.toCharArray()));
    }

    @Command(description="Sign some data")
    public void sign(
            @Param(name="keyId", description="Key ID")
                String keyId,
            @Param(name="data", description="Data to sign (<data1> <data2> ...)")
                String... data) throws Exception {
        String algorithm = "SHA512withRSA";
        for (String d : data) {
            byte[] digest = calculateDigest(getDigestAlgorithmId(algorithm),
                    d.getBytes(StandardCharsets.UTF_8));
            SignResponse response = SignerClient.execute(
                    new Sign(keyId, algorithm, digest));
            System.out.println("Signature: " +
                    Arrays.toString(response.getSignature()));
        }
    }

    @Command(description="Sign a file")
    public void signFile(
            @Param(name="keyId", description="Key ID")
                String keyId,
            @Param(name="fileName", description="File name")
                String fileName) throws Exception {
        String algorithm = "SHA512withRSA";
        byte[] digest = calculateDigest(
                getDigestAlgorithmId(algorithm), fileToBytes(fileName));

        SignResponse response =
                SignerClient.execute(new Sign(keyId, algorithm, digest));
        System.out.println("Signature: " +
                Arrays.toString(response.getSignature()));
    }

    @Command(description="Benchmark signing")
    public void signBenchmark(
            @Param(name="keyId", description="Key ID")
                String keyId) throws Exception {
        String algorithm = "SHA512withRSA";
        String data = "Hello world!";
        byte[] digest = calculateDigest(
                getDigestAlgorithmId(algorithm),
                data.getBytes(StandardCharsets.UTF_8));

        int iterations = 10;
        long startTime = System.currentTimeMillis();
        for (int i = 0 ; i < iterations; i++) {
             SignerClient.execute(new Sign(keyId, algorithm, digest));
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Signed " + iterations + " times in "
                + duration + " milliseconds");
    }

    @Command(description="Generate key on token")
    public void generateKey(
            @Param(name="tokenId", description="Token ID")
                String tokenId) throws Exception {
        KeyInfo response = SignerClient.execute(new GenerateKey(tokenId));
        System.out.println("Key ID: " + response.getId());
        System.out.println("Public key: " + response.getPublicKey());
    }

    @Command(description="Generate certificate request")
    public void generateCertRequest(
            @Param(name="keyId", description="Key ID")
                String keyId,
            @Param(name="memberId", description="Member identifier")
                ClientId memberId,
            @Param(name="usage", description="Key usage (a - auth, s - sign)")
                String usage,
            @Param(name="subjectName", description="Subject name")
                String subjectName) throws Exception {
        KeyUsageInfo keyUsage = "a".equals(usage)
                ? KeyUsageInfo.AUTHENTICATION : KeyUsageInfo.SIGNING;
        GenerateCertRequest request =
                new GenerateCertRequest(keyId, memberId, keyUsage, subjectName);
        GenerateCertRequestResponse response = SignerClient.execute(request);

        bytesToFile(keyId + ".csr", response.getCertRequest());
    }

    @Command(description="Create dummy public key certificate")
    public void dummyCert(
            @Param(name="keyId", description="Key ID")
                String keyId,
            @Param(name="cn", description="Common name")
                String cn) throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        for (TokenInfo token : tokens) {
            for (KeyInfo key : token.getKeyInfo()) {
                if (key.getId().equals(keyId)) {
                    if (key.getPublicKey() == null) {
                        throw new RuntimeException("Key '" + keyId
                                + "' has no public key");
                    }

                    PublicKey pk = readX509PublicKey(decodeBase64(
                            key.getPublicKey()));
                    X509Certificate cert =
                            DummyCertBuilder.build(keyId, cn, pk);
                    System.out.println("Certificate base64:");
                    System.out.println(encodeBase64(cert.getEncoded()));
                    bytesToFile(keyId + ".crt", cert.getEncoded());
                    base64ToFile(keyId + ".crt.b64",cert.getEncoded());
                    return;
                }
            }
        }

        throw new RuntimeException("Key '" + keyId + "' not found");
    }

    @Command(description="Create dummy public key certificate")
    public void batchSigningEnabled(
            @Param(name="keyId", description="Key ID")
                String keyId) throws Exception {
        Boolean enabled = SignerClient.execute(
                new GetTokenBatchSigningEnabled(keyId));
        if (enabled) {
            System.out.println("Batch signing is enabled");
        } else {
            System.out.println("Batch signing is NOT enabled");
        }
    }

    @Command(description="Show certificate")
    public void showCertificate(
            @Param(name="certId", description="Certificate ID")
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
        if (args.length > 0) {
            System.setProperty(SystemProperties.SIGNER_PORT, args[0]);
        }

        String prompt = "signer@" + SystemProperties.getSignerPort();

        ActorSystem actorSystem = ActorSystem.create("SignerClient",
                ConfigFactory.load().getConfig("signer-client"));
        try {
            SignerClient.init(actorSystem);

            String description = "Enter '?list' to get list of available commands\n";
            description += "Enter '?help <command>' to get command description\n";
            description += "\nNOTE: Member identifier is entered as " +
                    "\"<INSTANCE> <CLASS> <CODE>\" (in quotes)\n";

            ShellFactory.createConsoleShell(
                    prompt, description, new SignerCLI()).commandLoop();
        } finally {
            actorSystem.shutdown();
        }
    }

    private static ClientId createClientId(String string) throws Exception {
        String[] parts = string.split(" ");
        if (parts.length < 3) {
            throw new Exception("Must specify all parts for ClientId");
        }

        String subsystem = parts.length > 3 ? parts[3] : null;
        return ClientId.create(parts[0], parts[1], parts[2], subsystem);
    }

    private static String certHash(byte[] certBytes) {
        try {
            return calculateCertHexHash(certBytes);
        } catch (Exception e) {
            System.out.println("Failed to caluclate cert hash");
            return "";
        }
    }

    private static byte[] fileToBytes(String fileName) throws Exception {
        return IOUtils.toByteArray(new FileInputStream(fileName));
    }

    private static void bytesToFile(String file, byte[] bytes)
            throws Exception {
        try {
            IOUtils.write(bytes, new FileOutputStream(file));
            System.out.println("Saved to file " + file);
        } catch (Exception e) {
            System.out.println("ERROR: Cannot save to file" + file + ":" + e);
        }
    }

    private static void base64ToFile(String file, byte[] bytes)
            throws Exception {
        try {
            IOUtils.write(encodeBase64(bytes), new FileOutputStream(file));
            System.out.println("Saved to file " + file);
        } catch (Exception e) {
            System.out.println("ERROR: Cannot save to file" + file + ":" + e);
        }
    }
}
