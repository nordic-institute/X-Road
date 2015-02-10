package ee.cyber.sdsb.common.conf.globalconf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joda.time.DateTime;

/**
 * Downloaded configuration directory.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Configuration {

    private final ConfigurationLocation location;

    private final List<ConfigurationFile> files = new ArrayList<>();

    @Setter(AccessLevel.PACKAGE)
    private DateTime expirationDate;

    /**
     * For each file, calls a function taking the ConfigurationLocation
     * and ConfigurationFile as arguments.
     * @param consumer the consumer
     */
    public void eachFile(BiConsumer<ConfigurationLocation,
            ConfigurationFile> consumer) {
        files.forEach(file -> consumer.accept(location, file));
    }

    /**
     * @return true, if the configuration is expired at the current date
     */
    public boolean isExpired() {
        return expirationDate != null
                && new DateTime().isAfter(expirationDate);
    }
}
