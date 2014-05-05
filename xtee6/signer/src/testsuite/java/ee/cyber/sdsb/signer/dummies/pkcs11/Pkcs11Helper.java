package ee.cyber.sdsb.signer.dummies.pkcs11;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Random;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.objects.Attribute;
import iaik.pkcs.pkcs11.objects.GenericTemplate;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.parameters.Parameters;
import iaik.pkcs.pkcs11.wrapper.CK_ATTRIBUTE;
import iaik.pkcs.pkcs11.wrapper.CK_DATE;
import iaik.pkcs.pkcs11.wrapper.CK_MECHANISM;
import iaik.pkcs.pkcs11.wrapper.CK_MECHANISM_INFO;
import iaik.pkcs.pkcs11.wrapper.CK_VERSION;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;

public class Pkcs11Helper {
    private static final Logger LOG = 
            LoggerFactory.getLogger(Pkcs11Helper.class);
    
    final static Long KEY_SIZE = new Long(2048);
    
    
    public static CK_VERSION createCKVersion(
            int majorVersion, int minorVersion) {
        CK_VERSION version = new CK_VERSION();
        version.minor = (byte) minorVersion;
        version.major = (byte) majorVersion;
        return version;
    }
    
    public static CK_MECHANISM_INFO createCKMechanismInfo() {
        CK_MECHANISM_INFO mechInfo = new CK_MECHANISM_INFO();
        mechInfo.ulMinKeySize = (long) (Math.random() * 10);
        mechInfo.ulMaxKeySize = 10 + (long) (Math.random() * 10000);
        mechInfo.flags = 0;
        return mechInfo;
    }
    
    public static CK_MECHANISM createCKMechanism() {
        Mechanism mech = Mechanism.get(
                PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN); // from GenKeyRequestProcessor.generateKeyPairOnDevice()
        CK_MECHANISM ckmech = new CK_MECHANISM();
        ckmech.mechanism = mech.getMechanismCode();
        Parameters parameters = mech.getParameters();
        ckmech.pParameter = (parameters != null) ? 
                parameters.getPKCS11ParamsObject() : null;
        return ckmech;
    }
    
    public static byte[] generateId() {
        // from ee.cyber.xroad.signer.protocol.GenKeysRequestProcessor
        byte[] id = new byte[20];
        new Random().nextBytes(id);
        LOG.debug("Created key named: " + DatatypeConverter.printHexBinary(id));
        return id;
    }
    
    public static KeyPairContainer createKeyPair(
            CK_ATTRIBUTE[] pPublicKeyTemplate,
            CK_ATTRIBUTE[] pPrivateKeyTemplate) {
        byte[] id = generateId();        
        
        try {
            // Generate key pair
            RSAPrivateCrtKeySpec pkSpec = genKeyPairSpec(id);
            
            // Make parse keys over to iaik key containers
            RSAPublicKey uRSA = new RSAPublicKey();
            parseUKeyToIaikUKey(uRSA, pkSpec, id);
            
            RSAPrivateKey rRSA = new RSAPrivateKey();
            parseRKeyToIaikRKey(rRSA, pkSpec, id);
            
            KeyPair keyPair2 = new KeyPair(uRSA, rRSA);
            LOG.debug("Generated key with ID {}", 
                    DatatypeConverter.printHexBinary(id));
            return new KeyPairContainer(keyPair2); 
        } catch (Exception ex) {
            LOG.debug("Couldn't generate a keypair. Ex: {}", ex.getMessage());
        }
        return null;
    }
    
    
    public static KeyPairContainer createKeyPair() {
        byte[] id = generateId();        
        
        try {
            // Generate key pair
            RSAPrivateCrtKeySpec pkSpec = genKeyPairSpec(id);
            
            // Make parse keys over to iaik key containers
            RSAPublicKey uRSA = new RSAPublicKey();
            parseUKeyToIaikUKey(uRSA, pkSpec, id);
            
            RSAPrivateKey rRSA = new RSAPrivateKey();
            parseRKeyToIaikRKey(rRSA, pkSpec, id);
            
            KeyPair keyPair2 = new KeyPair(uRSA, rRSA);
            LOG.debug("Generated key with ID {}", 
                    DatatypeConverter.printHexBinary(id));
            return new KeyPairContainer(keyPair2); 
        } catch (Exception ex) {
            LOG.debug("Couldn't generate a keypair. Ex: {}", ex.getMessage());
        }
        return null;
    }
    
    private static RSAPrivateCrtKeySpec genKeyPairSpec(
            byte[] id) throws Exception {
        // Generate key pair spec 
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(KEY_SIZE.intValue(), new SecureRandom());
        java.security.KeyPair keyPair = keyPairGen.generateKeyPair();
        PrivateKey rKey = keyPair.getPrivate();

        KeyFactory keyFac = KeyFactory.getInstance("RSA");
        return keyFac.getKeySpec(rKey, RSAPrivateCrtKeySpec.class);
    }
    
    private static void parseUKeyToIaikUKey(
            RSAPublicKey uRSA, RSAPrivateCrtKeySpec pkSpec, byte[] id) {
        /* values taken from standard found here
         * <a href="http://www.cryptsoft.com/pkcs11doc/STANDARD/pkcs-11v2-30b-d5.pdf">PKCS11 Standard</a>
         */
        GenericTemplate uRSAAttributes = new GenericTemplate();
        uRSAAttributes.addAllAttributes(uRSA);
        uRSA.getWrapTemplate().setAttributeArrayValue(uRSAAttributes);
        uRSA.getModulusBits().setLongValue(KEY_SIZE);
        uRSA.getId().setByteArrayValue(id);
        uRSA.getModulus().setByteArrayValue(pkSpec.getModulus().toByteArray());
        uRSA.getPublicExponent().setByteArrayValue(
                pkSpec.getPublicExponent().toByteArray());
        uRSA.getToken().setBooleanValue(Boolean.TRUE);
        uRSA.getVerify().setBooleanValue(Boolean.TRUE);
        uRSA.getVerifyRecover().setBooleanValue(Boolean.TRUE);
        uRSA.getEncrypt().setBooleanValue(Boolean.TRUE);
        uRSA.getModifiable().setBooleanValue(Boolean.FALSE);
        uRSA.getLabel().setCharArrayValue("".toCharArray());
        uRSA.getSubject().setByteArrayValue("".getBytes());
        uRSA.getWrap().setBooleanValue(Boolean.FALSE);
        uRSA.getTrusted().setBooleanValue(Boolean.TRUE);
        long now = System.currentTimeMillis();
        uRSA.getStartDate().setDateValue(new Date(now - 1000*60*60*24*365*7));
        uRSA.getEndDate().setDateValue(new Date(now + 1000*60*60*24*365*7));
        uRSA.getDerive().setBooleanValue(Boolean.TRUE);
        uRSA.getLocal().setBooleanValue(Boolean.FALSE);
        return;
    }
    
    private static void parseRKeyToIaikRKey(
            RSAPrivateKey rRSA, RSAPrivateCrtKeySpec pkSpec, byte[] id) {
        
        GenericTemplate rRSAAttributes = new GenericTemplate();
        rRSAAttributes.addAllAttributes(rRSA);
        rRSA.getUnwrapTemplate().setAttributeArrayValue(rRSAAttributes);
        rRSA.getId().setByteArrayValue(id);
        rRSA.getModulus().setByteArrayValue(pkSpec.getModulus().toByteArray());
        rRSA.getPublicExponent().setByteArrayValue(
                pkSpec.getPublicExponent().toByteArray());
        rRSA.getPrivateExponent().setByteArrayValue(
                pkSpec.getPrivateExponent().toByteArray());
        rRSA.getExponent1().setByteArrayValue("".getBytes());
        rRSA.getExponent2().setByteArrayValue("".getBytes());
        rRSA.getPrime1().setByteArrayValue(pkSpec.getPrimeP().toByteArray());
        rRSA.getPrime2().setByteArrayValue(pkSpec.getPrimeQ().toByteArray());
        rRSA.getCoefficient().setByteArrayValue(
                pkSpec.getCrtCoefficient().toByteArray());
        rRSA.getSensitive().setBooleanValue(Boolean.TRUE);
        rRSA.getToken().setBooleanValue(Boolean.TRUE);
        rRSA.getPrivate().setBooleanValue(Boolean.TRUE);
        rRSA.getSign().setBooleanValue(Boolean.TRUE);
        rRSA.getDecrypt().setBooleanValue(Boolean.TRUE);
        rRSA.getModifiable().setBooleanValue(Boolean.FALSE);
        rRSA.getLabel().setCharArrayValue("".toCharArray());
        rRSA.getSubject().setByteArrayValue("".getBytes()); // default empty
        rRSA.getSignRecover().setBooleanValue(Boolean.TRUE);
        rRSA.getUnwrap().setBooleanValue(Boolean.TRUE); // NOTE: should it be true?
        rRSA.getExtractable().setBooleanValue(Boolean.TRUE); // 
        rRSA.getAlwaysSensitive().setBooleanValue(Boolean.TRUE);
        rRSA.getNeverExtractable().setBooleanValue(Boolean.FALSE); // key has never had the CKA_EXTRACTABLE attribute set to CK_TRUE
        rRSA.getWrapWithTrusted().setBooleanValue(Boolean.FALSE);
        rRSA.getAlwaysAuthenticate().setBooleanValue(Boolean.FALSE);
        long now = System.currentTimeMillis();
        rRSA.getStartDate().setDateValue(new Date(now - 1000*60*60*24*365*7));
        rRSA.getEndDate().setDateValue(new Date(now + 1000*60*60*24*365*7));
        rRSA.getDerive().setBooleanValue(Boolean.TRUE);
        rRSA.getLocal().setBooleanValue(Boolean.FALSE);
        rRSA.getKeyGenMechanism().setMechanism(
                Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN));
        Mechanism[] mechArr = new Mechanism[1];
        mechArr[0] = new Mechanism(PKCS11Constants.CKM_SHA256_RSA_PKCS);
        rRSA.getAllowedMechanisms().setMechanismAttributeArrayValue(mechArr);
        rRSA.getSecondaryAuth().setBooleanValue(Boolean.FALSE);
        rRSA.getAuthPinFlags().setLongValue(0L);
        return;
    }
    
    public static RSAPublicKey genUKey(CK_ATTRIBUTE[] pPublicKeyTemplate) {
        RSAPublicKey uKey = new RSAPublicKey();
        for (CK_ATTRIBUTE keyAttr : pPublicKeyTemplate) {
            if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.MODULUS_BITS) {
                uKey.getModulusBits().setLongValue((Long) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.ID) {
                uKey.getId().setByteArrayValue((byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.MODULUS) {
                uKey.getModulus().setByteArrayValue((byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PUBLIC_EXPONENT) {
                uKey.getPublicExponent().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.TOKEN) {
                uKey.getToken().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.TOKEN) {
                uKey.getVerify().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.TOKEN) {
                uKey.getEncrypt().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.MODIFIABLE) {
                uKey.getModifiable().setBooleanValue((Boolean) keyAttr.pValue);
            }
        }        
        return uKey;
    }
    
    public static RSAPrivateKey genRKey(CK_ATTRIBUTE[] pPrivateKeyTemplate) {
        RSAPrivateKey rKey = new RSAPrivateKey();
        for (CK_ATTRIBUTE keyAttr : pPrivateKeyTemplate) {
            if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.ID) {
                rKey.getId().setByteArrayValue((byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.MODULUS) {
                rKey.getModulus().setByteArrayValue((byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PUBLIC_EXPONENT) {
                rKey.getPublicExponent().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PRIVATE_EXPONENT) {
                rKey.getPrivateExponent().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PRIME_1) {
                rKey.getPrime1().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PRIME_2) {
                rKey.getPrime2().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.COEFFICIENT) {
                rKey.getCoefficient().setByteArrayValue(
                        (byte[]) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.SENSITIVE) {
                rKey.getSensitive().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.TOKEN) {
                rKey.getToken().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.PRIVATE) {
                rKey.getPrivate().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.SIGN) {
                rKey.getSign().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.DECRYPT) {
                rKey.getDecrypt().setBooleanValue((Boolean) keyAttr.pValue);
            } else if (keyAttr.type == 
                    iaik.pkcs.pkcs11.objects.Attribute.MODIFIABLE) {
                rKey.getModifiable().setBooleanValue((Boolean) keyAttr.pValue);
            }
            
        }
        return rKey;
    }
    
    public static boolean sessionHasSlot(long slotId) {
        for (Entry<Long, Session> session : 
                PKCS11ImplMock.sessions.entrySet()) {
            if (session.getValue().getSlotID() == slotId) {
                return true;
            }
        }
        return false;
    }
    
    public static Long slotInSession(long slotId) {
        for (Entry<Long, Session> session : 
                PKCS11ImplMock.sessions.entrySet()) {
            if (session.getValue().getSlotID() == slotId) {
                return session.getKey();
            }
        }
        return null;
    }
    
    public static String getType(long type) {
        // debug method to see what is behind attribute types
        if (type == iaik.pkcs.pkcs11.objects.Attribute.ID) {
            return "ID";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.MODULUS) {
            return "MODULUS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PUBLIC_EXPONENT) {
            return "PUBLIC_EXPONENT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIVATE_EXPONENT) {
            return "PRIVATE_EXPONENT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIME_1) {
            return "PRIME_1";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIME_2) {
            return "PRIME_2";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.COEFFICIENT) {
            return "COEFFICIENT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SENSITIVE) {
            return "SENSITIVE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.TOKEN) {
            return "TOKEN";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIVATE) {
            return "PRIVATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SIGN) {
            return "SIGN";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.DECRYPT) {
            return "DECRYPT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.MODIFIABLE) {
            return "MODIFIABLE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.LABEL) {
            return "LABEL";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.LOCAL) {
            return "LOCAL";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.NEVER_EXTRACTABLE) {
            return "NEVER_EXTRACTABLE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.ALWAYS_SENSITIVE) {
            return "ALWAYS_SENSITIVE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.EXTRACTABLE) {
            return "EXTRACTABLE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.KEY_GEN_MECHANISM) {
            return "KEY_GEN_MECHANISM";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.START_DATE) {
            return "START_DATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.END_DATE) {
            return "END_DATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.KEY_TYPE) {
            return "KEY_TYPE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SUBJECT) {
            return "SUBJECT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.ENCRYPT) {
            return "ENCRYPT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.WRAP) {
            return "WRAP";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.UNWRAP) {
            return "UNWRAP";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SIGN_RECOVER) {
            return "SIGN_RECOVER";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VERIFY) {
            return "VERIFY";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VERIFY_RECOVER) {
            return "VERIFY_RECOVER";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.DERIVE) {
            return "DERIVE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.MODULUS_BITS) {
            return "MODULUS_BITS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.EXPONENT_1) {
            return "EXPONENT_1";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.EXPONENT_2) {
            return "EXPONENT_2";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIME) {
            return "PRIME";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SUBPRIME) {
            return "SUBPRIME";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.BASE) {
            return "BASE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.PRIME_BITS) {
            return "PRIME_BITS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SUB_PRIME_BITS) {
            return "SUB_PRIME_BITS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VALUE_BITS) {
            return "VALUE_BITS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VALUE_LEN) {
            return "VALUE_LEN";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VALUE_BITS) {
            return "VALUE_BITS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.EC_PARAMS) {
            return "EC_PARAMS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.EC_POINT) {
            return "EC_POINT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.SECONDARY_AUTH) {
            return "SECONDARY_AUTH";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.AUTH_PIN_FLAGS) {
            return "AUTH_PIN_FLAGS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.ALWAYS_AUTHENTICATE) {
            return "ALWAYS_AUTHENTICATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.WRAP_WITH_TRUSTED) {
            return "WRAP_WITH_TRUSTED";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.WRAP_TEMPLATE) {
            return "WRAP_TEMPLATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.UNWRAP_TEMPLATE) {
            return "UNWRAP_TEMPLATE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.HW_FEATURE_TYPE) {
            return "HW_FEATURE_TYPE";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.RESET_ON_INIT) {
            return "RESET_ON_INIT";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.HAS_RESET) {
            return "HAS_RESET";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.ALLOWED_MECHANISMS) {
            return "ALLOWED_MECHANISMS";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.VENDOR_DEFINED) {
            return "VENDOR_DEFINED";
        } else if (type == iaik.pkcs.pkcs11.objects.Attribute.HAS_RESET) {
            return "HAS_RESET";
        } else {
            return "OTHER type: " + Long.toString(type);
        }
    }
}
