package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Global group ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.GlobalGroupIdAdapter.class)
public final class GlobalGroupId extends AbstractGroupId {

    GlobalGroupId() { // required by Hibernate
        this(null, null);
    }

    private GlobalGroupId(String xRoadInstance, String groupCode) {
        super(XRoadObjectType.GLOBALGROUP, xRoadInstance, groupCode);
    }

    /**
     * Factory method for creating a new GlobalGroupId.
     * @param xRoadInstance instance of the new group
     * @param groupCode code of the new group
     * @return GlobalGroupId
     */
    public static GlobalGroupId create(String xRoadInstance, String groupCode) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("groupCode", groupCode);
        return new GlobalGroupId(xRoadInstance, groupCode);
    }

}
