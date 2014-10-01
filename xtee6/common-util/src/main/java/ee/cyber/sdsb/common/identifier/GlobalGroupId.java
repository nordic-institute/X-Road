package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(IdentifierTypeConverter.GlobalGroupIdAdapter.class)
public final class GlobalGroupId extends AbstractGroupId {

    GlobalGroupId() { // required by Hibernate
        this(null, null);
    }

    private GlobalGroupId(String sdsbInstance, String groupCode) {
        super(SdsbObjectType.GLOBALGROUP, sdsbInstance, groupCode);
    }

    /**
     * Factory method for creating a new GlobalGroupId.
     */
    public static GlobalGroupId create(String sdsbInstance, String groupCode) {
        validateField("sdsbInstance", sdsbInstance);
        validateField("groupCode", groupCode);
        return new GlobalGroupId(sdsbInstance, groupCode);
    }

}
