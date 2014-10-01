package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.cyber.sdsb.common.identifier.SdsbId;

@Getter
@Setter
public class AuthorizedSubjectType {

    private Long id;

    private SdsbId subjectId;

    private Date rightsGiven;
}
