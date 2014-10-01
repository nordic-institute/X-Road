package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AclType {

    private Long id;

    private String serviceCode;

    private final List<AuthorizedSubjectType> authorizedSubject =
            new ArrayList<>();
}
