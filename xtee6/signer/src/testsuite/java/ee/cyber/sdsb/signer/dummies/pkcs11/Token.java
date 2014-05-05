package ee.cyber.sdsb.signer.dummies.pkcs11;

import java.util.ArrayList;

import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.wrapper.CK_ATTRIBUTE;
import iaik.pkcs.pkcs11.wrapper.CK_MECHANISM;
import iaik.pkcs.pkcs11.wrapper.CK_TOKEN_INFO;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;

import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.createCKVersion;
import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.genRKey;
import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.genUKey;

public class Token {

    private long tokenId;
    private CK_TOKEN_INFO tokenInfo;
    private char[] password = null;
    private ArrayList<KeyPairContainer> kps = new ArrayList<>();
    private ArrayList<Cert> certs = new ArrayList<>();

    // These two define key types. They will be added in front of the KeyPair handler
    private static final long PUBLIC_KEY = 1L;
    private static final long PRIVATE_KEY = 9L;

    private boolean active = false;

    public Token(long tokenId) {
        createCkTokenInfo();
//        setPassword("1234".toCharArray());
        setPassword("1234".toCharArray());
        this.tokenId = tokenId;
        tokenInfo.serialNumber = ("" + this.tokenId).toCharArray(); // TokenId
    }


    private CK_TOKEN_INFO createCkTokenInfo() {
        tokenInfo = new CK_TOKEN_INFO();
        tokenInfo.manufacturerID = "man1".toCharArray();
        tokenInfo.model = "model1".toCharArray();
        tokenInfo.flags = 0L;
        tokenInfo.flags |= PKCS11Constants.CKF_RNG; 
        // the token has its own random number generator
        tokenInfo.flags |= PKCS11Constants.CKF_CLOCK_ON_TOKEN; 
        // token has its own hardware clock
        tokenInfo.flags |= PKCS11Constants.CKF_PROTECTED_AUTHENTICATION_PATH; 
        /* token has a “protected authentication path”, whereby a user can log 
         * into the token without passing a PIN through the Cryptoki library.
         *  TODO: is it true?
        */
        tokenInfo.flags |= PKCS11Constants.CKF_TOKEN_INITIALIZED; 
        /* the token has been initialized using C_InitToken or an equivalent 
         * mechanism outside the scope of this standard. Calling C_InitToken 
         * when this flag is set will cause the token to be reinitialized.
        */
        tokenInfo.ulMaxSessionCount = 10461; // random numbers
        tokenInfo.ulSessionCount = 10462; // random numbers
        tokenInfo.ulMaxRwSessionCount = 10463; // random numbers
        tokenInfo.ulRwSessionCount = 10464; // random numbers
        tokenInfo.ulMaxPinLen = 4; // max 4 num pin
        tokenInfo.ulMinPinLen = 0; // min 0 num pin - no pass
        tokenInfo.ulTotalPublicMemory = 10466; // random numbers
        tokenInfo.ulFreePublicMemory = 10467; // random numbers
        tokenInfo.ulTotalPrivateMemory = 10468; // random numbers
        tokenInfo.ulFreePrivateMemory = 10469; // random numbers
        tokenInfo.hardwareVersion = createCKVersion(1, 2);
        tokenInfo.firmwareVersion = createCKVersion(1, 3);
        tokenInfo.utcTime = "01.01.2000".toCharArray();

        return tokenInfo;
    }

    public char[] getPassword() {
        return this.password;
    }

    public void setPassword(char[] newPassword) {
        password = newPassword;
        tokenInfo.flags |= PKCS11Constants.CKF_USER_PIN_INITIALIZED; 
        // the normal user’s PIN has been initialized
    }

    public CK_TOKEN_INFO getTokenInfo() {
        return tokenInfo;
    }

    public void setTokenInfo(CK_TOKEN_INFO tokenInfo) {
        this.tokenInfo = tokenInfo;
    }

    public ArrayList<KeyPairContainer> getKps() {
        return kps;
    }

    public long addKp(KeyPairContainer kp) {
        kps.add(kp);
        return kps.indexOf(kp);
    }

    public long getKpHandlePublic(KeyPairContainer kp) {
        return Long.parseLong("" + PUBLIC_KEY + kps.indexOf(kp));
    }

    public long getKpHandlePrivate(KeyPairContainer kp) {
        return Long.parseLong("" + PRIVATE_KEY + kps.indexOf(kp));
    }
    
    public long[] getKpHandle(KeyPairContainer kp) {
        long[] temp = new long[2];
        int index = kps.indexOf(kp);
        temp[0] = Long.parseLong("" + PUBLIC_KEY + index);
        temp[1] = Long.parseLong("" + PRIVATE_KEY + index);
        return temp;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean state) {
        this.active = state;
    }

    public void insertTokenToSlot(long slotId) {
        tokenInfo.label = ("" + slotId).toCharArray(); // SlotID
    }
    
    public long[] generateKeyPair(CK_MECHANISM pMechanism,
            CK_ATTRIBUTE[] pPublicKeyTemplate,
            CK_ATTRIBUTE[] pPrivateKeyTemplate) {
        RSAPublicKey uKey = genUKey(pPublicKeyTemplate);
        RSAPrivateKey rKey = genRKey(pPrivateKeyTemplate);
        KeyPairContainer kp = new KeyPairContainer(uKey, rKey);
        addKp(kp);
        return getKpHandle(kp);
    }
    
    public ArrayList<iaik.pkcs.pkcs11.objects.Object> getAllUKeys() {
        ArrayList<iaik.pkcs.pkcs11.objects.Object> uKeys = new ArrayList<>();
        for (KeyPairContainer kp : kps) {
            uKeys.add(kp.getuKey());
        }
        return uKeys;
    }
    
    public ArrayList<iaik.pkcs.pkcs11.objects.Object> getAllRKeys() {
        ArrayList<iaik.pkcs.pkcs11.objects.Object> rKeys = new ArrayList<>();
        for (KeyPairContainer kp : kps) {
            rKeys.add(kp.getrKey());
        }
        return rKeys;
    }
}
