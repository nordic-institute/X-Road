package ee.cyber.sdsb.signer;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;


// TODO: Refactor or completely redesign this class.
public class CsrCertStructure {

    private String id;

    private HashMap<TokenInfo, KeyInfo> tokenKeyReq =
            new HashMap<>(); /* is necessary for creating separately key
            and csr - creating csr assumes GenerateKeysResponse to create*/
    private HashMap<ClientId, byte[]> csrMap = new HashMap<>();
    private ArrayList<ObjectMemberPair> certList = new ArrayList<>();

    public CsrCertStructure(TokenInfo di, KeyInfo req) {
        this.tokenKeyReq.put(di, req);
    }

    public CsrCertStructure() {
    }

    public CsrCertStructure(byte[] csr, byte[] cert, ClientId member)
            throws Exception {
        addToCertList(cert, member);
        addToCsrList(csr, member);
    }

    public CsrCertStructure(ArrayList<byte[]> r, String what,
            ClientId member) throws Exception {
        if (what.equalsIgnoreCase("csr")) {
            for (byte[] csr : r) {
                addToCsrList(csr, member);
            }
        } else {
            for (byte[] cert : r) {
                addToCertList(cert, member);

            }
        }
    }

    public CsrCertStructure(String id, byte[] r, String what, ClientId member)
            throws Exception {
        this.id = id;

        if (what.equalsIgnoreCase("csr")) {
            addToCsrList(r, member);
        } else if (what.equalsIgnoreCase("cert")) {
            addToCertList(r, member);
        } else {
            throw new Exception("'what' can only be 'csr' or 'cert'");
        }
    }

    public String getId() {
        return id;
    }

    public ArrayList<ClientId> getCertListMember() {
        ArrayList<ClientId> members = new ArrayList<>();
        for (ObjectMemberPair certMem : this.certList) {
            members.add(certMem.getMember());
        }
        return members;
    }

    public ArrayList<byte[]> getCertListCerts() {
        ArrayList<byte[]> certs = new ArrayList<>();
        for (ObjectMemberPair certMem : this.certList) {
            certs.add(certMem.getObj());
        }
        return certs;
    }

    public HashMap<ClientId, byte[]> getCsrMemList() {
        return this.csrMap;
    }

    public void setCertList(ArrayList<byte[]> certLst, ClientId member) {
        ArrayList<ObjectMemberPair> lst = new ArrayList<>();
        for (byte[] cert : certLst) {
            lst.add(new ObjectMemberPair(member, cert));
        }
        this.certList = lst;
    }

    public void setCsrMap(HashMap<ClientId, byte[]> csrs) {
        this.csrMap = csrs;
    }

    public void setTokenKeyReq(HashMap<TokenInfo, KeyInfo> memKeyReq) {
        this.tokenKeyReq = memKeyReq;
    }

    public ArrayList<ObjectMemberPair> getCertList() {
        return this.certList;
    }

    public byte[] getCert(int index) {
        return this.certList.get(index).getObj();
    }

    public byte[] getCsr(ClientId member) {
        return this.csrMap.get(member);
    }

    public KeyInfo getTokenKeyReq(TokenInfo token) {
        return tokenKeyReq.get(token);
    }

    public boolean addToCertList(byte[] cert, ClientId member) {
        return this.certList.add(new ObjectMemberPair(member, cert));
    }

    public void addToCsrList(byte[] csr, ClientId member) throws Exception {
        this.csrMap.put(member, csr);
        if (null == this.csrMap.get(member)) {
            throw new Exception("FATAL");
        }
    }

    public void addToTokenKeyReqList(TokenInfo token, KeyInfo req) {
        this.tokenKeyReq.put(token, req);
    }

    public void delCert(byte[] cert) throws Exception {
        for (ObjectMemberPair pair : certList) {
            if (pair.getObj().equals(cert)) {
                certList.remove(pair);
                return;
            }
        }
        throw new Exception("Cannot delete cert. Cert not in configuration.");
    }

    public void delCsr(ClientId member) {
        if (null == this.csrMap.get(member)) { //csrMap.remove(member)) {
            // TODO: there is a problem with random tester; ongoing debugging
            System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        }
        this.csrMap.remove(member);
        if (null == this.csrMap.get(member)) { //csrMap.remove(member)) {
            System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvv");
        }
    }

    public void delAllCsr() {
        csrMap.clear();
    }

    public void delMemKeyReq(ClientId member) {
        this.tokenKeyReq.remove(member);
    }

    public boolean certListContains(byte[] cert) {
        for (ObjectMemberPair certMem : this.certList) {
            if (Arrays.equals(certMem.getObj(), cert)) {
                return true;
            }
        }
        return false;
    }
    public boolean csrListContains(byte[] csrByte) {
        for (byte[] csr: this.csrMap.values()) {
            if (Arrays.equals(csr, csrByte)) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(CsrCertStructure ccs) {
        for (ObjectMemberPair certMem : ccs.certList) {
            if (!this.certList.contains(certMem)) {
                return false;
            }
        }

        for (ClientId csrMem : ccs.getCsrMemList().keySet()) {
            if (!csrMap.keySet().contains(csrMem)) {
                return false;
            }
        }
        return true;
    }

    public String objListToString(String obj) {
        String niceList = "";
        switch (obj) {
            case "csrMem":
                for (ClientId mem : csrMap.keySet()) {
                    niceList += mem.getMemberCode() + ", ";
                }
                break;
        }
        return niceList;
    }

    public class ObjectMemberPair {
        private ClientId member;
        private byte[] object;
        public ObjectMemberPair(ClientId client, byte[] object) {
            this.member = client;
            this.object = object;
        }
        public ClientId getMember() {
            return member;
        }
        public byte[] getObj() {
            return object;
        }
    }
}
