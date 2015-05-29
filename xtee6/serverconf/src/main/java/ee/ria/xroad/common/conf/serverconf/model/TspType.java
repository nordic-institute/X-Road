package ee.ria.xroad.common.conf.serverconf.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Time stamping provider.
 */
@Getter
@Setter
public class TspType {

    private Long id;

    private String name;

    private String url;
}
