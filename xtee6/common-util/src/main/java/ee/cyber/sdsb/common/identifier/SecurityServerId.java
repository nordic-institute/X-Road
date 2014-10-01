package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityServerIdAdapter.class)
public final class SecurityServerId extends SdsbId {

    private final String memberClass;
    private final String memberCode;
    private final String serverCode;

    SecurityServerId() { // required by Hibernate
        this(null, null, null, null);
    }

    private SecurityServerId(String sdsbInstance,
            String memberClass, String memberCode, String serverCode) {
        super(SdsbObjectType.SERVER, sdsbInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serverCode = serverCode;
    }

    public String getMemberClass() {
        return memberClass;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public String getServerCode() {
        return serverCode;
    }

    public ClientId getOwner() {
        return ClientId.create(getSdsbInstance(), memberClass, memberCode,
                null);
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] { memberClass, memberCode, serverCode };
    }

    /**
     * Factory method for creating a new SecurityServerId.
     */
    public static SecurityServerId create(String sdsbInstance,
            String memberClass, String memberCode, String serverCode) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateField("serverCode", serverCode);
        return new SecurityServerId(sdsbInstance, memberClass, memberCode,
                serverCode);
    }

    /**
     * Factory method for creating a new SecurityServerId from ClientId and
     * server code.
     */
    public static SecurityServerId create(ClientId client, String serverCode) {
        return create(client.getSdsbInstance(), client.getMemberClass(),
                client.getMemberCode(), serverCode);
    }

}
