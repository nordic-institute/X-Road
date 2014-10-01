package ee.cyber.sdsb.common.identifier;


import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import static ee.cyber.sdsb.common.identifier.SdsbObjectType.MEMBER;
import static ee.cyber.sdsb.common.identifier.SdsbObjectType.SUBSYSTEM;

@XmlJavaTypeAdapter(IdentifierTypeConverter.ClientIdAdapter.class)
public final class ClientId extends SdsbId {

    private final String memberClass;
    private final String memberCode;
    private final String subsystemCode;

    ClientId() { // required by Hibernate
        this(null, null, null, null);
    }

    private ClientId(String sdsbInstance, String memberClass,
            String memberCode, String subsystemCode) {
        super(subsystemCode == null ? MEMBER : SUBSYSTEM, sdsbInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.subsystemCode = subsystemCode;
    }

    public String getMemberClass() {
        return memberClass;
    }

    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns subsystem code, if present, or null otherwise.
     */
    public String getSubsystemCode() {
        return subsystemCode;
    }

    /**
     * Returns true if and only if this object represents a subsystem and
     * the argument represents a member and this object contains the member
     * class and code.
     */
    public boolean subsystemContainsMember(ClientId member) {
        if (getObjectType() == SdsbObjectType.SUBSYSTEM &&
                member.getObjectType() == SdsbObjectType.MEMBER) {
            return getSdsbInstance().equals(member.getSdsbInstance())
                    && getMemberClass().equals(member.getMemberClass())
                    && getMemberCode().equals(member.getMemberCode());
        }

        return false;
    }

    /**
     * Returns true, if two identifiers, this and other, refer to the same
     * SDSB member. This comparison ignores subsystem part of the identifier.
     * Thus, SUBSYSTEM:XX/YY/ZZ/WW is considered equal to
     * SUBSYSTEM:XX/YY/ZZ/TT and MEMBER:XX/YY/ZZ.
     */
    public boolean memberEquals(ClientId other) {
        return getSdsbInstance().equals(other.getSdsbInstance())
                && getMemberClass().equals(other.getMemberClass())
                && getMemberCode().equals(other.getMemberCode());
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] { memberClass, memberCode, subsystemCode };
    }

    /**
     * Factory method for creating a new Subsystem.
     */
    public static ClientId create(String sdsbInstance,
            String memberClass, String memberCode, String subsystemCode) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateOptionalField("subsystemCode", subsystemCode);

        return new ClientId(
                sdsbInstance, memberClass, memberCode, subsystemCode);
    }

    /**
     * Factory method for creating a new ClientId.
     */
    public static ClientId create(String sdsbInstance,
            String memberClass, String memberCode) {
        return create(sdsbInstance, memberClass, memberCode, null);
    }

}
