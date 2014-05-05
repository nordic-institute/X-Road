package ee.cyber.sdsb.signer.dummies.certificateauthority;
import org.slf4j.LoggerFactory;

// TODO: REIMPLEMENT THIS FOR NEW SIGNER
public class CAMockTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(
            CAMockTest.class);

    private static final boolean VERBOSE = false;

    public static void main(String[] args) throws Exception {
        //SignerClient.getInstance().start();
        try {
            //doLogin();
            //softSign();
            //doHsmSign();
            //listDevices();
//            doGenerateKeys();
            //doGetMemberSigningInfo();
//            doCreateCertRequest();
            //doGenKeyAndCreateCertRequest();

            //doGenKeyAndCreateCertRequest();
        } finally {
            //SignerClient.getInstance().stop();
        }
    }

    /*

    private static void doLogin() throws Exception {
        String deviceId = "1";

        PasswordStore.storePassword(deviceId, "1234".toCharArray());

        new ActivateDeviceRequest(deviceId, true).execute();
        System.out.println("Device activated");
    }

    private static void doGenKeyAndCreateCertRequest() throws Exception {
        String deviceId = "1";
        GenerateKeysRequest request = new GenerateKeysRequest(deviceId);
        GenerateKeysResponse response = request.execute();
        System.out.println("Generated key with id: " + response.getKeyId());

        String pubKeyB64 = response.getPublicKeyBase64();
        PublicKey pubKey = readX509PublicKey(decodeBase64(pubKeyB64));

        ClientId memberId = createClientId("producer");
        KeyUsageInfo keyUsage = KeyUsageInfo.SIGNING;
        String subjectName = "DN=producer";


        GenerateCertRequestRequest req =
                new GenerateCertRequestRequest(deviceId, response.getKeyId(),
                memberId, keyUsage, subjectName, pubKeyB64);

        GenerateCertRequestResponse response2 =
                req.execute();


        Log.debug("Generated cert request: " +
                response2.getCertRequest().toString());

        byte[] theCertInBytes = CAMock.certRequest(
                response2.getCertRequest(), KeyUsageInfo.SIGNING, 1000);
        Log.debug("Got sert bytes: " + theCertInBytes.toString());

    }

    private static void doCreateCertRequest() throws Exception {
        // XXX: NB! Currently missing publicKey base64 data
        String deviceId = "1";
        String keyId = "636f6e73756d6572"; // on softToken
        //String keyId = "02d66d5589f5b8626875ef99a11a84f7b2aa202f"; // on HSM

        ClientId memberId = createClientId("producer");
        KeyUsageInfo keyUsage = KeyUsageInfo.SIGNING;
        String subjectName = "Foobar Baz";
        String publicKeyBase64 = "TODO"; // TODO


        GenerateCertRequestRequest req =
                new GenerateCertRequestRequest(deviceId, keyId, memberId,
                        keyUsage, subjectName, publicKeyBase64);
        System.out.println(req);
        GenerateCertRequestResponse response =
                req.execute();

        System.out.println("Generated cert request: " +
                response.getCertRequest());

    }

    private static void doSoftSign() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] tbsData = new byte[1024];
        random.nextBytes(tbsData);

        byte[] digest = calculateDigest(
                getDigestAlgorithmId(SHA512WITHRSA_ID), tbsData);

        String keyId = "70726f6475636572";

        SignRequest req = new SignRequest(keyId, digest);
        SignResponse response = req.execute();
        byte[] signature = response.getSignature();

        System.out.println("Signed!");
        System.out.println("Signature is " + Arrays.toString(signature));
    }

    private static void doHsmSign() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] tbsData = new byte[1024];
        random.nextBytes(tbsData);

        byte[] digest = calculateDigest(
                getDigestAlgorithmId(SHA512WITHRSA_ID), tbsData);

        String keyId = "02d66d5589f5b8626875ef99a11a84f7b2aa202f";

        SignRequest req = new SignRequest(keyId, digest);
        SignResponse response = req.execute();
        byte[] signature = response.getSignature();

        System.out.println("Signed!");
        System.out.println("Signature is " + Arrays.toString(signature));
    }

    private static void doListDevices() throws Exception {
        ListDevicesResponse response = new ListDevicesRequest().execute();
        System.out.println("Got " + response.getDevicesList().size() + " keys");
        for (DeviceInfo device : response.getDevicesList()) {
            System.out.println("============================================");
            System.out.println("Device Type:\t" + device.type);
            System.out.println("Device Id:  \t" + device.id);
            System.out.println("Available:  \t" + device.available);
            System.out.println("Active:     \t" + device.active);
            System.out.println("Token Id:   \t" + device.tokenId);
            System.out.println("Slot Id:    \t" + device.slotId);
            System.out.println("Keys:");
            for (KeyInfo key : device.keyInfo) {
                System.out.println("\tKey Id:  \t" + key.keyId);
                if (key.publicKey != null) {
                    System.out.println("\tPublic Key BASE64:\n" + key.publicKey);
                    if (VERBOSE) {
                        System.out.println("\tPublic Key Info:\n" +
                            readX509PublicKey(decodeBase64(key.publicKey)));
                    }
                } else {
                    System.out.println("\t<no public key available>");
                }
                System.out.println();
                System.out.println("\t\tCerts:");
                for (CertificateInfo cert : key.certs) {
                    System.out.println("\t\t\tsavedToConfiguration: "
                            + cert.savedToConfiguration);
                    if (VERBOSE) {
                        X509Certificate certObject =
                            CryptoUtils.readCertificate(cert.certificateBytes);
                        System.out.println("\t\t\tcertObject: " + certObject);
                    }
                }
            }
        }
    }

    private static void doGenerateKeys() throws Exception {
        String deviceId = "1";
        GenerateKeysRequest request = new GenerateKeysRequest(deviceId);
        GenerateKeysResponse response = request.execute();
        System.out.println("Generated key with id: " + response.getKeyId());

        String pubKeyBase64 = response.getPublicKeyBase64();
        PublicKey pubKey = readX509PublicKey(decodeBase64(pubKeyBase64));
        System.out.println("Public Key: " + pubKey);
    }

    private static void doGetMemberSigningInfo() throws Exception {
        ClientId memberId = ClientId.create("EE", "BUSINESS", "consumer");

        GetMemberSigningInfoResponse response =
                new GetMemberSigningInfoRequest(memberId).execute();

        System.out.println("Got signing info for member " + memberId);
        System.out.println("KeyId: " + response.getKeyId());
        System.out.println("Cert: " + response.getCert());
    }


    private static ClientId createClientId(String name) {
        return ClientId.create("EE", "BUSINESS", name);
    }
    */
}
