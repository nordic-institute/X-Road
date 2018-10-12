# Change Log

## 6.20.0 - XXXX-XX-XX
- TBD

## 6.19.0 - 2018-09-27
- PVAYLADEV-1107/XRJD #214: Security Server: Fix SSL handshake does not include internal certificate's certificate chain.
- PVAYLADEV-1139: Documentation: Update build and development environment instructions.
- PVAYLADEV-785: Security Server: Fix malformed global configuration can cause a NPE in the admin UI.
- PVAYLADEV-1137/XRJD #228: Security server: List approved certificate authorities.
- XRDDEV-36: Fixed Messagelog tests randomly failing.
- XRDDEV-39: Fix subsystem registration request failing on CIS hardened RHEL security server.
- XRDDEV-53: Fix access right error message in webview.
- XRDDEV-51: Security Server refresh OCSP responses recovery algorithm during OCSP responder service breaks was changed from Fibonacci based schedule to fixed schedule.
- XRDDEV-79: Fix X-Road package dependencies on Ubuntu 14 so that OpenJDK 8 is always installed.
- XRDDEV-28: Fix performance problem in FastestConnectionSelectingSSLSocketFactory when connecting to a clustered X-Road server.
- XRDDEV-85: Fix failure in message log archiving.
- XRDDEV-61: Fix last hash step tmp files written in the filesystem.
- XRDDEV-74: Fix blocker and critical level issues reported by SonarQube static analysis.
- XRDEV-60 / XTE-425: Central Server: Added script for changing IP address of cluster nodes.

## 6.18.0 - 2018-05-14
- XTE-314 / Backlog #221: Central Server: Bugfix: Write globalconf files atomic way.
- XTE-377: Fixed XXE issues (CWE-827) found by Coverity.
- XTE-396 / Backlog #202: Security Server / Central Server / Configuration Proxy: Improved the globalconf validity check and removal of the globalconf files not distributed any more.
- XTE-397 / Backlog #203: Configuration-Client: Better old globalconf files cleanup after download.
- XTE-409 / Backlog #222: Document UG-OPMONSYSPAR: Better explanation for the parameter 'op-monitor-buffer.sending-interval-seconds'.
- PVAYLADEV-989 / XRDJ #206: Fixed incomplete back-up archives remaining on disk after back-up ending in error
- PVAYLADEV-1081 / XRDJ #210: Fixed visible whitespace in central server address text field
- PVAYLADEV-981 / XRDJ #183: Fix java import order to comply with the style guide
- PVAYLADEV-1102: Fixed test CA instructions, added UTF-8 support for test certificate fields and replaced deprecated Ansible include commands with import_tasks commands
- PVAYLADEV-814: Removed XroadSizeBasedRollingPolicy.java which was previously used as a work-around for a logback bug.
- PVAYLADEV-1101: JRuby, Rubocop and Warbler updated
- PVAYLADEV-594: Removed obsolete 6.9x/6.7x compatibility fix
- PVAYLADEV-984: X-Road term and abbreviation explanations collected into one terminology document
- PVAYLADEV-1116: Update Vagrant development environment instructions
- XTE-385 / Backlog #199: Security Server: Fixed ACL removal after refreshing WSDLs.
- XTE-406 / Backlog #218: Common: Improved hardware module initialization with additional (multithreading) properties.
- XTE-411 / Backlog #220: Security Server: Actually TLSv1.1 is not supported on the client-side interfaces for incoming requests, documentation improved.

## 6.17.0 - 2018-02-14
- PVAYLADEV-861 / XRJD #172: Built a mechanism for configuration loader, that allows the loading of mutually alternative configuration files without unnecessary log errors. This mechanism is used to load explicit configuration values from either proxy, center or confproxy components on signer startup. Also refactored central server UI configuration loading to avoid unnecessary log errors. Update performs a migration for existing local configuration values if needed.
- PVAYLADEV-918: Fixed ansible playbook xroad_init.yml installation for remote RHEL machines.
- PVAYLADEV-799: Monitoring Akka-implementation is enhanced for handling possible restart of actors.
- PVAYLADEV-908 / XRJD #176: Added certificate activation status to enviromental monitoring
- PVAYLADEV-841: Added support for CentOS 7 LXD-containers to public X-Road installation Ansible playbooks
- PVAYLADEV-891: Updated documentation for environmental monitoring. ug-ss_x-road_6_security_server_user_guide.md
- PVAYLADEV-926: Removed automated testing environment Ansible setup from the public repository
- PVAYLADEV-740: Created Dockerfile for compiling xroad codebase and created jenkins pipeline which will use that for compiling, packaging and deploying X-Road versions.
- PVAYLADEV-878: Use case documentation changed from docx to md
- XTE-355 / Backlog #152: Security Server: Improved message exchange performance at a time when periodical timestamping is performed
- XTE-368: Added new security server metapackage xroad-securityserver-ee with default configuration for Estonian instances
- XTE-375: Security Server / Central Server: Enabled HttpOnly flag and set security flag to true for the session cookies
- XTE-376: Security Server: Fixed system resource leak of the monitoring component
- XTE-380: Security Server: Fixed audit logging of the restore process
- PVAYLADEV-809 / XRJD #190: The xroad package xroad-common has been split into four packages to allow removing unnecessary dependencies in the future. The package xroad-common still remains but is now a metapackage that depends on the new packages xroad-nginx, xroad-confclient, and xroad-signer which in turn depend on the new package xroad-base. X-Road packages that were dependant on xroad-common are, for now, still dependant on that package.
- PVAYLADEV-921: Ansible playbook support for selecting the security server variant (ee, fi, vanilla) to be installed, defaults to vanilla
- PVAYLADEV-883: Added feature to limit environmental monitoringdata result, via env-monitor parameter
- PVAYLADEV-962: Fixed path that is displayed to user in central server and security server backup
- PVAYLADEV-978 / XRJD #185: Fixed xroad-jetty high resource usage
- PVAYLADEV-947 / XRJD #179: Defined an documented a common way that should be used to transfer loosely defined security tokens (like JSON Web Tokens) as SOAP headers over X-Road.
- XTE-386 / Backlog #187: Security Server: OCSP log messages more verbal.
- XTE-391 / Backlog #196: X-Road Operations Monitoring Daemon: Use local SWA-Ref schema (swaref.xsd).
- PVAYLADEV-983 / XRJD #195: Documentation fixes
- PVAYLADEV-1040: Fix sonar blocker issue
- PVAYLADEV-1025: Documentation fixes
- PVAYLADEV-954: Security server: Optimize DB connection pool sizes
- PVAYLADEV-1042 / XRJD #194: X-Road monitor: Fix SystemCpuLoad metric calculation
- PVAYLADEV-1034: Security server: Fix access logs
- PVAYLADEV-1057: Documentation fixes
- PVAYLADEV-1039: Added support for ee-metapackage when installing ee-variant with Ansible
- PVAYLADEV-1026 / XRJD #195: Security server getWsdl metaservice's security improved and added parameter that can be used to switch off getWsdl (HTTP GET) metaservice.
- PVAYLADEV-1027: Updated and improved meta-service documentation
- XTE-405 / Backlog #205: Security Server: Fixed changing internal TLS certificate of Security Server using UI
- PVAYLADEV-1087: Fixed the documented WSDL-definition for security server meta-services
- PVAYLADEV-1033 / XRJD #58 / XRJD #25: Enhancements to security server internal load balancing
- PVAYLADEV-986: X-Road installation and update changed to require identical package version numbers in dependencies
- PVAYLADEV-1091: Upgraded some third party dependencies (for security fixes).
- PVAYLADEV-1029: Fix intermittent test failure

## 6.16.0 - 2017-09-13
- PVAYLADEV-848	Updated Gradle to version 4.1
- PVAYLADEV-815	Load Balancer documentation updated with Autologin setup and installing guide for slaves.
- PVAYLADEV-847 / XRJD #169	Fixed UI empty table double click event handling
- PVAYLADEV-367	Extend environmental monitoring to report optionally specified monitoring data.
- PVAYLADEV-438 / XRDJ #57 	For security reasons, security server metaservice no longer returns the network addresses of subsystem's services when retrieving the WSDL of a service. Instead it returns "http://example.org/xroad-endpoint".
- PVAYLADEV-822 / XRJD #162 	Environmental monitoring data now shows fewer certificate details, but for more certificates. SHA-1 hashes and validity periods (start and end date) are shown. The certificate data still contains the authentication and signing certificates and as a new addition, the internal TLS certificate for the security server as well as the client information system authentication certificates. The aim is to provide details about expiring certificates that would prevent message delivery but keep any private certificate details private.
- PVAYLADEV-860 / XRJD #168	The central server's environmental monitoring component is installed by default.
- PVAYLADEV-783 / XRJD #155 Fixed security server diagnostics view breaking if any of its status queries fails.
- PVAYLADEV-794 Packaging in development and release modes. The changelog is installed on target servers.
- XTE-349: Fixed some typos related with document PR-OPMON.
- XTE-335 / Backlog #134: Security Server, Op Monitoring Daemon : Updated Dropwizard to 3.2.2 and removed unnecessary bugfix.
- XTE-332, XTE-353 / Backlog #129: Security Server, Central Server, Configuration Proxy: Added support for PKCS#11 sign mechanism CKM_RSA_PKCS_PSS and made key creation template configurable.
- PVAYLADEV-780 / XRJD #146: Updated xroad-jetty to version 9.4.5. Now using jetty logging-logback module and updated logback (both library and jetty module version to 1.2.3). Removed XRoadSizeBasedRollingPolicy from the logback configurations. The policy class remains available (in case someone really wants to use it for awhile still), but will be removed entirely in a future release. Due to the log rolling policy change, the file name of archived files will lose the zip-creation time from the custom policy.
- PVAYLADEV-807: Added a maintenance mode to the health check interface to force health check to return HTTP status code 503. Mode can be enabled through proxy's admin port.
- PVAYLADEV-746: Added environment monitoring sensor for certificates which are associated with this security server.
- PVAYLADEV-717: Added license for IAIK PKCS Wrapper
- PVAYLADEV-800 / XRJD #71: Security server should preserve and propagate the SOAPAction header from the consumer to the provider.
- PVAYLADEV-838: Fix Ansible script does not build installation packages
- PVAYLADEV-804: Added instructions on performing an online rolling upgrade on a security server cluster. See the External Load Balancer documentation (doc id: IG-LXB).
- PVAYLADEV-834: Updated dependencies to latest stable versions to fix known vulnerabilities (jackson-databind, apache-mime4j-core, JRuby)
- PVAYLADEV-760 / XRJD #160: Modified connector SO-linger defaults to -1. Additionally moved RHEL-proxy configuration to file override-rhel-proxy.ini and reduced the file to only contain differences to debian-proxy configuration file proxy.ini, that is now also included in RHEL packaging as a baseline configuration. Also modified SystemPropertiesLoader to load glob-defined ini-files in alphabetic order based on the filename.
- PVAYLADEV-798 / XRJD #170: Fixed the horizontal centering of central server UI tab bars as well as the vertical positioning of the advanced search window tab bar.
- PVAYLADEV-818: Updated the frontpage of X-Road Github repository (README.md file) to contain more information about the X-Road development.
- PVAYLADEV-816: Add missing load balancing installation document image source files
- PVAYLADEV-772: The X-Road restore backup script names the services that need to be restarted so that the service list is always correct and the services are restarted in correct order.
- PVAYLADEV-797 / XRJD #156: Federation has been disabled by default on the security server level. Federation can be enabled with the system parameter allowed-federations for the configuration-client server component. More information can be found in the Security Server User Guide (Doc. ID: UG-SS) and the System Parameters User Guide (Doc. ID: UG-SYSPAR)
- PVAYLADEV-868:Added link from ig-xlb_x-road_external_load_balancer_installation_guide.md to /doc/README.md
- PVAYLADEV-856: Exclude audit.log from automatic log cleanup on reboot
- XTE-248 / Backlog #55: Security Server: Fixed creation of signed documents (backward compatible) to follow e-signature standards (XAdES, ASiC).
- XTE-330 / Backlog #127: Security Server: Added support for "NEE" member class in certificates provided by SK ID Solutions AS.
- XTE-357 / Backlog #164: Security Server: Fixed temporary files removal in error situations.
- PVAYLADEV-933: Fixed build failure on clean machine
- PVAYLADEV-934: Fixed problem in wsdlvalidator install paths

## 6.15.0 - 2017-05-12
- PVAYLADEV-730 / XRJD #147 Packaged wsdlvalidator and included it in the RHEL distribution.
- PVAYLADEV-621 / XRJD #148 Fix environmental monitoring does not return correct value for open file handles.
- PVAYLADEV-738 / XRJD #139 Fix concurrency issue in AdminPort synchronous request handling
- PVAYLADEV-743 Modified cluster configuration to allow only read-only users on slave-nodes
- PVAYLADEV-724 Make it possible to disable configuration synchronization on a load balancing cluster slave

## 6.14.0 - 2017-04-13
- XTE-334 / Joint development issue #135: Security Server bugfix: Fixed not correctly functioning timeouts.
- XTE-337 / Joint development issue #140: Remove X-Road version 5 migration support. **Warning:** Central Server database schema changed, old Central Server backups are not usable.
- XTE-341 / Joint development issue #142: Security Server: Enable detect connections that have become stale (half-closed) while kept inactive in the connection pool.

## 6.13.0 - 2017-04-11
- PVAYLADEV-695: Increase akka remoting maximum message size to 256KiB and enable logging of akka error events.
- PVAYLADEV-729: During a configuration restore from backup, the access rights of the directory /var/lib/xroad for other users will no longer be removed.
- PVAYLADEV-726: Fixed an issue where diagnostics view in security server UI was not able to show CA-information that contained special characters.
- PVAYLADEV-722: Added support for external load balancing.
- PVAYLADEV-714: Added documentation for SecurityServer protocol extension.
- PVAYLADEV-709: Added a read-only user role for security server user interface.
- PVAYLADEV-707: SOAP Faults wrapped in a multipart message will now be passed to the client. Previously, the security server replaced the fault with a generalized error message.
- PVAYLADEV-615: Fix for never ending messagelog archiving. Doing it now in smaller transactions.
- PVAYLADEV-704: Added Jenkinsfile to support building Github pull requests in Jenkins.

## 6.12.0 - 2017-03-13
- XTE-99 / Joint development issue #79: Security Server UI: Added uniqueness check of the entered security server code when initializing the server.
- XTE-252 / Joint development issue #53: Security Server: Upgraded embedded Jetty to the version 9.4.2. Due to upgrade SHA1 ciphers are no longer supported for communication between security server and client.
- XTE-293: Security Server: A field set used to generate the token ID of the SSCD has been made configurable.
- XTE-294 / Joint development issue #84: Security Server: Added configuration file for the OCSP responder Jetty server (and increased max threads size of the thread pool).
- XTE-307 / Joint development issue #131: Security Server bugfix: Added missing HTTP header "Connection: close" into the server proxy response in cases error occurs before parsing a service provider's response.
- XTE-308 / Joint development issue #132: Security Server bugfix: Added missing read timeout for OCSP responder client.
- XTE-310 / Joint development issue #125: Security Server bugfix: SOAP messages with attachments caused in some cases a temopray file handle leak.
- XTE-333 / Joint development issue #128: Security Server bugfix: Fixed parsing SOAP messages containing &amp; or &lt; entities.
- Security Server: TCP socket SO_LINGER values in the proxy configuration file (proxy.ini) set to -1 according to avoid unexpected data stream closures.

## 6.11.0 - 2017-03-01
- PVAYLADEV-609 / PVAYLADEV-703 / Joint development issue #120: Added a partial index to the messagelog database to speed up retrieval of messages requiring timestamping. This should increase proxy performance in cases where the logrecord table is large.
- PVAYLADEV-685 / Joint development issue #121: Added a system property to deactivate signer's periodic OCSP-response retrieval on both central server and configuration proxy.

## 6.10.0 - 2017-02-15
- PVAYLADEV-684: Change source code directory structure so that doc folder moves to root level and xtee6 folder is renamed to src. Checkstyle configuration moves to src from doc.
- PVAYLADEV-670: The document DM-CS central server data model was converted to markdown format and the included ER diagram was done with draw.io tool.
- PVAYLADEV-253: Serverproxy ensures that client certificate belongs to registered security server before reading the SOAP message.
- PVAYLADEV-369: Environmental monitoring port configuration system property group monitor has been renamed to env-monitor. If the system property monitor.port was previously configured, it has to be done again using env-monitor.port. Monitor sensor intervals are now also configurable as system properties.
- PVAYLADEV-657: Added version history table and license text to markdown documentation.
- PVAYLADEV-661: Packaging the software is done in Docker container. This includes both deb and rpm packaging.
- PVAYLADEV-675: Fixed problem in central server and security server user interface's file upload component. The problem caused the component not to clear properly on close.
- PVAYLADEV-680: Fixed problem in Debian changelog that caused warnings on packaging.
- PVAYLADEV-682: Added Ansible scripts to create test automation environment.
- PVAYLADEV-547: Added Vagrantfile for creating X-Road development boxes. It is possible to run X-Road servers in LXD containers inside the development box.

## 6.9.5 - 2017-03-27
- XTE-293: Security Server: A field set used to generate the token ID of the SSCD has been made configurable.
- XTE-333 / Joint development issue #128: Security Server bugfix: Fixed parsing SOAP messages containing &amp; or &lt; entities.

## 6.9.4 - 2017-02-13
- XTE-301: Security Server UI bugfix: race condition of the adding a new client caused duplicates
- XTE-319: Security Server UI bugfix: WSDL deletion caused incorrect ACL removal
- XTE-322: Security Server bugfix: a typo in the configuration file proxy.ini (client-timeout)

## 6.9.3 - 2017-02-10
- PVAYLADEV-691: Hotfix for ExecListingSensor init. (Fixes package listing information, etc)

## 6.9.2 - 2017-01-23
- PVAYLADEV-662: Fixed proxy memory parameters
- PVAYLADEV-662: Altered OCSP fetch interval default value from 3600 to 1200 seconds
- PVAYLADEV-662: Converted DM-ML document to markdown format
- PVAYLADEV-662: Fixed bug in handling HW token's PIN code
- PVAYLADEV-662: Fixed bug in prepare_buildhost.sh script that caused build to fail on a clean machine
- PVAYLADEV-656: Added a reading timeout of 60 seconds for OcspClient connections
- PVAYLADEV-666: Fixed bug that caused metaservices and environmental monitoring replies to be returned in multipart format

## 6.9.1 - 2017-01-13
- Updated documents: ARC-OPMOND, UG-OPMONSYSPAR, UG-SS, PR-OPMON, PR-OPMONJMX, TEST_OPMON, TEST_OPMONSTRAT, UC-OPMON
- Updated example Zabbix related scripts and configuration files

## 6.9.0 - 2017-01-06

- PVAYLADEV-505: Fixed a bug in Security Server's UI that was causing the text in the pop-window to be messed with HTML code in some cases when "Remove selected" button was clicked.
- PVAYLADEV-484: Changed the value of Finnish setting for time-stamping of messages (acceptable-timestamp-failure-period parameter). Value was increased from value 1800s to value 18000s. By this change faults related to time-stamping functionality will be decreased.
- PVAYLADEV-475 / Joint Development issue #70: Security Server's connection pool functionality improved so that existing and already opened connections will re-used more effectively. Old implementation was always opening a new connection when a new message was sent. This new functionality will boost performance of Security Server several percents. Global settings use the old functionality by default but the Finnish installation packages override the global setting, opting to use the improvements. See the system parameters documentation UG-SYSPAR for more details.  
- PVAYLADEV-523: An Ansible script for installing the test-CA was created and published in the Github repository. The script will execute the installation automatically without any manual actions needed.
- PVAYLADEV-536: Improved OCSP diagnostics and logging when handling a certificate issued by an intermediate (non-root) certificate authority: The certificates are now listed in OCSP diagnostics and a false positive error message is no longer logged.
- PVAYLADEV-467 / PVAYLADEV-468 / PVAYLADEV-469 / PVAYLADEV-553 / Joint Development issue #81: Several improvements and checks to make sure there are no security threats related to Security Server's user rights and system generated files:
  - /var/tmp/xroad and /var/log/xroad folders checked and user rights validated
  - /var/lib/xroad folder user rights restricted
  - /var/log/secure and /var/log/up2date folders checked through and validated if files generated here are necessary
  - /var/log/messages folder checked through and validated if files generated here are necessary. Fixed functionality so that xroad-confclient is not writing log files to this folder if it is running as a service.
  - N.B! if there are monitoring actions and processed related to xroad-confclient, that are using log files of this folder, the configuration of monitoring must be changed so that the source of logs is from now on /var/log/xroad folder.
- PVAYLADEV-556: All installed additional parts of Central Server are seen on UI of Central Server. Earlier some parts that where installed could not be seen on UI.
- PVAYLADEV-531: Fixed the bug in functionality of "Unregister" dialog window in security server's "Keys and Certificates" -view so that no nonsensical error messages are shown to user. Erroneous notification was shown if user had created an authentication certificate and then made a request to register it and immediately canceled the request before it was accepted. This caused an unexpected error text from the Keys -table to be translated and the subsequent message to be shown to the user. The underlying error was a fixed removing any unnecessary error messages.
- PVAYLADEV-560 / Joint Development issue #65: Improved the handling of OCSP responses at startup phase of Security Server. If at startup the global configuration is expired then the next OCSP validation is scheduled within one minute. In earlier versions this was scheduled within one hour and caused extra delay until OCSP status was 'good'. Also, error message 'Server has no valid authentication' was generated.
- PVAYLADEV-489 / PVAYLADEV-571 / Joint Development issue #69: From version 6.9.0 Security Server is supporting new XML schema that makes possible to use a set of different Global Configuration versions. This makes possible that Global Configuration can be updated without breaking the compatibility to the Security Servers that are still using the older version of Global Configuration. Each Security Server knows the correct Global Configuration version it is using and based on this information is able to request that version from the Central Server. Central Server in turn is able to distribute all the Global Configurations that might be in use.
- PVAYLADEV-570 / Joint Development issue #69: From version 6.9.0 Configuration Proxy supports a new XML schema that makes it possible to use a set of different Global Configuration versions. Configuration Proxy can download, cache and distribute all the Global Configurations that might be in use.
- PVAYLADEV-588 / Joint Development issue #64: Fixed a bug that caused Security Server to start doing duplicate OCSP fetches at the same time. This happened if two or more OCSP related parameter values were changed (almost) at the same time.
- PVAYLADEV-457: Improved the INFO level logging information of Signer module so that more information will be written to log. It makes easier to see afterward what Signer has been doing.
- PVAYLADEV-607 / Joint Development issue #69: Global Configuration version that is generated and distributed by default can be set both in Central Server and Configuration Proxy (using a parameter value).
- PVAYLADEV-616: Fixed a bug in environment monitoring causing file handles not to be closed properly.
- PVAYLADEV-618 / Joint Development issue #89: Partial index is created to messagelog database so that non-archived messages will be fetched faster. This change will make the archiver process much faster.
- PVAYLADEV-634 / Joint Development issue #96: Fixed a bug in 'Generate certificate request' dialog. A refactored method was not updated to certificate request generation from System Parameters -view which caused the certificate generation to fail with the error message "Undefined method".

## 6.8.11 - 2016-12-20
- Added documents: PR-OPMON, PR-OPMONJMX, ARC-OPMOND, UG-OPMONSYSPAR
- Updated documents: ARC-G, ARC-SS, UC-OPMON, UG-SS, TEST_OPMON, TEST_OPMONSTRAT

## 6.8.10 - 2016-12-12
- Operational data monitoring improvements and bug fixes

## 6.8.9 - 2016-12-05
- Operational data monitoring

## 6.8.8 - 2016-12-01
- CDATA parsing fix (XTE-262)
- Converting PR-MESS to Markdown

## 6.8.7 - 2016-10-21
- DOM parser replacement with SAX parser (XTE-262)

## 6.8.6 - 2016-09-30
- Fixed: security server processes MIME message incorrectly if there is a "\t" symbol before boundary parameter (XTE-265)
- Documentation update: apt-get upgrade does not upgrade security server from 6.8.3 to 6.8.5 (XTE-278)
- Added xroad-securityserver conflicts uxp-addon-monitoring <= 6.4.0

## 6.7.13 - 2016-09-20
 - PVAYLADEV-485: Fixed a bug in the message archiver functionality that caused very long filenames or filenames including XML to crash the archiver process.
 - PVAYLADEV-398: Security Server backup/restore functionality fixed to work also when the existing backup is restored to a clean environment (new installation of a Security Server to a new environment).
 - PVAYLADEV-238: OCSP dianostic UI now shows the connection status. Color codes:
   - Green: Ok
   - Yellow: Unknown status
   - Red: Connection cannot be established to OCSP service or OCSP response could not be interpreted or no response from OCSP service
 - PVAYLADEV-360: Namespace prefixes other than 'SOAP-ENV' for SOAP fault messages are now supported. Previously other prefixes caused an error.
 - PVAYLADEV-424: Improved the messagelog archiver functionality so that it cannot consume an unlimited amount of memory even if there would be tens of thousands of messages to archive.
 - PVAYLADEV-304: Fixed a situation that caused time-stamping to get stuck if some (rare) preconditions were realized.
 - PVAYLADEV-351: Performance of Security Server improved by optimizing database queries at the time when Security Server's configuration data is fetched.
 - PVAYLADEV-454: Adjusted the amount of metaspace memory available for xroad processes so that the signer service does not run out of memory.
 - PVAYLADEV-416: Added Security Server support for the old Finnish certificate profile used in the FI-DEV environment.
 - PVAYLADEV-459 / PVAYLADEV-352 / PVAYLADEV-460 / PVAYLADEV-461: Several improvements to OCSP protocol related functionalities:
    - After a failure to fetch an OCSP respose, fetch retries are now scheduled based on the Fibonacci Sequence. The first retry is done after 10 seconds, then after 20 seconds, then after 30 seconds, 50 seconds, 80 seconds, 130 seconds etc. until a successful OCSP response is fetched.
    - Validation of OCSP response is done only once per message and the validation result is then stored to cache. If result is needed later, cache will be used for checking the result. This change will make the checking faster.
    - OCSP responses are fetched by the time interval defined in Global Configuration so that the Security Server is able to operate longer in a case on OCSP service is down.
    - OCSP response is no longer fetched immediately if ocspFreshnessSeconds or nextUpdate parameter values are changed. This allows the Security Server to operate longer in case the OCSP service is down and the parameters are changed to avoid calling the unreachable OCSP service.
 - PVAYLADEV-353: Added system parameters to stop the security servers from leaking TCP connections between them. These settings maintain status quo by default so connections are leaked without configuration changes. The prevention is overridden to be enabled in the Finnish installation package. The added configuration options are described in the UG-SYSPAR document. In short, configuring the [proxy] on the server side with server-connector-max-idle-time=120000 will close idle connections after 120 seconds.
 - PVAYLADEV-455 / PVAYLADEV-458: Updated several Security Server dependency packages to the latest version so that possible vulnerabilities are fixed.
 - PVAYLADEV-400: Security Server UI: when adding a new subsystem only subsystems that are owned by the organization are visible by default. Previously all the subsystems were visible (all the registered subsystems).
 - PVAYLADEV-464: Security Server UI: implemented 'Certificate Profile Info' feature for Finnish certificates which allows the UI to automatically fill most of the necessary fields when creating a new certificate request (either sign or auth certificate request).
 - PVAYLADEV-476: Improved the performance of Security Server by optimizing the caching of global configuration.
 - Bug fixes and minor enhancements

## 6.8.5 - 2016-08-16
- Bugfix: it's not possible to send duplicate client registration requests (https://github.com/vrk-kpa/xroad-joint-development/issues/48)
- Bugfix: added one missing translation
- Updated configuration client test data
- Some minor corrections

## 6.8.4 - 2016-05-11
- Merged with XTEE6 repo

## 6.7.12 - 2016-04-25
- Fixed security server not starting if xroad-addon-messagelog is not installed
- Added connection timeouts to configuration client to prevent hanging problems
- In security server's keys and certificates view delete button now removes both key and certificates
- Signer reacts to ocspFreshnessSeconds parameter change immediately
- OCSP nextUpdate verification can be switched off with optional global configuration part
- Fixed bug in xroad-create-cluster.sh script that created the certificates with 30 days expiry period
- Fix software token batch signing setting

## 6.7.11 - 2016-02-08
- Minor documentation changes

## 6.7.10 - 2016-01-15
- Change configuration client admin port default to 5675 and make it configurable
- Security server message exchange performance optimizations
- Security server environmental monitoring

## 6.7.9 - 2016-01-15
- Fix timestamper connection checking
- Fix timestamper status when batch timestamping is used
- Timestamping diagnostics displays status for all defined timestamping services

## 6.7.8 - 2016-01-07
- Security server offers diagnostics information for time stamping service
- Fixed configuration restore (RHEL)
- Fixed database backup to support generated passwords

## 6.7.7 - 2015-12-09
- Fixed critical errors found by SonarQube
- Added option for requiring strong password for PIN-code
- Security server offers diagnostics information for global configuration fetching
- Taken to use HTML-attribute data-name to improve testability

## 6.7.6 - 2015-11-27
- Fixed changing of security server configuration anchor

## 6.7.5 - 2015-11-26
- Updated member code/identifier extractor for Finnish instance
- Fixed XSS vulnerabilities in central server and security server user interfaces
- RHEL installation does not redirect clientproxy ports automatically
- Security server's internal TLS certificate can be replaced from the UI

## 6.7.4 - 2015-11-12
- Add MIT license header to security server source code
- Add LICENSE.info file to security server source code and binaries
- Add LICENSE.info file to central server source code and binaries
- Add LICENSE.info file to configuration proxy source code and binaries
- Add LICENSE file containing MIT license to security server source code and binaries
- Fixed 'Global configuration expired' error occurring under heavy load
- The password for messagelog and serverconf databases is generated during installation
- Remove hard-coded iface from Red Hat security server port redirection and make it configurable

## 6.7.3 - 2015-10-26
- Add license information
- Refactor proxy setup scripts (RHEL)

## 6.7.2 - 2015-10-19
- Fix nginx configuration (remove X-Frame-Options)

## 6.7.1 - 2015-10-14
- Finnish settings set SHA256withRSA as default signature algorithm
- Finnish settings set SHA256withRSA as default CSR signature algorithm
- Configurable message body logging
- Perfect forward secrecy management services
- Security server user interface uses TLSv1.2
- Central server user interface uses TLSv1.2
- Security server communicates with backend services using TLSv1.2
- Perfect forward secrecy for security server user interface
- Perfect forward secrecy for central server user interface
- Perfect forward secrecy for security server communications with backend services
- Fixed SonarQube static analysis blocker issues
- Management services use TLSv1.2

## 6.7.0 - 2015-09-29
- Partial support for RHEL 7
  - Security server can be installed on RHEL 7
  - rpm packages for security server
    - xroad-securityserver-fi (meta-package for Finnish instances), xroad-securityserver, xroad-proxy, xroad-common, xroad-jetty9, xroad-addon-messagelog, xroad-addon-metaservices
    - Note. optional package xroad-addon-hwtokens is not included in this release
- Member Code/Identifier Extractor for Finnish instance (PVAYLADEV-94)
  - Member Code/Identifier Extractor Method: ee.ria.xroad.common.util.FISubjectClientIdDecoder.getSubjectClientId
  - Signing certificate subject DN format supported by the decoder: C=FI,O=<instanceIdentifier>, OU=<memberClass>, CN=<memberCode> (e.g. C=FI, O=FI-DEV, OU=PUB, CN=1234567-8)
- Configurable key size for signing and authentication RSA keys (PVAYLADEV-28)
  - New configuration parameter signer.key-length (default 2048)
- Configurable certificate signing request signing algorithm (PVAYLADEV-29)
  - New configuration parameter signer.csr-signature-algorithm (default: SHA1withRSA)
- New security server metapackage with default configuration for Finnish instance
  - xroad-securityserver-fi
  - uses SHA256withRSA as signer.csr-signature-algorithm
- Fixed atomic save to work between separate file systems (PVAYLADEV-125)
  - OS temp directory and X-Road software can now reside on different file systems

---
XRDDEV references: see https://jira.niis.org/projects/XRDDEV/
(Backlog/XRJD references: see https://github.com/vrk-kpa/xroad-joint-development/issues)
