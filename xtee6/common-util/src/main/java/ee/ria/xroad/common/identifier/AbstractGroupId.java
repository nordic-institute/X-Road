package ee.ria.xroad.common.identifier;

/**
 * Base class for group IDs.
 */
public abstract class AbstractGroupId extends XroadId {

    private final String groupCode;

    AbstractGroupId() { // required by Hibernate
        this(null, null, null);
    }

    protected AbstractGroupId(XroadObjectType type, String xRoadInstance,
            String groupCode) {
        super(type, xRoadInstance);

        this.groupCode = groupCode;
    }

    /**
     * Gets the group code.
     * @return String
     */
    public String getGroupCode() {
        return groupCode;
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {groupCode};
    }
}
