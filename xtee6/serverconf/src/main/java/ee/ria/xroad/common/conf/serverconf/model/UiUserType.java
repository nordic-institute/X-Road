package ee.ria.xroad.common.conf.serverconf.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Ui user.
 */
@Getter
@Setter
public class UiUserType {

    private Long id;

    private String username;

    private String locale;
}
