package ee.ria.xroad.proxy.edc;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Optional;

@Component
public class InMemoryAuthorizedAssetRegistry implements AuthorizedAssetRegistry {

    /*
    TODO xroad8 This implementation is missing any registry invalidation, persistance. Usable only for POC.
    * */
    private final HashMap<String, GrantedAssetInfo> registry = new HashMap<>();

    public Optional<GrantedAssetInfo> getAssetInfoById(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public record GrantedAssetInfo(String id,
                                   String contractId,
                                   String endpoint,
                                   String authKey,
                                   String authCode) {
    }


}
