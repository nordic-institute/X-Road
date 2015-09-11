package ee.ria.xroad.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.ria.xroad.common.identifier.XroadId;

/**
 * Access right.
 */
@Getter
@Setter
public class AccessRightType {

    private Long id;

    private String serviceCode;

    private XroadId subjectId;

    private Date rightsGiven;
}
