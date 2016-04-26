/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.conf.globalconf;

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
