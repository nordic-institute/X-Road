/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * version information
 */
export type VersionInfo = {
  /**
   * information about the security server
   */
  info: string;
  /**
   * java version currently in use
   */
  java_version: number;
  /**
   * minimum supported java version
   */
  min_java_version: number;
  /**
   * maximum supported java version
   */
  max_java_version: number;
  /**
   * true if currently used java version is supported
   */
  using_supported_java_version: boolean;
  /**
   * java vendor string from java.vendor system property
   */
  java_vendor: string;
  /**
   * java runtime version string from java.runtime.version system property
   */
  java_runtime_version: string;
};
