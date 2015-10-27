package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Local group ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.LocalGroupIdAdapter.class)
public final class LocalGroupId extends AbstractGroupId {

    LocalGroupId() { // required by Hibernate
        this(null);
    }

    private LocalGroupId(String groupCode) {
        super(XRoadObjectType.LOCALGROUP, null, groupCode);
    }

    /**
     * Factory method for creating a new LocalGroupId.
     * @param groupCode code of the new group
     * @return LocalGroupId
     */
    public static LocalGroupId create(String groupCode) {
        validateField("groupCode", groupCode);
        return new LocalGroupId(groupCode);
    }
}
