package ee.cyber.sdsb.signer.dummies.pkcs11;

import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.PublicKey;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;


public class KeyPairContainer {
    
    // TODO: exists only because I don't know whether I need to use keyIdBase64 or not.
    // If not then I could use KeyPair
    
    private RSAPublicKey uKey;
    private RSAPrivateKey rKey;
    private String keyIdBase64;
    
    public KeyPairContainer(KeyPair keyPair) {
        this.uKey = (RSAPublicKey) keyPair.getPublicKey();
        this.rKey = (RSAPrivateKey) keyPair.getPrivateKey();
    }
    
    public KeyPairContainer(RSAPublicKey uKey, RSAPrivateKey rKey) {
        this.uKey = uKey;
        this.rKey = rKey;
    }
    
    public KeyPairContainer(String keyIdBase64, 
            KeyPair keyPair) {
        this.uKey = (RSAPublicKey) keyPair.getPublicKey();
        this.rKey = (RSAPrivateKey) keyPair.getPrivateKey();
        this.keyIdBase64 = keyIdBase64;
    }
    
    public KeyPairContainer(String keyIdBase64, 
            PublicKey uKey) {
        this.uKey = (RSAPublicKey) uKey;
        this.keyIdBase64 = keyIdBase64;
    }

    public KeyPairContainer(String keyIdBase64, 
            PrivateKey rKey) {
        this.rKey = (RSAPrivateKey) rKey;
        this.keyIdBase64 = keyIdBase64;
    }
    
    public RSAPublicKey getuKey() {
        return uKey;
    }

    public RSAPrivateKey getrKey() {
        return rKey;
    }

    public String getKeyIdHex() {
        return keyIdBase64;
    }

}
