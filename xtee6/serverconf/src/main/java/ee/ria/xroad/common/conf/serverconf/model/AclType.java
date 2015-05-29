package ee.ria.xroad.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Access control list.
 */
@Getter
@Setter
public class AclType {

    private Long id;

    private String serviceCode;

    private final List<AuthorizedSubjectType> authorizedSubject =
            new ArrayList<>();
}
