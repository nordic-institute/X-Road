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

package org.niis.xroad.signer.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration properties for Argon2 password hashing algorithm used in PIN hashing.
 * <p>
 * The parameters can be tuned to balance between security and performance:
 * - Higher values increase security but also increase computation time and memory usage
 * - Lower values improve performance but may reduce security
 */
@ConfigMapping(prefix = "xroad.signer.pin-hasher")
public interface SoftwarePinHasherProperties {
    /**
     * Number of iterations to perform.
     * <p>
     * Increasing this value increases the time required to compute the hash.
     * Each iteration makes the hash more resistant to brute-force attacks.
     *
     * @return number of iterations (default: 3)
     */
    @WithName("iterations")
    @WithDefault("4")
    int iterations();

    /**
     * Amount of memory to use in kilobytes.
     * <p>
     * This parameter determines the memory hardness of the algorithm.
     * Higher values make the algorithm more resistant to GPU-based attacks
     * but require more memory during computation.
     *
     * @return memory usage in kilobytes (default: 12)
     */
    @WithName("memory-kb")
    @WithDefault("19456")
    int memoryKb();

    /**
     * Degree of parallelism.
     * <p>
     * This parameter determines how many parallel threads can be used for computation.
     * Higher values allow better utilization of multi-core systems but may increase
     * the total memory usage.
     *
     * @return degree of parallelism (default: 4)
     */
    @WithName("parallelism")
    @WithDefault("4")
    int parallelism();

    /**
     * Length of the generated hash in bytes.
     * <p>
     * This determines the size of the output hash. A longer hash provides
     * more security but requires more storage space.
     *
     * @return hash length in bytes (default: 32)
     */
    @WithName("hash-length")
    @WithDefault("32")
    int hashLength();
}
