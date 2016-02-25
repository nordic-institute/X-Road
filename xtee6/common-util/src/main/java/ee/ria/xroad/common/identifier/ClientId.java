package ee.ria.xroad.common.identifier;


import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import static ee.ria.xroad.common.identifier.XRoadObjectType.MEMBER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;

/**
 * Client ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.ClientIdAdapter.class)
public final class ClientId extends XRoadId {

    private final String memberClass;
    private final String memberCode;
    private final String subsystemCode;

    ClientId() { // required by Hibernate
        this(null, null, null, null);
    }

    private ClientId(String xRoadInstance, String memberClass,
            String memberCode, String subsystemCode) {
        super(subsystemCode == null ? MEMBER : SUBSYSTEM, xRoadInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.subsystemCode = subsystemCode;
    }

    /**
     * Returns the member class of the client.
     * @return String
     */
    public String getMemberClass() {
        return memberClass;
    }

    /**
     * Returns the member code of the client.
     * @return String
     */
    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns subsystem code, if present, or null otherwise.
     * @return String or null
     */
    public String getSubsystemCode() {
        return subsystemCode;
    }

    /**
     * Determines whether the given member is a subsystem of this client.
     * @param member ID of the potential subsystem
     * @return true if and only if this object represents a subsystem and
     * the argument represents a member and this object contains the member
     * class and code
     */
    public boolean subsystemContainsMember(ClientId member) {
        if (member != null
                && getObjectType() == XRoadObjectType.SUBSYSTEM
                && member.getObjectType() == XRoadObjectType.MEMBER) {
            return getXRoadInstance().equals(member.getXRoadInstance())
                    && getMemberClass().equals(member.getMemberClass())
                    && getMemberCode().equals(member.getMemberCode());
        }

        return false;
    }

    /**
     * Determines if another client is equal to this one. This comparison
     * ignores subsystem part of the identifier.
     * Thus, SUBSYSTEM:XX/YY/ZZ/WW is considered equal to
     * SUBSYSTEM:XX/YY/ZZ/TT and MEMBER:XX/YY/ZZ.
     * @param other the ID of the other client
     * @return true, if two identifiers, this and other, refer to the same
     * X-Road member
     */
    public boolean memberEquals(ClientId other) {
        if (other == null) {
            return false;
        }

        return getXRoadInstance().equals(other.getXRoadInstance())
                && getMemberClass().equals(other.getMemberClass())
                && getMemberCode().equals(other.getMemberCode());
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {memberClass, memberCode, subsystemCode};
    }

    /**
     * Factory method for creating a new Subsystem.
     * @param xRoadInstance instance of the new subsystem
     * @param memberClass member class of the new subsystem
     * @param memberCode member code of the new subsystem
     * @param subsystemCode subsystem code of the new subsystem
     * @return ClientId
     */
    public static ClientId create(String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateOptionalField("subsystemCode", subsystemCode);

        return new ClientId(
                xRoadInstance, memberClass, memberCode, subsystemCode);
    }

    /**
     * Factory method for creating a new ClientId.
     * @param xRoadInstance instance of the new client
     * @param memberClass member class of the new client
     * @param memberCode member code of the new client
     * @return ClientId
     */
    public static ClientId create(String xRoadInstance,
            String memberClass, String memberCode) {
        return create(xRoadInstance, memberClass, memberCode, null);
    }

}
