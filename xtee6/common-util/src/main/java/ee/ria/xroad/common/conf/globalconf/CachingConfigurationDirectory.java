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

import ee.ria.xroad.common.SystemProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Caching configuration directory
 */
@Slf4j
public class CachingConfigurationDirectory extends ConfigurationDirectory {

  public static final String INSTANCE_IDENTIFIER = "instanceIdentifier";
  public static final String PRIVATE_PARAMS = "privateParams";
  public static final String SHARED_PARAMS = "sharedParams";
  public static final String VERIFY_UP_TO_DATE = "verifyUpToDate";
  public static final String RELOAD = "reload";

  private final int expireSeconds;
  private final TimeBasedObjectCache cache;

  /**
   * Constructs new caching directory from the given path.
   * @param directoryPath the path to the directory.
   * @throws Exception if loading configuration fails
   */
  public CachingConfigurationDirectory(String directoryPath) throws Exception {
    super(directoryPath, false);
    expireSeconds = SystemProperties.getConfigurationClientUpdateIntervalSeconds();
    cache = new TimeBasedObjectCache(expireSeconds);
    reload();
  }

  /**
   * Constructs new caching directory from the given path.
   * @param directoryPath the path to the directory.
   * @param reloadIfChanged if true, automatic reload and detection of
   * parameters is performed.
   * @throws Exception if loading configuration fails
   */
  public CachingConfigurationDirectory(String directoryPath,
                                boolean reloadIfChanged) throws Exception {
    super(directoryPath, reloadIfChanged);
    expireSeconds = SystemProperties.getConfigurationClientUpdateIntervalSeconds();
    cache = new TimeBasedObjectCache(expireSeconds);
    reload();
  }

  /**
   *
   */
  @Override
  public synchronized String getInstanceIdentifier() {
    if (!cache.isValid(INSTANCE_IDENTIFIER)) {
      cache.setValue(INSTANCE_IDENTIFIER, super.getInstanceIdentifier());
    }
    return (String) cache.getValue(INSTANCE_IDENTIFIER);
  }

  /**
   * Returns private parameters for a given instance identifier.
   * @param instanceId the instance identifier
   * @return private parameters or null, if no private parameters exist for
   * given instance identifier
   * @throws Exception if an error occurs while reading parameters
   */
  @Override
  public synchronized PrivateParameters getPrivate(String instanceId)
      throws Exception {
    final String key = String.format("%s-%s", PRIVATE_PARAMS, instanceId);
    if (!cache.isValid(key)) {
      cache.setValue(key, super.getPrivate(instanceId));
    }
    return (PrivateParameters) cache.getValue(key);
  }

  /**
   * Returns shared parameters for a given instance identifier.
   * @param instanceId the instance identifier
   * @return shared parameters or null, if no shared parameters exist for
   * given instance identifier
   * @throws Exception if an error occurs while reading parameters
   */
  @Override
  public synchronized SharedParameters getShared(String instanceId)
      throws Exception {
    final String key = String.format("%s-%s", SHARED_PARAMS, instanceId);
    if (!cache.isValid(key)) {
      cache.setValue(key, super.getShared(instanceId));
    }
    return (SharedParameters) cache.getValue(key);
  }

  /**
   * Throws exception with error code ErrorCodes.X_OUTDATED_GLOBALCONF if any of the
   * configuration files is too old.
   */
  @Override
  public synchronized void verifyUpToDate() throws Exception {
    if (!cache.isValid(VERIFY_UP_TO_DATE)) {
      super.verifyUpToDate();
      cache.setValue(VERIFY_UP_TO_DATE, 1);
    }
  }

  /**
   * Reloads the configuration directory. Only files that are new or have
   * changed, are actually loaded.
   * @throws Exception if an error occurs during reload
   */
  @Override
  public synchronized void reload() throws Exception {
    // cache validity indicates whether reloading should be done at this time
    // cache value is meaningless in this case
    if (cache != null && !cache.isValid(RELOAD)) {
      cache.setValue(RELOAD, 1);
      super.reload();
    }
  }
}
