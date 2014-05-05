package ee.cyber.sdsb.signer.dummies.pkcs11;

import iaik.pkcs.pkcs11.wrapper.CK_INFO;
import iaik.pkcs.pkcs11.wrapper.CK_NOTIFY;
import iaik.pkcs.pkcs11.wrapper.CK_SLOT_INFO;

import ee.cyber.sdsb.signer.dummies.pkcs11.PKCS11ImplMock;

import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.*;

public class Slot {
    /* 
     * This class is for describing the existence of slot.
     * 
     * Cannot use original one refers in the end 
     * to PKCS11 interface implementation
     * */
    private long slotId; // sSlotId is unique identifier of this slot
    private long flags;
    private Object pApplication;
    private CK_NOTIFY notify;
    
    
    private CK_SLOT_INFO slotInfo;
    private CK_INFO info;
    
    private Boolean hasToken = false;
    private long tokenId = -1L; // if nothing is inside a slot expect -1 
    
    public Slot() {
        createCkInfo();
        createCkSlotInfo();
    }
    
    public Slot(long slotId) {
        this.slotId = slotId;
        createCkInfo();
        createCkSlotInfo();
    }
    
//    public Slot(long slotId, long flags, Object pApplication, 
//            CK_NOTIFY notify) {
//        this.slotId = slotId;
//        this.flags = flags;
//        this.pApplication = pApplication;
//        this.notify = notify;
//        
//        this.token = new Token();
//        this.hasToken = true;
//        createCkInfo();
//        createCkSlotInfo();
//        this.token.getTokenInfo();
//    }
    
    public void insertToken(long tokenId) {
        this.tokenId = tokenId;
        PKCS11ImplMock.tokens.get(tokenId).insertTokenToSlot(this.slotId);
        this.hasToken = true;
        slotInfo.flags = 1L;
    }
    
    public void removeToken() {
        this.tokenId = -1L;
        this.hasToken = false;
        slotInfo.flags = 0L;
    }
    
    public long getSlotId() {
        return slotId;
    }


    public void setSlotId(long sSlotId) {
        this.slotId = sSlotId;
    }


    public long getFlags() {
        return flags;
    }


    public void setFlags(long sFlags) {
        this.flags = sFlags;
    }


    public Object getpApplication() {
        return pApplication;
    }


    public void setpApplication(Object spApplication) {
        this.pApplication = spApplication;
    }

    public CK_NOTIFY getNotify() {
        return notify;
    }

    public void setNotify(CK_NOTIFY sNotify) {
        this.notify = sNotify;
    }

    public CK_SLOT_INFO getSlotInfo() {
        return slotInfo;
    }


    public void setSlotInfo(CK_SLOT_INFO slotInfo) {
        this.slotInfo = slotInfo;
    }


    public Boolean hasToken() {
        return hasToken;
    }

    public long getTokenId() {
        return tokenId;
    }

    public void setHasToken(Boolean hasToken) {
        this.hasToken = hasToken;
    }
    
    
    
    public CK_INFO createCkInfo() { 
        info = new CK_INFO();
        info.cryptokiVersion = createCKVersion(1, 1);
        info.manufacturerID = "manufacturer01".toCharArray();
        info.flags = 0;
        info.libraryDescription = "Dummy Library".toCharArray();
        info.libraryVersion = createCKVersion(2, 2);
        return info;
    }
    
    public CK_SLOT_INFO createCkSlotInfo() {
        slotInfo = new CK_SLOT_INFO();
        slotInfo.slotDescription = "This is slot description.".toCharArray(); /* blank padded */
        slotInfo.manufacturerID = "man2".toCharArray(); /* blank padded */
        slotInfo.flags = 0L;
        slotInfo.hardwareVersion = createCKVersion(3, 3); /* version of hardware */
        slotInfo.firmwareVersion = createCKVersion(4, 4); /* version of firmware */
        return slotInfo;
    }
}
