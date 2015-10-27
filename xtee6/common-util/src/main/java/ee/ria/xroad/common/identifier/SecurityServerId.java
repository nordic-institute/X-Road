package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Security server ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityServerIdAdapter.class)
public final class SecurityServerId extends XRoadId {

    private final String memberClass;
    private final String memberCode;
    private final String serverCode;

    SecurityServerId() { // required by Hibernate
        this(null, null, null, null);
    }

    private SecurityServerId(String xRoadInstance,
            String memberClass, String memberCode, String serverCode) {
        super(XRoadObjectType.SERVER, xRoadInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serverCode = serverCode;
    }

    /**
     * Returns the owner member class of thesecurity server.
     * @return String
     */
    public String getMemberClass() {
        return memberClass;
    }

    /**
     * Returns the owner member code of the security server.
     * @return String
     */
    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns the server code of the security server.
     * @return String
     */
    public String getServerCode() {
        return serverCode;
    }

    /**
     * Returns the client ID of the owner of the security server.
     * @return ClientId
     */
    public ClientId getOwner() {
        return ClientId.create(getXRoadInstance(), memberClass, memberCode);
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {memberClass, memberCode, serverCode};
    }

    /**
     * Factory method for creating a new SecurityServerId.
     * @param xRoadInstance instance of the new security server
     * @param memberClass class of the new security server owner
     * @param memberCode code of the new security server owner
     * @param serverCode code of the new security server
     * @return SecurityServerId
     */
    public static SecurityServerId create(String xRoadInstance,
            String memberClass, String memberCode, String serverCode) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateField("serverCode", serverCode);
        return new SecurityServerId(xRoadInstance, memberClass, memberCode,
                serverCode);
    }

    /**
     * Factory method for creating a new SecurityServerId from ClientId and
     * server code.
     * @param client ID of the new security server owner
     * @param serverCode code of the new security server
     * @return SecurityServerId
     */
    public static SecurityServerId create(ClientId client, String serverCode) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), serverCode);
    }

}
