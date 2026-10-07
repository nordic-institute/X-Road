/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.confclient.common.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Describes one signed global configuration fixture set: the directory holding the configuration parts, the
 * configuration version, the expiry written into the signed index and the location of that index.
 *
 * @param name          human-readable name of the set
 * @param confRoot      directory under which {@code V<version>/} resides
 * @param confPath      location of the configuration parts below {@code confRoot}, with a leading slash
 * @param version       global configuration version
 * @param expireDate    expiry written into the signed index
 */
record GlobalConfFixtureSet(String name, String confRoot, String confPath, int version, Instant expireDate) {
    private static final String INSTANCE_IDENTIFIER = "DEV";
    private static final String CLIENT_COMMON_ROOT = "src/test/resources/nginx-container-files/var/lib/xroad/public";
    private static final String API_TEST_RESOURCES = "../../../security-server/api-test/src/intTest/resources";
    private static final Instant BASELINE_EXPIRE_DATE = Instant.parse("2035-11-11T03:07:40Z");
    private static final Instant ROTATION_EXPIRE_DATE = Instant.parse("2035-11-11T03:08:40Z");

    static Stream<GlobalConfFixtureSet> all() {
        return Stream.concat(Stream.of(clientCommonV6()), version7());
    }

    static Stream<GlobalConfFixtureSet> version7() {
        return Stream.of(
                new GlobalConfFixtureSet("configuration client V7", CLIENT_COMMON_ROOT,
                        "/V7/20260810120000000000000", 7, BASELINE_EXPIRE_DATE),
                new GlobalConfFixtureSet("security server api-test baseline V7",
                        API_TEST_RESOURCES + "/nginx-container-files/var/lib/xroad/public",
                        "/V7/20260827090000000000000", 7, BASELINE_EXPIRE_DATE),
                new GlobalConfFixtureSet("security server api-test rotation V7",
                        API_TEST_RESOURCES + "/files/global_conf_signed_with_rotated_keys",
                        "/V7/20260827090100000000000", 7, ROTATION_EXPIRE_DATE)
        );
    }

    private static GlobalConfFixtureSet clientCommonV6() {
        return new GlobalConfFixtureSet("configuration client V6", CLIENT_COMMON_ROOT,
                "/V6/20251110170000548026000", 6, BASELINE_EXPIRE_DATE);
    }

    String instanceIdentifier() {
        return INSTANCE_IDENTIFIER;
    }

    Path partsDirectory() {
        return Path.of(confRoot, confPath);
    }

    Path signedIndexPath() {
        return Path.of(confRoot, "V" + version, "internalconf");
    }

    @Override
    public String toString() {
        return name;
    }
}
