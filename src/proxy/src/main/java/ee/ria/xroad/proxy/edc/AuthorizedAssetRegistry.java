package ee.ria.xroad.proxy.edc;

import java.util.Optional;

public interface AuthorizedAssetRegistry {


    Optional<InMemoryAuthorizedAssetRegistry.GrantedAssetInfo> getAssetInfoById(String id);

    void registerAsset(InMemoryAuthorizedAssetRegistry.GrantedAssetInfo assetInfo);
}
