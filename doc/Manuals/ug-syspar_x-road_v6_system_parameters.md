# X-Road: System Parameters User Guide

Version: 2.29  
Doc. ID: UG-SYSPAR

| Date       | Version  | Description                                                                  | Author             |
|------------|----------|------------------------------------------------------------------------------|--------------------|
| 29.05.2015 | 1.0      | First draft                                                                                                                        | Siim Annuk                 |
| 01.06.2015 | 1.1      | Some corrections done, typos fixed                                                                                                 | Siim Annuk                 |
| 30.06.2015 | 1.2      | Minor corrections done                                                                                                             | Vello Hansen, Imbi Nõgisto |
| 26.08.2015 | 1.3      | Corrections regarding SQL done                                                                                                     | Marju Ignatjeva            |
| 09.09.2015 | 2.0      | Editorial changes made                                                                                                             | Imbi Nõgisto               |
| 11.09.2015 | 2.1      | Global configuration generation interval added                                                                                     | Martin Lind                |
| 20.09.2015 | 2.2      | Editorial changes made                                                                                                             | Imbi Nõgisto               |
| 23.09.2015 | 2.3      | Warning added about changing system parameters                                                                                     | Siim Annuk                 |
| 24.09.2015 | 2.4      | Note added about setting the *timeStampingIntervalSeconds* system parameter                                                        | Siim Annuk                 |
| 07.10.2015 | 2.5      | Default value of the parameter *acceptable-timestamp-failure-period* set to 14400                                                  | Kristo Heero               |
| 8.12.2015  | 2.6      | New parameters for configuring signature algorithms and key length, proxy client-side TLS protocols, and software token pin policy | Jarkko Hyöty               |
| 8.12.2015  | 2.7      | Added parameters for toggling SOAP body logging on/off                                                                             | Janne Mattila              |
| 17.12.2015 | 2.8      | Added monitoring parameters                                                                                                        | Ilkka Seppälä              |
| 28.1.2016  | 2.9      | Added configuration client admin port                                                                                              | Ilkka Seppälä              |
| 04.10.2016 | 2.10     | Converted to markdown format | Sami Kallio |
| 05.10.2016 | 2.11     | Added options for proxy client and server connections. Clarified client-timeout option. | Olli Lindgren |
| 02.11.2016 | 2.12     | Fix ocspFreshnessSeconds description in system parameters document. | Ilkka Seppälä |
| 01.12.2017 | 2.13     | Added documentation for minimum global conf version | Sami Kallio |
| 20.01.2017 | 2.14     | Added license text and version history | Sami Kallio |
| 08.02.2017 | 2.15     | Updated documentation with new environmental monitoring parameters describing sensor intervals | Sami Kallio |
| 23.02.2017 | 2.16     | Added documentation for OCSP-response retrieval deactivation parameter | Tatu Repo |
| 03.03.2017 | 2.17     | Added new parameter *jetty-ocsp-responder-configuration-file*                 | Kristo Heero       |
| 07.03.2017 | 2.18     | Added new parameters *ocsp-responder-client-connect-timeout* and *ocsp-responder-client-read-timeout* | Kristo Heero |
| 11.04.2017 | 2.19     | Changed default values of the proxy parameter *client-timeout* to *30000*, *client-use-fastest-connecting-ssl-socket-autoclose* and *client-use-idle-connection-monitor* to *true*. Added new messagelog parameters *timestamper-client-connect-timeout* and *timestamper-client-read-timeout*. Changed default value of the proxy parameter *pool-validate-connections-after-inactivity-of-millis* to *2000*. | Kristo Heero |
| 06.06.2017 | 2.20     | Removed parameter *default-signature-algorithm*, replaced parameters *csr-signature-algorithm* with *csr-signature-digest-algorithm*, *signature-algorithm-id* with *signature-digest-algorithm-id*, and *confSignAlgoId* with *confSignDigestAlgoId*. Added new proxy-ui parameter *auth-cert-reg-signature-digest-algorithm-id*. | Kristo Heero |
| 14.06.2017 | 2.21     | Added new parameter *allowed-federations* for enabling federation in a security server. | Olli Lindgren |
| 11.07.2017 | 2.22     | Changed connector SO-linger values to -1 as per code changes | Tatu Repo |
| 18.08.2017 | 2.23     | Update wsdl-validator-command description | Jarkko Hyöty |
| 31.08.2017 | 2.24     | Moved ocsp-cache-path and enforce-token-pin-policy from under proxy to under signer and added them to central server and configuration proxy lists | Tatu Repo |
| 17.10.2017 | 2.25     | Added new security server env-monitor parameter (limit-remote-data-set). | Joni Laurila |
| 20.10.2017 | 2.26     | Clarified the effects of disabling SOAP body logging on the SOAP Headers. Split the system parameters to different tables for readability| Olli Lindgren |
| 22.11.2017 | 2.27     | Default changed to vanilla. New colums added for FI and EE values. | Antti Luoma |
| 02.01.2018 | 2.28     | Added proxy parameter allow-get-wsdl-request. | Ilkka Seppälä |
| 29.01.2018 | 2.29     | Removed proxy parameter client-fastest-connecting-ssl-use-uri-cache. Added proxy parameter client-fastest-connecting-ssl-uri-cache-period. | Ilkka Seppälä |

## Table of Contents

<!-- toc -->

  * [License](#license)
- [Introduction](#introduction)
  * [References](#references)
- [Changing the System Parameter Values](#changing-the-system-parameter-values)
  * [Changing the System Parameter Values in Configuration Files](#changing-the-system-parameter-values-in-configuration-files)
  * [Changing the System Parameter Values in the Central Server Database](#changing-the-system-parameter-values-in-the-central-server-database)
  * [Changing the Global Configuration Generation Interval in the Central Server](#changing-the-global-configuration-generation-interval-in-the-central-server)
- [Security Server System Parameters](#security-server-system-parameters)
  * [Common parameters : `[common]`](#common-parameters--common)
  * [Proxy parameters: `[proxy]`](#proxy-parameters-proxy)
  * [Proxy User Interface parameters: `[proxy-ui]`](#proxy-user-interface-parameters-proxy-ui)
  * [Signer parameters: `[signer]`](#signer-parameters-signer)
  * [Anti-DOS parameters: `[anti-dos]`](#anti-dos-parameters-anti-dos)
  * [Configuration Client parameters: `[configuration-client]`](#configuration-client-parameters-configuration-client)
  * [Message log add-on parameters: `[message-log]`](#message-log-add-on-parameters-message-log)
    + [Note on logged X-Road message headers](#note-on-logged-x-road-message-headers)
  * [Environmental monitoring add-on configuration parameters: `[env-monitor]`](#environmental-monitoring-add-on-configuration-parameters-env-monitor)
- [Central Server System Parameters](#central-server-system-parameters)
  * [System Parameters in the Configuration File](#system-parameters-in-the-configuration-file)
    + [Common parameters: `[common]`](#common-parameters-common)
    + [Center parameters: `[center]`](#center-parameters-center)
    + [Signer parameters: `[signer]`](#signer-parameters-signer-1)
  * [System Parameters in the Database](#system-parameters-in-the-database)
  * [Global Configuration Generation Interval Parameter](#global-configuration-generation-interval-parameter)
- [Configuration Proxy System Parameters](#configuration-proxy-system-parameters)
    + [Configuration proxy module parameters: `[configuration-proxy]`](#configuration-proxy-module-parameters-configuration-proxy)
    + [Signer parameters: `[signer]`](#signer-parameters-signer-2)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# Introduction

This document describes the system parameters of the X-Road components – of the security server, the central server and the configuration proxy. Additionally, changing the default values of the system parameters are explained.
Please note the term 'vanilla' in the documentation. In X-Road context vanilla means the X-Road without any of the country specific customizations, settings etc. The vanilla version of X-Road security server is installed with xroad-securityserver package. The country specific versions are installed with xroad-securityserver-XX where XX is the country code f.ex. FI or EE.

## References

1.  \[INI\] INI file, [*http://en.wikipedia.org/wiki/INI\_file*](http://en.wikipedia.org/wiki/INI_file)

2.  \[CRON\] Quartz Scheduler
    CRON expression,
    *http://www.quartz-scheduler.org/documentation/quartz-2.1.x/tutorials/crontrigger.html*
3.  <a name="Ref_PR-MESS"></a>\[PR-MESS\] [X-Road Message Protocol v. 4.0](../Protocols/pr-mess_x-road_message_protocol.md)
4.  <a name="Ref_PR-TARGETSS"></a>\[PR-TARGETSS\] [Security Server Targeting Extension for the X-Road Message Protocol](../Protocols/SecurityServerExtension/pr-targetss_security_server_targeting_extension_for_the_x-road_protocol.md)
5.  <a name="Ref_PR-SECTOKEN"></a>\[PR-SECTOKEN\] [Security Token Extension for the X-Road Message Protocol](../Protocols/SecurityTokenExtension/pr-sectoken_security_token_extension_for_the_x-road_protocol.md)

# Changing the System Parameter Values

The system parameters specify various characteristics of the system, such as port numbers, timeouts and paths to files on disk. The system parameters of the X-Road components are mainly stored in configuration files. Additionally, X-Road central server stores some system parameters in the database.

**Changing the values of the system parameters is strongly discouraged, since it may cause unexpected system behaviour.**

## Changing the System Parameter Values in Configuration Files

The configuration files are INI files \[INI\], where each section contains parameters for a particular server component.

In order to override the default values of system parameters, create or edit the file

	/etc/xroad/conf.d/local.ini

Each system parameter affects a specific server component. To change the value of a system parameter, a section for the affected component must be created in the INI file. The name-value pairs of the system parameters for that component are written under the section, one pair per line.

The following format is used for the sections:

	[ServerComponent]
	SystemParameterName1=Value1
	SystemParameterName2=Value2

For example, to configure the parameter *client-http-port* for the *proxy* component, the following lines must be added to the configuration file:

	[proxy]
	client-http-port=1234

Multiple parameters can be configured under the same section:

	[proxy]
	client-http-port=1234
	server-listen-port=20000

**NB! Changing the parameter values in the configuration files requires restarting of the server.**

**WARNING! The value of the parameter is not validated, thus care must be taken when changing the value. For example, setting the port number to a non-numeric value in the configuration will cause the system to crash.**

## Changing the System Parameter Values in the Central Server Database

The central server database can be accessed with the psql utility using the following command (password: *centerui*):

	psql -U centerui -h localhost centerui_production

The default value of a system parameter can be overridden by adding the parameter name and value to the *system_parameters* table:

	INSERT INTO system_parameters (key, value, created_at, updated_at) VALUES ('parameter_name', 'parameter_value', (now() at time zone 'utc'), (now() at time zone 'utc'));

To edit the value of a system parameter already inserted into the *system_parameters* table:

	UPDATE system_parameters SET value = '*parameter_value*', updated_at = (now() at time zone 'utc') WHERE key = 'parameter_name';

To restore the default value of a system parameter, delete the parameter from the *system_parameters* table:

	DELETE FROM system_parameters WHERE key = 'parameter_name';

**NB! Modifying or deleting system parameters other than the ones listed in section** [System Parameters in the Database](#system-parameters-in-the-database) **will cause the system to crash.**


## Changing the Global Configuration Generation Interval in the Central Server

In order to override the default value of the global configuration generation interval, edit the cron expression[1] in the file

	/etc/cron.d/xroad-center

The default contents of the file are the following:

	#!/bin/sh
	* * * * * xroad curl http://127.0.0.1:8084/managementservice/gen_conf 2>&1 > /dev/null;

**NB!** Global configuration generation interval must be modified with extreme care. Misuse of the global configuration generation interval may hinder the operation of the whole X-Road instance.

The configuration generation interval must be shorter than the value of global configuration expiration interval (*confExpireIntervalSeconds*, see section 4.2 ), or else the configuration downloaded by the configuration clients (security servers or configuration proxies) will always expire before valid configuration becomes available. It is highly recommended that the global configuration generation interval is multiple times smaller than the global configuration expiration interval.

# Security Server System Parameters

This chapter describes the system parameters used by the components of the X-Road security server. For instructions on how to change the parameter values, see section [Changing the System Parameter Values in Configuration Files](#changing-the-system-parameter-values-in-configuration-files).

## Common parameters : `[common]`

| **Parameter**                                    | **Vanilla value**                          | **Description**   |
|--------------------------------------------------|--------------------------------------------|------------------ |
| configuration-path                               | /etc/xroad/globalconf/                     | Absolute path to the directory where global configuration is stored.|
| temp-files-path                                  | /var/tmp/xroad/                            | Absolute path to the directory where temporary files are stored. |

## Proxy parameters: `[proxy]`

| **Parameter**                                    | **Vanilla value**                          | **FI-package value** | **EE-package value** | **Description** |
|--------------------------------------------------|--------------------------------------------|----------------------|----------------------|-----------------|
| client-http-port                                 | 80 <br/> 8080 (RHEL)                       |   |   | TCP port on which the service client's security server listens for HTTP requests from client applications. |
| client-https-port                                | 443 <br/> 8443 (RHEL)                      |   |   | TCP port on which the service client's security server listens for HTTPS requests from client applications. |
| client-timeout                                   | 30000                                      |   |   | Defines the time period (in milliseconds), for which the service client's security server tries to connect to the service provider's security server. When the timeout is reached, the service client's security server informs the service client's information system that a service timeout has occurred. |
| configuration-anchor-file                        | /etc/xroad/configuration-anchor.xml        |   |   | Absolute file name of the configuration anchor that is used to download global configuration. |
| connector-host                                   | 0.0.0.0                                    |   |   | IP address on which the service client's security server listens for connections from client applications. The value 0.0.0.0 allows listening on all IPv4 interfaces. |
| database-properties                              | /etc/xroad/db.properties                   |   |   | Absolute file name of the properties file for the configuration of the security server database. |
| ocsp-responder-listen-address                    | 0.0.0.0                                    |   |   | IP address on which the service provider's security server listens for requests for OCSP responses from the service client's security server. The service client's security server downloads OCSP responses from the service provider's security server while establishing a secure connection between the security servers. The value 0.0.0.0 allows listening on all IPv4 interfaces. Must match the value of proxy.server-listen-address. |
| ocsp-responder-port                              | 5577                                       |   |   | TCP port on which the service provider's security server listens for requests for OCSP responses from the service client's security server. The service client's security server downloads OCSP responses from the service provider's security server while establishing a secure connection between the security servers. |
| ocsp-responder-client-connect-timeout            | 20000                                      |   |   | Connect timeout (in milliseconds) of the OCSP responder client. The service client's security server downloads OCSP responses from the service provider's security server while establishing a secure connection between the security servers. |
| ocsp-responder-client-read-timeout               | 30000                                      |   |   | Read timeout (in milliseconds) of the OCSP responder client. The service client's security server downloads OCSP responses from the service provider's security server while establishing a secure connection between the security servers. |
| server-listen-address                            | 0.0.0.0                                    |   |   | IP address on which the service provider's security server listens for connections from the service client's security servers. The value 0.0.0.0 allows listening on all IPv4 interfaces. |
| server-listen-port                               | 5500                                       |   |   | TCP port on which the service provider's security server listens for connections from the service client's security server. |
| server-port                                      | 5500                                       |   |   | Destination TCP port for outgoing queries in the service client's security server. |
| jetty-clientproxy-configuration-file             | /etc/xroad/jetty/clientproxy.xml           |   |   | Absolute filename of the Jetty configuration file for the service client's security server. For more information about configuring Jetty server, see https://wiki.eclipse.org/Jetty/Reference/jetty.xml\_usage. |
| jetty-serverproxy-configuration-file             | /etc/xroad/jetty/serverproxy.xml           |   |   | Absolute filename of the Jetty configuration file for the service provider's security server. For more information about configuring Jetty server, see https://wiki.eclipse.org/Jetty/Reference/jetty.xml\_usage. |
| jetty-ocsp-responder-configuration-file          | /etc/xroad/jetty/ocsp-responder.xml        |   |   | Absolute filename of the Jetty configuration file for the OCSP responder of the service provider's security server. For more information about configuring Jetty server, see https://wiki.eclipse.org/Jetty/Reference/jetty.xml\_usage. |
| ssl-enabled                                      | true                                       |   |   | If true, TLS is used for connections between the service client's and service provider's security servers. |
| client-tls-ciphers                               | See [2]                                    | See [2] |   | TLS ciphers enabled on the client-side interfaces (for both incoming and outgoing requests). (since version 6.7) |
| client-tls-protocols                             | TLSv1.2,TLSv1.1                            | TLSv1.2 |   | TLS protocols enabled on the client-side interfaces (for both incoming and outgoing requests) (since version 6.7) |
| server-connector-max-idle-time                   | 0                                          | 120000 |   | The maximum time (in milliseconds) that connections from a service consuming security server to a service providing security server are allowed to be idle before the provider security server starts closing them. Value of 0 means that an infinite idle time is allowed. A non-zero value should allow some time for a pooled connection to be idle, if  pooled connections are to be supported.|
| server-connector-so-linger                       | -1                                         |   |   | The SO_LINGER time (in seconds) at the service providing security server end for connections between security servers.<br>A value larger than 0 means that upon closing a connection, the system will allow SO_LINGER seconds for the transmission and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout of zero does a forceful close immediately.<br>Value of -1 disables the forceful close.|
| server-support-clients-pooled-connections        | false                                      | true |   | Whether this service providing security server supports pooled connections from the service consumer side. If set to *false*, connections are to be closed immediately after each message. This may be a wanted approached for security servers behind load balancers. |
| client-connector-max-idle-time                   | 0                                          |   |   | The maximum time (in milliseconds) that connections from a service consumer to the service consumer's security server are allowed to be idle before the security server starts closing them. Value of 0 means that an infinite idle time is allowed.|
| client-connector-so-linger                       | -1                                         |   |   | The SO_LINGER time (in seconds) at the service consuming security server end for connections between a consumer and a security server.<br>A value larger than 0 means that upon closing a connection, the system will allow SO_LINGER seconds for the transmission and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout of zero does a forceful close immediately.<br>Value of -1 disables the forceful close.|
| client-httpclient-timeout                        | 0                                          |   |   | The maximum time (SO_TIMEOUT, in milliseconds) that connections from a service consuming security server to a service providing security server are allowed to wait for a response before the consumer end httpclient gives up. Value of 0 means that an infinite wait time is allowed. This does not affect idle connections.|
| client-httpclient-so-linger                      | -1                                         |   |   | The SO_LINGER time (in seconds) at the service consuming security server end for connections between security servers.<br>A value larger than 0 means that upon closing a connection, the system will allow SO_LINGER seconds for the transmission and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout of zero does a forceful close immediately.<br>Value of -1 disables the forceful close.|
| client-use-idle-connection-monitor               | true                                       |   |   | Should the idle connection monitor be used to clean up idle and expired connections from the connection pool. |
| client-idle-connection-monitor-interval          | 30000                                      |   |   | How often (in milliseconds) should the connection monitor go through the pooled connections to see if it can clean up any idle or expired connections. This option requires the connection monitor to be enabled to have any effect.|
| client-idle-connection-monitor-timeout           | 60000                                      |   |   | The minimum time (in milliseconds) that a pooled connection must be unused (idle) before it can be removed from the pool. Note that removal from the pool also depends on how often the connection monitor runs. This option requires the connection monitor to be enabled to have any effect. |
| pool-total-max-connections                       | 10000                                      |   |   | The total maximum number of connections that are allowed in the pool. |
| pool-total-default-max-connections-per-route     | 2500                                       |   |   | The default route specific connection maximum that is set unless a route specific connection limit is set. Due to the current implementation, this is actually the total maximum limit of connections, indepedent of what the above setting is.|
| pool-validate-connections-after-inactivity-of-millis | 2000                                   |   |   | When reusing a pooled connection to a service providing security server, check that the connection (the socket) is not half-closed if it has been idle for at least this many milliseconds. This method cannot detect half-open connections. Value of -1 disables the check. |
| pool-enable-connection-reuse                     | false                                      | true |   | Allow pooled connections between security servers to be used more than once on the client side. The service provider end of the connections has to have the setting `server-support-clients-pooled-connections=true` for the pooling to work between a provider and consumer security servers.|
| client-use-fastest-connecting-ssl-socket-autoclose | true                                     |   |   | On TLS connections between security servers, should the underlying TCP-layer connection (socket) be closed on the service consumer end when the TLS layer connection is terminated.|
| client-fastest-connecting-ssl-uri-cache-period      | 3600                                       |   |   | When a service consumer's security server finds the fastest responding service providing security server, how long the result should be kept in the TLS session cache? 0 to disable. |
| health-check-port                                | 0 (disabled)                               |   |   | The TCP port where the health check service listens to requests. Setting the port to 0 disables the health check service completely.|
| health-check-interface                           | 0.0.0.0                                    |   |   | The network interface where the health check service listens to requests. Default is all available interfaces.|
| actorsystem-port                                 | 5567                                       |   |   | The (localhost) port where the proxy actorsystem binds to. Used for communicating with xroad-signer and xroad-monitor. |
| allow-get-wsdl-request                           | false                                      |   |   | Whether to allow getWsdl metaservice to be called with HTTP/HTTPS GET method. |

## Proxy User Interface parameters: `[proxy-ui]`

| **Parameter**                                    | **Vanilla value**                          | **Description** |
|--------------------------------------------------|--------------------------------------------|-----------------|
| wsdl-validator-command                           |                                            | The command to validate the given X-Road service WSDL. The command script must:<br/>a) read the WSDL from the URI given as an argument or from standard input (*stdin*) if no arguments are given,<br/>b) return exit code 0 on success,<br/>c) return exit code 0 and write warnings to the standard error (*stderr*), if warnings occurs,<br/>d) return exit code other then 0 and write error messages to the standard error (*stderr*), if errors occurs.<br/>Defaults to no operation. |
| auth-cert-reg-signature-digest-algorithm-id      | SHA-512                                    | Signature digest algorithm used for generating authentication certificate registration request.<br/>Possible values are<br/>-   SHA-256,<br/>-   SHA-384,<br/>-   SHA-512. |

## Signer parameters: `[signer]`

| **Parameter**                                    | **Vanilla value**                          | **FI-package value** | **EE-package value** | **Description** |
|--------------------------------------------------|--------------------------------------------|----------------------|----------------------|-----------------|
| ocsp-cache-path                                  | /var/cache/xroad                           |   |   | Absolute path to the directory where the cached OCSP responses are stored. |
| enforce-token-pin-policy                         | false                                      | true |   | Controls enforcing the token pin policy. When set to true, software token pin is required to be at least 10 ASCII characters from at least tree character classes (lowercase letters, uppercase letters, digits, special characters). (since version 6.7.7) |
| client-timeout                                   | 15000                                      |   |   | Signing timeout in milliseconds. |
| device-configuration-file                        | /etc/xroad/signer/devices.ini              |   |   | Absolute filename of the configuration file of the signature creation devices. |
| key-configuration-file                           | /etc/xroad/signer/keyconf.xml              |   |   | Absolute filename of the configuration file containing signature and authentication keys and certificates. |
| port                                             | 5556                                       |   |   | TCP port on which the signer process listens. |
| key-length                                       | 2048                                       |   |   | Key length for generating authentication and signing keys (since version 6.7) |
| csr-signature-digest-algorithm                   | SHA-256                                    |   |   | Certificate Signing Request signature digest algorithm.<br/>Possible values are<br/>-   SHA-256,<br/>-   SHA-384,<br/>-   SHA-512. |

## Anti-DOS parameters: `[anti-dos]`

| **Parameter**                                    | **Vanilla value**                          | **Description** |
|--------------------------------------------------|--------------------------------------------|-----------------|
| enabled                                          | true                                       | Flag for enabling or disabling the AntiDOS system. |
| max-cpu-load                                     | 1.1                                        | Maximum allowed CPU load for accepting new connections. If set to &gt; 1.0, then CPU load is not checked. |
| max-heap-usage                                   | 1.1                                        | Specifies the maximum allowed Java heap usage when accepting new connections. If set to &gt; 1.0, then heap usage is not checked. |
| max-parallel-connections                         | 5000                                       | Maximum number of parallel connections for AntiDOS. |
| min-free-file-handles                            | 100                                        | Minimum amount of free file handles in the system for accepting new connections. At least one free file handle must be available to accept a new connection. |

## Configuration Client parameters: `[configuration-client]`

| **Parameter**                                    | **Vanilla value**                          | **Description** |
|--------------------------------------------------|--------------------------------------------|-----------------|
| port                                             | 5665                                       | TCP port on which the configuration client process listens. |
| update-interval                                  | 60                                         | Global configuration download interval in seconds. |
| admin-port                                       | 5675                                       | TCP port on which the configuration client process listens for admin commands. |
| allowed-federations                              | none                                       | A comma-separated list of case-insensitive X-Road instances that fetching configuration anchors is allowed for. This enables federation with the listed instances if the X-Road instance is already federated at the central server level . Special value *none*, if present, disables all federation (the default value), while *all* allows all federations if *none* is not present. Example: *allowed-federations=ee,sv* allows federation with example instances *EE* and *Sv* while *allowed-federations=all,none* disables federation. X-Road services `xroad-confclient` and `xroad-proxy` need to be restarted (in that order) for the setting change to take effect.|

## Message log add-on parameters: `[message-log]`

| **Parameter**                                    | **Vanilla value**                          | **FI-package value** | **EE-package value** | **Description** |
|--------------------------------------------------|--------------------------------------------|----------------------|----------------------|-----------------|
| soap-body-logging                                | true                                       | false  |   | Whether SOAP body of the messages should be logged or not.<br/><br/>If *true*, the SOAP messages are logged in their original form. If *false*, the SOAP body is cleared of its contents and only has an empty child element inside it. In addition, the SOAP header will only have specific set of elements logged, see [Note on logged X-Road message headers](#note-on-logged-x-road-message-headers) . As a side effect, details such as formatting and namespace labels of the xml message can be changed and new elements may be introduced for default values in SOAP header.<br/><br/>Removal of SOAP body is usually done for confidentiality reasons (body contains data that we do not want to have in the logs).<br/><br/>Note that changing the message this way prevents verifying its signature with the asicverifier tool. |
| enabled-body-logging-local-producer-subsystems   |                                            |   |   | Subsystem-specific overrides for SOAP body logging when soap-body-logging = false.<br/><br/>This parameter defines logging for **local producer** subsystems, that is, our subsystems that produce some service which external clients use.<br/><br/>Comma-separated list of client identifiers for which SOAP body logging is enabled. For example FI/ORG/1710128-9/SUBSYSTEM\_A1, FI/ORG/1710128-9/SUBSYSTEM\_A2 where<br/>-   FI = x-road instance<br/>-   ORG = member class<br/>-   1710128-9 = member code<br/>-   SUBSYSTEM\_A1 = subsystem code<br/><br/>This parameter can only be used on subsystem-level, it is not possible to configure SOAP body logging per member.<br/><br/>If a subsystem has forward slashes “/” in for example subsystem code, those subsystems can’t be configured with this parameter. |
| enabled-body-logging-remote-producer-subsystems  |                                            |   |   | Subsystem-specific overrides for **remote producer** subsystems, that is, remote subsystems that produce services which we use.<br/><br/>Parameter is used when soap-body-logging = false. |
| disabled-body-logging-local-producer-subsystems  |                                            |   |   | Same as enabled-body-logging-local-producer-subsystems, but this parameter is used when soap-body-logging = true. |
| disabled-body-logging-remote-producer-subsystems |                                            |   |   | Same as enabled-body-logging-remote-producer-subsystems, but this parameter is used when soap-body-logging = true. |
| acceptable-timestamp-failure-period              | 14400                                      | 18000   |   | Defines the time period (in seconds) for how long is time-stamping allowed to fail (for whatever reasons) before the message log stops accepting any more messages (and consequently the security server stops accepting requests). Set to 0 to disable this check. The value of this parameter should not be lower than the value of the central server system parameter *timeStampingIntervalSeconds.* |
| archive-interval                                 | 0 0 0/6 1/1 \* ? \*                        |   |   | CRON expression \[CRON\] defining the interval of archiving the time-stamped messages. |
| archive-max-filesize                             | 33554432                                   |   |   | Maximum size for archived files in bytes. Reaching the maximum value triggers file rotation. |
| archive-path                                     | /var/lib/xroad                             |   |   | Absolute path to the directory where time-stamped log records are archived. |
| clean-interval                                   | 0 0 0/12 1/1 \* ? \*                       |   |   | CRON expression \[CRON\] for deleting any time-stamped and archived records that are older than *message-log.keep-records-for* from the database. |
| hash-algo-id                                     | SHA-512                                    |   |   | The algorithm identifier used for hashing in the message log.<br/>Possible values are<br/>-   SHA-224,<br/>-   SHA-256,<br/>-   SHA-384,<br/>-   SHA-512. |
| keep-records-for                                 | 30                                         |   |   | Number of days to keep time-stamped and archived records in the database of the security server. If a time-stamped and archived message record is older than this value, the record is deleted from the database. |
| timestamp-immediately                            | false                                      |   |   | If true, the time-stamp is created synchronously for each request message. This is a security policy requirement to guarantee the time-stamp at the time of logging the message. |
| timestamp-records-limit                          | 10000                                      |   |   |Maximum number of message records to time-stamp in one batch. |
| timestamper-client-connect-timeout               | 20000                                      |   |   | The timestamper client connect timeout in milliseconds. A timeout of zero is interpreted as an infinite timeout. |
| timestamper-client-read-timeout                  | 60000                                      |   |   | The timestamper client read timeout in milliseconds. A timeout of zero is interpreted as an infinite timeout. |
| archive-transaction-batch                        | 10000                                      |   |   | Size of transaction batch for archiving messagelog. This size is not exact because it will always make sure that last archived batch includes timestamp also (this might mean that it will go over transaction size).

### Note on logged X-Road message headers
If the messagelog add-on has the SOAP body logging disabled, only a preconfigured set of the SOAP headers will be included in the message log.

The logged SOAP headers are the X-Road message headers listed in [Chapter 2.2](../Protocols/pr-mess_x-road_message_protocol.md#22-message-headers) of
the X-Road Message Protocol document \[[PR-MESS](#Ref_PR-MESS)\], as well as the `representedParty` extension of the X-Road protocol described in the
extension's [XML schema](http://x-road.eu/xsd/representation.xsd). The security server targeting extension for the X-Road message protocol
\[[PR-TARGETSS](#Ref_PR-TARGETSS)\] or the Security Token Extension \[[PR-SECTOKEN](#Ref_PR-SECTOKEN)\] will not be included in the message log.

## Environmental monitoring add-on configuration parameters: `[env-monitor]`

| **Parameter**                                    | **Vanilla value**                          | **Description** |
|--------------------------------------------------|--------------------------------------------|-----------------|
| port                                             | 2552                                       | TCP port number used in communications with xroad-proxy and xroad-monitor components. |
| system-metrics-sensor-interval                   | 5                                          | Interval of systems metrics sensor in seconds. How often system metrics data is collected.|
| disk-space-sensor-interval                       | 60                                         | Interval of disk space sensor in seconds. How often disk space data is collected.|
| exec-listing-sensor-interval                     | 60                                         | Interval of exec listing sensor in seconds. How often sensor data using external command are collected.|
| certificate-info-sensor-interval                 | 86400                                      | Interval of certificate information sensor in seconds. How often certificate data is collected. The first collection is always done after a delay of 10 seconds. |
| limit-remote-data-set                            | false                                      | On/Off switch for filtering out optional monitoring data. With flag set to true, only security server owner can request and get full data set. |

# Central Server System Parameters

The system parameters described in this chapter are used by the X-Road central server, except for the parameters *ocspFreshnessSeconds* and *timeStampingIntervalSeconds.*

The values of *ocspFreshnessSeconds* and *timeStampingIntervalSeconds* are distributed to the security servers via the global configuration. These parameters determine the interval of calling OCSP responder services and time-stamping services (respectively) by the security servers.

## System Parameters in the Configuration File

For instructions on how to change the parameter values, see section [Changing the System Parameter Values in Configuration Files](#changing-the-system-parameter-values-in-configuration-files).

### Common parameters: `[common]`

| **Server component** | **Name**                | **Vanilla value**    | **Description**   |
|----------------------|-------------------------|----------------------|-------------------|
| common               | temp-files-path         | /var/tmp/xroad/      | Absolute path to the directory where temporary files are stored. |

### Center parameters: `[center]`

| **Name**                | **Vanilla value**                       | **Description**       |
|-------------------------|-----------------------------------------|-----------------------|
| conf-backup-path        | /var/lib/xroad/backup/                  | Absolute path to the directory where configuration backups are stored. |
| database-properties     | /etc/xroad/db.properties                | Absolute path to file where the properties of the database of the central server are stored. |
| external-directory      | externalconf                            | Name of the signed external configuration directory that is distributed to the configuration clients (security servers and/or configuration proxies) of this and federated X-Road instances. |
| generated-conf-dir      | /var/lib/xroad/public                   | Absolute path to the directory where both the private and shared parameter files are created for distribution. |
| internal-directory      | internalconf                            | Name of the signed internal configuration directory that is distributed to the configuration clients (security servers and/or configuration proxies) of this X-Road instance. |
| trusted-anchors-allowed | false                                   | True if federation is allowed for this X-Road instance. |
| minimum-global-configuration-version | 2                          | Minimum supported global configuration version on central server. Change this if old global configuration versions need to be supported. |

### Signer parameters: `[signer]`

| **Name**                | **Vanilla value**                      | **Description** |
|-------------------------|----------------------------------------|-----------------|
| ocsp-response-retrieval-active | false <br/> _(see Description for more information)_ | This property is used as an override to deactivate periodic OCSP-response retrieval for components that don't need that functionality, but still use signer. <br/><br/> Values: <br/> `false` - OCSP-response retrieval jobs are never scheduled <br/> `true` - periodic OCSP-response retrieval is active based on ocspFetchInterval. **Note that if the entire property is missing, it is interpreted as true.** <br/><br/>  This property is delivered as an override and only for the components where the OCSP-response retrieval jobs need to be deactivated. The property is missing for components that require OCSP-response retrieval to be activated. |
| ocsp-cache-path                | /var/cache/xroad                | Absolute path to the directory where the cached OCSP responses are stored. |
| enforce-token-pin-policy       | false                           | Controls enforcing the token pin policy. When set to true, software token pin is required to be at least 10 ASCII characters from at least tree character classes (lowercase letters, uppercase letters, digits, special characters). (since version 6.7.7) |

## System Parameters in the Database

This section describes the system parameters used by the X-Road central server. For instructions on how to change the parameter values, see section [Changing the System Parameter Values in the Central Server Database](#changing-the-system-parameter-values-in-the-central-server-database).

| **Name**                    | **Value type** | **Vanilla value**                        | **Description**         |
|-----------------------------|----------------|------------------------------------------|-------------------------|
| confExpireIntervalSeconds   | integer        | 600                                      | Time in seconds of the validity of the configuration after creation. |
| confHashAlgoUri             | string         | http://www.w3.org/2001/04/xmlenc#sha512  | URI of the algorithm used for calculating the hash values of the global configuration files.<br/>Possible values are<br/>http://www.w3.org/2001/04/xmlenc#sha256,<br/>http://www.w3.org/2001/04/xmlenc#sha512. |
| confSignDigestAlgoId        | string         | SHA-512                                  | Identifier of the digest algorithm used for signing the global configuration.<br/>Possible values are<br/>-   SHA-256,<br/>-   SHA-384,<br/>-   SHA-512. |
| confSignCertHashAlgoUri     | string         | http://www.w3.org/2001/04/xmlenc#sha512  | URI of the algorithm used for calculating the hash value of the certificate used to sign the global configuration.<br/>Possible values are<br/>http://www.w3.org/2001/04/xmlenc\#sha256,<br/>http://www.w3.org/2001/04/xmlenc\#sha512. |
| ocspFreshnessSeconds        | integer        | 3600                                     | Defines the validity period (in seconds) for the OCSP responses retrieved from the OCSP responders. OCSP responses older than the validity period are considered expired and cannot be used for certificate verification. |
| timeStampingIntervalSeconds | integer        | 60                                       | Defines the interval of time-stamping service calls. Interval in seconds after which message log records must be timestamped. The interval must be between 60 and 86400 seconds. **Note: this value must be less than *ocspFreshnessSeconds.*** |

## Global Configuration Generation Interval Parameter

The global configuration generation interval parameter regulates the timing for global configuration generation. Global configuration generation is invoked by the Cron daemon[3]. The parameter is located at following file:

	/etc/cron.d/xroad-center

The file is deployed during X-Road installation and by default has following content[4]:

	#!/bin/sh
	* * * * * xroad curl http://127.0.0.1:8084/managementservice/gen_conf 2>1 >/dev/null;

The parameter regulating the timing of global configuration generation is the cron expression at the start of the last line (\* \* \* \* \*), which means that global configuration generation is invoked every minute by default.

# Configuration Proxy System Parameters

This chapter describes the system parameters used by the X-Road configuration proxy.

### Configuration proxy module parameters: `[configuration-proxy]`

| **Name**                       | **Vanilla value**                       | **Description**    |
|--------------------------------|-----------------------------------------|--------------------|
| address                        | 0.0.0.0                                 | The public IP or NAT address which is accessed for downloading the distributed global configuration. |
| configuration-path             | /etc/xroad/configurationproxy/          | Absolute path to the directory containing the configuration files of the configuration proxy instance. |
| generated-conf-path            | /var/lib/xroad/public                   | Absolute path to the public web server directory where the global configuration files that this configuration proxy generates are be placed for distribution. |
| signature-digest-algorithm-id  | SHA-512                                 | ID of the digest algorithm the configuration proxy uses when computing global configuration signatures.<br/>The possible values are<br/>-   SHA-256,<br/>-   SHA-384,<br/>-   SHA-512. |
| hash-algorithm-uri             | http://www.w3.org/2001/04/xmlenc#sha512 | URI that identifies the algorithm the configuration proxy uses when calculating hash values for the global configuration files.<br/>The possible values are<br/>http://www.w3.org/2001/04/xmlenc#sha256,<br/>http://www.w3.org/2001/04/xmlenc#sha512 |
| download-script                | /usr/share/xroad/scripts/download\_instance\_configuration.sh | Absolute path to the location of the script that initializes the global configuration download procedure. |
| minimum-global-configuration-version | 2                                 | Minimum supported global configuration version on configuration proxy. Change this if old global configuration versions need to be supported. |

### Signer parameters: `[signer]`

| **Name**                       | **Vanilla value**                       | **Description** |
|--------------------------------|-----------------------------------------|-----------------|
| ocsp-response-retrieval-active | false <br/> _(see Description for more information)_ | This property is used as an override to deactivate periodic OCSP-response retrieval for components that don't need that functionality, but still use signer. <br/><br/> Values: <br/> `false` - OCSP-response retrieval jobs are never scheduled <br/> `true` - periodic OCSP-response retrieval is active based on ocspFetchInterval. **Note that if the entire property is missing, it is interpreted as true.** <br/><br/>  This property is delivered as an override and only for the components where the OCSP-response retrieval jobs need to be deactivated. The property is missing for components that require OCSP-response retrieval to be activated. |
| ocsp-cache-path                | /var/cache/xroad                        | Absolute path to the directory where the cached OCSP responses are stored. |
| enforce-token-pin-policy       | false                                   | Controls enforcing the token pin policy. When set to true, software token pin is required to be at least 10 ASCII characters from at least tree character classes (lowercase letters, uppercase letters, digits, special characters). (since version 6.7.7) |

[1] See also [*http://www.quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger*](http://www.quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger).

[2] Default value for proxy.client-tls-ciphers.
>TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
TLS_DHE_RSA_WITH_AES_256_CBC_SHA

In Finnish package overridden to:
> TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
  TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
  TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
  TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
  TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
  TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
  TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
  TLS_DHE_RSA_WITH_AES_256_GCM_SHA384

> (see [*https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider*](https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider) for possible values)
>
> Note. OpenJDK 8 on RHEL 7 does not support ECDHE key agreement protocol, only DHE cipher suites are supported.

[3] See also [*http://linux.die.net/man/8/cron*](http://linux.die.net/man/8/cron).

[4] For exact format specification see also [*https://help.ubuntu.com/community/CronHowto*](https://help.ubuntu.com/community/CronHowto).
