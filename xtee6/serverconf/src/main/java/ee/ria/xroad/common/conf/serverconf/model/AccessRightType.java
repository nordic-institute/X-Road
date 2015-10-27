package ee.ria.xroad.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.ria.xroad.common.identifier.XRoadId;

/**
 * Access right.
 */
@Getter
@Setter
public class AccessRightType {

    private Long id;

    private String serviceCode;

    private XRoadId subjectId;

    private Date rightsGiven;
}
