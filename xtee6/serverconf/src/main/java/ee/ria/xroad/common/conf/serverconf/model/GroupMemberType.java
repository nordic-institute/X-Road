package ee.ria.xroad.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Group member.
 */
@Getter
@Setter
public class GroupMemberType {

    private Long id;

    private ClientId groupMemberId;

    private Date added;

}
