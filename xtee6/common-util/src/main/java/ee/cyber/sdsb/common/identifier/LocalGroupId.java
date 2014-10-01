package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.LocalGroupIdAdapter.class)
public final class LocalGroupId extends AbstractGroupId {

    LocalGroupId() { // required by Hibernate
        this(null);
    }

    private LocalGroupId(String groupCode) {
        super(SdsbObjectType.LOCALGROUP, null, groupCode);
    }

    /**
     * Factory method for creating a new LocalGroupId.
     */
    public static LocalGroupId create(String groupCode) {
        validateField("groupCode", groupCode);
        return new LocalGroupId(groupCode);
    }
}
