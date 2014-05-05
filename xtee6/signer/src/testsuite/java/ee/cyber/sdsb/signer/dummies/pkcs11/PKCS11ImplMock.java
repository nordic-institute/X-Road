package ee.cyber.sdsb.signer.dummies.pkcs11;

import iaik.pkcs.pkcs11.wrapper.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ee.cyber.sdsb.signer.dummies.pkcs11.Pkcs11Helper.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PKCS11ImplMock implements PKCS11 {

    private static final Logger LOG =
            LoggerFactory.getLogger(PKCS11ImplMock.class);


    static HashMap<Long, Token> tokens = new HashMap<>();
    static HashMap<Long, Slot> slots = new HashMap<>();
    static HashMap<Long, Session> sessions = new HashMap<>();
    // assume that only one application is communicating with dummy.so library

    private static long tokenCount = 0L;
    private static long slotCount = 0L;
    private static long sessionCount = 0L;

    PKCS11ImplMock(String pkcs11ModulePath) {
        Slot slot = new Slot(slotCount);
        slots.put(slotCount, slot);
        Token token = new Token(tokenCount);
        token.addKp(createKeyPair()); // Security Officer puts a keypair on token
        tokens.put(tokenCount, token);
        slot.insertToken(tokenCount); // Act of putting token inside slot
        // TODO: char[] as id? not only number are allowed. Atm only nr are assumed.
        }

    PKCS11ImplMock(String pkcs11ModulePath, String pkcs11WrapperPath)
            throws IOException {
//            ensureLinkedAndInitialized(pkcs11WrapperPath);
//            connect(pkcs11ModulePath);
//            pkcs11ModulePath_ = pkcs11ModulePath;
        }

    @Override
    public void C_Initialize(Object pInitArgs, boolean arg1)
            throws PKCS11Exception {
        LOG.debug("C_Initialize()");
        assertTrue("Warning! Initialize arg Object wasn't null",
                pInitArgs == null);
    }

    @Override
    public void C_Finalize(Object pReserved) throws PKCS11Exception {
        LOG.debug("C_Finalize()");
    }

    @Override
    public CK_INFO C_GetInfo() throws PKCS11Exception {
        LOG.debug("C_GetInfo()");
        return null;
    }

    @Override
    public long[] C_GetSlotList(boolean tokenPresent) throws PKCS11Exception {
        HashSet<Long> selectedSlots = new HashSet<>();
        for (Entry<Long, Slot> pair : slots.entrySet()) {
            long slotID = pair.getKey();
            Slot slot = pair.getValue();

            if (slot.hasToken() == tokenPresent) {
                selectedSlots.add(slotID);
            }
        }
        long[] slotsIdArray = new long[selectedSlots.size()];
        Iterator<Long> it2 = selectedSlots.iterator();
        for (int i = 0; i < slotsIdArray.length && it2.hasNext(); i++) {
            slotsIdArray[i] = it2.next();
        }
        return slotsIdArray; // result <> null from doc
    }

    @Override
    public CK_SLOT_INFO C_GetSlotInfo(long slotID) throws PKCS11Exception {
        LOG.debug("Getting SlotInfo for slot ID: {}", slotID);
        if (slots.containsKey(slotID)) {
            return slots.get(slotID).getSlotInfo();
        }
        return new CK_SLOT_INFO(); // result <> null from doc
    }

    @Override
    public CK_TOKEN_INFO C_GetTokenInfo(long slotID) throws PKCS11Exception {
        LOG.debug("Getting TokenInfo with slotID: {}", slotID);
        if (slots.containsKey(slotID)) {

            return tokens.get(slots.get(slotID).getTokenId()).getTokenInfo();
        }
        return new CK_TOKEN_INFO(); // result <> null from doc
    }

    @Override
    public long[] C_GetMechanismList(long slotID) throws PKCS11Exception {
        LOG.debug("Getting MechanismList with slotID: {}", slotID);
        long[] mech = new long[1];
        mech[0] = PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN; // from GenKeyRequestProcessor.generateKeyPairOnDevice()
        return mech;
    }

    @Override
    public CK_MECHANISM_INFO C_GetMechanismInfo(long slotID, long type)
            throws PKCS11Exception {
        LOG.debug("Getting MechanismInfo for slotID: {}", slotID);
        return createCKMechanismInfo();
    }

    @Override
    public void C_InitToken(long arg0, char[] arg1, char[] arg2, boolean arg3)
            throws PKCS11Exception {
        LOG.debug("C_InitToken()");

    }

    @Override
    public void C_InitPIN(long arg0, char[] arg1, boolean arg2)
            throws PKCS11Exception {
        LOG.debug("C_InitPIN()");

    }

    @Override
    public void C_SetPIN(long arg0, char[] arg1, char[] arg2, boolean arg3)
            throws PKCS11Exception {
        LOG.debug("C_SetPIN()");

    }

    @Override
    public long C_OpenSession(long slotID, long flags, Object pApplication,
            CK_NOTIFY Notify) throws PKCS11Exception {
        assertTrue("Assumption: flags is always 6 meaning " +
                "R/W and serial session", flags == 6L);
        // TODO: probably has to change because R/O sessions will be created during signing
        assertNull("Assumption: application pointer is always null, " +
        		"as signer determined it.",
        		pApplication);
        assertNull("Assumption: no Notify callback function.", Notify);

        LOG.debug("Opening Read/Write Session with slotID {}", slotID);
        if (slots.containsKey(slotID) && sessionHasSlot(slotID)) {
            return slotInSession(slotID);
        } else if (slots.containsKey(slotID)) {
            sessionCount++;
            long newSessionKey = sessionCount;
            Session newSession = new Session(slotID, flags,
                    (iaik.pkcs.pkcs11.objects.Object) pApplication, Notify);
            sessions.put(newSessionKey, newSession);
            return newSessionKey;
        }
        throw new PKCS11Exception(3); // CKR_SLOT_ID_INVALID
    }

    @Override
    public void C_CloseSession(long hSession) throws PKCS11Exception {
        LOG.debug("C_Closesession() with slotID {}",
                sessions.get(hSession).getSlotID());
        sessions.remove(hSession);
    }

    @Override
    public void C_CloseAllSessions(long slotID) throws PKCS11Exception {
        LOG.debug("C_CloseAllSessions() with slotID {}", slotID);
        for (Entry<Long, Session> set : sessions.entrySet()) {
            if (set.getValue().getSlotID() == slotID) {
                sessions.remove(set.getKey());
            }
        }
        LOG.debug("{} sessions won't be closed.", sessions.size());
    }

    @Override
    public CK_SESSION_INFO C_GetSessionInfo(long hSession)
            throws PKCS11Exception {
        LOG.debug("C_GetSessionInfo()");
        return sessions.get(hSession).getSessionInfo();
    }

    @Override
    public byte[] C_GetOperationState(long hSession) throws PKCS11Exception {
        LOG.debug("C_GetOperationState()");
        return null;
    }

    @Override
    public void C_SetOperationState(long hSession, byte[] pOperationState,
            long hEncryptionKey, long hAuthenticationKey)
                    throws PKCS11Exception {
        LOG.debug("C_SetOperationState()");
    }

    @Override
    public void C_Login(long hSession,
            long userType /* TODO differ between SO and USER */, char[] pPin,
            boolean arg3) throws PKCS11Exception {
        assertTrue("User type is assumed to be USER eg. 1",
                userType == PKCS11Constants.CKU_USER);

        long slotID = sessions.get(hSession).getSlotID();
        LOG.debug("C_Login() to slotID {} with sessionID: {}", slotID, hSession);
        Token token = tokens.get(slots.get(slotID).getTokenId());
        // TODO: Maybe to notice users when they have logged in already.

        if (Arrays.equals(pPin, token.getPassword())) {
            token.setActive(true);
            LOG.debug("Successfully logged in " +
                    "to token on slot with ID: {}", slotID);
            return;
        }
        throw new PKCS11Exception(160);
        /* error-code taken from iaik/pkcs/pkcs11/wrapper/ExceptionMessages.properties
         * CKR_PIN_INCORRECT
         * */
    }

    @Override
    public void C_Logout(long hSession) throws PKCS11Exception {
        LOG.debug("C_Logout()");
        long slotID = sessions.get(hSession).getSlotID();
        LOG.debug("Logging out of slotID {} with sessionID: {}", slotID, hSession);
        tokens.get(slots.get(slotID).getTokenId()).setActive(false);
    }

    @Override
    public long C_CreateObject(long hSession, CK_ATTRIBUTE[] pTemplate,
            boolean arg3) throws PKCS11Exception {
        LOG.debug("C_CreateObject()");
        return 0;
    }

    @Override
    public long C_CopyObject(long hSession, long hObject,
            CK_ATTRIBUTE[] pTemplate, boolean b) throws PKCS11Exception {
        LOG.debug("C_CopyObject()");
        return 0;
    }

    @Override
    public void C_DestroyObject(long hSession, long hObject)
            throws PKCS11Exception {
        LOG.debug("C_DestroyObject()");

    }

    @Override
    public long C_GetObjectSize(long hSession, long hObject)
            throws PKCS11Exception {
        LOG.debug("C_GetObjectSize()");
        return 0;
    }

    @Override
    public void C_GetAttributeValue(long hSession, long hObject,
            CK_ATTRIBUTE[] pTemplate, boolean b) throws PKCS11Exception {
        LOG.debug("C_GetAttributeValue()");
        sessions.get(hSession).getAttributeValue(hObject, pTemplate);
    }

    @Override
    public void C_SetAttributeValue(long hSession, long hObject,
            CK_ATTRIBUTE[] pTemplate, boolean b) throws PKCS11Exception {
        LOG.debug("C_SetAttributeValue()");
    }

    @Override
    public void C_FindObjectsInit(long hSession, CK_ATTRIBUTE[] pTemplate,
            boolean b) throws PKCS11Exception {
        LOG.debug("FindObjectsInit() in session: {}", hSession);

        sessions.get(hSession).findObjectsInit(pTemplate);
        LOG.debug("Found #{} objects in session #{}.",
                sessions.get(hSession).getFoundObj().size(), hSession);
    }

    @Override
    public long[] C_FindObjects(long hSession, long ulMaxObjectCount)
            throws PKCS11Exception {
        LOG.debug("FindObjects() in session: {}", hSession);
        return sessions.get(hSession).findObjects(ulMaxObjectCount);
    }

    @Override
    public void C_FindObjectsFinal(long hSession) throws PKCS11Exception {
        LOG.debug("C_FindObjectsFinal()");
        sessions.get(hSession).findObjectsFinal();
    }

    @Override
    public void C_EncryptInit(long hSession, CK_MECHANISM pMechanism, long hKey,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_EncryptInit()");

    }

    @Override
    public byte[] C_Encrypt(long hSession, byte[] pData) throws PKCS11Exception {
        LOG.debug("C_Encrypt()");
        return null;
    }

    @Override
    public byte[] C_EncryptUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_EncryptUpdate()");
        return null;
    }

    @Override
    public byte[] C_EncryptFinal(long hSession) throws PKCS11Exception {
        LOG.debug("C_EncryptFinal()");
        return null;
    }

    @Override
    public void C_DecryptInit(long hSession, CK_MECHANISM pMechanism, long hKey,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_DecryptInit()");

    }

    @Override
    public byte[] C_Decrypt(long hSession, byte[] pEncryptedData)
            throws PKCS11Exception {
        LOG.debug("C_Decrypt()");
        return null;
    }

    @Override
    public byte[] C_DecryptUpdate(long hSession, byte[] pEncryptedPart)
            throws PKCS11Exception {
        LOG.debug("C_DecryptUpdate()");
        return null;
    }

    @Override
    public byte[] C_DecryptFinal(long hSession) throws PKCS11Exception {
        LOG.debug("C_DecryptFinal()");
        return null;
    }

    @Override
    public void C_DigestInit(long hSession, CK_MECHANISM pMechanism,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_DigestInit()");

    }

    @Override
    public byte[] C_Digest(long hSession, byte[] data) throws PKCS11Exception {
        LOG.debug("C_Digest()");
        return null;
    }

    @Override
    public void C_DigestUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_DigestUpdate()");

    }

    @Override
    public void C_DigestKey(long hSession, long hKey) throws PKCS11Exception {
        LOG.debug("C_DigestKey()");

    }

    @Override
    public byte[] C_DigestFinal(long hSession) throws PKCS11Exception {
        LOG.debug("C_DigestFinal()");
        return null;
    }

    @Override
    public void C_SignInit(long hSession, CK_MECHANISM pMechanism, long hKey,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_SignInit()");

    }

    @Override
    public byte[] C_Sign(long hSession, byte[] pData) throws PKCS11Exception {
        LOG.debug("C_Sign()");
        return null;
    }

    @Override
    public void C_SignUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_SignUpdate()");

    }

    @Override
    public byte[] C_SignFinal(long hSession) throws PKCS11Exception {
        LOG.debug("C_SignFinal()");
        return null;
    }

    @Override
    public void C_SignRecoverInit(long hSession, CK_MECHANISM pMechanism,
            long hKey, boolean b) throws PKCS11Exception {
        LOG.debug("C_SignRecoverInit()");

    }

    @Override
    public byte[] C_SignRecover(long hSession, byte[] pData)
            throws PKCS11Exception {
        LOG.debug("C_SignRecover()");
        return null;
    }

    @Override
    public void C_VerifyInit(long hSession, CK_MECHANISM pMechanism, long hKey,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_VerifyInit()");

    }

    @Override
    public void C_Verify(long hSession, byte[] pData, byte[] pSignature)
            throws PKCS11Exception {
        LOG.debug("C_Verify()");

    }

    @Override
    public void C_VerifyUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_VerifyUpdate()");

    }

    @Override
    public void C_VerifyFinal(long hSession, byte[] pSignature)
            throws PKCS11Exception {
        LOG.debug("C_VerifyFinal()");

    }

    @Override
    public void C_VerifyRecoverInit(long hSession, CK_MECHANISM pMechanism,
            long hKey, boolean b) throws PKCS11Exception {
        LOG.debug("C_VerifyRecover()");

    }

    @Override
    public byte[] C_VerifyRecover(long hSession, byte[] pSignature)
            throws PKCS11Exception {
        LOG.debug("C_VerifyRecover()");
        return null;
    }

    @Override
    public byte[] C_DigestEncryptUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_DigestEncryptUpdate()");
        return null;
    }

    @Override
    public byte[] C_DecryptDigestUpdate(long hSession, byte[] pEncryptedPart)
            throws PKCS11Exception {
        LOG.debug("C_DecryptDigestUpdate()");
        return null;
    }

    @Override
    public byte[] C_SignEncryptUpdate(long hSession, byte[] pPart)
            throws PKCS11Exception {
        LOG.debug("C_SignEncryptUpdate()");
        return null;
    }

    @Override
    public byte[] C_DecryptVerifyUpdate(long hSession, byte[] pEncryptedPart)
            throws PKCS11Exception {
        LOG.debug("C_DecryptVerifyUpdate()");
        return null;
    }

    @Override
    public long C_GenerateKey(long hSession, CK_MECHANISM pMechanism,
            CK_ATTRIBUTE[] pTemplate, boolean b) throws PKCS11Exception {
        LOG.debug("C_GenerateKey()");
        return 0;
    }

    @Override
    public long[] C_GenerateKeyPair(long hSession, CK_MECHANISM pMechanism,
            CK_ATTRIBUTE[] pPublicKeyTemplate,
            CK_ATTRIBUTE[] pPrivateKeyTemplate, boolean b)
                    throws PKCS11Exception {
        // TODO: Read templates? Or just make less generic and
        // use RSAPublicKey and RSAPrivateKey with right arguments? probably no.
        // TODO: Save keys. like Softtoken is holding keys? Other option is to
        // hold them in java objects?
        LOG.debug("C_GenerateKeyPair()");
        long slotId = sessions.get(hSession).getSlotID();
        long tokenId = slots.get(slotId).getTokenId();
        long[] kpHandle = tokens.get(tokenId).generateKeyPair(
                pMechanism, pPublicKeyTemplate, pPrivateKeyTemplate);
        sessions.get(hSession).update();
        return kpHandle;
    }

    @Override
    public byte[] C_WrapKey(long hSession, CK_MECHANISM pMechanism,
            long hWrappingKey, long hKey, boolean b) throws PKCS11Exception {
        LOG.debug("C_WrapKey()");
        return null;
    }

    @Override
    public long C_UnwrapKey(long hSession, CK_MECHANISM pMechanism,
            long hUnwrappingKey, byte[] pWrappedKey, CK_ATTRIBUTE[] pTemplate,
            boolean b) throws PKCS11Exception {
        LOG.debug("C_UnwrapKey()");
        return 0;
    }

    @Override
    public long C_DeriveKey(long hSession, CK_MECHANISM pMechanism,
            long hBaseKey, CK_ATTRIBUTE[] pTemplate, boolean b)
                    throws PKCS11Exception {
        LOG.debug("C_DeriveKey()");
        return 0;
    }

    @Override
    public void C_SeedRandom(long hSession, byte[] pSeed)
            throws PKCS11Exception {
        LOG.debug("C_SeedRandom()");

    }

    @Override
    public void C_GenerateRandom(long hSession, byte[] randomData)
            throws PKCS11Exception {
        LOG.debug("C_GenerateRandom()");

    }

    @Override
    public void C_GetFunctionStatus(long hSession) throws PKCS11Exception {
        LOG.debug("C_GetFunctionStatus()");

    }

    @Override
    public void C_CancelFunction(long hSession) throws PKCS11Exception {
        LOG.debug("C_CancelFunction()");

    }

    @Override
    public long C_WaitForSlotEvent(long flags, Object pReserved)
            throws PKCS11Exception {
        LOG.debug("C_WaitForSlotEvent()");
        return 0;
    }

    @Override
    public void finalize() throws Throwable {
        LOG.debug("finalize()");
        super.finalize();
    }

}
