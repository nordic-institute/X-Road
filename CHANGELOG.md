# Change Log

## 7.2.1 - 2023-02-10
- XRDDEV-2210: As a Security Server Administrator I want that the Security Server uses the ocspFreshnessSeconds value of the X-Road instance where it has been registered so that the configuration of federated instances doesn't affect my Security Server's operations.
- XRDDEV-2230: Gpg is not marked as a dependency so initial configuration might fail.
- XRDDEV-2235: As a Developer I want to check vulnerable dependencies and fix them for the 7.1.2 and 7.2.1 releases so that we don't have potential security issues in the software.

## 7.2.0 - 2022-11-10
- XRDDEV-2167: As an X-Road user I would like the default maximum memory for the X-Road message log addon to be increased to 200m so that it wouldn't cause failures on higher traffic servers
- XRDDEV-2161: Automatic backup generation does not work in the Sidecar
- XRDDEV-2160: As a Developer I want to deprecate the SkEsteIdCertificateProfileInfoProvider class since it is no longer user according to our knowledge
- XRDDEV-2157: As a Security Server sidecar user I would like to be able to use a version without a local postgres installed so that I don't have it installed if I use a remote database
- XRDDEV-2133: Secondary SS node shows "Globlal configuration is expired" alert although everything seems to be fine with globalconf
- XRDDEV-2132: As the Estonian X-Road operator I would like the default key length in my instance to be increased to 3072 so that security is increased
- XRDDEV-2131: Secondary node keys/certificates not updated without manual xroad-signer restart when used with external HSM
- XRDDEV-2117: File "files" is missing from global configuration on the Security Server
- XRDDEV-2116: Read-only state on secondary Security Server nodes disables ability to log into the token
- XRDDEV-2114: As a Security Server Sidecar user I want to have an improved migration experience so that I don't need to troubleshoot the installation using the codebase
- XRDDEV-2113: Proxy does not re-establish connection to remote database
- XRDDEV-2112: Deleting a version of the service also deletes ACL rules of other versions of the service
- XRDDEV-2111: As a Security Server administrator I want that instructions for Security Server Clustering are updated for Ubuntu 22.04
- XRDDEV-2109: As a Security Server administrator I want that instructions for Security Server version upgrade from Ubuntu 20.04 to Ubuntu 22.04 are documented so that I know how to complete the update
- XRDDEV-2106: As a Product Owner I want that the test environments contain Ubuntu 22 X-Road servers so they are up to date
- XRDDEV-2105: As a Developer I want that development environment (DEV) has an Ubuntu 22.04 Security Server so that changes to the software can be tested on Ubuntu 22
- XRDDEV-2104: As a Product Owner I want that Ubuntu 22 clustering testing environment is set up so that I know that clustering works on Ubuntu 22
- XRDDEV-2103: As a Product Owner I want that the test pipelines are expanded so that also Ubuntu 22 can be tested with them
- XRDDEV-2102: As an X-Road operator I want that Configuration Proxy installation packages are available for Ubuntu 22.04 LTS so that I can use the latest LTS version of Ubuntu OS
- XRDDEV-2101: As an X-Road operator I want that Central Server installation packages are available for Ubuntu 22.04 LTS so that I can use the latest LTS version of Ubuntu OS
- XRDDEV-2100: As an X-Road user I want that Security Server installation packages are available for Ubuntu 22.04 LTS so that I can use the latest LTS version of Ubuntu OS
- XRDDEV-2098: As a Developer I want that Ubuntu 18.04 LTS packaging is removed from all components because Ubuntu 18 is not supported anymore
- XRDDEV-2097: Uploading a new configuration anchor fails on the Security Server
- XRDDEV-2073: As a contributor I would like PR#1079 to be reviewed and merged so that my contribution would be accepted
- XRDDEV-2072: As a contributor I would like PR#702 to be reviewed and merged so that my contribution would be accepted
- XRDDEV-2042: As an X-Road user I want the representation party header extension for SOAP messages to be managed by NIIS so that all the information is in a central location
- XRDDEV-2041: As a Security Server Administrator I want that the opmonitor add-on stores the Represented Party header when it's used with the REST protocol.
- XRDDEV-2039: As a Developer I want to have ARM64 based servers in our testing environments so that we can verify the packages work correctly
- XRDDEV-2038: As a Developer I want to update our release pipelines so that we are also able to release ARM64 versions of our debian packages
- XRDDEV-2022: As an X-Road user I would like the content-length HTTP header to be calculated based on the body on the recipient side so that I can know the original size
- XRDDEV-1561: As a Security Server Administrator I want that the Security Server health check interface supports HSMs so that I know if a sign key stored in an HSM is available

## 7.1.1 - 2022-08-18
- XRDDEV-2095: Operational monitoring database migrations are not executed on RHEL8 during installation
- XRDDEV-2115: Update dependencies with known vulnerabilities

## 7.1.0 - 2022-05-26
- XRDDEV-1788: Show a warning if the deprecated "/etc/xroad/services/local.conf" configuration file exists when generating a backup on the Security Server. The warning is shown in the Security Server UI and on the command line.
- XRDDEV-1800: Add primary and secondary node information to the Security Server UI for clustered HA setups. Display global alert for read-only state on the secondary node UI.
- XRDDEV-1824: Clean up Security Server UI styles and remove unused ones.
- XRDDEV-1825: Clean up Security Server UI localisation files and improve localisation string resolution methods for better consistency.
- XRDDEV-1827: Set the Security Server UI into read-only mode on secondary node instances in a clustered HA setup.
- XRDDEV-1840: Update Security Server UI components to handle empty states in a more consistent and clear manner.
- XRDDEV-1852: Update Security Server UI table components to handle loading states in a more consistent and clear manner.
- XRDDEV-1853: Update Security Server UI views to handle loading states in a more consistent and clear manner.
- XRDDEV-1875: Update Spring Boot to a later version to fix a false positive vulnerability warning in the Security Server API UI component.
- XRDDEV-1876: Fix Security Server message log archiving to resolve edge case where messages might be timestamped repeatedly, causing the message log to grow.
- XRDDEV-1890: Fix Security Server UI local storage handling to resolve issues when the user does not close the browser between multiple initialisation attempts for the same server.
- XRDDEV-1893: Migrate Security Server UI from Vuex to Pinia for better type checking as well as future compatibility when upgrading to Vue 3.
- XRDDEV-1907: Improve Security Server configuration client to handle expired federated configurations and local instance configuration separately. Improve caching behaviour to consider configuration expiration. 
- XRDDEV-1961: Make the Security Server UI more modular and disable sections that are not needed. After the change, timestamping and message log related sections in the UI are disabled when the message log add-on is not installed or enabled.
- XRDDEV-1964: Add new endpoints to the Security Server's management REST API that provide information about backup encryption, message log archive encryption, message log archive grouping and message log database encryption.
- XRDDEV-1966: Add sections to the Security Server UI diagnostics page to display information about backup encryption, message log archive encryption, message log archive grouping and message log database encryption.
- XRDDEV-1986: Increase the default maximum body size for REST messages from 10mb to 20mb for the Security Server in the Estonian metapackage.
- XRDDEV-1994: Fix issue with the configuration proxy where temporary files were not getting removed if the global configuration generation failed during the process. The issue caused temporary files to accumulate in the "/var/tmp/xroad/{INSTANCE_IDENTIFIER}" directory and eat up disk space.
- XRDDEV-2043: Fix database migrations fail during installation in RHEL8.

## 7.0.3 - 2022-04-25
- XRDDEV-1973: Update dependencies with known vulnerabilities

## 7.0.2 - 2022-02-11
- XRDDEV-1920: Restoring a backup fails for Security Servers that have not been freshly installed since 6.24.0
- XRDDEV-1921: Upgrading X-Road on RHEL does not create symlink for messagelog.conf
- XRDDEV-1927: local.properties not enforcing Java memory properties in messagelog-archiver

## 7.0.1 - 2022-01-10
- XRDDEV-1889: Update dependencies with known vulnerabilities

## 7.0.0 - 2021-11-26
- XRDDEV-1375: HSM token certificates do not show Deleted status
- XRDDEV-1461: Permissions handling has inconsistencies when it comes to non-sign-non-auth keys
- XRDDEV-1466: User interface reports different version than package manager
- XRDDEV-1468: "Please enter soft token PIN" is illogical on some user roles
- XRDDEV-1469: Tokens and Certs free text filtering does not consider key id
- XRDDEV-1470: Buttons hidden in the add service dialog when managing access rights for a producer with a lot of services
- XRDDEV-1471: Lighthouse analysis shows some possible improvements
- XRDDEV-1477: Add member usability issues
- XRDDEV-1551: CertificateDetails view loses usage param when refreshed
- XRDDEV-1553: NPE in AccessRightService.accessRightTypeToServiceClientDto()
- XRDDEV-1559: Adding a client to a member with a bad cert ocsp response shows the Register checkbox in the final step of the wizard
- XRDDEV-1566: Create client wizard does not always take into account cert status and OCSP status - for example offers creation of key & CSR if member has a signer cert with OCSP status REVOKED
- XRDDEV-1584: As a Developer I want to fix the General conflicts in the new Security Server frontend implementation so that it matches with the Styleguide
- XRDDEV-1585: As a Developer I want to fix the Table conflicts in the new Security Server frontend implementation so that it matches with the Styleguide
- XRDDEV-1586: As a Developer I want to fix the "Add client wizard" conflicts in the new Security Server frontend implementation so that it matches with the Styleguide
- XRDDEV-1590: System settings view is broken when management subsystem member is deleted
- XRDDEV-1610: Timestamping services have Next update field in the Security Server Diagnostics view
- XRDDEV-1614: User can't import configuration anchor in security server initialisation
- XRDDEV-1640: xroad-jetty9 still depends on openjdk-8-jre-headless instead of the more generic dependency in the other modules
- XRDDEV-1667: Security server API certificate cannot be verified
- XRDDEV-1669: X-Road properties no longer overridable
- XRDDEV-1676: Importing invalid certificate chain as the new Internal Security Server TLS Keys' certificate succeeds
- XRDDEV-1765: OCSP responders that are removed from global configuration are not automatically removed from the Security Server diagnostics view.
- XRDDEV-1791: Security server presents a warning when importing WSDL
- XRDDEV-333: As a Security Server Administrator I want that changes in the TSA URL are automatically updated and taken into use by Security Server so that I don't have to re-add the TSA manually.
- XRDDEV-427: As a Security Server admin I want that Security Server logs x-forwarded-for HTTP headers so that I know the original IP address of a client information system.
- XRDDEV-541: As a Security Server Administrator I want that the diagnostics view shows the response status (success/failure) of the latest OCSP requests so that I can see the state of OCSP on the Security Server UI.
- XRDDEV-542: As a Security Server Administrator I want that the diagnostics view shows the response status (success/failure) of the latest TSA request so that I can see the state of TSA on the Security Server UI.
- XRDDEV-964: As a Security Server Administrator I want the serverconf cache (at least Internal TLS Key) to be invalidated after the configuration changes
- XRDDEV-1092: As a Server Administrator I want to be able to install the X-Road Security Server or Central Server without a local postgres
- XRDDEV-1127: As an Architect I want to build X-Road on JDK 11 so that newer language features can be utilized
- XRDDEV-1184: As a Security Server Administrator I want to be able to change the soft token PIN so that I can rotate the PIN code
- XRDDEV-1225: As a Security Server Administrator I want to be able to change the soft token PIN so that I can rotate the PIN code
- XRDDEV-1226: As a Security Server Administrator I want to be able to change the soft token PIN so that I can rotate the PIN code
- XRDDEV-1282: As a Security Server Administrator I want the installation scripts to be improved so that installing with a remote database is easier
- XRDDEV-1295: Clarify ServiceFailed.SslAuthenticationFailed error message in two situations
- XRDDEV-1351: As a Developer I want that the Router and tabs use unified permission data so that there is single source of truth
- XRDDEV-1397: As a Frontend developer I want to copy the shared Security Server UI components to the new UI library so that sharing the components with other applications is easier
- XRDDEV-1398: As a Frontend developer I want to apply X-Road 7 style guide to the UI library so that all the shared components are aligned with the style guide
- XRDDEV-1450: As a Frontend Developer I want to update the general look and feel of the Security Server UI so that it matches the new style guide
- XRDDEV-1488: As a Software Developer I want to update the Security Server login page so it matches the new style guide
- XRDDEV-1489: As a Software Developer I want to update the Security Server clients table view so that it matches the new style guide
- XRDDEV-1490: As a Software Developer I want to update the Security Server client wizards so that they match the new style guide
- XRDDEV-1491: As a Software Developer I want to update the Security Server client and subsystem subviews so that they match the new style guide
- XRDDEV-1492: As a Software Developer I want to update the Security Server dialogs and wizards under the client and subsystem subview so that they match the new style guide
- XRDDEV-1493: As a Software Developer I want to update the Security Server diagnostics and settings views so that they match the new style guide
- XRDDEV-1494: As a Software Developer I want to update the Security Server keys and certificates view so that it matches the new style guide
- XRDDEV-1496: As a Security Manager I want to study alternatives for encrypting message payloads in the messagelog database so that the implementation is more secure.
- XRDDEV-1497: As a Security Manager I want to study alternatives for encrypting messagelog archive files so that the implementation is more secure.
- XRDDEV-1506: As an Architect I want to analyse how X-Road could support EC keys and ECDSA certificates so that we can improve the performance
- XRDDEV-1507: As a Product Owner I want to analyse how we could prevent users from updating their X-Road software form an unsupported version so that we would have less issues related to upgrades
- XRDDEV-1525: As a Security Server Administrator I want to get better visual feedback about key and certificate statuses in the keys and certificates tables in the Security Server UI so that I have a better overview of the status
- XRDDEV-1544: As a Developer I want update the way messagelog message records are updated so that potential conflicts when multiple nodes are updating the records are avoided
- XRDDEV-1549: As a Developer I want to have a consistent approach for handling control characters in non-identifier property values
- XRDDEV-1552: As a Developer I want to have defined strategy for page refreshes and router parameters
- XRDDEV-1569: As a Security Server Administrator I want to be able to see the information about the JAVA version in use in the diagnostics view so that I can see if there are problems
- XRDDEV-1581: As a Security Server Administrator I would like to be able to access the API description file used in the software so that I can be sure I am using the right one
- XRDDEV-1583: As a Developer I want that shared-ui components are named uniformly so that it's easy to recognize them
- XRDDEV-1591: As a Server Administrator I want there to be documentation available on how to install the Security Server and Central Servere without a local postgres
- XRDDEV-1597: As a Security Server Administrator I want to be able to use a placeholder value for the service URL in my OpenAPI3 file that would automatically be replaced by the software so that I don't leak the services internal address
- XRDDEV-1604: As a Security Server Administrator I want the processes to run on JAVA 11 by default so that we can upgrade our runtime
- XRDDEV-1605: As a Security Expert I want to implement encryption on the backup/restore mechanism for the Security Server so that it wouldn't be vulnerable to tampering
- XRDDEV-1608: As a Security Manager I want to study alternatives for better message log multi-tenancy support so that archive files of different members could be separated.
- XRDDEV-1611: As a Security Expert I want to implement verification in the backup/restore mechanism for the Security Server so that it wouldn't be vulnerable to tampering
- XRDDEV-1612: As a Security Expert I want to update the backup/restore mechanism for the Security Server so that potential remote code execution vulnerabilities would be solved
- XRDDEV-1626: As an X-Road user I would like to have a generic certificate profile that is compliant with the x.509 standard so that those types of certificates could be used
- XRDDEV-1629: As a Security Expert I want to fix a XSS vulnerability in the Central Server UI so that potential attack vectors are prevented
- XRDDEV-1634: As a Developer I want to check the max-width of all the Security Server subviews to make sure they are according to the style guide
- XRDDEV-1639: As a Security Server Administrator I want to have a better layout for the keys and certificates view so that I can better navigate the information
- XRDDEV-1641: As a Security Expert I want to design a way to enforce strength rules for the keys we use in backup/restore and message log encryption and signing so that we guide the users to use appropriate keys
- XRDDEV-1647: As an X-Road user I want X-Road to support OpenAPI 3.1 so that I can use the latest tooling
- XRDDEV-1648: As an X-Road Security Server administrator I want to be able to group message log archives by members and subsystems so that messages are easier to find and encryption can use different keys
- XRDDEV-1653: As a Product Owner I want the Security Server 404 not found page to be updated
- XRDDEV-1654: As a Software Developer I want to extract the authentication code that could be shared with the Central Server into a common module so that the implementations wouldn't go out of sync
- XRDDEV-1656: As a Developer I want to update the documentation regarding backup and restore to include the new encryption and verification changes so that the documentation is up to date
- XRDDEV-1659: As a Developer I want to forbid uploading an internal TLS certificate that doesn't belong to the key to safeguard users against mistakes
- XRDDEV-1660: As a Product Owner I want that the incoming pull request #956 is reviewed so that it can be approved/rejected
- XRDDEV-1661: As a Security Server Administrator I want the X-Road services to be restarted after a meta-package is installed as this may change certain settings
- XRDDEV-1663: As Frontend Developer I want to investigate how to generate an API client for the Security Server based on the OpenAPI descriptions so that I can have type checking for API parameters as well
- XRDDEV-1664: As an X-Road Developer I want the messagelog archiving to happen in a separate process so that the proxy messaging is more resilient to failures
- XRDDEV-1670: As a Developer I want all X-Road properties to share a common prefix so that managing and whitelisting them would be simpler
- XRDDEV-1671: As an X-Road Security Server user I want to have a nice permissions denied page so it looks consistent
- XRDDEV-1679: As a Security Server administrator I want to be able to run the Security Server without the messagelog addon so that I can save resources if I don't need it 
- XRDDEV-1691: As a Security Server administrator I want the message log archives to be encrypted and signed so that security is improved
- XRDDEV-1692: As a Security Server administrator I want the messagelog database records to be encrypted so that security is improved
- XRDDEV-1696: As a Developer I want the last string in the message log archive name to be a part of the digest so that the resulting file names would be deterministic
- XRDDEV-1697: As a Security Server administrator I want to get a meaningful error when using and incorrect OpenAPI version so that I can understand what goes wrong
- XRDDEV-1702: As a security expert, I want to add CSP and STS http headers, so that implementation meets secuirty best practises
- XRDDEV-1704: As a Central Server Administrator I want references to BDR1 to be removed from scripts so that they don't break my BDR3 installation
- XRDDEV-1705: As a Central Server Administrator I want to be able to skip database restoration during the backup/restore process so that they don't cause issues with my BDR installation
- XRDDEV-1706: As a Central Server Administrator I want to be able to skip database migrations during the install/upgrade process so that it won't break my BDR setup
- XRDDEV-1707: As a Central Server Administrator I want to be able to run database migrations manually so that I can have a working BDR setup
- XRDDEV-1708: As a Security Server administrator I want to be able to configure message log archive encryption keys so that I can set up encruption
- XRDDEV-1722: As a Developer I want to check if our ASiC containers are correct when a REST message includes a message body that is stored as a MIME attachment
- XRDDEV-1725: As a Developer I want to upgrade the OpenAPI Generator version to 5.x in the Security Server admin API
- XRDDEV-1726: As a Developer I want to fix the errors reported by SonarQube so that we pass the quality gate
- XRDDEV-1743: As a Security Server Administrator I want the generate gpg keypair script doesn't automatically remove the gpg home directory so that I don't accidentally delete the Security Server's existing gpg key.
- XRDDEV-1744: As a Product Owner I want that the X-Road version number in the documentation is changed from 6 to 7 so that it's up-to-date
- XRDDEV-1749: As a Security Server Administrator I want the Security Server health check to detect invalid global configuration so that I know when the Security Server is not operational
- XRDDEV-1753: As a Developer I want to update the Sidecar repository structure so that it has the same structure with the X-Road core
- XRDDEV-1754: As a Developer I want to plan the Sidecar maintenance process between the releases so that the images include up-to-date dependencies.
- XRDDEV-1757: As a Security Server Administrator I want to have documentation on how to install messagelogging so that it works correctly
- XRDDEV-1767: As a Security Server administrator I want to be able to configure message log archive encryption keys using GPG keyring
- XRDDEV-1768: As a Security Server administrator I want to be able to configure backup encryption keys using GPG keyring
- XRDDEV-1771: As a Security Server Administrator I want the asic container metaservice to return encrypted archives if I have enabled encryption
- XRDDEV-1772: As a Security Server Administrator I want to have a migration guide to understand how to upgrade from version 6 to version 7
- XRDDEV-1778: As a Developer I want to upgrade the Spring Boot library version that we use so that it is supported for the duration of the applications lifetime
- XRDDEV-1784: As a X-Road software administrator I want to have information on how I can add command-line arguments to the applications in the new local.properties file
- XRDDEV-1789: As a Security Server administrator I want to have a static reminder about the change from local.conf to local.properties
- XRDDEV-1811: XRDSD-205: Improvements to the metadata protocol for REST services
- XRDDEV-1812: Rest metaservice responds incorrectly to "allowedMethods"
- XRDDEV-1570: Update the Security Server diagnostics view UX design and add JAVA version information
- XRDDEV-1571: Create REST API to get JAVA version information
- XRDDEV-1572: Update the diagnostics view to match the UX design and implement JAVA version information
- XRDDEV-1790: archive-transfer-command documentation is missing from system parameters -doc
- XRDDEV-1832: As a Security Server Administrator I want the confclient to be more resilient to errors so that it doesn't cause issues
- XRDDEV-1830: Member/Client/Subsystem ordering incorrect on the Security Server frontend and API
- XRDDEV-1843: The Security Server health check does not properly check the connection to the serverconf database
- XRDDEV-1855: As a Security Expert I want to verify and fix the Security Server security vulnerability so that the potential vulnerability is resolved

## 6.26.0 - 2021-03-22
- XRDDEV-1357: Fix various permission check inconsistencies in the Security Server UI frontend implementation
- XRDDEV-1368: Improve Security Server UI Keys and Certificates view to give better visual feedback about different token statuses
- XRDDEV-1395: Update local memory caches for the Security Server API to have a TTL of 60 seconds. This resolves issue with clustered configurations where modifications to api keys on primary node were not reflected on secondary nodes
- XRDDEV-1402: Add Subject Alternative Name (SAN) to the Security Server UI certificate details view. For API users this introduces a new field to the CertificateDetails type
- XRDDEV-1423: Update the HSM wrapper library to current version
- XRDDEV-1439: Fix incorrectly displaying session expiration errors in the Security Server UI when navigating away and back to the server
- XRDDEV-1460: Fix incorrect Security Server API OpenApi description concerning possible key usage values
- XRDDEV-1465: Update the Security Server UI Keys and Certificates view search field label from "Service" to "Search"
- XRDDEV-1473: Update the Security Server UI add member/client/subsystem views and searches to have correct texts based on the wizard
- XRDDEV-1474: Fix the Security Server UI add service clients view add subject wizard, where filtering the subjects would cause the radio button to visually appear to be deselected
- XRDDEV-1475: Update the Security Server UI local groups view to forbid adding non-printable characters
- XRDDEV-1480: Improve the Security Server UI endpoint input validation
- XRDDEV-1503: Fix "Generate CSR" button not becoming disabled in Security Server UI add key flow while generating the CSR was in progress
- XRDDEV-1504: Improve audit logging to escape special characters so that they would not cause certain file readers to show the log entries incorrectly
- XRDDEV-1505: Fix a potential CSRF vulnerability in the Security Server API keys endpoints
- XRDDEV-1509: Fix installing xroad-opmonitor packages on a server with no Security Server installed
- XRDDEV-1510: Fix cases where missing OCSP responses would cause incorrect actions to be available to the user in the keys and certificates view of the Security Server UI
- XRDDEV-1517: Improve configuration on Ubuntu based releases to make using alternative JAVA installations easier
- XRDDEV-1527: Fix issue with log files where entries are hardcoded to be in the UTC timezone. After this update the logs will default to the servers timezone
- XRDDEV-1537: Added functionality to X-Road components to log the JAVA version being used to run the component at startup. In case the version is not supported by the software a warning is logged
- XRDDEV-1538: Update PostgreSQL JDBC driver that fixes a bug mentioned in: https://www.postgresql.org/message-id/flat/87h82kzwqn.fsf%40news-spur.riddles.org.uk
- XRDDEV-1548: Fix issue caused by updating the Spring Boot library which caused the Security Server API validation to stop working
- XRDDEV-1554: Update Akka to version 2.6.11 to properly fix a bug that affected Akka remoting in X-Road
- XRDDEV-1567: Update Xerces to version 2.12.1 to get latest bug fixes 
- XRDDEV-1609: Fix persistence error in adding members to local group on security server
- XRDDEV-1613: Fix error in starting xroad-jetty in central server HA installation
- XRDDEV-1620: Fix log file proxy_ui_api_access.log not being generated on security server

## 6.25.0 - 2020-11-26
- XRDDEV-1222: Update installation and user guides
- XRDDEV-1299: Add Ubuntu 20.04 packaging
- XRDDEV-1125: Initial support for running X-Road Security Server on Java 11 platform
- XRDDEV-1340: Fix admin user groups on a secondary security server
- XRDDEV-1085: Upgrade Gradle to version 6.6
- XRDDEV-1090, XRDDEV-1091, XRDDEV-1344: Update installation guides to describe the deployment options, database setup customization, database user roles and customization and how the required database users can be created manually
- XRDDEV-1353: Use key id as a label in UI if auth or sign key is missing both label and friendly name
- XRDDEV-1360: Fix add member and add client showing member classes from federated instances.
- XRDDEV-1302: Use secure Akka remote transport
- XRDDEV-1362: Fix add member and add client showing members and clients from federated instances
- XRDDEV-1366: Add defensive checks against bad client configuration when adding a new local client and sending a client registration request
- XRDDEV-1324: Add Ubuntu 20 support to public Ansible deployment scripts
- XRDDEV-1233: Replaced dtsgenerator tool with openapi-typescript-codegen.
- XRDDEV-1394: In a Security Server cluster, remove access to management REST API from all non-observer roles on a secondary node. In case the observer role is not present, the API key does not grant any permissions.
- XRDDEV-1326: Add support for setting up Ubuntu 20 clusters with Ansible scripts. Fix /etc/xroad/jetty permission problems.
- XRDDEV-1327: Jenkinsfile builds also Ubuntu 20 packages
- XRDDEV-1446: Update licensing files and footer information
- XRDDEV-1403: Fix signer becomes unreachable in certain conditions
- XRDDEV-1371: Fix proxy-ui-api does not apply database schema setting
- XRDDEV-1421: Fix http 500 errors from security server UI when HSM contained certificates which were neither sign nor auth certificates. 
- XRDDEV-1425: Fix problem where security server UI allowed attempts to create authentication CSR for a HSM key (only signing CSRs should be possible to create for HSM)
- XRDDEV-1286: Add missing error message localisations
- XRDDEV-1237: Security server user interface input fields are autofocused
- XRDDEV-1244, XRDDEV-1391: Very long identifiers no longer break security server layout
- XRDDEV-1365: Improve client selection instance id dropdown
- XRDDEV-1405: Fix security server user interface coming up with 401 error
- XRDDEV-1445: Fix layout bug in snackbar
- XRDDEV-1457: Fix broken certificate details link
- XRDDEV-1448: Fix security server docker image does not work on kernel 5.8
- XRDDEV-1487: Make connection timeouts between configuration proxy and signer less frequent

## 6.24.1 - 2020-09-18
- XRDDEV-1306: Fix security server docker image build
- XRDDEV-1305: Fix central server schema rename when BDR 1.0 is in use.
- XRDDEV-1303: Fix security server UI permission handling in "keys and certificates" view
- XRDDEV-1304: Fix security server UI routing in "security server tls certificate" view
- XRDDEV-1308: Fix UI bug in security server settings view child tab active state
- XRDDEV-1316: Update XROAD_SYSTEM_ADMINISTRATOR permissions
- XRDDEV-1317: Fix duplicate routing console log warning messages in security server UI.
- XRDDEV-1320: Verify TLS certificate field is checked for services using http
- XRDDEV-1321: Key details read only value is missing
- XRDDEV-1339: Fix not after -value in certificate details
- XRDDEV-1337: Update Keys and Certificates submenu items' names
- XRDDEV-1231: Remove unused dependency libraries
- XRDDEV-1319: Fix several permissions related bugs in the UI
- XRDDEV-1309: Minor fix to front-end login process
- XRDDEV-1352: Fix queryparameter name for filtering service client candidates
- XRDDEV-1335: Update icon for certificates
- XRDDEV-1350: Fix member code input validation in add client flow
- XRDDEV-1245: Fix security server REST API permission handling for security server TLS certificate related operations

## 6.24.0 - 2020-08-28
- New Security Server API based UI, see details in [JIRA](https://jira.niis.org/issues/?jql=project%20%3D%20XRDDEV%20AND%20fixVersion%20%3D%206.24.0%20AND%20labels%20%3D%20api-based-ui%20ORDER%20BY%20key%20ASC)
- XRDDEV-437 - Add SonarQube analysis for Github pull requests
- XRDDEV-125 - Add process view to Security Server architecture diagram
- XRDDEV-595 - Replace '-' characters with '_' in ansible scripts to fix deprecation warnings
- XRDDEV-739 - Clarified documentation related to downloading service descriptions
- XRDDEV-712 - Update Ansible scripts to support Ubuntu 18 minimal OS
- XRDDEV-711 - Update Akka dependency to 2.5.x
- XRDDEV-113 - Optimize message log archiving
- XRDDEV-261 - Update fastest wins connection selection to take successful TLS handshake into account
- XRDDEV-700 - Optimize serverconf caching and access rights evaluation
- XRDDEV-747 - Split nginx default-xroad.conf so that it can be updated independently per X-Road server type
- XRDDEV-773 - Install Central Server with remote database using Ansible
- XRDDEV-377 - Implement failover on TSA requests when TSA returns invalid response
- XRDDEV-546 - Update operational monitoring implementation and protocols.
- XRDDEV-591 - Security Server respects Accept-header value when providing error responses.
- XRDDEV-752 - Update Akka to version 2.6
- XRDDEV-745 - Fix rsyslog and nginx config changes not applied on fresh install (RHEL)
- XRDDEV-807 - Move central server DB tables to a separate schema, so that maintenance does not require super-user rights.
- XRDDEV-913 - Fix xroad-opmonitor standalone installation for Ubuntu 18
- XRDDEV-951 - Update dependencies containing known vulnerabilities. Fix dependency check false positives.
- XRDDEV-911 - Add RHEL8 packaging
- XRDDEV-925 - Define ordering for TSPs
- XRDDEV-805 - Move Security Server DB tables to a separate schema
- XRDDEV-972 - Move serverconf tables to separate schema, so that maintenance does not require super-user rights.
- XRDSD-124 - getSecurityServerOperationalData return contains other client's data
- XRDDEV-1010 - Fix operational monitoring filtering when results overflow
- XRDDEV-825 - Remove the deprecated HTTP GET metaservice interface for fetching WSDL descriptions
- XRDDEV-822 - Make the Security Server listen to connections from client information systems only on localhost when EE meta package is installed.
- XRDDEV-999 - Update Rake version
- XRDDEV-1021 - Update jackson-databind version
- XRDDEV-608 - Fix WSDLValidator returns warnings as errors
- XRDDEV-1011 - Add summary output to check_ha_cluster_status
- XRDDEV-17 - Validate client certificate's expiry date
- XRDDEV-828 - Make it possible configure database super-user name.
- XRDDEV-440 - Add separate database user for migrations, so that normal DB user used by the application cannot update the DB schema.
- XRDDEV-59 - Date and time format in the text form MUST be based on the international standard ISO 8601 so that it's consistent
- XRDDEV-814 - Update license and file headers.
- XRDDEV-728 - Remove old port forwarding scripts for RHEL7 Security Server
- XRDDEV-827 - Adds checking of X-Road identifying entities to Central Server.
- XRDDEV-727 - Refactor Ansible roles to remove duplication between private and public repositories
- XRDDEV-68 - Fix thread local variables
- XRDDEV-1159 - Disable Security Server JMX interfaces by default
- XRDDEV-1173 - Enable enforcing token PIN policy in the Estonian meta package
- XRDDEV-1156 - Bind rsyslog udp interface to localhost
- XRDDEV-1017 - Make SignerClient to recover faster from signer connection failures.
- XRDDEV-1201 - Unify file permissions on RHEL
- XRDDEV-1200 - Fix xroad-base ansible role
- XRDDEV-1181 - Upgrade dependencies
- XRDDEV-1160 - Automatically generated default password for Central Server database user centerui
- XRDDEV-1207 - Fix Iceland's certificate profile
- XRDDEV-1161 - Update Iceland's security server meta-package
- XRDDEV-1123 - Remove unnecessary ocsp fetching in Security Server UI
- XRDDEV-1180 - Add more detailed instructions on required network configuration in the Security Server installation guides.
- XRDDEV-1242 - Fix Central Server database disappearing on upgrade
- XRDDEV-1209 - Implementaion of the Faroe Islands's certificate profiles
- XRDDEV-1247 - Fix static analysis findings
- XRDDEV-1277 - Fix database backup failure
- XRDDEV-1228 - Update documentation for RHEL8
- XRDDEV-1281 - Remove unused secret token
- XRDDEV-1296 - Fix RHEL8 release packaging

## 6.23.0 - 2020-02-19
- XRDDEV-730: Validate security server addresses on security server.
- XRDDEV-732: Validate security server addresses on cental server.
- XRDDEV-734: Fix operational monitoring data retrieval.
- XRDDEV-741: Fix too strict log directory permissions cause missing audit.log.
- XRDDEV-757: Add Central Server support for external databases.
- XRDDEV-773: Central Server with remote database can be installed with Ansible scripts.
- XRDDEV-760: Remove central server HA dependency on PostgreSQL BDR extension.
- XRDDEV-780: Add instructions for setting up a PostgreSQL database cluster for central server HA.
- XRDDEV-813: Detect PostgreSQL BDR on update and update configuration if necessary.
- XRDDEV-821: Fix central Server remote database support should respect changes in db.properties during upgrade.
- XRDDEV-853: Fix environmental monitoring does not list X-Road processes on Ubuntu.
- XRDDEV-856: Update dependencies with known vulnerabilities.
- XRDDEV-871: Fix can't setup Central Server remote database.
- XRDDEV-820: Update metaspace memory parameters.
- XRDDEV-916: Add security server metapackage for Iceland.
- XRDDEV-917: Update Iceland certificate profile.

## 6.22.1 - 2019-11-07
- XRDDEV-730: Validate security server addresses.
- XRDDEV-734: Fix missing operationaldata.
- XRDDEV-741: Fix too strict log directory permissions.

## 6.22.0 - 2019-10-22
- XRDDEV-450: Make a docker image of central server.
- XRDDEV-501: Fix Test TSA on Ubuntu 18.
- XRDDEV-474: Implement Security Server TSA recovery algorithm during TSA service breaks.
- XRDDEV-384: Update Hibernate to version 5.3.10.
- XRDDEV-288: Update supported platforms on X-Road build instructions.
- XRDDEV-506: Fix operational monitoring does not record request_out_ts and response_in_ts values for REST requests and responses.
- XRDDEV-508: Update Ubuntu package dependencies and install instructions.
- XRDDEV-516: Added documentation of ownerChange management service.
- XRDDEV-140: Add optional HSM device specific slot configuration.
- XRDDEV-538: Increase the default metaspace memory of proxy component.
- XRDDEV-462: Add JSON response for listClients metaservice.
- XRDDEV-574: Fix undefined method error when WSDL is refreshed.
- XRDDEV-456: Add support for injecting autologin pin via environment varible to Security Server Docker container.
- XRDDEV-507: Set operational monitoring succeeded field based on REST service's HTTP response code.
- XRDDEV-593: Add support for registering another member on Security Server.
- XRDDEV-428: Change service client's default connection type from HTTP to HTTPS.
- XRDDEV-476: Create new X-Road Security Architecture (ARC-SEC) document.
- XRDDEV-573: Update serverconf database schema to support REST authorization.
- XRDDEV-560: Implement new ownerChange central service.
- XRDDEV-561: Update Central Server UI to support processing of Security Server owner change requests.
- XRDDEV-562: Add support for sending owner change requests from Security Server.
- XRDDEV-580: Add operational monitoring package as required for the installation of Finnish Security Server meta package.
- XRDDEV-586: Implement support for fine-grained REST service authorization in xroad-proxy.
- XRDSD-94: Make configuration reading more tolerant.
- XRDDEV-517: Update Java dependencies to a newer version.
- XRDDEV-464: Add REST metaservice allowedMethods. Change SOAP metaservice allowedMethods to return only SOAP services.
- XRDDEV-463: Add REST metaservice listMethods. Change SOAP metaservice listMethods to return only SOAP services.
- XRDDEV-465: Add REST metaservice getOpenAPI.
- XRDDEV-468: Parse OpenAPI description when adding a REST service.
- XRDDEV-136: Add automatic backups for Central and Security Server.
- XRDDEV-612: Refactor REST access rights.
- XRDDEV-615: Store OpenAPI endpoints into serverconf.
- XRDDEV-571: Remove the need for PostgreSQL 'lo' extension.
- XRDDEV-592: Update security server log file permissions.
- XRDDEV-540: Fix Ubuntu install fails if admin user groups can not be modified.
- XRDDEV-622: Add certificate profile for Iceland.
- XRDDEV-649: Remove Ubuntu 14.04 packaging, references in documentation and support from Ansible installation scripts.
- XRDDEV-636: Update Java dependencies.
- XRDDEV-652: Add Ansible configuration option for extra locales.
- XRDDEV-547: Add remote database support to Security Server.
- XRDDEV-683: Fix installation on top of existing op-monitor.
- XRDDEV-588: Security Server user interface for defining fine-grained access rules for REST services.
- XRDDEV-682: Fixes to fine-grained REST service management UI.
- XRDDEV-692: Update jackson-databind.
- XRDDEV-691: Fix typo in add endpoint dialog.
- XRDDEV-694: Fix listMethods not returning all REST services.
- XRDDEV-695: Fix allowedMethods not returning all REST services.
- XRDDEV-697: Fix metadata services documentation concerning listCentralServices.
- XRDDEV-648: Make it possible to bind xroad-proxy to ports 80 and 443 on RHEL.
- XRDDEV-696: Update REST endpoint type names to better reflect the endpoint type.
- XRDDEV-710: Update Test CA documentation.
- XRDDEV-704: Fix fine-grained REST service management missing from Security Server user guide.
- XRDDEV-610: Metaspace for xroad-monitoring has been expanded from 50m to 60m.
- XRDDEV-716: Update bouncy castle and jackson-databind.
- XRDDEV-717: Fixed restarting xroad-proxy fails during internal key generation and certificate import.

## 6.21.1 - 2019-05-22
- XRDDEV-526: Fix adding a WSDL with a newer version of an existing service fails.
- XRDDEV-506: Fix operational monitoring does not record request_out_ts and response_in_ts for REST messages.

## 6.21.0 - 2019-04-24
- XRDDEV-263: Security Server data model extended to cover REST services.
- XRDDEV-225: Add configuration option that allows auto-accepting auth cert registration requests on Central Server.
- XRDDEV-258: Update JRuby to version 9.1.17.
- XRDDEV-226: Add configuration option that allows auto-accepting Security Server client registration requests on Central Server.
- XRDDEV-341: Update Hibernate to version 5.1.17.
- XRDDEV-230: Add an additional message id to every request/response pair, so that it is be possible to distinguish messages in message log.
- XRDDEV-380: Fix missing getSecurityServerMetrics request in message log.
- XRDDEV-337: Hide the admin ui X-Road logo on smaller screens
- XTE-432: Fix resource leak - close discarded socket.
- XRDDEV-353: Add support for verifying ASiC containers containing REST messages.
- XRDDEV-352: Add support for downloading RESET message records via ASiC web service.
- XRDDEV-358: Record rest messages to operational monitoring.
- XRDDEV-375: Add securityserver protocol extension.
- XRDDEV-264: Support transporting REST messages of arbitrary size.
- XRDDEV-314: Extend messagelog database for REST messages.
- XRDDEV-328: Archive REST message records as ASiC containers.
- XRDDEV-285: Log rest messages to message log.
- XRDDEV-155: Update intial REST implementation.
- XRDDEV-120: Initial implementation of the REST support.
- XRDDEV-284: Add support for configuring REST services in the admin UI.
- XRDDEV-418: Align REST implementation with the specification.
- XRDDEV-426: Fix performance regression and native memory leak in messagelog archiving.
- XRDDEV-419: Include X-Road-Request-Id into REST message signature and request headers.
- XRDDEV-400: Return descriptive error if one tries to download WSDL for a REST service.
- XRDDEV-398: Add markdown documentation for REST message protocol.
- XRDDEV-412: Prevent SOAP service calls from REST interface and vice versa.
- XRDDEV-432: Prevent adding new services with already existing service code.
- XRDDEV-423: Fix op-monitor db migrations not run on upgrade/reinstall on RHEL.
- XRDDEV-439: Fix message log cleaning can fail if log is large.
- XRDDEV-383: Add security server Dockerfile and usage instructions.
- XRDDEV-411: Fix SonarQube duplication warning.
- XRDDEV-443: Fix updating REST service code removes all access rights.
- XRDDEV-1455: Security server footer opens a web page containing licensing information

## 6.20.1 - 2019-02-05
- XRDDEV-351: Fix XRDDEV-351

## 6.20.0 - 2019-01-23
- XTE-427 / XRDDEV-108: Operational monitoring timestamp 'responseOutTs' is taken just before payload byte array is sent out with HTTP response.
- XRDDEV-8: Update wsdlvalidator to use the latest Apache CXF wsdlvalidator version.
- XRDDEV-117: Secure XML external entity processing
- XRDDEV-105: Fix global configuration returning outdated data
- XRDDEV-119: Add NIIS as copyright owner in license files and source code license headers (.java, .rb)
- XRDDEV-94: Create security server installation packages for Ubuntu 18.04 LTS
- XRDDEV-106: Improved performance by making authentication key and signing context caching implementation more efficient
- XRDDEV-86: Separate X-Road version number from packaging
- XRDDEV-29: Update cryptographic strength of key exchange to 128bits on communication between security servers and op monitoring. Introduce whitelist setting to configure accepted cipher suites.
- XRDDEV-62: Log a warning in proxy.log when the amount of timestamped records reaches 70% of timestamp-records-limit
- XRDDEV-141: Fix queries can fail when the service provider's subsystem is registered in multiple security servers and only some of the host names do not resolve.
- XRDDEV-162: NIIS package repository updated to documentation
- XRDDEV-150, XRDDEV-60: Central Server: Added script for changing IP address of cluster nodes & updated document IG-CSHA respectively.
- XRDDEV-144: Change BatchSigner to use configuration parameter for timeouts
- XRDDEV-165: Fix ClientProxy to enforce defined cipher suites
- XRDDEV-145: Batch time-stamping cycle is repeated until the number of time-stamped records is lower than "timestamp-records-limit".
- XRDDEV-29: The cipher suite that's used in connections between Security Servers was changed so that cryptographic strength of key exchange is 128 bits.
- XRDDEV-95, XRDDEV-96: Fixes to Ubuntu 18 packaging. Add Ubuntu 18 support to local development environment. Discard xroad-common package (Ubuntu) and remove dependencies to obsoleted xroad-common (RHEL).
- XRDDEV-138: Fix namespace in metaservices document
- XRDDEV-143: Make Signer's module manager update interval configurable
- XRDDEV-169: Add installation instructions for Security Server on RHEL7
- XRDDEV-184: Conver UG-SIGDOC from Word to Markdown.
- XRDDEV-192: Add support for extracting message from ASiC container when verification of the container fails.
- XRDDEV-220: Fix FastestSocketSelector can cause timeout if none of the target hosts' names are resolvable.
- XRDDEV-170: Update security server cluster Ansible setup scripts to support Ubuntu 18.04.
- XRDDEV-191: Fix environmental monitoring daemon missing from the architecture document
- XRDDEV-146: Drop support for global configuration v1
- XRDDEV-10: Replace outdated Logback logging module by Slf4jRequestLog
- XRDDEV-229: Update default authentication and signing key length to 3072 bits (Finnish national settings)
- XRDDEV-231: Add X-Road brand colors and and X-Road logo int CS and SS layouts
- XRDDEV-232: Add feedback page
- XRDDEV-177: Change version number format
- XRDDEV-108: Set operational monitoring timestamp 'responseOutTs' just before payload byte array is sent.
- XRDDEV-101: Installation instructions for Ubuntu 18
- XRDDEV-178: Add Ubuntu 18 support to Central Server clustering
- XRDDEV-168: Remove unnecessary Ruby helper method
- XRDDEV-248: Avoid infinite read timeout when establishing a connection to security server.
- XRDDEV-257: Remove dependency on ntpd.
- XRDDEV-256: Add option that displays the X-Road software version to the AsicVerifier utility.

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
- PVAYLADEV-1426: HSM tokens got incorrectly grouped with SIGN keys

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
