package ee.cyber.sdsb.signer;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.dummies.certificateauthority.CAMock;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.*;
import ee.cyber.sdsb.signer.tokenmanager.module.SoftwareModuleType;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestSuiteHelper {
    private static final Logger LOG = LoggerFactory.getLogger(
            TestSuiteHelper.class);

    private static final boolean VERBOSE = false;

    public static ClientId createClientId(String memberclass, String name,
            String subsystem) {
        ClientId client = ClientId.create("EE", memberclass, name, subsystem);
        LOG.debug("Created clientID: {}", client.toString());
        return client;
    }

    public static ClientId createClientId(String name,
            String subsystem) {
        ClientId client = ClientId.create("EE", "BUSINESS", name, subsystem);
        LOG.debug("Created clientID: {}", client.toString());
        return client;

    }

    public static ClientId createClientId(String name) {
        ClientId client = ClientId.create("EE", "BUSINESS", name);
        LOG.debug("Created clientID: {}", client.toString());
        return client;
    }

    public static TokenInfo initializeSoftToken(String password) {
        try {
            TokenInfo softToken;
            for (TokenInfo token : listTokens()) {

                if (token.getType().equals(SoftwareModuleType.TYPE)) {
                    softToken = token;
                    setPassword(softToken, password);
                    LOG.debug("Softtoken: {}: {}, {}", new Object[] {
                            softToken.getId(), softToken.getFriendlyName(),
                            (softToken.isActive() ? "active" : "inactive")});
                    return softToken;
                }
            }

            throw new Exception("Couldn't find softToken.");
        } catch (Exception e) {
            LOG.error("softToken not found.", e);
            return null;
        }
    }

    public static void setPassword(TokenInfo token, String password)
            throws Exception {
        // Set token password in signer side
        // TODO: maybe to change password also on token...
        PasswordStore.storePassword(token.getId(), password.toCharArray());
    }

    public static TokenInfo initiateDummyToken(String password) {
        LOG.debug("initiateDummyToken()");
        try {
            TokenInfo dummyToken;
            for (TokenInfo token : listTokens()) {
                if (token.getType().equals("hsm_dummy")) {
                    dummyToken = token;
                    setPassword(dummyToken, password);
                    LOG.debug("Dummytoken: {}: {}, {}", new Object[] {
                            dummyToken.getId(), dummyToken.getFriendlyName(),
                            (dummyToken.isActive() ? "active" : "inactive")});
                    return dummyToken;
                }
            }

            throw new Exception("Couldn't find DummyToken.");
        } catch (Exception e) {
            LOG.error("dummyToken not found.", e.getCause());
            return null;
        }
    }

    public static HashMap<String, CsrCertStructure> addKeyWithNCerts() {
        return null;
    }

    public static void activateDevice(TokenInfo token, boolean active)
            throws Exception {
        if (active) {
            PasswordStore.storePassword(token.getId(), "test".toCharArray());
        } else {
            PasswordStore.storePassword(token.getId(), null);
        }

        SignerClient.execute(new ActivateToken(token.getId(), active));

        LOG.debug("Token: {}, is now active: {}", token.getId(), active);
    }

    public static void activateCert(byte[] certBytes, boolean active)
            throws Exception {
        String certHash = certByteConversion(certBytes);
        SignerClient.execute(new ActivateCert(certHash, active));
    }

    public static HashMap<String, CsrCertStructure> genKeysWithSignCerts(
            int nrOfKeys, int nrOfCerts, KeyUsageInfo certType,
            int certDurationSec, ClientId client, TokenInfo token)
                    throws Exception {
        HashMap<String, CsrCertStructure> keyCertList = new HashMap<>();
        for (int j = 0; j < nrOfKeys; j++) {
            KeyInfo keyRequest = generateKey(token);
            String key = keyRequest.getId();

            ArrayList<byte[]> certList = new ArrayList<>();
            for (int i = 0; i < nrOfCerts; i++) {
                GenerateCertRequestResponse csrReqest =
                        generateCertRequest(token, keyRequest,
                                client, certType,
                                subjectName(client));

                certList.add(CAMock.certRequest(
                        csrReqest.getCertRequest(), certType, certDurationSec));

                /* NOTE: Whaat?? 1??? Created many csrs and certs from them,
                    Signer understands that it has always 1 csr, but it allows
                    to import all the cert which had csrs in the past.*/
            }
            keyCertList.put(
                    key, new CsrCertStructure(certList, "cert", client));
        }
        return keyCertList;
    }

    public static HashMap<String, CsrCertStructure> createCsrs(
            int keys, KeyUsageInfo certType, int secUntilExpired,
            ClientId client, TokenInfo token) throws Exception {
        HashMap<String, CsrCertStructure> keyCertList = new HashMap<>();

        for (int j = 0; j < keys; j++) {
            KeyInfo keyRequest = generateKey(token);
            String key = keyRequest.getId();

            GenerateCertRequestResponse req =
                    generateCertRequest(token, keyRequest, client, certType,
                            subjectName(client));

            keyCertList.put(key, new CsrCertStructure(req.getCertReqId(),
                    req.getCertRequest(), "csr", client));
        }

        return keyCertList;
    }

    // TODO: this method could return keyId to make the calling code simpler.
    public static KeyInfo generateKey(TokenInfo token) throws Exception {
        KeyInfo genKeysResp = SignerClient.execute(
                new GenerateKey(token.getId()));

        LOG.debug("Generated key: {}:{}\nContent: {}", new Object[] {
                token.getId(), genKeysResp.getId(),
                genKeysResp.getPublicKey()});
        assertNotNull(genKeysResp.getId());
        assertNotNull(genKeysResp.getPublicKey());
        return genKeysResp;
    }

    public static void setKeyFriendlyName(String keyId, String friendlyName)
            throws Exception {
        SignerClient.execute(
                new SetKeyFriendlyName(keyId, friendlyName));
    }

    public static void setDeviceFriendlyName(String id, String friendlyName)
            throws Exception {
        SignerClient.execute(
                new SetTokenFriendlyName(id, friendlyName));
    }

    public static String subjectName(ClientId member) {
        String cocn = "C=" + member.getSdsbInstance() + ", "
                + "O=" + member.getMemberClass() + ", "
                + "CN=" + member.getMemberCode();
        return cocn;
    }

    public static GenerateCertRequestResponse generateCertRequest(
            TokenInfo token, KeyInfo genKeysResp,
            ClientId memberId, KeyUsageInfo keyUsageInfo, String subjectName)
                    throws Exception {
        GenerateCertRequestResponse genCertResp = SignerClient.execute(
                new GenerateCertRequest(genKeysResp.getId(), memberId,
                        keyUsageInfo, subjectName));
        LOG.debug("Generated cert request for key {}", genKeysResp.getId());
        assertNotNull(genCertResp.getCertRequest());
        return genCertResp;
    }

    public static void softSign(String keyId) throws Exception {
        LOG.trace("Devices listed above{}.", listDevices());
        SecureRandom random = new SecureRandom();
        byte[] tbsData = new byte[1024];
        // TODO: Does signing bigger chunk of data make any difference?
        random.nextBytes(tbsData);

        byte[] digest = calculateDigest(
                getDigestAlgorithmId(SHA512WITHRSA_ID), tbsData);

        Sign req = new Sign(keyId, SHA512WITHRSA_ID, digest);
        //        assertNotNull(req);
        SignResponse response = SignerClient.execute(req);
        byte[] signature = response.getSignature();
        //        assertNotNull(signature);
        LOG.debug("Signed! The signature is " + Arrays.toString(signature));
    }

    public static void softSign(String keyId, byte[] data)
            throws Exception {
        byte[] digest = calculateDigest(
                getDigestAlgorithmId(SHA512WITHRSA_ID), data);

        Sign req = new Sign(keyId, SHA512WITHRSA_ID, digest);
        SignResponse response = SignerClient.execute(req);
        byte[] signature = response.getSignature();
        //        assertNotNull(signature);
        LOG.debug("Signed! The signature is " + Arrays.toString(signature));
    }

    public static String importCert(byte[] certBytes) throws Exception {
        ClientId clientId = getClientId(readCertificate(certBytes));
        ImportCertResponse cert = SignerClient.execute(
                new ImportCert(certBytes, "SAVED", clientId));
        return cert.getKeyId();
    }

    public static ClientId getClientId(X509Certificate cert) {
        return CertUtils.getSubjectClientId(cert);
    }

    public static void importCerts(
            HashMap<String, CsrCertStructure> keyCertList) throws Exception {
        for (String key : keyCertList.keySet()) {
            for (byte[] cert : keyCertList.get(key).getCertListCerts()) {
                String keyId = importCert(cert);
                LOG.trace(listDevices());
                assertEquals(key, keyId);
            }
        }
    }

    public static void deleteCert(String certId) throws Exception {
        SignerClient.execute(new DeleteCert(certId));
    }

    public static void deleteCsr(String certId) throws Exception {
        SignerClient.execute(new DeleteCertRequest(certId));
    }

    public static void deleteKey(String keyId) throws Exception {
        SignerClient.execute(new DeleteKey(keyId, true));
    }

    public static Pair getMemberSignInfo(ClientId memberId,
            boolean both)
            throws Exception {
        LOG.trace("Devices listed above{}.", listDevices());
        MemberSigningInfo memberSigningInfo =
                SignerClient.execute(new GetMemberSigningInfo(memberId));
        return new Pair(memberSigningInfo.getKeyId(),
                memberSigningInfo.getCert().getCertificateBytes());
    }

    public static String getMemberSignInfo(ClientId memberId)
            throws Exception {
        LOG.trace("Devices listed above{}.", listDevices());
        MemberSigningInfo memberSigningInfo =
                SignerClient.execute(new GetMemberSigningInfo(memberId));
        return memberSigningInfo.getKeyId();
    }

    public static List<byte[]> getMemberCerts(ClientId memberId)
            throws Exception {
        GetMemberCertsResponse memberCerts =
                SignerClient.execute(new GetMemberCerts(memberId));
        List<byte[]> certs = new ArrayList<>();
        for (CertificateInfo certInfo : memberCerts.getCerts()) {
            certs.add(certInfo.getCertificateBytes());
        }
        return certs;
    }

    public static String getFirstKey(
            HashMap<String, CsrCertStructure> keyCertStruct) {
        return keyCertStruct.entrySet().iterator().next().getKey();
    }

    public static String getAuthKey() throws Exception {
        SecurityServerId server =
                SecurityServerId.create("EE", "BUSINESS", "clientmember",
                        "topSecret");
        AuthKeyInfo authKeyInfo = SignerClient.execute(
                new GetAuthKey(server));
        String alias = authKeyInfo.getAlias();
        String keyStoreFileName = authKeyInfo.getKeyStoreFileName();
        LOG.debug("Received auth key is: {} : {}", keyStoreFileName, alias);
        return alias;
    }

    public static boolean isCertActive(byte[] certBytes) throws Exception {
        for (TokenInfo device : listTokens()) {
            for (KeyInfo key : device.getKeyInfo()) {
                for (CertificateInfo cert : key.getCerts()) {
                    if (certByteConversion(cert.getCertificateBytes()).equals(
                            certByteConversion(certBytes))) {
                        return cert.isActive();
                    }
                }
            }
        }
        throw new Exception("Cert not in configuration");
    }

    public static boolean areMemberCertsSameInConf(ClientId memberId,
            HashMap<String, CsrCertStructure> keyCertList) throws Exception {
        // Assumes that all certs in  keyCertList are only for this member.
        List<byte[]> memberCerts = getMemberCerts(memberId);
        for (String key : keyCertList.keySet()) {
            for (byte[] cert : keyCertList.get(key).getCertListCerts()) {
                boolean tempSuccess = false;
                for (byte[] membercert : memberCerts) {
                    if (Arrays.equals(cert, membercert)) {
                        tempSuccess = true;
                    }
                }
                if (!tempSuccess) {
                    LOG.debug("There wasn't cert with bytes[]: {} "
                            + "\n under key: {}", Arrays.toString(cert), key);
                    return false;
                }
            }
        }
        return true;
    }

    public static String certByteConversion(byte[] certByte)
            throws Exception {
        return CryptoUtils.calculateCertHexHash(certByte);
    }

    public static int keyCount(String tokenId) throws Exception {
        for (TokenInfo device : listTokens()) {
            if (device.getId().equals(tokenId)) {
                return device.getKeyInfo().size();
            }
        }
        throw new Exception("No token with id " + tokenId + " available.");
    }

    public static int keyCount(TokenInfo token) throws Exception {
        for (TokenInfo device : listTokens()) {
            if (token.getId().equals(device.getId())) {
                return device.getKeyInfo().size();
            }
        }
        throw new Exception("No token '" + token.getFriendlyName()
                + "' available.");
    }

    public static List<KeyInfo> getKeys(TokenInfo token) throws Exception {
        for (TokenInfo device : listTokens()) {
            if (token.getId().equals(device.getId())) {
                return device.getKeyInfo();
            }
        }
        throw new Exception("No token '" + token.getFriendlyName()
                + "' available.");
    }

    public static ArrayList<ClientId> getCsrMembers(TokenInfo token,
            String keyId) throws Exception {
        for (TokenInfo device : listTokens()) {
            if (token.getId().equals(device.getId())) {
                for (KeyInfo key : device.getKeyInfo()) {
                    if (keyId.equals(key.getId())) {
                        ArrayList<ClientId> arrMem = new ArrayList<>();
                        for (CertRequestInfo csrInfo : key.getCertRequests()) {
                            arrMem.add(csrInfo.getMemberId());
                        }
                        return arrMem;
                    }
                }
            }
        }
        throw new Exception("Token or Key  Not found.");
    }

    public static int csrCount(String keyId) throws Exception {
        for (TokenInfo device : listTokens()) {
            for (KeyInfo key : device.getKeyInfo()) {
                String id = key.getId();
                if (id.equals(keyId)) {
                    return key.getCertRequests().size();
                }
            }
        }
        throw new Exception("No key with id " + keyId + " available.");
    }

    public static int certCount(String keyId) throws Exception {
        for (TokenInfo device : listTokens()) {
            for (KeyInfo key : device.getKeyInfo()) {
                String id = key.getId();
                if (id.equals(keyId)) {
                    return key.getCerts().size();
                }
            }
        }
        throw new Exception("No key with id " + keyId + " available.");
    }

    public static String getKeyFriendlyName(String keyId) throws Exception {
        for (TokenInfo device : listTokens()) {
            for (KeyInfo key : device.getKeyInfo()) {
                if (key.getId().equals(keyId)) {
                    return key.getFriendlyName();
                }
            }
        }
        throw new CodedException(X_KEY_NOT_FOUND).withPrefix(SIGNER_X);
    }

    public static String getDeviceFriendlyName(String tokenId)
            throws Exception {
        for (TokenInfo device : listTokens()) {
            if (device.getId().equals(tokenId)) {
                return device.getFriendlyName();
            }
        }
        throw new CodedException(X_TOKEN_NOT_FOUND).withPrefix(SIGNER_X);
    }

    public static String listDevices() throws Exception {
        List<TokenInfo> tokens = listTokens();
        System.out.println("Got " + tokens.size() + " token(s)");
        for (TokenInfo token : tokens) {
            System.out.println("============================================");
            System.out.println("Token Type:    " + token.getType());
            System.out.println("Token Id:      " + token.getId());
            System.out.println("Friendly name: " + token.getFriendlyName());
            System.out.println("Read-Only:     " + token.isReadOnly());
            System.out.println("Available:     " + token.isAvailable());
            System.out.println("Active:        " + token.isActive());
            System.out.println("Status:        " + token.getStatus());
            System.out.println("SerialNumber:  " + token.getSerialNumber());
            System.out.println("Label:         " + token.getLabel());
            System.out.println("Keys:");
            for (KeyInfo key : token.getKeyInfo()) {
                System.out.println("\tKey Id:  \t" + key.getId());
                System.out.println("\tName:  \t" + key.getFriendlyName());
                System.out.println("\tAvailable:\t" + key.isAvailable());
                if (key.getPublicKey() != null) {
                    System.out.println("\tPublic Key BASE64:\n"
                            + key.getPublicKey());
                } else {
                    System.out.println("\t<no public key available>");
                }
                if (!key.getCerts().isEmpty()) {
                    System.out.println("\t\tCerts:");
                    for (CertificateInfo cert : key.getCerts()) {
                        System.out.println("\t\t\tmember: "
                                + cert.getMemberId());
                        System.out.println("\t\t\thash: "
                                + calculateCertHexHash(cert.getCertificateBytes()));
                        System.out.println("\t\t\tsavedToConfiguration: "
                                + cert.isSavedToConfiguration());
                    }
                }
                if (!key.getCertRequests().isEmpty()) {
                    System.out.println("\t\tCert requests:");
                    for (CertRequestInfo certReq : key.getCertRequests()) {
                        System.out.println("\t\t\tmember: "
                                + certReq.getMemberId());
                        System.out.println("\t\t\tsubject name: "
                                + certReq.getSubjectName());
                    }
                }
                System.out.println();
                System.out.println("----------------------------------------");
            }
        }

        return "";
        // I want to use this function with LOG.trace(), with the purpose
        // not to see all the info all the time, (lol)
    }

    public static String withSignerPrefix(String fault) {
        return StringUtils.join(SIGNER_X, ".", fault);
    }

    public static String stringArrayListToString(ArrayList<String> lst) {
        StringBuilder sb = new StringBuilder();
        for (String s : lst) {
            sb.append(s);
            sb.append(", ");
        }
        return sb.toString();
    }

    private static List<TokenInfo> listTokens() throws Exception {
        return SignerClient.execute(new ListTokens());
    }

    public static class Pair {
        byte[] cert;
        String keyId;

        Pair(String key, byte[] crt) {
            this.cert = crt;
            this.keyId = key;
        }

        public byte[] getCert() {
            return this.cert;
        }

        public String getId() {
            return this.keyId;
        }

        public boolean equals(Pair pair) {
            return keyId.equals(pair.getId())
                    && (this.cert.equals(pair.getCert()));
        }
    }
}
