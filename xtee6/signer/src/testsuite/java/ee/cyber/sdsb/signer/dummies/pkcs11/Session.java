package ee.cyber.sdsb.signer.dummies.pkcs11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.xml.bind.DatatypeConverter;

import iaik.pkcs.pkcs11.objects.Attribute;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.Object.ObjectClass;
import iaik.pkcs.pkcs11.wrapper.CK_ATTRIBUTE;
import iaik.pkcs.pkcs11.wrapper.CK_NOTIFY;
import iaik.pkcs.pkcs11.wrapper.CK_SESSION_INFO;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;
import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.*;

public class Session {

    private static final Logger LOG =
            LoggerFactory.getLogger(Session.class);

    private long slotID;
    private long flags;
    private Object pApplication;
    private CK_NOTIFY notify;
    private HashMap<Long, iaik.pkcs.pkcs11.objects.Object> objectList =
            new HashMap<>();

    private long objCounter = 0L;
    
    private HashMap<Long, iaik.pkcs.pkcs11.objects.Object> foundObj = 
            new HashMap<>();

    public Session(long slotID, long flags,
            iaik.pkcs.pkcs11.objects.Object pApplication, CK_NOTIFY notify) {
        this.slotID = slotID;
        this.flags = flags;
        this.pApplication = pApplication;
        this.notify = notify;
        // add key references to session objects
        long tokenId = PKCS11ImplMock.slots.get(slotID).getTokenId();
        addObjects(PKCS11ImplMock.tokens.get(tokenId).getAllUKeys());
        addObjects(PKCS11ImplMock.tokens.get(tokenId).getAllRKeys());
        // TODO: anything other to add?
    }
    
    public void update() {
        // Must be called every time something is added to tokens or taken away from them
        long tokenId = PKCS11ImplMock.slots.get(slotID).getTokenId();
        addObjects(PKCS11ImplMock.tokens.get(tokenId).getAllUKeys());
        addObjects(PKCS11ImplMock.tokens.get(tokenId).getAllRKeys());
    }
    
    public void addObject(iaik.pkcs.pkcs11.objects.Object obj) {
        objCounter++;
        objectList.put(objCounter, obj);
    }

    public void addObjects(ArrayList<iaik.pkcs.pkcs11.objects.Object> objs) {
        for (iaik.pkcs.pkcs11.objects.Object obj : objs) {
            addObject(obj);
        }
    }
    
    public HashSet<Long> getAllPublicKeyHandles() {
        HashSet<Long> a = new HashSet<>();
        for (Entry<Long, iaik.pkcs.pkcs11.objects.Object> pair :
                objectList.entrySet()) {
            if (pair.getValue().getClass().equals(RSAPublicKey.class)) {
                a.add(pair.getKey());
            }
        }
        return a;
    }

    public HashSet<Long> getAllPrivateKeyHandles() {
        HashSet<Long> a = new HashSet<>();
        for (Entry<Long, iaik.pkcs.pkcs11.objects.Object> pair : 
                objectList.entrySet()) {
            if (pair.getValue().getClass().equals(RSAPrivateKey.class)) {
                a.add(pair.getKey());
            }
        }
        return a;
    }
    
    public HashMap<Long, iaik.pkcs.pkcs11.objects.Object> getAllPrivateKeys() {
        HashMap<Long, iaik.pkcs.pkcs11.objects.Object> keyMap = new HashMap<>();
        for (Entry<Long, iaik.pkcs.pkcs11.objects.Object> pair : 
                objectList.entrySet()) {
            if (pair.getValue().getClass().equals(RSAPrivateKey.class)) {
                keyMap.put(pair.getKey(), pair.getValue());
            }
        }
        return keyMap;
    }

    public HashMap<Long, iaik.pkcs.pkcs11.objects.Object> getObjects() {
        return objectList;
    }

    public long getSlotID() {
        return slotID;
    }

    public void getAttributeValue(long hObject, CK_ATTRIBUTE[] pTemplate)
            throws PKCS11Exception {
        CK_ATTRIBUTE[] objAttrs =
                iaik.pkcs.pkcs11.objects.Object.getSetAttributes(
                        objectList.get(hObject));
        System.out.println("hObject" + hObject);
        System.out.println(objectList.keySet());
        
        outerLoop:
        for (CK_ATTRIBUTE templateAttr : pTemplate) {
            for (CK_ATTRIBUTE objAttr : objAttrs) {
                if (objAttr.type == templateAttr.type) {
                    templateAttr.pValue = objAttr.pValue;
                    continue outerLoop;
                }
            }
            assertTrue("This place should never be reached, PKCS11 standard " +
            		"tells that all required fields must be initialized, " +
            		"but attribute " + getType(templateAttr.type) + 
            		" wasn't!", false);
        }
    }
    
    public CK_SESSION_INFO getSessionInfo() throws PKCS11Exception {
        CK_SESSION_INFO sessionInfo = new CK_SESSION_INFO();
        sessionInfo.flags = this.flags;
        sessionInfo.slotID = this.slotID;
//        sessionInfo.ulDeviceError = this.;
//        sessionInfo.state = this.;
        return sessionInfo;
    }
    
    public void findObjectsInit(CK_ATTRIBUTE[] pTemplate)
            throws PKCS11Exception {
        update();        
        for (CK_ATTRIBUTE ckAttr : pTemplate) {
            if (iaik.pkcs.pkcs11.objects.Attribute.CLASS == ckAttr.type) {
                if (ckAttr.pValue == ObjectClass.PUBLIC_KEY) {
                    LOG.debug("Searching for Public Key");
                    for (CK_ATTRIBUTE uAttr : pTemplate) {
                        if (uAttr.type == 
                                iaik.pkcs.pkcs11.objects.Attribute.ID) {
                            for (Long keySessionId : getAllPublicKeyHandles()) {
                                RSAPublicKey uKey = (RSAPublicKey) 
                                        getObjects().get(keySessionId);
                                if (Arrays.equals((byte[]) uAttr.pValue, 
                                        (byte[]) DatatypeConverter.parseHexBinary(
                                                uKey.getId().toString()))) {
                                    foundObj.put(keySessionId, uKey);
                                }
                            }
                        }
                    }
                } else if (ckAttr.pValue == ObjectClass.PRIVATE_KEY) {
                    LOG.debug("Searching for Private Key");
                    foundObj.putAll(getAllPrivateKeys());
                } else {
                    LOG.debug("Searching for something else... Class ID {}", 
                            ckAttr.pValue);
                }
            }
        }
    }
    
    public long[] findObjects(long ulMaxObjectCount)
            throws PKCS11Exception {
        long[] a = new long[foundObj.size()]; // TODO: when size > ulMaxObjectCount some elements won't be found
        Iterator<Long> it = foundObj.keySet().iterator();
        for (int i = 0; i < foundObj.size() && it.hasNext(); i++) {
            a[i] = it.next();
            it.remove();
        }
        return a;
    }
    
    public HashMap<Long, iaik.pkcs.pkcs11.objects.Object> getFoundObj() {
        return foundObj;
    }

    public void findObjectsFinal() {
        foundObj.clear();
    }
    

}
