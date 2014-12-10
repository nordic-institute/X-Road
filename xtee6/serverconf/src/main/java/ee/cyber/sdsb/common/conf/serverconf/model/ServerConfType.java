package ee.cyber.sdsb.common.conf.serverconf.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServerConfType {

    private Long id;

    private String serverCode;

    private ClientType owner;

    private final List<ClientType> client = new ArrayList<>();

    private final List<TspType> tsp = new ArrayList<>();

}
