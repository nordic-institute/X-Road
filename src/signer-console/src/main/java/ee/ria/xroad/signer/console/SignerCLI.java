/**
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
package ee.ria.xroad.signer.console;

import ee.ria.xroad.common.AuditLogger;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.MemberSigningInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ActivateCert;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.protocol.message.DeleteCertRequest;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequestResponse;
import ee.ria.xroad.signer.protocol.message.GenerateKey;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCert;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCertResponse;
import ee.ria.xroad.signer.protocol.message.GetAuthKey;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHash;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.ria.xroad.signer.protocol.message.GetMemberCerts;
import ee.ria.xroad.signer.protocol.message.GetMemberCertsResponse;
import ee.ria.xroad.signer.protocol.message.GetMemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetSignMechanism;
import ee.ria.xroad.signer.protocol.message.GetSignMechanismResponse;
import ee.ria.xroad.signer.protocol.message.GetTokenBatchSigningEnabled;
import ee.ria.xroad.signer.protocol.message.ImportCert;
import ee.ria.xroad.signer.protocol.message.ImportCertResponse;
import ee.ria.xroad.signer.protocol.message.InitSoftwareToken;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import ee.ria.xroad.signer.protocol.message.SetKeyFriendlyName;
import ee.ria.xroad.signer.protocol.message.SetTokenFriendlyName;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;
import ee.ria.xroad.signer.protocol.message.UpdateSoftwareTokenPin;

import akka.actor.ActorSystem;
import asg.cliche.CLIException;
import asg.cliche.Command;
import asg.cliche.InputConverter;
import asg.cliche.Param;
import asg.cliche.Shell;
import asg.cliche.ShellFactory;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.AuditLogger.XROAD_USER;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.ACTIVATE_THE_CERTIFICATE_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.CERT_FILE_NAME_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.CERT_ID_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.CERT_REQUEST_ID_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.CLIENT_IDENTIFIER_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.CSR_FORMAT_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.DEACTIVATE_THE_CERTIFICATE_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.DELETE_THE_CERT_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.DELETE_THE_CERT_REQUEST_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.DELETE_THE_KEY_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.GENERATE_A_CERT_REQUEST_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.GENERATE_A_KEY_ON_THE_TOKEN_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.IMPORT_A_CERTIFICATE_FROM_THE_FILE;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.INITIALIZE_THE_SOFTWARE_TOKEN_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.KEY_FRIENDLY_NAME_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.KEY_ID_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.KEY_LABEL_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.KEY_USAGE_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.LOGOUT_FROM_THE_TOKEN_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.LOG_INTO_THE_TOKEN;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.SET_A_FRIENDLY_NAME_TO_THE_KEY_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.SET_A_FRIENDLY_NAME_TO_THE_TOKEN_EVENT;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.SUBJECT_NAME_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.TOKEN_FRIENDLY_NAME_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.TOKEN_ID_PARAM;
import static ee.ria.xroad.signer.console.AuditLogEventsAndParams.UPDATE_SOFTWARE_TOKEN_PIN;
import static ee.ria.xroad.signer.console.Utils.base64ToFile;
import static ee.ria.xroad.signer.console.Utils.bytesToFile;
import static ee.ria.xroad.signer.console.Utils.createClientId;
import static ee.ria.xroad.signer.console.Utils.fileToBytes;
import static ee.ria.xroad.signer.console.Utils.printCertInfo;
import static ee.ria.xroad.signer.console.Utils.printKeyInfo;
import static ee.ria.xroad.signer.console.Utils.printTokenInfo;

/**
 * Signer command line interface.
 */
public class SignerCLI {

    private static final String APP_NAME = "xroad-signer-console";
    private static final String PIN_PROMPT = "PIN: ";
    private static final int BENCHMARK_ITERATIONS = 10;
    static boolean verbose;

    static {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .with(CONF_FILE_SIGNER)
                .load();
    }

    /**
     * Shell input converters
     *
     * @see <a href="http://cliche.sourceforge.net/">Cliche Manual</a>
     */
    @SuppressWarnings({"squid:S1873", "squid:S2386"})
    public static final InputConverter[] CLI_INPUT_CONVERTERS = {
            new InputConverter() {
                @Override
                @SuppressWarnings("rawtypes")
                public Object convertInput(String original, Class toClass) throws Exception {
                    if (toClass.equals(ClientId.class)) {
                        return createClientId(original);
                    } else {
                        return null;
                    }
                }
            },
    };

    /**
     * Lists all tokens.
     *
     * @throws Exception if an error occurs
     */
    @Command(description = "Lists all tokens")
    public void listTokens() throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        tokens.forEach(t -> printTokenInfo(t, verbose));
    }

    /**
     * Lists all keys on all tokens.
     *
     * @throws Exception if an error occurs
     */
    @Command(description = "Lists all keys on all tokens")
    public void listKeys() throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        tokens.forEach(t -> {
            printTokenInfo(t, verbose);

            if (verbose) {
                System.out.println("Keys: ");
            }

            t.getKeyInfo().forEach(k -> printKeyInfo(k, verbose, "\t"));

            System.out.println();
        });
    }

    /**
     * Lists all certs on all keys on all tokens.
     *
     * @throws Exception if an error occurs
     */
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

    /**
     * Sets token friendly name.
     *
     * @param tokenId      token id
     * @param friendlyName friendly name
     * @throws Exception if an error occurs
     */
    @Command(description = "Sets token friendly name")
    public void setTokenFriendlyName(
            @Param(name = "tokenId", description = "Token ID") String tokenId,
            @Param(name = "friendlyName", description = "Friendly name") String friendlyName) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(TOKEN_ID_PARAM, tokenId);
        logData.put(TOKEN_FRIENDLY_NAME_PARAM, friendlyName);

        try {
            SignerClient.execute(new SetTokenFriendlyName(tokenId, friendlyName));

            AuditLogger.log(SET_A_FRIENDLY_NAME_TO_THE_TOKEN_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(SET_A_FRIENDLY_NAME_TO_THE_TOKEN_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Sets key friendly name.
     *
     * @param keyId        key id
     * @param friendlyName friendly name
     * @throws Exception if an error occurs
     */
    @Command(description = "Sets key friendly name")
    public void setKeyFriendlyName(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "friendlyName", description = "Friendly name") String friendlyName) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(KEY_ID_PARAM, keyId);
        logData.put(KEY_FRIENDLY_NAME_PARAM, friendlyName);

        try {
            SignerClient.execute(new SetKeyFriendlyName(keyId, friendlyName));

            AuditLogger.log(SET_A_FRIENDLY_NAME_TO_THE_KEY_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(SET_A_FRIENDLY_NAME_TO_THE_KEY_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Returns key ID for certificate hash.
     *
     * @param certHash certificate hash
     * @throws Exception if an error occurs
     */
    @Command(description = "Returns key ID for certificate hash")
    public void getKeyIdForCertHash(@Param(name = "certHash", description = "Certificare hash") String certHash)
            throws Exception {
        GetKeyIdForCertHashResponse response = SignerClient.execute(new GetKeyIdForCertHash(certHash));

        System.out.println(response.getKeyId());
    }

    /**
     * Returns all certificates of a member.
     *
     * @param memberId member if
     * @throws Exception if an error occurs
     */
    @Command(description = "Returns all certificates of a member")
    public void getMemberCerts(
            @Param(name = "memberId", description = "Member identifier") ClientId memberId) throws Exception {
        GetMemberCertsResponse response = SignerClient.execute(new GetMemberCerts(memberId));

        System.out.println("Certs of member " + memberId + ":");

        for (CertificateInfo cert : response.getCerts()) {
            System.out.println("\tId:\t" + cert.getId());
            System.out.println("\t\tStatus:\t" + cert.getStatus());
            System.out.println("\t\tActive:\t" + cert.isActive());
        }
    }

    /**
     * Activates a certificate.
     *
     * @param certId certificate id
     * @throws Exception if an error occurs
     */
    @Command(description = "Activates a certificate")
    public void activateCertificate(@Param(name = "certId", description = "Certificate ID") String certId)
            throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(CERT_ID_PARAM, certId);

        try {
            SignerClient.execute(new ActivateCert(certId, true));

            AuditLogger.log(ACTIVATE_THE_CERTIFICATE_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(ACTIVATE_THE_CERTIFICATE_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Deactivates a certificate.
     *
     * @param certId certificate id
     * @throws Exception if an error occurs
     */
    @Command(description = "Deactivates a certificate")
    public void deactivateCertificate(@Param(name = "certId", description = "Certificate ID") String certId)
            throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(CERT_ID_PARAM, certId);

        try {
            SignerClient.execute(new ActivateCert(certId, false));

            AuditLogger.log(DEACTIVATE_THE_CERTIFICATE_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(DEACTIVATE_THE_CERTIFICATE_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Deletes a key.
     *
     * @param keyId key id
     * @throws Exception if an error occurs
     */
    @Command(description = "Deletes a key")
    public void deleteKey(@Param(name = "keyId", description = "Key ID") String keyId) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(KEY_ID_PARAM, keyId);

        try {
            SignerClient.execute(new DeleteKey(keyId, true));

            AuditLogger.log(DELETE_THE_KEY_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(DELETE_THE_KEY_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Deletes a certificate.
     *
     * @param certId certificate id
     * @throws Exception if an error occurs
     */
    @Command(description = "Deletes a certificate")
    public void deleteCertificate(@Param(name = "certId", description = "Certificate ID") String certId)
            throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(CERT_ID_PARAM, certId);

        try {
            SignerClient.execute(new DeleteCert(certId));

            AuditLogger.log(DELETE_THE_CERT_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(DELETE_THE_CERT_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Deletes a certificate request.
     *
     * @param certReqId certificate request id
     * @throws Exception if an error occurs
     */
    @Command(description = "Deletes a certificate request")
    public void deleteCertificateRequest(
            @Param(name = "certReqId", description = "Certificate request ID") String certReqId) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(CERT_REQUEST_ID_PARAM, certReqId);

        try {
            SignerClient.execute(new DeleteCertRequest(certReqId));

            AuditLogger.log(DELETE_THE_CERT_REQUEST_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(DELETE_THE_CERT_REQUEST_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Returns suitable authentication key for security server.
     *
     * @param clientId   client id
     * @param serverCode server code
     * @throws Exception if an error occurs
     */
    @Command(description = "Returns suitable authentication key for security server")
    public void getAuthenticationKey(@Param(name = "clientId", description = "Member identifier") ClientId clientId,
            @Param(name = "serverCode", description = "Security server code") String serverCode) throws Exception {
        SecurityServerId serverId = SecurityServerId.Conf.create(clientId, serverCode);
        AuthKeyInfo authKey = SignerClient.execute(new GetAuthKey(serverId));

        System.out.println("Auth key:");
        System.out.println("\tAlias:\t" + authKey.getAlias());
        System.out.println("\tKeyStore:\t" + authKey.getKeyStoreFileName());
        System.out.println("\tCert:   " + authKey.getCert());
    }

    /**
     * Returns signing info for member.
     *
     * @param clientId client id
     * @throws Exception if an error occurs
     */
    @Command(description = "Returns signing info for member")
    public void getMemberSigningInfo(@Param(name = "clientId", description = "Member identifier") ClientId clientId)
            throws Exception {
        MemberSigningInfo response = SignerClient.execute(new GetMemberSigningInfo(clientId));

        System.out.println("Signing info for member " + clientId + ":");
        System.out.println("\tKey id:         " + response.getKeyId());
        System.out.println("\tCert:           " + response.getCert());
        System.out.println("\tSign mechanism: " + response.getSignMechanismName());
    }

    /**
     * Imports a certificate.
     *
     * @param file     file
     * @param clientId client id
     * @throws Exception if an error occurs
     */
    @Command(description = "Imports a certificate")
    public void importCertificate(@Param(name = "file", description = "Certificate file (PEM)") String file,
            @Param(name = "clientId", description = "Member identifier") ClientId.Conf clientId) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(CERT_FILE_NAME_PARAM, file);
        logData.put(CLIENT_IDENTIFIER_PARAM, clientId);

        try {
            byte[] certBytes = fileToBytes(file);
            ImportCertResponse response = SignerClient.execute(new ImportCert(certBytes,
                    CertificateInfo.STATUS_REGISTERED, clientId));

            logData.put(KEY_ID_PARAM, response.getKeyId());
            AuditLogger.log(IMPORT_A_CERTIFICATE_FROM_THE_FILE, XROAD_USER, null, logData);

            System.out.println(response.getKeyId());
        } catch (Exception e) {
            AuditLogger.log(IMPORT_A_CERTIFICATE_FROM_THE_FILE, XROAD_USER, null, e.getMessage(), logData);

            System.out.println("ERROR: " + e);
        }
    }

    /**
     * Log in token.
     *
     * @param tokenId token id
     * @throws Exception if an error occurs
     */
    @Command(description = "Log in token", abbrev = "li")
    public void loginToken(@Param(name = "tokenId", description = "Token ID") String tokenId) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(TOKEN_ID_PARAM, tokenId);

        try {
            char[] pin;
            if (System.console() != null) {
                pin = System.console().readPassword(PIN_PROMPT);
            } else {
                pin = new Scanner(System.in).nextLine().toCharArray();
            }
            PasswordStore.storePassword(tokenId, pin);
            SignerClient.execute(new ActivateToken(tokenId, true));
            AuditLogger.log(LOG_INTO_THE_TOKEN, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(LOG_INTO_THE_TOKEN, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Log out token.
     *
     * @param tokenId token id
     * @throws Exception if an error occurs
     */
    @Command(description = "Log out token", abbrev = "lo")
    public void logoutToken(@Param(name = "tokenId", description = "Token ID") String tokenId) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(TOKEN_ID_PARAM, tokenId);

        try {
            PasswordStore.storePassword(tokenId, null);
            SignerClient.execute(new ActivateToken(tokenId, false));

            AuditLogger.log(LOGOUT_FROM_THE_TOKEN_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(LOGOUT_FROM_THE_TOKEN_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Initialize software token
     *
     * @throws Exception if an error occurs
     */
    @Command(description = "Initialize software token")
    public void initSoftwareToken() throws Exception {
        char[] pin = System.console().readPassword(PIN_PROMPT);
        char[] pin2 = System.console().readPassword("retype PIN: ");

        if (!Arrays.equals(pin, pin2)) {
            System.out.println("ERROR: PINs do not match");

            return;
        }

        try {
            SignerClient.execute(new InitSoftwareToken(pin));

            AuditLogger.log(INITIALIZE_THE_SOFTWARE_TOKEN_EVENT, XROAD_USER, null, null);
        } catch (Exception e) {
            AuditLogger.log(INITIALIZE_THE_SOFTWARE_TOKEN_EVENT, XROAD_USER, null, e.getMessage(), null);

            throw e;
        }
    }

    /**
     * Update the pin of a software token.
     *
     * @throws Exception if an error occurs
     */
    @Command(description = "Update software token pin")
    public void updateSoftwareTokenPin() throws Exception {
        String softwareTokenId = "0";
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(TOKEN_ID_PARAM, softwareTokenId);

        try {
            char[] oldPin = System.console().readPassword(PIN_PROMPT);
            char[] newPin = System.console().readPassword("new PIN: ");
            char[] newPin1 = System.console().readPassword("retype new PIN: ");

            if (!Arrays.equals(newPin, newPin1)) {
                throw new Exception("PINs do not match");
            }

            if (newPin.length < 1) {
                throw new Exception("new PIN was empty");
            }

            SignerClient.execute(new UpdateSoftwareTokenPin(softwareTokenId, oldPin, newPin));
            AuditLogger.log(UPDATE_SOFTWARE_TOKEN_PIN, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(UPDATE_SOFTWARE_TOKEN_PIN, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }
    }

    /**
     * Sign some data
     *
     * @param keyId the key id
     * @param data  the data
     * @throws Exception if an error occurs
     */
    @Command(description = "Sign some data")
    public void sign(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "data", description = "Data to sign (<data1> <data2> ...)") String... data) throws Exception {
        String digestAlgoId = CryptoUtils.SHA512_ID;

        GetSignMechanismResponse mechanismResponse = SignerClient.execute(new GetSignMechanism(keyId));

        String signAlgoId = CryptoUtils.getSignatureAlgorithmId(digestAlgoId, mechanismResponse.getSignMechanismName());

        for (String d : data) {
            byte[] digest = calculateDigest(digestAlgoId, d.getBytes(StandardCharsets.UTF_8));
            SignResponse response = SignerClient.execute(new Sign(keyId, signAlgoId, digest));

            System.out.println("Signature: " + Arrays.toString(response.getSignature()));
        }
    }

    /**
     * Sign a file.
     *
     * @param keyId    the key id
     * @param fileName the file name
     * @throws Exception if an error occurs
     */
    @Command(description = "Sign a file")
    public void signFile(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "fileName", description = "File name") String fileName) throws Exception {
        String digestAlgoId = CryptoUtils.SHA512_ID;

        GetSignMechanismResponse mechanismResponse = SignerClient.execute(new GetSignMechanism(keyId));

        String signAlgoId = CryptoUtils.getSignatureAlgorithmId(digestAlgoId, mechanismResponse.getSignMechanismName());

        byte[] digest = calculateDigest(digestAlgoId, fileToBytes(fileName));

        SignResponse response = SignerClient.execute(new Sign(keyId, signAlgoId, digest));

        System.out.println("Signature: " + Arrays.toString(response.getSignature()));
    }

    /**
     * Benchmark signing.
     *
     * @param keyId key id
     * @throws Exception if an error occurs
     */
    @Command(description = "Benchmark signing")
    public void signBenchmark(@Param(name = "keyId", description = "Key ID") String keyId) throws Exception {
        signBenchmark(keyId, BENCHMARK_ITERATIONS);
    }

    /**
     * Benchmark signing.
     *
     * @param keyId key id
     * @throws Exception if an error occurs
     */
    @Command(description = "Benchmark signing")
    public void signBenchmark(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "iterations", description = "iterations") int iterations) throws Exception {
        String data = "Hello world!";
        String digestAlgoId = CryptoUtils.SHA512_ID;

        GetSignMechanismResponse mechanismResponse = SignerClient.execute(new GetSignMechanism(keyId));

        String signAlgoId = CryptoUtils.getSignatureAlgorithmId(digestAlgoId, mechanismResponse.getSignMechanismName());
        final byte[] digest = calculateDigest(digestAlgoId, data.getBytes(StandardCharsets.UTF_8));
        final long startTime = System.nanoTime();
        final Sign cmd = new Sign(keyId, signAlgoId, digest);

        for (int i = 0; i < iterations; i++) {
            SignerClient.execute(cmd);
        }

        final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        System.out.println("Signed " + iterations + " times in " + duration + " milliseconds");
    }

    /**
     * Generate key on token.
     *
     * @param tokenId token id
     * @param label   label
     * @throws Exception if an error occurs
     */
    @Command(description = "Generate key on token")
    public void generateKey(@Param(name = "tokenId", description = "Token ID") String tokenId,
            @Param(name = "label", description = "Key label") String label) throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(TOKEN_ID_PARAM, tokenId);
        logData.put(KEY_LABEL_PARAM, label);

        KeyInfo response;

        try {
            response = SignerClient.execute(new GenerateKey(tokenId, label));

            logData.put(KEY_ID_PARAM, response.getId());
            AuditLogger.log(GENERATE_A_KEY_ON_THE_TOKEN_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(GENERATE_A_KEY_ON_THE_TOKEN_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }

        System.out.println(response.getId());
    }

    /**
     * Generate certificate request.
     *
     * @param keyId       key id
     * @param memberId    member id
     * @param usage       usage
     * @param subjectName subject name
     * @param format      request format
     * @throws Exception if an error occurs
     */
    @Command(description = "Generate certificate request")
    public void generateCertRequest(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "memberId", description = "Member identifier") ClientId.Conf memberId,
            @Param(name = "usage", description = "Key usage (a - auth, s - sign)") String usage,
            @Param(name = "subjectName", description = "Subject name") String subjectName,
            @Param(name = "format", description = "Format of request (der/pem)") String format) throws Exception {
        KeyUsageInfo keyUsage = "a".equals(usage) ? KeyUsageInfo.AUTHENTICATION : KeyUsageInfo.SIGNING;

        CertificateRequestFormat requestFormat = format.equalsIgnoreCase("der")
                ? CertificateRequestFormat.DER : CertificateRequestFormat.PEM;

        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put(KEY_ID_PARAM, keyId);
        logData.put(CLIENT_IDENTIFIER_PARAM, memberId);
        logData.put(KEY_USAGE_PARAM, keyUsage.name());
        logData.put(SUBJECT_NAME_PARAM, subjectName);
        logData.put(CSR_FORMAT_PARAM, requestFormat.name());

        GenerateCertRequestResponse response;

        try {
            GenerateCertRequest request = new GenerateCertRequest(keyId, memberId, keyUsage, subjectName,
                    requestFormat);
            response = SignerClient.execute(request);

            AuditLogger.log(GENERATE_A_CERT_REQUEST_EVENT, XROAD_USER, null, logData);
        } catch (Exception e) {
            AuditLogger.log(GENERATE_A_CERT_REQUEST_EVENT, XROAD_USER, null, e.getMessage(), logData);

            throw e;
        }

        bytesToFile(keyId + ".csr", response.getCertRequest());
    }

    /**
     * Create dummy public key certificate.
     *
     * @param keyId key id
     * @param cn    common name
     * @throws Exception if an error occurs
     */
    @Command(description = "Create dummy public key certificate")
    public void dummyCert(@Param(name = "keyId", description = "Key ID") String keyId,
            @Param(name = "cn", description = "Common name") String cn) throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        Date notBefore = cal.getTime();
        cal.add(Calendar.YEAR, 2);
        Date notAfter = cal.getTime();

        ClientId.Conf memberId = ClientId.Conf.create("FOO", "BAR", "BAZ");

        GenerateSelfSignedCert request = new GenerateSelfSignedCert(keyId, cn, notBefore, notAfter,
                KeyUsageInfo.SIGNING, memberId);

        GenerateSelfSignedCertResponse response = SignerClient.execute(request);
        X509Certificate cert = readCertificate(response.getCertificateBytes());

        System.out.println("Certificate base64:");
        System.out.println(encodeBase64(cert.getEncoded()));

        bytesToFile(keyId + ".crt", cert.getEncoded());
        base64ToFile(keyId + ".crt.b64", cert.getEncoded());
    }

    /**
     * Check if batch signing is available on token.
     *
     * @param keyId key id
     * @throws Exception if an error occurs
     */
    @Command(description = "Check if batch signing is available on token")
    public void batchSigningEnabled(@Param(name = "keyId", description = "Key ID") String keyId) throws Exception {
        Boolean enabled = SignerClient.execute(new GetTokenBatchSigningEnabled(keyId));

        if (enabled) {
            System.out.println("Batch signing is enabled");
        } else {
            System.out.println("Batch signing is NOT enabled");
        }
    }

    /**
     * Show certificate.
     *
     * @param certId certificate id
     * @throws Exception if an error occurs
     */
    @Command(description = "Show certificate")
    public void showCertificate(@Param(name = "certId", description = "Certificate ID") String certId)
            throws Exception {
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());

        for (TokenInfo token : tokens) {
            for (KeyInfo key : token.getKeyInfo()) {
                for (CertificateInfo cert : key.getCerts()) {
                    if (certId.equals(cert.getId())) {
                        X509Certificate x509 = readCertificate(cert.getCertificateBytes());

                        System.out.println(x509);

                        return;
                    }
                }
            }
        }

        System.out.println("Certificate " + certId + " not found");
    }

    // ------------------------------------------------------------------------

    /**
     * Program entry point.
     *
     * @param args arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        Version.outputVersionInfo(APP_NAME);

        CommandLine cmd = getCommandLine(args);
        if (cmd.hasOption("verbose")) {
            verbose = true;
        }

        if (cmd.hasOption("help")) {
            processCommandAndExit("?list");

            return;
        }

        ActorSystem actorSystem = ActorSystem.create("SignerConsole", ConfigFactory.load().getConfig("signer-console")
                .withFallback(ConfigFactory.load()));

        try {
            SignerClient.init(actorSystem);

            String[] arguments = cmd.getArgs();

            if (arguments.length > 0) {
                processCommandAndExit(StringUtils.join(arguments, " "));
            } else {
                startCommandLoop();
            }
        } finally {
            actorSystem.terminate();
        }
    }

    private static void startCommandLoop() throws IOException {
        String prompt = "signer@" + SystemProperties.getSignerPort();

        StringBuilder description = new StringBuilder("Enter '?list' to get list of available commands\n");
        description.append("Enter '?help <command>' to get command description\n");
        description.append("\nNOTE: Member identifier is entered as \"<INSTANCE> <CLASS> <CODE>\" (in quotes)\n");

        getShell(prompt, description.toString()).commandLoop();
    }

    private static void processCommandAndExit(String command) throws CLIException {
        getShell("", "").processLine(command);
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("h", "help", false, "shows available commands");
        options.addOption("v", "verbose", false, "more detailed output");

        return parser.parse(options, args);
    }

    private static Shell getShell(String prompt, String description) {
        return ShellFactory.createConsoleShell(prompt, description, new SignerCLI());
    }
}
