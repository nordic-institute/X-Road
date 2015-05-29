package ee.ria.xroad.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Local group.
 */
@Getter
@Setter
public class LocalGroupType {

    private Long id;

    private String groupCode;

    private String description;

    private final List<GroupMemberType> groupMember = new ArrayList<>();

    private Date updated;
}
