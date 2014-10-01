package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.cyber.sdsb.common.identifier.ClientId;

@Getter
@Setter
public class GroupMemberType {

    private Long id;

    private ClientId groupMemberId;

    private Date added;

}
