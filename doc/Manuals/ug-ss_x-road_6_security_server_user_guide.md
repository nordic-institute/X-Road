# SECURITY SERVER USER GUIDE <!-- omit in toc -->

**X-ROAD 7**

Version: 2.76
Doc. ID: UG-SS

---


## Version history <!-- omit in toc -->

| Date       | Version | Description                                                                                                                                                                                                                                                                                                                                                                                                 | Author            |
|------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| 05.09.2014 | 0.1     | Initial draft                                                                                                                                                                                                                                                                                                                                                                                               |                   |
| 24.09.2014 | 0.2     | Translation to English                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 10.10.2014 | 0.3     | Update                                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 14.10.2014 | 0.4     | Title page, header, footer added                                                                                                                                                                                                                                                                                                                                                                            |                   |
| 16.10.2014 | 0.5     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 12.11.2014 | 0.6     | Asynchronous messages section removed. Global Configuration distributors section replaced with Configuration Anchor section ([10.1](#101-managing-the-configuration-anchor)). Added Logback information (Chapter [16](#17-logs-and-system-services)). A note added about the order of timestamping services (Section [10.2](#102-managing-the-timestamping-services)).                                      |                   |
| 1.12.2014  | 1.0     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 19.01.2015 | 1.1     | License information added                                                                                                                                                                                                                                                                                                                                                                                   |                   |
| 27.01.2015 | 1.2     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 30.04.2015 | 1.3     | "sdsb" changed to "xroad"                                                                                                                                                                                                                                                                                                                                                                                   |                   |
| 29.05.2015 | 1.4     | Message Log chapter added (Chapter [11](#11-message-log))                                                                                                                                                                                                                                                                                                                                                   |                   |
| 30.06.2015 | 1.5     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 3.07.2015  | 1.6     | Audit Log chapter added (Chapter [12](#12-audit-log))                                                                                                                                                                                                                                                                                                                                                       |                   |
| 7.09.2015  | 1.7     | Message Log – how to use remote database (Section [11.3](#113-using-a-remote-database))                                                                                                                                                                                                                                                                                                                     |                   |
| 14.09.2015 | 1.8     | Reference to the audit log events added                                                                                                                                                                                                                                                                                                                                                                     |                   |
| 18.09.2015 | 1.9     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                      |                   |
| 21.09.2015 | 2.0     | References fixed                                                                                                                                                                                                                                                                                                                                                                                            |                   |
| 07.10.2015 | 2.1     | Default value of the parameter *acceptable-timestamp-failure-period* set to 14400                                                                                                                                                                                                                                                                                                                           |                   |
| 14.10.2015 | 2.2     | Instructions for using an external database for the message log corrected                                                                                                                                                                                                                                                                                                                                   |                   |
| 05.11.2015 | 2.3     | Updates related to backup and restore (Chapter [13](#13-back-up-and-restore))                                                                                                                                                                                                                                                                                                                               |                   |
| 30.11.2015 | 2.4     | X-Road concepts updated (Section [1.2](#12-x-road-concepts)). Security server registration updated (Chapter [3](#3-security-server-registration)). Security server clients updated (Chapter [4](#4-security-server-clients)); only subsystems (and not members) can be registered as security server clients and have services or access rights configured. Cross-references fixed. Editorial changes made. |                   |
| 09.12.2015 | 2.5     | Security server client deletion updated (Section [4.5.2](#452-deleting-a-client)). Editorial changes made.                                                                                                                                                                                                                                                                                                  |                   |
| 14.12.2015 | 2.6     | Message log updated (Chapter [11](#11-message-log))                                                                                                                                                                                                                                                                                                                                                         |                   |
| 14.01.2016 | 2.7     | Logs updated (Chapter [16](#17-logs-and-system-services))                                                                                                                                                                                                                                                                                                                                                   |                   |
| 08.02.2016 | 2.8     | Corrections in chapter [16](#17-logs-and-system-services)                                                                                                                                                                                                                                                                                                                                                   |                   |
| 20.05.2016 | 2.9     | Merged changes from xtee6-doc repo. Added Chapter [14](#14-diagnostics) Diagnostics and updated content of [10.3](#103-changing-the-internal-tls-key-and-certificate) Changing the Internal TLS Key and Certificate.                                                                                                                                                                                        |                   |
| 29.11.2016 | 2.10    | User Management updated (Chapter [2](#2-user-management)). XTE-297: Internal Servers tab is displayed to security server owner (Chapter [9](#9-communication-with-the-client-information-systems)).                                                                                                                                                                                                         |                   |
| 19.12.2016 | 2.11    | Added Chapter [15](#15-operational-monitoring) Operational Monitoring                                                                                                                                                                                                                                                                                                                                       |                   |
| 20.12.2016 | 2.12    | Minor corrections in Chapter [15](#15-operational-monitoring)                                                                                                                                                                                                                                                                                                                                               |                   |
| 22.12.2016 | 2.13    | Corrections in Chapter [15.2.5](#1525-configuring-an-external-operational-monitoring-daemon-and-the-corresponding-security-server)                                                                                                                                                                                                                                                                          |                   |
| 04.01.2016 | 2.14    | Corrections in Chapter [15.2.5](#1525-configuring-an-external-operational-monitoring-daemon-and-the-corresponding-security-server)                                                                                                                                                                                                                                                                          |                   |
| 20.02.2017 | 2.15    | Converted to Github flavoured Markdown, added license text, adjusted tables for better output in PDF                                                                                                                                                                                                                                                                                                        | Toomas Mölder     |
| 16.03.2017 | 2.16    | Added observer role to Chapters [2.1](#21-user-roles) and [2.2](#22-managing-the-users)                                                                                                                                                                                                                                                                                                                     | Tatu Repo         |
| 15.06.2017 | 2.17    | Added [Chapter 17](#18-federation) on federation                                                                                                                                                                                                                                                                                                                                                            | Olli Lindgren     |
| 25.09.2017 | 2.18    | Added chapter [16 Environmental Monitoring](#16-environmental-monitoring)                                                                                                                                                                                                                                                                                                                                   | Tomi Tolvanen     |
| 17.10.2017 | 2.19    | Added section [16.3 Limiting environmental monitoring remote data set](#163-limiting-environmental-monitoring-remote-data-set)                                                                                                                                                                                                                                                                              | Joni Laurila      |
| 05.03.2018 | 2.20    | Added terms and abbreviations reference, document links, moved concepts to terms and abbreviations.                                                                                                                                                                                                                                                                                                         | Tatu Repo         |
| 10.04.2018 | 2.21    | Update internal server certificate documentation.                                                                                                                                                                                                                                                                                                                                                           | Jarkko Hyöty      |
| 25.05.2018 | 2.22    | Update system parameters documentation.                                                                                                                                                                                                                                                                                                                                                                     | Jarkko Hyöty      |
| 15.11.2018 | 2.23    | Minor updates for Ubuntu 18.04                                                                                                                                                                                                                                                                                                                                                                              | Jarkko Hyöty      |
| 06.02.2019 | 2.24    | Minor updates on security server client registration in Chapters [4.3](#43-configuring-a-signing-key-and-certificate-for-a-security-server-client) and [4.4](#44-registering-a-security-server-client-in-the-x-road-governing-authority).                                                                                                                                                                   | Petteri Kivimäki  |
| 15.03.2019 | 2.25    | Update documentation to cover REST service usage in chapter [6]                                                                                                                                                                                                                                                                                                                                             | Jarkko Hyöty      |
| 26.03.2019 | 2.26    | Added chapter on API keys [19](#19-management-rest-api)                                                                                                                                                                                                                                                                                                                                                     | Janne Mattila     |
| 16.04.2019 | 2.27    | Minor updates regarding REST services in chapter [6]                                                                                                                                                                                                                                                                                                                                                        | Petteri Kivimäki  |
| 30.06.2019 | 2.28    | Update the default connection type from HTTP to HTTPS in chapter [9]                                                                                                                                                                                                                                                                                                                                        | Petteri Kivimäki  |
| 01.07.2019 | 2.29    | Changing the Security Server Owner chapter added (Chapter [3.4](#34-changing-the-security-server-owner))                                                                                                                                                                                                                                                                                                    | Petteri Kivimäki  |
| 14.08.2019 | 2.30    | Added automatic backups                                                                                                                                                                                                                                                                                                                                                                                     | Ilkka Seppälä     |
| 29.09.2019 | 2.31    | Added chapter [19.3](#193-correlation-id-http-header) on REST API correlation id                                                                                                                                                                                                                                                                                                                            | Janne Mattila     |
| 30.09.2019 | 2.32    | Added remote database migration guide                                                                                                                                                                                                                                                                                                                                                                       | Ilkka Seppälä     |
| 15.10.2019 | 2.33    | Updated REST services in chapter [6]                                                                                                                                                                                                                                                                                                                                                                        | Ilkka Seppälä     |
| 04.11.2019 | 2.34    | Added information about REST API request rate and size limits                                                                                                                                                                                                                                                                                                                                               | Janne Mattila     |
| 07.11.2019 | 2.35    | Add more information about service descriptions to chapter [6]                                                                                                                                                                                                                                                                                                                                              | Ilkka Seppälä     |
| 05.12.2019 | 2.36    | Add information about timestamping failover capabilities in chapter [10.2](#102-managing-the-timestamping-services)                                                                                                                                                                                                                                                                                         | Petteri Kivimäki  |
| 24.02.2020 | 2.37    | Updated notes about key caching after changing internal TLS key and certificate [10.3](#103-changing-the-internal-tls-key-and-certificate)                                                                                                                                                                                                                                                                  | Caro Hautamäki    |
| 26.03.2020 | 2.38    | Added chapter on updating API keys [19.1.3](#1913-updating-api-keys)                                                                                                                                                                                                                                                                                                                                        | Petteri Kivimäki  |
| 30.03.2020 | 2.39    | Added description of pre-restore backups                                                                                                                                                                                                                                                                                                                                                                    | Ilkka Seppälä     |
| 01.04.2020 | 2.40    | Added notes about IP whitelists for REST API                                                                                                                                                                                                                                                                                                                                                                | Janne Mattila     |
| 03.06.2020 | 2.41    | Updated audit logging description                                                                                                                                                                                                                                                                                                                                                                           | Janne Mattila     |
| 05.06.2020 | 2.42    | Added chapter about validation errors [19.4](#194-validation-errors)                                                                                                                                                                                                                                                                                                                                        | Caro Hautamäki    |
| 25.06.2020 | 2.43    | Update environmental and operational monitoring JMXMP details                                                                                                                                                                                                                                                                                                                                               | Petteri Kivimäki  |
| 08.07.2020 | 2.44    | Update chapter on access rights [7](#7-access-rights)                                                                                                                                                                                                                                                                                                                                                       | Petteri Kivimäki  |
| 30.07.2020 | 2.45    | Added mention about proxy_ui_api.log to [17 Logs and System Services](#17-logs-and-system-services)                                                                                                                                                                                                                                                                                                         | Janne Mattila     |
| 10.08.2020 | 2.46    | Added mention about unit start rate limits to [17.1 System Services](#171-system-services)                                                                                                                                                                                                                                                                                                                  | Janne Mattila     |
| 21.09.2020 | 2.47    | Added a validation error example to [19.4 Validation errors](#194-validation-errors)                                                                                                                                                                                                                                                                                                                        | Caro Hautamäki    |
| 29.09.2020 | 2.48    | Update chapters [3](#3-security-server-registration), [4](#4-security-server-clients), [6](#6-x-road-services), [7](#7-access-rights), [8](#8-local-access-right-groups) and [13](#13-back-up-and-restore) to match the new management API                                                                                                                                                                  | Tapio Jaakkola    |
| 30.09.2020 | 2.49    | Update chapters [3](#3-security-server-registration), [5](#5-security-tokens-keys-and-certificates), [9](#9-communication-with-the-client-information-systems), [10](#10-system-parameters), [14](#14-diagnostics) and [17](#17-logs-and-system-services) to match the new management API                                                                                                                   | Caro Hautamäki    |
| 10.10.2020 | 2.50    | Corrections in Chapter [19 Management REST API](#19-management-rest-api)                                                                                                                                                                                                                                                                                                                                    | Janne Mattila     |
| 13.10.2020 | 2.51    | Added a section about the warning responses [19.5 Warning responses](#195-warning-responses)                                                                                                                                                                                                                                                                                                                | Caro Hautamäki    |
| 15.10.2020 | 2.52    | Added chapter [2.3 Managing API Keys](#23-managing-api-keys)                                                                                                                                                                                                                                                                                                                                                | Caro Hautamäki    |
| 22.10.2020 | 2.53    | Added reference to management REST API's OpenAPI description                                                                                                                                                                                                                                                                                                                                                | Petteri Kivimäki  |
| 01.12.2020 | 2.54    | Added endpoint for getting one API key to [19.1.2 Listing API keys](#1912-listing-api-keys)                                                                                                                                                                                                                                                                                                                 | Janne Mattila     |
| 25.02.2020 | 2.55    | Added information to find X-Road ID from conf backup file in chapter [13.2 Restore from the Command Line](#132-restore-from-the-command-line)                                                                                                                                                                                                                                                               | Karl Talumäe      |
| 31.05.2021 | 2.56    | Added information about backup archive contents and encryption                                                                                                                                                                                                                                                                                                                                              | Andres Allkivi    |
| 23.06.2021 | 2.57    | Fix incorrect link in Chapter [3.1](#31-configuring-the-signing-key-and-certificate-for-the-security-server-owner)                                                                                                                                                                                                                                                                                          | Petteri Kivimäki  |
| 11.08.2021 | 2.58    | Minor updates to backup archive contents and encryption                                                                                                                                                                                                                                                                                                                                                     | Petteri Kivimäki  |
| 13.08.2021 | 2.59    | Add documentation about message log archive grouping and encryption                                                                                                                                                                                                                                                                                                                                         | Jarkko Hyöty      |
| 25.08.2021 | 2.60    | Update X-Road references from version 6 to 7                                                                                                                                                                                                                                                                                                                                                                | Caro Hautamäki    |
| 31.08.2021 | 2.61    | Describe new messagelog and message archive functionality                                                                                                                                                                                                                                                                                                                                                   | Ilkka Seppälä     |
| 13.09.2021 | 2.62    | Added a new chapter about custom command line arguments [21](#21-adding-command-line-arguments)                                                                                                                                                                                                                                                                                                             | Caro Hautamäki    |
| 22.09.2021 | 2.63    | Update backup encryption instructions                                                                                                                                                                                                                                                                                                                                                                       | Jarkko Hyöty      |
| 05.10.2021 | 2.64    | Moved the chapter about command line arguments to the system parameters document                                                                                                                                                                                                                                                                                                                            | Caro Hautamäki    |
| 24.11.2021 | 2.65    | Updated anchors to match correct sections                                                                                                                                                                                                                                                                                                                                                                   | Raido Kaju        |
| 30.11.2021 | 2.66    | Added chapter about configuring account lockouts                                                                                                                                                                                                                                                                                                                                                            | Caro Hautamäki    |
| 09.12.2021 | 2.67    | Added instructions for ensuring user account security                                                                                                                                                                                                                                                                                                                                                       | Ilkka Seppälä     |
| 09.12.2021 | 2.68    | Updated chapter [22](#22-additional-security-hardening) and added information about password policies                                                                                                                                                                                                                                                                                                       | Caro Hautamäki    |
| 13.04.2022 | 2.69    | Updated max loggable body size parameter name to correct one                                                                                                                                                                                                                                                                                                                                                | Raido Kaju        |
| 03.05.2022 | 2.70    | Minor updates to system services                                                                                                                                                                                                                                                                                                                                                                            | Petteri Kivimäki  |
| 17.05.2022 | 2.71    | Updates to Diagnostics section, minor updates to backup encryption, message log database encryption and archive encryption and grouping                                                                                                                                                                                                                                                                     | Petteri Kivimäki  |
| 13.07.2022 | 2.72    | Updated chapter [21](#21-adding-command-line-arguments) and added `XROAD_MESSAGELOG_ARCHIVER_PARAMS` argument                                                                                                                                                                                                                                                                                               | Petteri Kivimäki  |
| 09.01.2023 | 2.73    | Improved chapter [9](#9-communication-with-information-systems)                                                                                                                                                                                                                                                                                                                                             | Andres Rosenthal  |
| 30.01.2023 | 2.74    | Updated chapter [13.3 Automatic Backups](#133-automatic-backups) to reflect recent configuration changes.                                                                                                                                                                                                                                                                                                   | Ričardas Bučiūnas |
| 01.06.2023 | 2.75    | Update references                                                                                                                                                                                                                                                                                                                                                                                           | Petteri Kivimäki  |
| 31.05.2023 | 2.76    | Updated chapter [19.1.5 API key caching](#1915-api-key-caching) with additional configuration suggestions.                                                                                                                                                                                                                                                                                                  | Ričardas Bučiūnas |

## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1 Introduction](#1-introduction)
  * [1.1 The X-Road Security Server](#11-the-x-road-security-server)
  * [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
  * [1.3 References](#13-references)
* [2 User Management](#2-user-management)
  * [2.1 User Roles](#21-user-roles)
  * [2.2 Managing the Users](#22-managing-the-users)
    * [2.2.1 Adding and Removing Users](#221-adding-and-removing-users)
  * [2.3 Managing API Keys](#23-managing-api-keys)
    * [2.3.1 Creating a new API key](#231-creating-a-new-api-key)
    * [2.3.2 Editing the roles of an API key](#232-editing-the-roles-of-an-api-key)
    * [2.3.3 Revoking an API key](#233-revoking-an-api-key)
* [3 Security Server Registration](#3-security-server-registration)
  * [3.1 Configuring the Signing Key and Certificate for the Security Server Owner](#31-configuring-the-signing-key-and-certificate-for-the-security-server-owner)
    * [3.1.1 Generating a Signing Key and Certificate Signing Request](#311-generating-a-signing-key-and-certificate-signing-request)
    * [3.1.2 Importing a Certificate from the Local File System](#312-importing-a-certificate-from-the-local-file-system)
    * [3.1.3 Importing a Certificate from a Security Token](#313-importing-a-certificate-from-a-security-token)
  * [3.2 Configuring the Authentication Key and Certificate for the Security Server](#32-configuring-the-authentication-key-and-certificate-for-the-security-server)
    * [3.2.1 Generating an Authentication Key](#321-generating-an-authentication-key)
    * [3.2.2 Generating a Certificate Signing Request for an Authentication Key](#322-generating-a-certificate-signing-request-for-an-authentication-key)
    * [3.2.3 Importing an Authentication Certificate from the Local File System](#323-importing-an-authentication-certificate-from-the-local-file-system)
  * [3.3 Registering the Security Server in the X-Road Governing Authority](#33-registering-the-security-server-in-the-x-road-governing-authority)
    * [3.3.1 Registering an Authentication Certificate](#331-registering-an-authentication-certificate)
  * [3.4 Changing the Security Server Owner](#34-changing-the-security-server-owner)
* [4 Security Server Clients](#4-security-server-clients)
  * [4.1 Security Server Client States](#41-security-server-client-states)
  * [4.2 Adding a Security Server Client](#42-adding-a-security-server-client)
  * [4.3 Adding a Security Server Member Subsystem](#43-adding-a-security-server-member-subsystem)
  * [4.4 Configuring a Signing Key and Certificate for a Security Server Client](#44-configuring-a-signing-key-and-certificate-for-a-security-server-client)
  * [4.5 Registering a Security Server Client in the X-Road Governing Authority](#45-registering-a-security-server-client-in-the-x-road-governing-authority)
    * [4.5.1 Registering a Security Server Client](#451-registering-a-security-server-client)
  * [4.6 Deleting a Client from the Security Server](#46-deleting-a-client-from-the-security-server)
    * [4.6.1 Unregistering a Client](#461-unregistering-a-client)
    * [4.6.2 Deleting a Client](#462-deleting-a-client)
* [5 Security Tokens, Keys, and Certificates](#5-security-tokens-keys-and-certificates)
  * [5.1 Availability States of Security Tokens](#51-availability-states-of-security-tokens)
  * [5.2 Registration States of Certificates](#52-registration-states-of-certificates)
    * [5.2.1 Registration States of the Signing Certificate](#521-registration-states-of-the-signing-certificate)
    * [5.2.2 Registration States of the Authentication Certificate](#522-registration-states-of-the-authentication-certificate)
  * [5.3 Validity States of Certificates](#53-validity-states-of-certificates)
  * [5.4 Activating and Disabling the Certificates](#54-activating-and-disabling-the-certificates)
  * [5.5 Configuring and Registering an Authentication key and Certificate](#55-configuring-and-registering-an-authentication-key-and-certificate)
  * [5.6 Deleting a Certificate](#56-deleting-a-certificate)
    * [5.6.1 Unregistering an Authentication Certificate](#561-unregistering-an-authentication-certificate)
    * [5.6.2 Deleting a Certificate or a certificate Signing Request notice](#562-deleting-a-certificate-or-a-certificate-signing-request-notice)
  * [5.7 Deleting a Key](#57-deleting-a-key)
* [6 X-Road Services](#6-x-road-services)
  * [6.1 Adding a service description](#61-adding-a-service-description)
    * [6.1.1 SOAP](#611-soap)
    * [6.1.2 REST](#612-rest)
  * [6.2 Refreshing a service description](#62-refreshing-a-service-description)
  * [6.3 Enabling and Disabling a service description](#63-enabling-and-disabling-a-service-description)
  * [6.4 Changing the Address of a service description](#64-changing-the-address-of-a-service-description)
  * [6.5 Deleting a service description](#65-deleting-a-service-description)
  * [6.6 Changing the Parameters of a Service](#66-changing-the-parameters-of-a-service)
  * [6.7 Managing REST Endpoints](#67-managing-rest-endpoints)
* [7 Access Rights](#7-access-rights)
  * [7.1 Changing the Access Rights of a Service](#71-changing-the-access-rights-of-a-service)
  * [7.2 Adding a Service Client](#72-adding-a-service-client)
  * [7.3 Changing the Access Rights of a Service Client](#73-changing-the-access-rights-of-a-service-client)
* [8 Local Access Right Groups](#8-local-access-right-groups)
  * [8.1 Adding a Local Group](#81-adding-a-local-group)
  * [8.2 Displaying and Changing the Members of a Local Group](#82-displaying-and-changing-the-members-of-a-local-group)
  * [8.3 Changing the description of a Local Group](#83-changing-the-description-of-a-local-group)
  * [8.4 Deleting a Local Group](#84-deleting-a-local-group)
* [9 Communication with Information Systems](#9-communication-with-information-systems)
  * [9.1 Communication with Service Consumer Information Systems](#91-communication-with-service-consumer-information-systems)
  * [9.2 Communication with Service Provider Information Systems](#92-communication-with-service-provider-information-systems)
  * [9.3 Managing Information System TLS Certificates](#93-managing-information-system-tls-certificates)

* [10 System Parameters](#10-system-parameters)
  * [10.1 Managing the Configuration Anchor](#101-managing-the-configuration-anchor)
  * [10.2 Managing the Timestamping Services](#102-managing-the-timestamping-services)
  * [10.3 Changing the Internal TLS Key and Certificate](#103-changing-the-internal-tls-key-and-certificate)
  * [10.4 Approved Certificate Authorities](#104-approved-certificate-authorities)
* [11 Message Log](#11-message-log)
  * [11.1 Changing the Configuration of the Message Log](#111-changing-the-configuration-of-the-message-log)
    * [11.1.1 Common Parameters](#1111-common-parameters)
    * [11.1.2 Logging Parameters](#1112-logging-parameters)
    * [11.1.3 Message Log Encryption](#1113-message-log-encryption)
    * [11.1.4 Timestamping Parameters](#1114-timestamping-parameters)
    * [11.1.5 Archiving Parameters](#1115-archiving-parameters)
    * [11.1.6 Archive Files](#1116-archive-files)
    * [11.1.7 Archive Encryption and Grouping](#1117-archive-encryption-and-grouping)
  * [11.2 Transferring the Archive Files from the Security Server](#112-transferring-the-archive-files-from-the-security-server)
  * [11.3 Using a Remote Database](#113-using-a-remote-database)
* [12 Audit Log](#12-audit-log)
  * [12.1 Changing the Configuration of the Audit Log](#121-changing-the-configuration-of-the-audit-log)
  * [12.2 Archiving the Audit Log](#122-archiving-the-audit-log)
* [13 Back up and restore](#13-back-up-and-restore)
  * [13.1 Back up and Restore in the User Interface](#131-back-up-and-restore-in-the-user-interface)
  * [13.2 Restore from the Command Line](#132-restore-from-the-command-line)
  * [13.3 Automatic Backups](#133-automatic-backups)
  * [13.4 Backup Encryption Configuration](#134-backup-encryption-configuration)
  * [13.5 Verifying Backup Archive Consistency](#135-verifying-backup-archive-consistency)
* [14 Diagnostics](#14-diagnostics)
  * [14.1 Examine security server services status information](#141-examine-security-server-services-status-information)
  * [14.2 Examine security server Java version information](#142-examine-security-server-java-version-information)
  * [14.3 Examine security server encryption status information](#143-examine-security-server-encryption-status-information)
* [15 Operational Monitoring](#15-operational-monitoring)
  * [15.1 Operational Monitoring Buffer](#151-operational-monitoring-buffer)
    * [15.1.1 Stopping the Collecting of Operational Data](#1511-stopping-the-collecting-of-operational-data)
  * [15.2 Operational Monitoring Daemon](#152-operational-monitoring-daemon)
    * [15.2.1 Configuring the Health Statistics Period](#1521-configuring-the-health-statistics-period)
    * [15.2.2 Configuring the Parameters Related to Database Cleanup](#1522-configuring-the-parameters-related-to-database-cleanup)
    * [15.2.3 Configuring the Parameters related to the HTTP Endpoint of the Operational Monitoring Daemon](#1523-configuring-the-parameters-related-to-the-http-endpoint-of-the-operational-monitoring-daemon)
    * [15.2.4 Installing an External Operational Monitoring Daemon](#1524-installing-an-external-operational-monitoring-daemon)
    * [15.2.5 Configuring an External Operational Monitoring Daemon and the Corresponding Security Server](#1525-configuring-an-external-operational-monitoring-daemon-and-the-corresponding-security-server)
    * [15.2.6 Monitoring Health Data over JMXMP](#1526-monitoring-health-data-over-jmxmp)
* [16 Environmental Monitoring](#16-environmental-monitoring)
  * [16.1 Usage via SOAP API](#161-usage-via-soap-api)
  * [16.2 Usage via JMX API](#162-usage-via-jmx-api)
  * [16.3 Limiting environmental monitoring remote data set](#163-limiting-environmental-monitoring-remote-data-set)
* [17 Logs and System Services](#17-logs-and-system-services)
  * [17.1 System Services](#171-system-services)
  * [17.2 Logging configuration](#172-logging-configuration)
  * [17.3 Fault Detail UUID](#173-fault-detail-uuid)
* [18 Federation](#18-federation)
* [19 Management REST API](#19-management-rest-api)
  * [19.1 API key management operations](#191-api-key-management-operations)
    * [19.1.1 Creating new API keys](#1911-creating-new-api-keys)
    * [19.1.2 Listing API keys](#1912-listing-api-keys)
    * [19.1.3 Updating API keys](#1913-updating-api-keys)
    * [19.1.4 Revoking API keys](#1914-revoking-api-keys)
    * [19.1.5 API key caching](#1915-api-key-caching)
  * [19.2 Executing REST calls](#192-executing-rest-calls)
  * [19.3 Correlation ID HTTP header](#193-correlation-id-http-header)
  * [19.4 Validation errors](#194-validation-errors)
  * [19.5 Warning responses](#195-warning-responses)
* [20 Migrating to Remote Database Host](#20-migrating-to-remote-database-host)
* [21 Adding command line arguments](#21-adding-command-line-arguments)
* [22 Additional Security Hardening](#22-additional-security-hardening)
  * [22.1 Configuring account lockout](#221-configuring-account-lockout)
    * [22.1.1 Considerations and risks](#2211-considerations-and-risks)
    * [22.1.2 Account lockout examples](#2212-account-lockout-examples)
  * [22.2 Configuring password policies](#222-configuring-password-policies)
    * [22.2.1 Considerations and risks](#2221-considerations-and-risks)
  * [22.3 Ensuring User Account Security](#223-ensuring-user-account-security)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

This document describes the management and maintenance of an X-Road version 7 security server.


### 1.1 The X-Road Security Server

The main function of a security server is to mediate requests in a way that preserves their evidential value.

The security server is connected to the public Internet from one side and to the information system within the organization's internal network from the other side. In a sense, the security server can be seen as a specialized application-level firewall that supports the SOAP and REST protocols; hence, it should be set up in parallel with the organization's firewall, which mediates other protocols.

The security server is equipped with the functionality needed to secure the message exchange between a client and a service provider.

-   Messages transmitted over the public Internet are secured using digital signatures and encryption.

-   The service provider's security server applies access control to incoming messages, thus ensuring that only those users that have signed an appropriate agreement with the service provider can access the data.

To increase the availability of the entire system, the service user's and service provider's security servers can be set up in a redundant configuration as follows.

-   One service user can use multiple security servers in parallel to perform requests.

-   If a service provider connects multiple security servers to the network to provide the same services, the requests are load-balanced between the security servers.

-   If one of the service provider's security servers goes offline, the requests are automatically redirected to other available security servers.

The security server also depends on a central server, which provides the global configuration.

### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1.  <a id="Ref_ASiC" class="anchor"></a>\[ASiC\] ETSI TS 102 918, Electronic Signatures and Infrastructures (ESI); Associated Signature Containers (ASiC)

2.  <a id="Ref_CRON" class="anchor"></a>\[CRON\] Quartz Scheduler CRON expression,  
    <http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html>

3.  <a id="Ref_INI" class="anchor"></a>\[INI\] INI file,  
    <http://en.wikipedia.org/wiki/INI_file>

4.  <a id="Ref_JDBC" class="anchor"></a>\[JDBC\] Connecting to the Database,   
    <https://jdbc.postgresql.org/documentation/93/connect.html>

5.  <a id="Ref_JSON" class="anchor"></a>\[JSON\] Introducing JSON,  
    <http://json.org/>

6.  <a id="Ref_PR-MESS" class="anchor"></a>\[PR-MESS\] X-Road: Message Protocol v4.0. Document ID: [PR-MESS](../Protocols/pr-mess_x-road_message_protocol.md)

7.  <a id="Ref_SPEC-AL" class="anchor"></a>\[SPEC-AL\] X-Road: Audit log events. Document ID: [SPEC-AL](https://github.com/nordic-institute/X-Road/blob/master/doc/Architecture/spec-al_x-road_audit_log_events.md)

8.  <a id="Ref_PR-OPMON" class="anchor"></a>\[PR-OPMON\] X-Road: Operational Monitoring Protocol. Document ID: [PR-OPMON](../OperationalMonitoring/Protocols/pr-opmon_x-road_operational_monitoring_protocol_Y-1096-2.md)

9.  <a id="Ref_PR-OPMONJMX" class="anchor"></a>\[PR-OPMONJMX\] X-Road: Operational Monitoring JMX Protocol. Document ID: [PR-OPMONJMX](../OperationalMonitoring/Protocols/pr-opmonjmx_x-road_operational_monitoring_jmx_protocol_Y-1096-3.md)

10. <a id="Ref_UG-OPMONSYSPAR" class="anchor"></a>\[UG-OPMONSYSPAR\] X-Road: Operational Monitoring System Parameters. Document ID: [PR-OPMONSYSPAR](../OperationalMonitoring/Manuals/ug-opmonsyspar_x-road_operational_monitoring_system_parameters_Y-1099-1.md)

11. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] X-Road: Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)

12. <a id="Ref_JMX" class="anchor"></a>\[JMX\] Monitoring and Management Using JMX Technology,  
    <http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html>

13. <a id="Ref_ZABBIX-GATEWAY" class="anchor"></a>\[ZABBIX-GATEWAY\] Zabbix Java Gateway,  
    <https://www.zabbix.com/documentation/3.0/manual/concepts/java>

14. <a id="Ref_ZABBIX-JMX" class="anchor"></a>\[ZABBIX-JMX\] Zabbix JMX Monitoring,  
    <https://www.zabbix.com/documentation/3.0/manual/config/items/itemtypes/jmx_monitoring>

15. <a id="Ref_ZABBIX-API" class="anchor"></a>\[ZABBIX-API\] Zabbix API,  
    <https://www.zabbix.com/documentation/3.0/manual/api>

16. <a id="Ref_ARC-ENVMON" class="anchor"></a>\[ARC-ENVMON\] X-Road: Environmental Monitoring Architecture. Document ID: [ARC-ENVMON](../EnvironmentalMonitoring/Monitoring-architecture.md).

17. <a id="Ref_PR-ENVMONMES" class="anchor"></a>\[PR-ENVMONMES\] X-Road: Environmental Monitoring Messages. Document ID: [PR-ENVMONMES](../EnvironmentalMonitoring/Monitoring-messages.md).

18. <a id="Ref_MONITORING_XSD" class="anchor"></a>\[MONITORING_XSD\] X-Road XML schema for monitoring extension. [monitoring.xsd](../../src/addons/proxymonitor/common/src/main/resources/monitoring.xsd).

19. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

20. <a id="Ref_PR-META" class="anchor"></a>\[PR-META\] X-Road: Service Metadata Protocol. Document ID: [PR-META](../Protocols/pr-meta_x-road_service_metadata_protocol.md).

21. <a id="Ref_PR-MREST" class="anchor"></a>\[PR-MREST\] X-Road: Service Metadata Protocol for REST. Document ID: [PR-MREST](../Protocols/pr-mrest_x-road_service_metadata_protocol_for_rest.md).

22. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR](../Manuals/ug-syspar_x-road_v6_system_parameters.md).

23. <a id="Ref_REST_UI-API" class="anchor"></a>\[REST_UI-API\] X-Road Security Server Admin API OpenAPI Specification, <https://github.com/nordic-institute/X-Road/blob/develop/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml>.

24. <a id="Ref_GnuPG" class="anchor"></a>\[GnuPG\] The GNU Privacy Guard, <https://gnupg.org>.

25. <a id="Ref_UG-SIGDOC" class="anchor"></a>\[UG-SIGDOC\] X-Road: Signed Document Download and Verification Manual. Document ID: [UG-SIGDOC](../Manuals/ug-sigdoc_x-road_signed_document_download_and_verification_manual.md).


## 2 User Management


### 2.1 User Roles

Security servers support the following user roles:

-   <a id="xroad-security-officer" class="anchor"></a>**Security Officer** (`xroad-security-officer`) is responsible for the application of the security policy and security requirements, including the management of key settings, keys, and certificates.

-   <a id="xroad-registration-officer" class="anchor"></a>**Registration Officer** (`xroad-registration-officer`) is responsible for the registration and removal of security server clients.

-   <a id="xroad-service-administrator" class="anchor"></a>**Service Administrator** (`xroad-service-administrator`) manages the data of and access rights to services

-   <a id="xroad-system-administrator" class="anchor"></a>**System Administrator** (`xroad-system-administrator`) is responsible for the installation, configuration, and maintenance of the security server.

-   <a id="xroad-securityserver-observer" class="anchor"></a>**Security Server Observer** (`xroad-securityserver-observer`) can view the status of the security server without having access rights to edit the configuration. This role can be used to offer users read-only access to the security server admin user interface.

One user can have multiple roles and multiple users can be in the same role. Each role has a corresponding system group, created upon the installation of the system.

Henceforth each applicable section of the guide indicates, which user role is required to perform a particular action. For example:

**Access rights:** [Security Officer](#xroad-security-officer)

If the logged-in user does not have a permission to carry out a particular task, the button that would initiate the action is hidden (and neither is it possible to run the task using its corresponding keyboard combinations or mouse actions). Only the permitted data and actions are visible and available to the user.


### 2.2 Managing the Users

User management is carried out on the command line in root user permissions.

#### 2.2.1 Adding and Removing Users

To add a new user, enter the command:

    adduser username

To grant permissions to the user you created, add it to the corresponding system groups, for example:

    adduser username xroad-security-officer
    adduser username xroad-registration-officer
    adduser username xroad-service-administrator
    adduser username xroad-system-administrator
    adduser username xroad-securityserver-observer

To remove user permission, remove the user from the corresponding system group, for example:

    deluser username xroad-security-officer

Modified user permissions are applied only after a user does a new login.

To remove a user, enter:

    deluser username

### 2.3 Managing API Keys

API keys are used to authenticate API calls to Security Server's management REST API. API keys are associated with roles that define the permissions granted to the API key. If the API key is lost, it can be revoked.

#### 2.3.1 Creating a new API key

**Access rights**

-   All activities: [System Administrator](#xroad-system-administrator)

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  In the opening view, select **API KEYS** tab.

3.  In the opening view, click **CREATE API KEY**. In the wizard that opens

    1. Select the roles you want to be associated with the API key. Click **NEXT**.
    
    2. Click **CREATE KEY**. The API key, API key id and assigned roles will be displayed in the view. **The API key will only be displayed this once so save it in a secure location**.
    
    3. Click **FINISH**.

#### 2.3.2 Editing the roles of an API key

**Access rights**

-   All activities: [System Administrator](#xroad-system-administrator)

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  In the opening view, select **API KEYS** tab.

3.  In the opening view, in the API key list, locate the API key you want to edit and click **Edit** at the end of the API key row. In the popup that opens

    1. Select the roles you want to be associated with the API key. Click **SAVE**.
    
#### 2.3.3 Revoking an API key

**Access rights**

-   All activities: [System Administrator](#xroad-system-administrator)

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  In the opening view, select **API KEYS** tab.

3.  In the opening view, in the API key list, locate the API key you want to revoke and click **Revoke Key** at the end of the API key row. In the dialog that opens click **YES**.


## 3 Security Server Registration

To use a security server for mediating (exchanging) messages, the security server and its owner must be certified by a certification service provider approved by the X-Road governing authority, and the security server has to be registered in the X-Road governing authority.


### 3.1 Configuring the Signing Key and Certificate for the Security Server Owner

The signing keys used by the security servers for signing X-Road messages can be stored on software or hardware based (a Hardware Security Module or a smartcard) security tokens, according to the security policy of the X-Road instance.

Depending on the certification policy, the signing keys are generated either in the security server or by the certification service provider. Sections [3.1.1](#311-generating-a-signing-key-and-certificate-signing-request) and [3.1.2](#312-importing-a-certificate-from-the-local-file-system) describe the actions necessary to configure the signing key and certificate in case the key is generated in the security server. Section [3.1.3](#313-importing-a-certificate-from-a-security-token) describes the importing of the signing key and certificate in case the key is generated by the certification service provider.

The **background colors** of the devices, keys and certificate are explained in Section [5.1](#51-availability-states-of-security-tokens).


#### 3.1.1 Generating a Signing Key and Certificate Signing Request

**Access rights:**

-   All activities: [Security Officer](#xroad-security-officer)

-   All activities except logging into the key device: [Registration Officer](#xroad-registration-officer)

-   Logging in to the key device: [System Administrator](#xroad-system-administrator)

To generate a Signing key and a Certificate Signing Request, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  If you are using a hardware security token, ensure that the device is connected to the security server. The device information must be displayed in the **SIGN AND AUTH KEYS** table.

3.  To log in to the token, click **LOG IN** on the token's row in the table and enter the PIN code. Once the correct PIN is entered, the **LOG IN** button changes to **LOG OUT**.

4.  To generate a signing key and CSR for it, expand the token's information by clicking the caret next to the token name and click **ADD KEY**. In the wizard that opens 
    
    1. Define a label for the newly created signing key (not mandatory) and click **NEXT**. 
    
    2. In the dialog page that opens
    
       1. Select the certificate usage policy from the **Usage** drop down list (SIGNING for signing certificates)
    
       2. Select the X-Road member the certificate will be issued for from the **Client** drop-down list
    
       3. Select the issuer of the certificate from the **Certification Service** drop-down list
    
       4. Select the format of the certificate signing request (PEM or DER) from the **CSR Format** drop-down list, according to the certification service provider's requirements
    
       5. Click **CONTINUE**
    
    3. In the dialog that opens 
    
       1. Review the certificate owner's information that will be included in the CSR and fill in the empty fields, if needed
    
       2. Click **GENERATE CSR**
    
       3. Click **DONE**

After the generation of the CSR, a "Request" record is added under the key's row in the table, indicating that a certificate signing request has been created for this key. The record is added even if the request file was not saved to the local file system.

**To certify the signing key, transmit the certificate signing request to the approved certification service provider and accept the signing certificate created from the certificate signing request.**


#### 3.1.2 Importing a Certificate from the Local File System

**Access rights:** [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

To import the signing certificate to the security server, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click **IMPORT CERT.**.

4.  Locate the certificate file from the local file system and click **OK**. After importing the certificate, the "Request" record under the signing key's row is replaced with the information from the imported certificate. By default, the signing certificate is imported in the "Registered" state.


#### 3.1.3 Importing a Certificate from a Security Token

**Access rights:** [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

To import a certificate from a security token, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Make sure that a key device containing the signing key and the signing certificate is connected to the security server. The device and the keys and certificates stored on the device must be displayed in the **SIGN AND AUTH KEYS** view.

4.  To log in to the security token, click **LOG IN** on the token's row in the table and enter the PIN. Once the correct PIN is entered, the **LOG IN** button changes to **LOG OUT**.

5.  Click the **Import** button on the row of the certificate. By default, the certificate is imported in the "Registered" state.


### 3.2 Configuring the Authentication Key and Certificate for the Security Server

The **background colors** of the devices, keys and certificate are explained in Section [5.1](#51-availability-states-of-security-tokens).


#### 3.2.1 Generating an Authentication Key

**Access rights**

-   All activities: [Security Officer](#xroad-security-officer)

-   Logging in to the key device: [System Administrator](#xroad-system-administrator)

**The security server's authentication keys can only be generated on software security tokens.**

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  To log in to the software token, click **LOG IN** on the token's row in the table and enter the token's PIN code. Once the correct PIN is entered, the **LOG IN** button changes to **LOG OUT**.

3.  Show more details about the token by clicking the caret next to the token name.

4.  To generate an authentication key and CSR for it, click the **ADD KEY** button below the token row. In the wizard that opens 
    
    1. Define a label for the newly created authentication key (not mandatory) and click **NEXT**. 
    
    2. In the dialog page that opens
    
       1. Select the certificate usage policy from the **Usage** drop down list (AUTHENTICATION for authentication certificates)
    
       2. Select the issuer of the certificate from the **Certification Service** drop-down list
    
       3. Select the format of the certificate signing request (PEM or DER) from the **CSR Format** drop-down list, according to the certification service provider's requirements
    
       4. Click **CONTINUE**
    
    3. In the dialog that opens 
    
       1. Review the certificate owner's information that will be included in the CSR and fill in the empty fields, if needed
    
       2. Click **GENERATE CSR**
    
       3. Click **DONE**


#### 3.2.2 Generating a Certificate Signing Request for an Authentication Key

**Access rights:** [Security Officer](#xroad-security-officer)

To generate a certificate signing request (CSR) for the authentication key, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  On the row of the desired key, click **Generate CSR**. In the dialog that opens

    2.1  Select the certificate usage policy from the **Usage** drop down list (AUTH for authentication certificates);

    2.2  select the issuer of the certificate from the **Certification Service** drop-down list;

    2.3  select the format of the certificate signing request (PEM or DER), according to the certification service provider's requirements

    2.4  click **CONTINUE**;

3.  In the form that opens, review the information that will be included in the CSR and fill in the empty fields, if needed.

4.  Click **GENERATE CSR** to complete the generation of the CSR and save the prompted file to the local file system.

5. Click **DONE**

After the generation of the CSR, a "Request" record is added under the key's row in the table, indicating that a certificate signing request has been created for this key. The record is added even if the request file was not saved to the local file system.

**To certify the authentication key, transmit the certificate signing request to the approved certification service provider and accept the authentication certificate created from the certificate signing request.**


#### 3.2.3 Importing an Authentication Certificate from the Local File System

**Access rights:** [Security Officer](#xroad-security-officer)

To import the authentication certificate to the security server, follow these steps.

1.  In the **Navigation tabs**, select Keys and Certificates.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click **Import certificate**.

4.  Locate the certificate file from the local file system and click **OK**. After importing the certificate, the "Request" record under the authentication key's row is replaced with the information from the imported certificate. By default, the certificate is imported in the "Saved" (see Section [5.2.2](#522-registration-states-of-the-authentication-certificate)) and "Disabled" states (see Section [5.3](#53-validity-states-of-certificates)).


### 3.3 Registering the Security Server in the X-Road Governing Authority

To register the security server in the X-Road governing authority, the following actions must be completed.

-   The authentication certificate registration request must be submitted from the security server (see [3.3.1](#331-registering-an-authentication-certificate)).

-   A request for registering the security server must be submitted to the X-Road governing authority according to the organizational procedures of the X-Road instance.

-   The registration request must be approved by the X-Road governing authority.


#### 3.3.1 Registering an Authentication Certificate

**Access rights:** [Security Officer](#xroad-security-officer)

The security server's registration request is signed in the security server with the server owner's signing key and the server's authentication key. Therefore, ensure that the corresponding certificates are imported to the security server and are in a usable state (the tokens holding the keys are in logged in state and the OCSP status of the certificates is "good").

To submit an authentication certificate registration request, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click **Register** at the end of the desired certificate row. Note that the certificate must be in "Saved" state.

4.  In the dialog that opens, enter the security server's public DNS name or its external IP address and click **ADD**.

On submitting the request, the message "Certificate registration request successful" is displayed, and the authentication certificate's state is set to "Registration in process".

After the X-Road governing authority has accepted the registration, the registration state of the authentication certificate is set to "Registered" and the registration process is completed.

### 3.4 Changing the Security Server Owner

**Access rights:** [Registration Officer](#xroad-registration-officer)

To change the security server owner, two registered Owner members must be available. If a registered member is already available, jump directly to step 3.

To add a new member and change it to Owner member, the following actions must be completed.

1.  Add a new Owner member to the security server

    1.1 On the **CLIENTS** view, select **ADD MEMBER**.
    
    1.2 In the opening wizard, Select the new Owner member from the list of security server clients
    
    1.3 Add the selected member
    
    Note: Signing Key and Certificate must be configured for the new Owner member. If needed, the wizard will automatically show the dedicated steps for Key and Certificate configuration to collect the needed information.
    
2.  Register the new member

    2.1 On the **CLIENTS** view, locate the new member in the Clients list and click **Register** in the corresponding row
    
    2.2 In the opening dialog, click **Register**. A registeration request is sent to the X-Road Governing Authority
    
    Note: Once the request is approved, the new member appears as "Registered" - it can be set as Owner member.

3.  Request a change of the security server owner

    3.1 On the **CLIENTS** view, locate the new member and click its name to open the member's detail view
    
    3.2 In the detail view, click **MAKE OWNER**
    
    1.3 In the opening dialog, click **MAKE OWNER**. An owner change request is sent to the X-Road Governing Authority
    
Once the owner change request is approved, the new member will be automatically shown as the security server Owner member.

- A new member must be added to the security server (see [4.2](#42-adding-a-security-server-client)). If needed, specify the token on which the member is configured

- If not yet available, a Signing Key and Certificate must be configured for the new member (see [4.4](#44-configuring-a-signing-key-and-certificate-for-a-security-server-client)).

- The new member must be registered in the X-Road Governing Authority (see [4.5](#45-registering-a-security-server-client-in-the-x-road-governing-authority)).

- The security server owner change request must be submitted from the security server. To submit an owner change request follow these steps.

  1. In the **Member Detail view** click **MAKE OWNER**.

  2. Click **MAKE OWNER** to submit a change request.

- The change request is sent to the X-Road governing authority according to the organizational procedures of the X-Road instance.

- Once the owner change request is approved by the X-Road governing authority, the member will automatically become the Owner Member.

- New Authentication Key and Certificate should be configured for the new security server owner (see [3.2](#32-configuring-the-authentication-key-and-certificate-for-the-security-server)).

## 4 Security Server Clients

**Important: to use or provide X-Road services, a security server client needs to be certified by a certification service provider approved by the X-Road governing authority, and the association between the client and the security server used by the client must be registered at the X-Road governing authority.**

**This section does not address managing the owner to a security server.** The owner's information has been already added to the security server upon the installation, and registered upon the security server's registration. The owner's registration status can be looked up by selecting **CLIENTS** on the main menu. In the list the item with text "(Owner)" after the name is security server's owner. Before the registration of the security server, the owner is in the "Saved" state and after the completion of the registration process, in the "Registered" state.

The registration of the security server's owner does not extend to the owner's subsystems. The subsystems must be registered as individual clients.


### 4.1 Security Server Client States

The security server distinguishes between the following client states.

![](img/ug-ss_saved.png) **Saved** – the client's information has been entered and saved into the security server's configuration (see [4.2](#42-adding-a-security-server-client)), but the association between the client and the security server is not registered in the X-Road governing authority. (If the association is registered in the central server prior to the entry of data, the client will move to the "Registered" state upon data entry.) From this state, the client can move to the following states:

-   "Registration in progress", if a registration request for the client is submitted from the security server (see [4.5.1](#451-registering-a-security-server-client));

-   "Deleted", if the client's information is deleted from the security server configuration (see [4.6.2](#462-deleting-a-client)).

![](img/ug-ss_registration_in_progress.png) **Registration in progress** – a registration request for the client is submitted from the security server to the central server, but the association between the client and the security server is not yet approved by the X-Road governing authority. From this state, the client can move to the following states:

-   "Registered", if the association between the client and the security server is approved by the X-Road governing authority (see [4.4.1](#441-registering-a-security-server-client));

-   "Deletion in progress", if a client deletion request is submitted from the security server (see [4.6.1](#461-unregistering-a-client)).

![](img/ug-ss_registered.png) **Registered** – the association between the client and the security server has been approved in the X-Road governing authority. In this state, the client can provide and use X-Road services (assuming all other prerequisites are fulfilled). From this state, the client can move to the following states:

-   "Global error", if the association between the client and the security server has been revoked by the X-Road governing authority;

-   "Deletion in progress", if a client deletion request is submitted from the security server (see [4.6.1](#461-unregistering-a-client)).

![](img/ug-ss_global_error.png) **Global error** – the association between the client and the security server has been revoked in the central server. From this state, the client can move to the following states:

-   "Registered", if the association between the client and the security server has been restored in the central server (e.g., the association between the client and the security server was lost due to an error);

-   "Deleted", if the client's information is deleted from the security server's configuration (see [4.6.2](#452-deleting-a-client)).

![](img/ug-ss_deletion_in_progress.png) **Deletion in progress** – a client deletion request has been submitted from the security server. From this state, the client can move to the following state:

-   "Deleted", if the client's information is deleted from the security server's configuration (see [4.6.2](#452-deleting-a-client)).

**Deleted** – the client's information has been deleted from the security server's configuration.


### 4.2 Adding a Security Server Client

**Access rights:** [Registration Officer](#xroad-registration-officer)

Follow these steps.

1.  In the **CLIENTS** view, click **ADD CLIENT**.

2.  In the wizard that opens
    
    1. Client details page: Select an existing client from the Global list by pressing **SELECT CLIENT** or specify the details of the Client to be added manually and click **NEXT**
    
    2. Token page: Select the token where you want to add the SIGN key for the new Client. Click **NEXT**
    
    3. Sign key page: Define a label (optional) for the newly created SIGN key and click **NEXT**
    
    4. CSR details page: Select the Certification Authority (CA) that will issue the certificate in **Certification Service** field and format of the certificate signing request according to the CA's requirements in the **CSR Format** field. Click **NEXT**.
    
    5. Generate CSR page: Define **Organization Name (O)** and click **NEXT**
    
    6. Finish page: click **SUBMIT** and the new client will be added to the Clients list and the new key and CSR will appear in the Keys and Certificates view.

The new client is added to the list of security server clients in the "Saved" state.

### 4.3 Adding a Security Server Member Subsystem

**Access rights:** [Registration Officer](#xroad-registration-officer)

Follow these steps.

1.  In the **CLIENTS** view in the client list, locate the X-Road member you want to add a subsystem to and click **Add Subsystem** at the end of the row.

2.  In the wizard that opens
    
    2.1. Select an existing subsystem from the Global list by pressing **SELECT SUBSYSTEM** or specify the **Subsystem Code** manually
    
    2.2. If you wish to register the new subsystem immediately, check the **Register subsystem** checkbox and then click **ADD SUBSYSTEM**.
    
    (2.3.) If you checked the **Register subsystem** checkbox, a popup will appear asking whether you wish to register the subsystem immediately. In the popup, click **YES**.
    
The new subsystem is added to the list of security server clients in the "Saved" state.

### 4.4 Configuring a Signing Key and Certificate for a Security Server Client

A signing key and certificate must be configured for the security server client to sign messages exchanged over the X-Road. In addition, a signing key and certificate are required for registering a security server client.

Certificates are not issued to subsystems; therefore, the certificate of the subsystem's owner (that is, an X-Road member) is used for the subsystem.

All particular X-Road member's subsystems that are registered in the same security server use the same signing certificate for signing messages. Hence, if the security server already contains the member's signing certificate, it is not necessary to configure a new signing key and/or certificate when adding a subsystem of that member.

The process of configuring the signing key and certificate for a security server client is the same as for the security server owner. The process is described in Section [3.1](#31-configuring-the-signing-key-and-certificate-for-the-security-server-owner).


### 4.5 Registering a Security Server Client in the X-Road Governing Authority

To register a security server client in the X-Road governing authority, the following actions must be completed.

-   A signing key and certificate must be configured for the member that owns the subsystem to be registered as a the security server client (see [4.4](#44-configuring-a-signing-key-and-certificate-for-a-security-server-client)).

-   The security server client registration request must be submitted from the security server (see [4.5.1](#451-registering-a-security-server-client)).

-   A request for registering the client must be submitted to the X-Road governing authority according to the organizational procedures of the X-Road instance.

-   The registration request must be approved by the X-Road governing authority.


#### 4.5.1 Registering a Security Server Client

**Access rights:** [Registration Officer](#xroad-registration-officer)

To submit a client registration request follow these steps.

1.  In the **CLIENTS** view.

2.  Click **Register** button on the row that contains the client you wish to register. 

3.  Click **YES** to submit the request.

On submitting the request, the message "Request sent" is displayed, and the client's state is set to "Registration in process".

After the X-Road governing authority has accepted the registration, the state of the client is set to "Registered" and the registration process is completed.


### 4.6 Deleting a Client from the Security Server

If a client is deleted from the security server, all the information related to the client is deleted from the server as well – that is, the WSDLs, services, access rights, and, if necessary, the certificates.

When one of the clients is deleted, it is not advisable to delete the signing certificate if the certificate is used by other clients registered to the security server, e.g., other subsystems belonging the same X-Road member as the deleted subsystem.

A client registered or submitted for registration in the X-Road governing authority (indicated by the "Registered" or "Registration in progress" state) must be unregistered before it can be deleted. The unregistering event sends a security server client deletion request from the security server to the central server.


#### 4.6.1 Unregistering a Client

**Access rights:** [Registration Officer](#xroad-registration-officer)

To unregister a client, follow these steps.

1.  In the **CLIENTS** view click the name of client that you wish to remove from the server

2.  In the window that opens, click **UNREGISTER** and then click **YES**. The security server automatically sends a client deletion request to the X-Road central server, upon the receipt of which the association between the security server and the client is revoked.

3.  Next, a notification is displayed about unregistering client. Now the client is moved to the "Deletion in progress" state, wherein the client cannot mediate messages and cannot be registered again in the X-Road governing authority.

*Note:* It is possible to unregister a registered client from the central server without sending a deletion request through the security server. In this case, the security server's administrator responsible for the client must transmit a request containing information about the client to be unregistered to the central server's administrator. If the client has been deleted from the central server without a prior deletion request from the security server, the client is shown in the "Global error" state in the security server.


#### 4.6.2 Deleting a Client

**Access rights:** [Registration Officer](#xroad-registration-officer)

A security server client can be deleted if its state is "Saved", "Global error" or "Deletion in progress". Clients that are in states "Registered" or "Registration in progress" need to be unregistered before they can be deleted (see Section [4.6.1](#461-unregistering-a-client)).

To delete a client, follow these steps.

1.  In the **CLIENTS** view click the name of the client you wish to remove from the security server.

2.  In the window that opens, click **DELETE** and then click **YES**. If there are no users for the signature key nor for the certificate associated then an option is presented to delete the client's certificates. To delete the certificates, click **YES** again.


## 5 Security Tokens, Keys, and Certificates


### 5.1 Availability States of Security Tokens

**Notice that the colors were introduced in version 6.25.0**

To display the availability of tokens, the following colors and labels are used in the "Keys and Certificates" view.

-   **Red** text and a label **Not saved** – the token is available to the security server, but it's information has not been saved to the security server configuration. For example, a smartcard could be connected to the server, but the certificates on the smartcard may not have been imported to the server. The user cannot interact with the token or it's content.

-   **Red** text and a label **Blocked** – the token is available to the security server and it's information has been saved to the security server's configuration but the token is unavailable. The user cannot interact with the token or it's content.

-   **Gray** text and a label **Inactive** – the token is not available for the security server. The user cannot interact with the token or it's content.

-   **Black** text and a **LOG IN** button – the token is logged out. The user must log in the token before interacting the content.

-   **Black** text and a **LOG OUT** button – the token is logged in. The user can interact with the token and it's content.

**Caution:** The key device's and key's information is automatically saved to the configuration when a certificate associated with either of them is imported to the security server, or when a certificate signing request is generated for the key. Similarly, the key device's and key's information is deleted from the security server configuration automatically upon the deletion of the last associated certificate and/or certificate signing request.


### 5.2 Registration States of Certificates

Registration states indicate if and how a certificate can be used in the X-Road system. In the "Keys and Certificates" view, a certificate's registration states (except "Deleted"  for certificates stored on soft token key) are displayed in the "Status" column.


#### 5.2.1 Registration States of the Signing Certificate

A security server signing certificate can be in one of the following registration states.

-   **Registered** – the certificate has been imported to the security server and saved to its configuration. A signing certificate in a "Registered" state can be used for signing X-Road messages.

-   **Deleted** – the certificate has been deleted from the server configuration. If the certificate is in the "Deleted" state and stored on a soft token key, the certificate will not be displayed in the table. If the certificate is in the "Deleted" state and stored on a hardware key device connected to the security server, the certificate status will be displayed with a **red circle** and a text **ONLY IN TOKEN**.


#### 5.2.2 Registration States of the Authentication Certificate

A security server authentication certificate can be in one of the following registration states.

**Saved** – the certificate has been imported to the security server and saved to its configuration, but the certificate has not been submitted for registration. From this state, the certificate can move to the following states:

-   "Registration in progress", if the authentication certificate registration request is sent from the security server to the central server (see [3.3.1](#331-registering-an-authentication-certificate));

-   "Deleted", if the authentication certificate's information is deleted from the security server configuration (see Section [5.6](#56-deleting-a-certificate)). Notice that after the certificate is deleted, it will not be displayed in the table anymore.

**Registration in progress** – an authentication certificate registration request has been created and sent to the central server, but the association between the certificate and the security server has not yet been approved. From this state, the certificate can move to the following states:

-   "Registered", if the association between the authentication certificate and the security server is approved by the X-Road governing authority (see [3.3](#33-registering-the-security-server-in-the-x-road-governing-authority));

-   "Deletion in progress", if the certificate deletion request has been submitted to the central server (see [5.6.1](#561-unregistering-an-authentication-certificate)). The user can force this state transition even if the sending of the authentication certificate deletion request fails.

**Registered** – the association between the authentication certificate and the security server has been approved in the central server. An authentication certificate in this state can be used to establish a secure data exchange channel for exchanging X-Road messages. From this state, the certificate can move to the following states:

-   "Global error", if the association between the authentication certificate and the security server has been revoked in the central server;

-   "Deletion in progress", if the certificate deletion request has been transmitted to the central server (see [5.6.1](#561-unregistering-an-authentication-certificate)). The user can force this state transition even if the sending of the authentication certificate deletion request fails.

**Global error** – the association between the authentication certificate and the security server has been revoked in the central server. From this state, the certificate can move to the following states:

-   "Registered", if the association between the authentication certificate and the security server has been restored in the central server (e.g., the association between the client and the security server was lost due to an error);

-   "Deleted", if the authentication certificate's information is deleted from the security server configuration (see [5.6](#56-deleting-a-certificate)). Notice that after the certificate is deleted, it will not be displayed in the table anymore.

**Deletion in progress** – an authentication certificate registration request has been created for the certificate and sent to the central server. From this state, the certificate can be deleted. If the certificate has been deleted from the security server configuration, it will not be displayed in the table anymore.


### 5.3 Validity States of Certificates

Validity states indicate if and how a certificate can be used independent of the X-Road system. In the "Keys and Certificates" view, the certificate's validity states are displayed in the "OCSP" column. Validity states (except "Disabled") are displayed for certificates that are in the "Registered" registration state.

A security server certificate can be in one of the following validity states.

-   **Unknown** (validity information missing) – the certificate does not have a valid OCSP response (the OCSP response validity period is set by the X-Road governing authority) or the last OCSP response was either "unknown" (the responder doesn't know about the certificate being requested) or an error.

-   **Suspended** – the last OCSP response about the certificate was "suspended".

-   **Good** (valid) – the last OCSP response about the certificate was "good". Only certificates in the "good" (valid) state can be used to sign messages or establish a connection between security servers.

-   **Expired** – the certificate's validity end date has passed. The certificate is not active and OCSP queries are not performed about it.

-   **Revoked** – the last OCSP response about the certificate was "revoked". The certificate is not active and OCSP queries are not performed about it.

-   **Disabled** – the user has marked the certificate as disabled. The certificate is not active and OCSP queries are not performed about it.


### 5.4 Activating and Disabling the Certificates

**Access rights**

-   For authentication certificates: [Security Officer](#xroad-security-officer)

-   For signing certificates: [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

Disabled certificates are not used for signing messages or for establishing secure channels between security servers (authentication). If a certificate is disabled, its status in the "OCSP" column in the "Keys and Certificates" table is "Disabled".

To activate or disable a certificate, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  To activate a certificate, click on the desired certificate's name.

    3.1 In the opening **Certificate** dialog, click **Activate**. To deactivate a certificate, click **DISABLE** in the **Certificate** dialog. 


### 5.5 Configuring and Registering an Authentication key and Certificate

A Security server can have multiple authentication keys and certificates (e.g., during authentication key change).

The process of configuring another authentication key and certificate is described in Section [3.2](#32-configuring-the-authentication-key-and-certificate-for-the-security-server).

The process of registering an authentication certificate is described in Section [3.3.1](#331-registering-an-authentication-certificate).


### 5.6 Deleting a Certificate

An authentication certificate registered or submitted for registration in the X-Road governing authority (indicated by the "Registered" or "Registration in progress" state) must be unregistered before it can be deleted. The unregistering event sends an authentication certificate deletion request from the security server to the central server.


#### 5.6.1 Unregistering an Authentication Certificate

**Access rights:** [Security Officer](#xroad-security-officer)

To unregister an authentication certificate, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click on an authentication certificate that is in the state "Registered" or "Registration in progress".

    3.1 In the opening **Certificate** dialog, click **UNREGISTER**.

    Next, an authentication certificate deletion request is automatically sent to the X-Road central server, upon the receipt of which the associated authentication certificate is deleted from the central server. If the request was successfully sent, the message "Certificate unregistration request sent successfully" is displayed and the authentication certificate is moved to the "Deletion in progress" state.

A registered authentication certificate can be deleted from the central server without sending a deletion request through the security server. In this case, the security server's administrator must transmit a request containing information about the authentication certificate to be deleted to the central server's administrator. If the authentication certificate has been deleted from the central server without a deletion request from the security server, the certificate is shown in the "Global error" state in the security server.


#### 5.6.2 Deleting a Certificate or a certificate Signing Request notice

**Access rights**

-   For authentication certificates: [Security Officer](#xroad-security-officer)

-   For signing certificates: [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

An authentication certificate saved in the system configuration can be deleted if its state is "Saved", "Global error" or "Deletion in progress". The signing certificate and request notices can always be deleted from the system configuration.

**If a certificate is stored on a hardware security token, then the deletion works on two levels:**

-   if the certificate is saved in the server configuration, then the deletion **deletes the certificate from server configuration**, but not from the security token;

-   if the certificate is not saved in the server configuration (certificate's status has a red circle and status is "STORED IN TOKEN"), then the deletion deletes the certificate from the security token (assuming the token supports this operation).

**To delete a certificate, follow these steps.**

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click on the certificate that you want to delete.

    3.1 In the opening **Certificate** dialog, click **DELETE**. Confirm the deletion by clicking **YES**.

**To delete a certificate signing request notice (CSR), follow these steps.**

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  At the end of the desired CSR row click **Delete CSR**. Confirm the deletion by clicking **YES**.

### 5.7 Deleting a Key

**Warning:** Deleting a key from the server configuration also deletes all certificates (and certificate signing request notices) associated with the key.

**Access rights**

-   For authentication keys: [Security Officer](#xroad-security-officer)

-   For signing keys: [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

-   For keys without a role: [Security Officer](#xroad-security-officer), [Registration Officer](#xroad-registration-officer)

To delete a key, follow these steps.

1.  In the **Navigation tabs**, select **KEYS AND CERTIFICATES**.

2.  Show more details about a token by clicking the caret next to the token name.

3.  Click on the desired Key.

    3.1 In the opening **Key** dialog, click **DELETE**. Confirm the deletion of the key (and its associated certificates) by clicking **YES**.


## 6 X-Road Services

X-Road supports both SOAP and REST services. The services are managed on two levels:

-   the addition, deletion, and deactivation of services is carried out on the WSDL / REST API / OpenAPI 3 level;

-   the service address, internal network connection method, and the service timeout values are configured at the service level for SOAP services and at the API level for REST / OpenAPI 3 services. In addition, for SOAP / WSDL, it is easy to extend the configuration of one service to all the other services.


### 6.1 Adding a service description

**Access rights:** [Service Administrator](#xroad-service-administrator)

#### 6.1.1 SOAP

When a new WSDL file is added, the security server reads service information from it and displays the information in the table of services. The service code, title and address are read from the WSDL.

**To add a WSDL**, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client for which you wish to add WSDL to and click the **SERVICES** tab. 

3.  Click **ADD WSDL**, enter the WSDL address in the dialog that opens and click **ADD**. Once the window is closed, the WSDL and the information about the services it contains are added to the client. By default, the WSDL is added in disabled state (see [6.3](#63-enabling-and-disabling-a-service-description)).

**To see a list of services contained in the WSDL**

-   click the caret next to the WSDL service url to expand the list.

#### 6.1.2 REST

After a new REST service is added, the security server displays text "REST" and url for that service.

**To add a REST service**, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client for which you wish to add REST service to and click the **SERVICES** tab. 

3.  Click **ADD REST**. Select whether the URL type is "REST API Base Path" or "OpenAPI 3 Description". Enter the url and service code in the window that opens and click **ADD**.

4.  Once the window is closed, the url and the service code are added to the service list. If the added URL type was OpenAPI 3 description, the service description is parsed and endpoints are added under the service. By default, the REST service is added in disabled state (see [6.3](#63-enabling-and-disabling-a-service-description)).

**To see the service details under the REST service**

-   click the caret on the REST service description row to expand the service details.

### 6.2 Refreshing a service description

**Access rights:** [Service Administrator](#xroad-service-administrator)

Upon refreshing, the security server reloads the service description file from the service description URL to the security server and checks the service information in the reloaded file against existing services. If the composition of services in the new service description has changed compared to the current version, a warning is displayed and you can either continue with the refresh or cancel.

To refresh the service description, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to refresh and click the **SERVICES** tab. 

2.  Click the arrow symbol in front of the WSDL or REST to be refreshed and click the **Refresh** button.

3.  If the new service description contains changes compared to the current service description in the security server, a warning is displayed. To proceed with the refresh, click **CONTINUE**.

When the service description is refreshed, the existing services' settings are not overwritten.


### 6.3 Enabling and Disabling a service description

**Access rights:** [Service Administrator](#xroad-service-administrator)

A disabled service description is displayed in the services' list with a disabled switch icon on the same row.

Services described by a disabled service description cannot be accessed by the service clients – if an attempt is made to access the service, an error message is returned, containing the information entered by the security server's administrator when the service description was disabled.

If a service description is enabled, the services described there become accessible to users. Therefore it is necessary to ensure that before enabling the service description, the parameters of all its services are correctly configured (see [6.6](#66-changing-the-parameters-of-a-service)).

To **enable** or **disable** a service description, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2. Click the switch icon on the same row with service WSDL or REST service you wish to enable or disable

(3.) If the service was disabled a popup will appear. In the popup, enter a Disable notice which is shown to clients who try to access any of the services in the service description, and click **OK**.


### 6.4 Changing the Address of a service description

**Access rights:** [Service Administrator](#xroad-service-administrator)

To change the service description address, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2. Click the link text containing the type of the service and its url in paranthesis

3.  In the dialog that opens you can edit the URL for all types of services and for REST services the service code can also be changed. Click **SAVE**. The service information updates accordingly (see section [6.2](#62-refreshing-a-service-description)).


### 6.5 Deleting a service description

**Access rights:** [Service Administrator](#xroad-service-administrator)

When a service description is deleted, all information related to the services described in the service description, including access rights, are deleted.

To delete a service description, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2. Click the link text containing the type of the service and its url in paranthesis. 

3. Click **DELETE** and confirm the deletion by clicking **YES** in the dialog that opens. 

### 6.6 Changing the Parameters of a Service

**Access rights:** [Service Administrator](#xroad-service-administrator)

Service parameters are

-   "Service URL" – the URL where requests targeted at the service are directed;

-   "Timeout (s)" – the maximum duration of a request to the database, in seconds;

-   "Verify TLS certificate" – toggles the verification of the certificate when a TLS connection is established. This option is used for two different scenarios:
    -   Between Security Server and service endpoint.
    -   Between Security Server and service description URL, when metaservices getWsdl or getOpenAPI are used for this subsystem and service. See \[[PR-META](#Ref_PR-META)\] and \[[PR-MREST](#Ref_PR-MREST)\].

To change service parameters, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2.  Click the arrow symbol in front of a REST or WSDL service and in the list that is displayed click the service code which you wish to edit.

3.  In the view that opens, configure the service parameters. To apply the selected parameter to all services described in the same service description, select the checkbox adjacent to this parameter in the **Apply to All in WSDL** column. To apply the configured parameters, click **SAVE**.


### 6.7 Managing REST Endpoints

**Access rights:** [Service Administrator](#xroad-service-administrator)

REST type service descriptions can contain API endpoints. The purpose of the endpoints is more fine-grained access control. More about that in chapter [7 Access Rights](#7-access-rights).

When URL type of the REST service is an OpenAPI 3 description, endpoints are parsed from the service description automatically. These endpoints cannot be manually updated or deleted. Additionally manual endpoints can be added as needed. When URL type is REST API base path, all the endpoints need to be created manually. Manually created endpoints can also be edited and deleted as needed.

To create API endpoint manually, follow these steps

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2.  Click the arrow symbol in front of a REST service and click the service code that is displayed.

3.  Click the **ENDPOINTS** tab and in the following view click **ADD ENDPOINTS**.

4.  In the dialog that opens fill in the HTTP Request method and path for the endpoint and click **ADD**


## 7 Access Rights

Access rights can be granted to the following access right subjects.

-   **An X-Road member's subsystem.**

-   **A global access rights group.** Global groups are created in the X-Road governing authority. If a group is granted an access right, it extends to all group members.

-   **A local access rights group.** To simplify access rights management, each client in the security server can create local access rights groups (see section [8](#8-local-access-right-groups)). If a group is granted an access right, it extends to all group members.

There are two options for managing access rights in a security server.

-   Service-based access rights management – if a single service needs to be opened/closed to multiple service clients (see [7.1](#71-changing-the-access-rights-of-a-service)).

-   Service client-based access rights management – if a single service client needs multiple services opened/closed (see [7.2](#72-adding-a-service-client)).

It is possible to define access rights on two levels for REST services:

-   REST service level
-   endpoint level

In general, a REST service usually has multiple endpoints. When access rights are defined on the service level, they apply to all the endpoints of the REST service. Instead, defining access rights on the endpoint level gives access to specific endpoint(s) only. The service level access rights support both service-based and service client-based access rights management. The endpoint level access rights support only service based access rights management.


### 7.1 Changing the Access Rights of a Service

**Access rights:** [Service Administrator](#xroad-service-administrator)

To change the access rights to a **service**, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2.  Click the arrow symbol in front of a service and click the service code that is displayed.

3.  In the window that opens, the access rights table displays information about all X-Road subsystems and groups that have access to the selected service.

4.  To add one or more access right subjects to the service, click **ADD SUBJECTS**. The subject search window appears. You can search among    all subsystems and global groups registered in the X-Road governing authority and among the security server client's local groups. Fill in optional filters for subjects and click **SEARCH**. Select one or more subjects from the list and click **ADD SELECTED**.

5.  To remove service access rights subjects, click **Remove** button on the respective row in the access rights table. To clear the access rights list (that is, remove all subjects), click **REMOVE ALL**.

To change access rights to an **endpoint**, follow there steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICES** tab. 

2.  Click the arrow symbol in front of a REST service and click the service code that is displayed.

3.  Click the **ENDPOINTS** tab and in the following views endpoints list click **Access Rights** on the respective row of an endpoint.

4.  To add one or more access right subjects to the endpoint, click **ADD SUBJECTS**. The subject search window appears. You can search among all subsystems and global groups registered in the X-Road governing authority and among the security server client's local groups. Fill in optional filters for subjects and click **SEARCH**. Select one or more subjects from the list and click **ADD SELECTED**.

5. To remove endpoint access rights subjects, click **Remove** button on the respective row in the access rights table and click **YES** in the confirmation dialog. To clear the access rights list (that is, remove all subjects), click **REMOVE ALL** and click **YES** in the confirmation dialog.


### 7.2 Adding a Service Client

**Access rights:** [Service Administrator](#xroad-service-administrator)

The service client view (**CLIENTS** -&gt; **SERVICE CLIENTS**) displays all the service level access rights subjects of the services mediated by this security server client. In other words, if an X-Road subsystem or group has been granted a service level access right to a service of this client, then the subject is shown in this view. Subjects that have been granted an endpoint level access right to a REST service, are not shown in the view.

To add a service client, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICE CLIENTS** tab. 

2.  Click **ADD SUBJECT**. In the following wizard that opens
    
    1. Select a subject (a subsystem, or a local or global group) to which you want to grant access rights to and click **NEXT**
    
    2. Select service(s) whose access rights you want to grant to the selected subject. Click **ADD SELECTED** to grant access rights to the selected services to this subject. Note that access rights to REST API endpoints can not be added using this view, those need to be added on **SERVICES** tab as described in [7.1](#71-changing-the-access-rights-of-a-service).

The subject is added to the list of service clients, after which the service clients view is displayed.


### 7.3 Changing the Access Rights of a Service Client

**Access rights:** [Service Administrator](#xroad-service-administrator)

To change the service client's access rights, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client containing service you wish to view and click the **SERVICE CLIENTS** tab. 

2.  In the view that opens click the name of a subject (a subsystem, or a local or global group) whose access rights you want to change

3.  In the window that opens, a list of services opened in the security server to the selected subject is displayed.

    - To add access rights to a service client, start by clicking **ADD SERVICE**. In the window that opens, select the service(s) that you wish to grant to the subject and click **ADD**. Note that access rights to REST API endpoints can not be added using this view, those need to be added on **SERVICES** tab as described in [7.1](#71-changing-the-access-rights-of-a-service).

    - To remove a single access right to a service from the service client click **Remove** button on the corresponding row and click **YES** in the confirmation dialog. 
    
    - To remove all access rights to a service from the service client click **REMOVE ALL** and click **YES** in the confirmation dialog. 
    
    - Removing service level access rights from the service client also removes all REST API endpoint level access rights to the endpoints of the service. In other words, removing access rights from the service client removes all access rights to a service and its endpoints.


## 8 Local Access Right Groups

A local access rights group can be created for a security server client in order to facilitate the management of service access rights for a group of X-Road subsystems that use the same services. The access rights granted for a group apply for all the members of the group. Local groups are client-based, that is, a local group can only be used to manage the service access rights of one security server client in one security server.


### 8.1 Adding a Local Group

**Access rights:** [Service Administrator](#xroad-service-administrator)

To create a local group for a security server client, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client and click the **LOCAL GROUPS** tab. In the view that opens, a list of the client's local groups is displayed.

2.  To create a new group, click **ADD GROUP**. In the view that opens, enter the code and description for the new group and click **ADD**.


### 8.2 Displaying and Changing the Members of a Local Group

**Access rights:** [Service Administrator](#xroad-service-administrator)

To **view the members** of a local group, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client and click the **LOCAL GROUPS** tab.

2.  In the view that opens click the code of the group you wish to edit.

To **add one or more members** to a local group, follow these steps in the group's detail view.

1.  Click **ADD MEMBERS**.

2.  In the window that opens add optional filters to your members search and click **SEARCH**. Select the subsystems that you wish to add to the group and click **ADD SELECTED**.

To **remove members** from a local group, click **Remove** on the corresponding row on group you wish to be deleted in the group's detail view and then click **YES** in the confirmation dialog. To remove all group members from the group, click **REMOVE ALL** and then click **YES** in the confirmation dialog.


### 8.3 Changing the description of a Local Group

**Access rights:** [Service Administrator](#xroad-service-administrator)

To change the description of a local group, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client and click the **LOCAL GROUPS** tab.

2.  In the view that opens click the code of the group you wish to edit.

3.  In the group´s detail view change the description. The description is saved when the input field loses focus. 


### 8.4 Deleting a Local Group

**Access rights:** [Service Administrator](#xroad-service-administrator)

**Warning:** When a local group is deleted, all the group members' access rights, which were granted through belonging to the group, are revoked.

To delete a local group, follow these steps.

1.  Navigate to **CLIENTS** tab, click the name of the client and click the **LOCAL GROUPS** tab.

2.  In the view that opens click the code of the group you wish to delete.

3.  In the group detail view, click **DELETE** and confirm the deletion by clicking **YES** in the dialog that opens.


## 9 Communication with Information Systems

**Access rights:** [Registration Officer](#xroad-registration-officer), [Service Administrator](#xroad-service-administrator)


### 9.1 Communication with Service Consumer Information Systems

A security server can be configured to require either the HTTP, HTTPS, or HTTPS with Client Authentication (i.e. HTTP over mTLS) protocol from the consumer role information systems for communication.

- HTTP protocol should be used if the consumer information system and the security server communicate in a private network segment where no other computers are connected to. Furthermore, the information system must not allow interactive log-in.


- HTTPS NOAUTH - a.k.a plain HTTPS protocol should be used if it is not possible to provide a separate network segment for the communication between the information system and the security server. In that case, cryptographic methods are used to protect their communication against potential eavesdropping and interception.


- HTTPS - a.k.a. HTTPS with Client Authentication protocol (**default for new clients**) should be used to protect against unauthorised communication in addition to potential eavesdropping and interception. Before HTTPS can be used, internal TLS certificates must be created for the information systems and uploaded to the security server.

**By default the connection type for all the security server clients is set to HTTPS to prevent unauthorised use of the clients.**

**It is strongly recommended to keep the connection type of the security server owner as HTTPS to prevent security server clients from making operational monitoring data requests as a security server owner.**

To set the connection method for information systems in the **service consumer role**, follow these steps:

1. In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table

2. In the view that opens, select the **INTERNAL SERVERS** tab
 
3. On the **Connection type** drop-down, select the connection method between HTTP, HTTPS NOAUTH or HTTPS. The changes will be saved immediately on selecting the new method and a "Connection type updated" message is displayed.


   **Note:** If the HTTP connection method is selected, but the information system connects to the security server over HTTPS, then the connection is accepted, but the client's internal TLS certificate is not verified (same behavior as with HTTPS NOAUTH).

   **Note:** If HTTPS NOAUTH method is selected keep in mind that the consumer information system must trust the security server's TLS certificate. This can be achieved by exporting security server's internal TLS certificate into information system's truststore (see section [9.3](#93-managing-information-system-tls-certificates)).

   **Note:** If HTTPS method is selected then additionally the client information system's TLS certificate must be trusted. In order to accomplish that the certificate must be added into security server's **Information System TLS certificate** list (see section [9.3](#93-managing-information-system-tls-certificates)).

Depending on the configured connection method, the request URL for information system is **`http://SECURITYSERVER/`** or **`https://SECURITYSERVER/`**. When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server.

### 9.2 Communication with Service Provider Information Systems


The connection method for information systems in the **service provider role** is determined by the protocol in the URL. To change the connection method, follow these steps.

1.  In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table.

2.  In the view that opens, select the **SERVICES** tab.
    
3.  Click the caret next to the desired service description to show all services related to it.

4.  Click on a service code in the table.
    
5.  In the view that opens, change the protocol (a.k.a. scheme) part in the Service URL to either **http://** or **https://**.

- HTTP – the service/adapter URL begins with "**http:**//...".

- HTTPS – the service/adapter URL begins with "**https**://".
  - If **Verify TLS certificate** checkbox is left unchecked it means that service provider information system's TLS certificate is not verified and trusted by default.
  - If **Verify TLS certificate** checkbox is checked it means that service provider information system's TLS certificate is verified. In order to make the information system's TLS certificate trusted, it must be added into security server's **Information System TLS certificate** list (see section [9.3](#93-managing-information-system-tls-certificates)).
  - When the service provider information system needs to verify the Security Server's internal TLS certificate, the certificate must be first exported and then imported into the service provider information system's truststore (see section [9.3](#93-managing-information-system-tls-certificates)).

### 9.3 Managing Information System TLS Certificates

To add an internal TLS certificate for a security server owner or security server client (for HTTPS connections), follow these steps.

1.  In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table

2.  In the view that opens, select the **INTERNAL SERVERS** tab

3.  To add a certificate, click **ADD** in the **Information System TLS certificate** section, select a certificate file from the local file system and click **OK**. The certificate fingerprint appears in the "Information System TLS certificate" table.

To display the detailed information of an internal TLS certificate, follow these steps.

1.  In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table

2.  In the view that opens, select the **INTERNAL SERVERS** tab

3.  Click on a certificate in the "Information System TLS certificate".

To delete an internal TLS certificate, follow these steps.

1.  In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table

2.  In the view that opens, select the **INTERNAL SERVERS** tab

3.  Click on a certificate in the "Information System TLS certificate".

4. In the **Certificate** view that opens, click **DELETE**. Confirm deletion by clicking **YES**.

To export the security server's internal TLS certificate, follow these steps.

1.  In the **Navigation tabs**, select **CLIENTS**, select a security server owner or a client from the table

2.  In the view that opens, select the **INTERNAL SERVERS** tab

2.  Click **Export** at the end of a certificate row in the "Security Server certificate" table and save the prompted file to the local file system.


## 10 System Parameters

The security server system parameters are:

-   **Configuration anchor's information.** The configuration anchor contains data that is used to periodically download signed configuration from the central server and to verify the signature of the downloaded configuration.

-   **Timestamping service information.** Timestamping is used to preserve the evidential value of messages exchanged over X-Road.

-   **Approved Certificate Authorities.** A read-only list of approved certificate authorities (defined in the global configuration). The security server trusts authentication and signing certificates signed by the listed authorities.

-   **The internal TLS key and certificate.** The internal TLS certificate is used to establish a TLS connection with the security server client's information system if the "HTTPS" connection method is chosen for the client's servers.


### 10.1 Managing the Configuration Anchor

**Access rights**

-   For uploading the configuration anchor: [Security Officer](#xroad-security-officer)

-   For downloading the configuration anchor: [Security Officer](#xroad-security-officer), [System Administrator](#xroad-system-administrator)

To upload the configuration anchor, follow these steps.

1.  In the **Navigation tabs**, select **SETTINGS**.

2.  In the opening view select **SYSTEM PARAMETERS** tab.

3.  In the **Configuration Anchor** section, click **UPLOAD**.

4.  Find the anchor file from the local file system and click **Open**.

5.  Ensure that the anchor file you are uploading is valid by comparing the hash value of the uploaded file with the hash value of the currently valid anchor published by the X-Road governing authority. If the hash values match, confirm the upload by clicking **CONFIRM**.

To download the configuration anchor, follow these steps.

1.  In the **Navigation tabs**, select **SETTINGS**.

2.  In the opening view select **SYSTEM PARAMETERS** tab.

3.  On the **Configuration Anchor** section, click **DOWNLOAD** and save the prompted file.


### 10.2 Managing the Timestamping Services

**Access rights:** [Security Officer](#xroad-security-officer)

To add a timestamping service, follow these steps.

1.  In the **Navigation tabs**, select **SETTINGS**.

2.  In the opening view select **SYSTEM PARAMETERS** tab.

3.  In the **Timestamping Services** section, click **ADD**.

4.  In the dialog that opens, select a service and click **ADD**.

To delete a timestamping service, follow these steps.

1.  In the **Navigation tabs**, select **SETTINGS**.

2.  In the opening view select **SYSTEM PARAMETERS** tab.

3.  In the **Timestamping Services** section, click **DELETE** at the end of the row of the service you wish to delete.

*Note*: If more than one timestamping service is configured, the security server will try to get a timestamp from the topmost service in the table, moving down to the next service if the try was unsuccessful. The failover covers both connection and timestamp response verification issues. For example, security server is not able to establish a connection to a timestamping service because of a misconfigured firewall, or verification of a timestamp response fails because of the sign certificate of the timestamping service is changed.


### 10.3 Changing the Internal TLS Key and Certificate

**Access rights:** [Security Officer](#xroad-security-officer), [System Administrator](#xroad-system-administrator)

_To change the security server's internal TLS key and certificate_, follow these steps.

1. On the **Navigation tabs**, select **Keys and Certificates**

2. In the opening view, select **SECURITY SERVER TLS KEY** tab

3. In the opening view, click **GENERATE KEY** and in the dialog that opens, click **CONFIRM**.

   The security server generates a key used for communication with the client information systems, and the corresponding self-signed certificate. The security server's certificate fingerprint will also change. The security server's domain name is saved to the certificate's **Common Name** field, and the internal IP address to the **subjectAltName** extension field.

_To generate a new certificate request_, follow these steps.

1. On the **Navigation tabs**, select **KEYS AND CERTIFICATES**

2. In the opening view, select **SECURITY SERVER TLS KEY** tab

3. In the "TLS Key and Certificate" section, at the end of the key row, click **Generate CSR**

4. In the opening view, input the **Distinguished Name** and click **GENERATE CSR**. Save the certificate request file to the local file system and click **DONE**.

   The security server generates a certificate request using the current key and the provided **Distinguished Name**.

_To import a new TLS certificate_, follow these steps.

1. On the **Navigation tabs**, select **KEYS AND CERTIFICATES**

2. In the opening view, select **SECURITY SERVER TLS KEY** tab

3. In the opening view, click **IMPORT CERT.** and point to the file to be imported.

   The imported certificate must be in PEM-format to be accepted. Certificate chains are supported; concatenate possible intermediate certificate(s) to the server certificate before importing the file.

_To export the security server's internal TLS certificate_, follow these steps.

1. On the **Navigation tabs**, select **KEYS AND CERTIFICATES**

2. In the opening view, select **SECURITY SERVER TLS KEY** tab

3. In the opening view, click **EXPORT CERT.** and save the prompted file to the local file system.

   Note that only the internal server certificate is exported, not the possible intermediate certificates.

_To view the detailed information of the security server's internal TLS certificate_, follow these steps.

1. On the **Navigation tabs**, select **Keys and Certificates**

2. In the opening view, select **SECURITY SERVER TLS KEY** tab

3. In the "TLS Key and Certificate" section, click on the certificate hash.

### 10.4 Approved Certificate Authorities

_To list the approved certificate authorities_, follow these steps.

1.  In the **Navigation tabs**, select **SETTINGS**.

2.  In the opening view select **SYSTEM PARAMETERS** tab. Approved certificate authorities are listed in the "Approved Certificate Authorities" section.

Lists approved certificate authorities. The listing contains the following information:

* CA certificate subject distinguished name. Top-level CAs are **emphasized**.
* OCSP response status (not applicable to top-level CAs, shown as N/A). See [5.3 Validity States of Certificates](#53-validity-states-of-certificates) for explanation, with the following exceptions:
  * Disabled status is not used
  * Additional status "not available" if the OCSP response is not available at all, e.g. due to an error.
* Certificate expiration date.

## 11 Message Log

The purpose of the message log is to provide means to prove the reception of a regular request or response message to a third party. The security server supports three options for configuring message log:

- Full logging
  - The whole message including both message body and metadata is logged. The log records can be verified afterwards and they can be used as evidence.
- Metadata logging
  - Only metadata is logged while message body is not logged. Verifying the log records afterwards is not possible and they cannot be used as evidence.
- No message logging
  - Message logging is fully disabled, neither message body nor metadata is logged. No log records are generated.
  
Full logging and metadata logging can be configured on security server and subsystem level. When the security server level configuration is used, the same configuration is applied to all the subsystems. Instead, when the subsystem level configuration is used, the configuration is applied to specific subsystems only. In addition, combining the security server and subsystem level configurations is also possible, e.g., set metadata logging on the security server level and enable full logging for specific subsystems only. Instead, message logging is fully disabled on a security server level. Therefore, a subsystem that requires full or metadata logging should not be registered on the same security server with a subsystem that requires fully disabling message logging.

Regardless of how logging is configured, messages exchanged between security servers are always signed and encrypted. Also, when full logging or metadata logging is enabled, the security server produces a signed and timestamped document (Associated Signature Container [ASiC]) for regular requests and responses.

Message log data is stored to the database of the security server during message exchange. When storing messages to the database, the message body can be optionally encrypted, but by default the encryption is switched off. According to the configuration ([11.1.4 Timestamping Parameters](#1114-timestamping-parameters)), the timestamping of the signatures of the exchanged messages is either synchronous to the message exchange process or is done asynchronously using the time period set by the X-Road governing agency. In case message logging is fully disabled, timestamping doesn't occur at all.

In case of synchronous timestamping, the timestamping is an integral part of the message exchange process (one timestamp is taken for the request and another for the response). If the timestamping fails, the message exchange fails as well and the security server responds with an error message.

In case of asynchronous timestamping, all the messages (maximum limit is determined in the configuration, see [11.1.4 Timestamping Parameters](#1114-timestamping-parameters)) stored in the message log since the last periodical timestamping event are timestamped with a single (batch) timestamp. By default, the security server uses asynchronous timestamping for better performance and availability.

The security server periodically composes signed (and timestamped) documents from the (optionally encrypted) message log data and archives them in the local file system. Archive files are ZIP containers containing one or more signed documents and a special linking information file for additional integrity verification purpose. Message log archive encryption and grouping can be enabled and configured separately. By default, both are disabled. Message grouping can be configured by member or subsystem. By default, all archive files go to the same default group. Grouping and encryption are enabled/disabled on a security server level - they are either enabled or disabled for all the members and subsystems. It's not possible to enable/disable neither of them for selected members or subsystems only.

### 11.1 Changing the Configuration of the Message Log

Configuration parameters are defined in INI files \[[INI](#Ref_INI)\], where each section contains the parameters for a particular security server component. The default message log configuration is located in the file

    /etc/xroad/conf.d/addons/message-log.ini

In order to override default values, create or edit the file

    /etc/xroad/conf.d/local.ini

Create the `[message-log]` section (if not present) in the file. Below the start of the section, list the values of the parameters, one per line.

For example, to configure the parameters `archive-path` and `archive-max-filesize`, the following lines must be added to the configuration file:

    [message-log]
    archive-path=/my/archive/path/
    archive-max-filesize=67108864


#### 11.1.1 Common Parameters

1.  `hash-algo-id` – the hash algorithm that is used for hashing in the message log. Possible choices are `SHA-256`, `SHA-384`, `SHA-512`. Defaults to `SHA-512`.

#### 11.1.2 Logging Parameters

1.  `message-body-logging` - if set to true, the messages are logged in their original form. If false, the message body is emptied of its contents.

2.  `enabled-body-logging-local-producer-subsystems` - when message-body-logging is set to false, this field contains the overrides for the local producer subsystems.

3.  `enabled-body-logging-remote-producer-subsystems` - when message-body-logging is set to false, this field contains the overrides for the remote producer subsystems.

4.  `disabled-body-logging-local-producer-subsystems` - when message-body-logging is set to true, this field contains the overrides for the local producer subsystems.
  
5.  `disabled-body-logging-remote-producer-subsystems` - when message-body-logging is set to true, this field contains the overrides for the remote producer subsystems.

6.  `max-loggable-message-body-size` - the maximum REST message body size that will be written to the messagelog.

7.  `truncated-body-allowed` - if the REST message body size exceeds the max-loggable-message-body-size truncate the body (true) or reject the message (false)

8.  `messagelog-encryption-enabled` - if set to true, the message bodies are written to the database in an encrypted format

9.  `messagelog-keystore` - path to the messagelog keystore

10.  `messagelog-keystore-password` - messagelog keystore password

11.  `messagelog-key-id` - messagelog keystore key id


#### 11.1.3 Message Log Encryption

The message bodies can be encrypted (`messagelog-encryption-enabled = true`) when stored to the database. By default, the encryption is disabled. Also, the encryption is fully transparent to all the external interfaces, e.g., the signed document download service. The encryption is symmetric, the used cipher is AES-CTR, and the encryption is performed using Java code.

In the message log database, there are two separate columns for plaintext (`message`) and encrypted (`ciphermessage`) message body. The message body is always stored in one of the two columns depending on the configuration. Instead, the other column that is not used is left empty. When message log database encryption is enabled/disabled, the change doesn't affect already existing records in the database. For example, when message log database encryption is enabled, all the records created after the configuration change will be encrypted and stored in the `ciphermessage` column. Instead, all the records stored before the change will remain in plaintext in the `message` column.

When encryption is switched on, the implementation expects to find the keystore in the location pointed by `messagelog-keystore`. The keystore should contain an encryption key with the identifier/alias specified in `messagelog-key-id`. The keystore password is specified in `messagelog-keystore-password`.

For example, add the following to `/etc/xroad/conf.d/local.ini`:

```ini
[message-log]
messagelog-encryption-enabled=true
messagelog-keystore=/etc/xroad/messagelog/messagelog.p12
messagelog-keystore-password=somepassword
messagelog-key-id=key1
```

Create the password store and import a key:

```bash
keytool -keystore /etc/xroad/messagelog/messagelog.p12 -storetype pkcs12 -importpassword -alias key1
```

Finally, restart `xroad-proxy` service.

To view the encrypted messages at some later stage, use the ASIC web service documented in \[[UG-SIGDOC](#Ref_UG-SIGDOC)\]. The web service performs automatic decryption, where needed.


#### 11.1.4 Timestamping Parameters

1.  `timestamp-immediately` – if set to true, the timestamps are created synchronously with the message exchange, i.e., one timestamp is created for a request and another for a response. This is a security policy to guarantee the timestamp at the time of logging the message, but if the timestamping fails, the message exchange fails as well, and if load to the security server increases, then the load to the timestamping service increases as well. The value of this parameter defaults to false for better performance and availability. In case the value of the parameter is false then the timestamping is performed as a periodic background process (the time period is determined in the X-Road governing agency and propagated to the security servers by global configuration) and signatures stored during the time period (see parameter `timestamp-records-limit`) are timestamped in one batch.

2.  `timestamp-records-limit` – maximum number of signed messages that can be timestamped in one batch. The message exchanging load (messages per minute) and the timestamping interval of the security server must be taken into account when changing the default value of this parameter. Do not modify this parameter without a good reason. Defaults to `10000`.

3.  `acceptable-timestamp-failure-period` – time period in seconds, for how long the asynchronous timestamping is allowed to fail before message exchange between security servers is stopped. Set to `0` to disable this check. Defaults to `14400`.


#### 11.1.5 Archiving Parameters

1. `keep-records-for` – time in days for which to keep timestamped and archived records in the database. Defaults to `30`.
2. `archive-max-filesize` – maximum size for archived files in bytes. Reaching the maximum value triggers the file rotation. Defaults to `33554432` (32 MB).
3. `archive-interval` – time interval as Cron expression \[[CRON](#Ref_CRON)\] for archiving timestamped records. Defaults to `0 0 0/6 1/1 * ? *` (fire every 6 hours).
4. `archive-path` – the directory where the timestamped log records are archived. Defaults to `/var/lib/xroad/`.
5. `clean-interval` – time interval as Cron expression \[[CRON](#Ref_CRON)\] for cleaning archived records from the database. Defaults to `0 0 0/12 1/1 * ? *` (fire every 12 hours).
6. `archive-transfer-command` – the command executed after the (periodic) archiving process. This enables one to configure an external script to transfer archive files automatically from the security server. Defaults to no operation.
7. `archive-grouping` - archive file grouping; `none` (default), by `member` or, by `subsystem`.
8.  `archive-encryption-enabled` - archive file encryption enabled: false (default) or true.
9.  `archive-gpg-home-directory` - GPG home directory for archive file signing and encryption keyring (default `/etc/xroad/gpghome`).
10. `archive-encryption-keys-config` - Configuration file for member gpg keys.
11. `archive-default-encryption-key` - Default archive encryption key id.


#### 11.1.6 Archive Files

Archive files (ZIP containers) are located in the directory specified by the configuration parameter `archive-path`. File names are in the format `mlog[-grouping]-X-Y-Z.zip[.gpg]`, where X is the timestamp (UTC time in the format `YYYYMMDDHHmmss`) of the first message log record, Y is the timestamp of the last message log record (records are processed in chronological order) and Z is the first 16 characters of the last linking info digest entry. If grouping is enabled, [-grouping] is a (possibly truncated and filename safe) member identifier. If encryption is enabled, the `[.gpg]` suffix is added. Creating archive files is deterministic -- given the same input (grouping, message records, previous archive linking info digest), the output (file name and contents) is the same after possible encryption is removed.

The most basic example of an archive file name when the encryption and grouping are switched off:

    mlog-20210901100858-20210901100905-95b1f27097524105.zip

When the archive encryption switched on:

    mlog-20210901101923-20210901101926-95b1f27097524105.zip.gpg

Switching on archive grouping by member produces the following:

    mlog-INSTANCE_CLASS_CODE-20210901102251-20210901102254-95b1f27097524105.zip.gpg

Finally, switching to archive grouping by subsystem gives:

    mlog-INSTANCE_CLASS_CODE_CONSUMERSUBSYSTEM-20210901102521-20210901102524-95b1f27097524105.zip.gpg
    mlog-INSTANCE_CLASS_CODE_PROVIDERSUBSYSTEM-20210901102521-20210901102524-b1f27097524105ac.zip.gpg


#### 11.1.7 Archive Encryption and Grouping

Archive files can be encrypted (when `archive-encryption-enabled = true`) using GnuPG ("gpg") which implements the OpenPGP (RFC 4880) specification. Please see e.g. [RFC 4880](https://www.ietf.org/rfc/rfc4880.txt) and [GnuPG](https://gnupg.org/) for more infomation. The encryption is enabled/disabled on a security server level - it's not possible to enable/disable it for specific subsystems only.

By default, the produced archive files contain messages from all the security server's members, but it's possible to group the archives by member or by subsystem if needed. The grouping is controlled by the setting `archive-grouping`. The grouping is enabled/disabled on a security server level - it is either enabled or disabled for all the members and subsystems.

Message log archive encryption and grouping can be configured separately. For example, the archives can be encrypted but not grouped (or vice versa). By default, both features are disabled.

When encryption is enabled, the archiving process expects to find a GnuPG keyring containing the server signing keypair in `archive-gpg-home-directory` (by default `/etc/xroad/gpghome`). When the default value is used the server signing keypair is the same as the backup signing and encryption keypair. This keypair is used to sign the generated archive files, and as a fallback encryption key if no other keys are configured.

The `archive-default-encryption-key` can be used to override the default encryption key id, which is used when `archive-grouping` is `none` or no member gpg key is defined. Changing this parameter requires restarting the xroad-addon-messagelog service.

In case `archive-grouping` is `member` or `subsystem`, gpg keys defined in file `archive-encryption-keys-config` are used (if no key is defined for the member, the default encryption key is used). This file maps member identifiers to gpg key identifiers and has the following format:
```
# This is a comment (ignored)
# One mapping per line (leading, trailing, and around `=` white space is ignored)
<member identifier> = <key id>

# Escaping (applies only to the member identifier):
## Member: test/member=class/\=code
   test/member\=class/\\=code = ABCD....
## Member #42/CLASS/#123 has two keys:
   \#42/CLASS/\#123 = ABCD....
   \#42/CLASS/\#123 = EF12....
```
* There can be several mappings (keys) per member (one mapping per line)
* Lines _starting with_ `#` are ignored
* Escaping special characters in the _member identifier_ part:
  * `=` is written as `\=` (a literal `\=` becomes `\\=`)
  * `#` is written as `\#` (a literal `\#` becomes `\\#`)

Warning. The archiving process fails if a required key is not present in the gpg keyring. Therefore, it is important to verify that the mappings are correct.

**Configuration example**

Generate a keypair for encryption with defaults and no expiration and export the public key:
```bash
gpg [--homedir <member gpghome>] --quick-generate-key INSTANCE/memberClass/memberCode default default never
gpg [--homedir <member gpghome>] --export INSTANCE/memberClass/memberCode >INSTANCE-memberClass-memberCode.pgp
```

Import the public key to the gpg keyring in `archive-gpg-home-directory` and take note of the key id.
```bash
gpg --homedir <archive-gpg-home-directory> --import INSTANCE-memberClass-memberCode.pgp
```

Edit the key and add ultimate trust.
```bash
gpg --homedir <archive-gpg-home-directory> --edit-key <key id>
```

At the `gpg>` prompt, type `trust`, then type `5` for ultimate trust, then `y` to confirm, then `quit`.

Add the mapping to `archive-encryption-keys-config` file (mappings can be edited without restarting X-Road services), e.g.:
```bash
INSTANCE/memberClass/memberCode = 96F20FF6578A5EF90DFBA18D8C003019508B5637
```

Add the mapping file location (`archive-encryption-keys-config`) and grouping level (`archive-grouping`) to `/etc/xroad/conf.d/local.ini` file (editing the file requires restarting X-Road services), e.g.:
```bash
[message-log]
archive-encryption-enabled = true
archive-grouping = member
archive-encryption-keys-config = /etc/xroad/messagelog/archive-encryption-mapping.ini
```

To decrypt the encrypted archives, use the following syntax:
```bash
gpg [--homedir <gpghome>] --decrypt <archive name> --output <output file name>
```

### 11.2 Transferring the Archive Files from the Security Server

In order to save hard disk space, it is recommended to transfer archive files periodically from the security server (manually or automatically) to an external location.

The message log package provides a helper script `/usr/share/xroad/scripts/archive-http-transporter.sh` for transferring archive files. This script uses the HTTP/HTTPS protocol (the POST method, the form name is file) to transfer archive files to an archiving server.

Usage of the script:

 Options:          | &nbsp;
------------------ | -----------------------------------------------------------------------------------------------
 `-d, --dir DIR`   | Archive directory. Defaults to '/var/lib/xroad'
 `-r, --remove`    | Remove successfully transported files form the archive directory.
 `-k, --key KEY`   | Private key file name in PEM format (TLS). Defaults to '/etc/xroad/ssl/internal.key'
 `-c, --cert CERT` | Client certificate file in PEM format (TLS). Defaults to '/etc/xroad/ssl/internal.crt'
 `-cacert FILE`    | CA certificate file to verify the peer (TLS). The file may contain multiple CA certificates. The certificate(s) must be in PEM format.
 `-h, --help`      | This help text.

The archive file has been successfully transferred when the archiving server returns the HTTP status code `200`.

Override the configuration parameter archive-transfer-command (create or edit the file `etc/xroad/conf.d/local.ini`) to set up a transferring script. For example:

    [message-log]
    archive-transfer-command=/usr/share/xroad/scripts/archive-http-transporter.sh -r http://my-archiving-server/cgi-bin/upload

The message log package contains the CGI script `/usr/share/doc/xroad-addon-messagelog/archive-server/demo-upload.pl` for a demo archiving server for the purpose of testing or development.


### 11.3 Using a Remote Database

The message log database can be located outside of the security server. The following guide describes how to configure and populate a remote database schema for the message log. It is assumed that access to the database from the security server has been configured. For detailed information about the configuration of database connections, refer to \[[JDBC](#Ref_JDBC)\].

1.  Create a database user at remote database host:

        postgres@db_host:~$ createuser -P messagelog_user
        Enter password for new role: <messagelog_password>
        Enter it again: <messagelog_password>

2.  Create a database owned by the message log user at remote database host:

        postgres@db_host:~$ createdb messagelog_dbname -O messagelog_user -E UTF-8

3.  Verify connectivity from security server to the remote database:

        user@security_server:~$ psql -h db_host -U messagelog_user messagelog_dbname
        Password for user messagelog_user: <messagelog_password>
        psql (9.3.9)
        SSL connection (cipher: DHE-RSA-AES256-GCM-SHA384, bits: 256)
        Type "help" for help.
        messagelog_dbname=>

4.  Stop xroad-proxy service for reconfiguration:

        root@security_server:~# service xroad-proxy stop

5.  Configure the database connection parameters to achieve encrypted connections, in `/etc/xroad/db.properties`:

        messagelog.hibernate.jdbc.use_streams_for_binary = true
        messagelog.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
        messagelog.hibernate.connection.driver_class = org.postgresql.Driver
        messagelog.hibernate.connection.url = jdbc:postgresql://db_host:5432/messagelog_dbname?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory
        messagelog.hibernate.connection.username = messagelog_user
        messagelog.hibernate.connection.password = messagelog_password

6.  Populate database schema by reinstalling messagelog addon package and start xroad-proxy

    Ubuntu: `apt-get install --reinstall xroad-addon-messagelog`  
    RHEL: `yum reinstall xroad-addon-messagelog`

    `service xroad-proxy start`

## 12 Audit Log

The security server keeps an audit log. The audit log events are generated by the user interface when the user changes the system's state or configuration. The user actions are logged regardless of whether the outcome was a success or a failure. The complete list of the audit log events is described in \[[SPEC-AL](#Ref_SPEC-AL)\].

Actions that change the system state or configuration but are not carried out using the user interface are not logged (for example, X-Road software installation and upgrade, user creation and permission granting, and changing the configuration files).

An audit log record contains correlation-id, which can be used to link the record to other log messages about the same request.

An audit log record also contains

-   the description of the user action,

-   the date and time of the event,

-   the username of the user performing the action

-   the authentication type used for this request (Session, ApiKey or HttpBasicPam)
    - `Session` – session based authentication (web application)
    - `ApiKey` - direct API call using API key authentication
    - `HttpBasicPam` – HTTP basic authentication with PAM login (for api key management API operations)

-   the API url for this request, and

-   the data related to the event.

For example, registering a new client in the security server produces the following log record:

  `2020-06-03T11:00:51+00:00 my-security-server-host correlation-id: [24b47d04dc6e1c49] INFO  [X-Road Proxy Admin REST API] 2020-06-03T11:00:51.944Z - {"event":"Register client","user":"admin1","auth":"Session","url":"/api/v1/clients/LXD:GOV:M1:audit-test/register","data":{"clientIdentifier":{"xRoadInstance":"LXD","memberClass":"GOV","memberCode":"M1","subsystemCode":"audit-test","clientStatus":"registration in progress"}}}`

The event is present in JSON \[[JSON](#Ref_JSON)\] format, in order to ensure machine processability.
The field event represents the description of the event, the field user represents the user name of the performer, and the field data represents data related with the event.
Field auth represents the authentication type, and url represents the API url.
The failed action event record contains additional fields reason for the error message, and boolean warning to document whether failure was due to an unhandled warning.
For example:

  `2020-06-03T10:57:46+00:00 my-security-server-host correlation-id: [49458d51a0bbe9ed] INFO  [X-Road Proxy Admin REST API] 2020-06-03T10:57:46.417Z - {"event":"Log in to token failed","user":"admin1","reason":"org.niis.xroad.restapi.service.TokenService$PinIncorrectException: Signer.PinIncorrect: PIN incorrect","warning":false,"auth":"Session","url":"/api/v1/tokens/0/login","data":{"tokenId":"0","tokenSerialNumber":null,"tokenFriendlyName":"softToken-0"}}`

By default, audit log is located in the file

    /var/log/xroad/audit.log


### 12.1 Changing the Configuration of the Audit Log

The X-Road software writes the audit log to the *syslog* (*rsyslog*) using UDP interface (default port is 514). Corresponding configuration is located in the file

    /etc/rsyslog.d/90-udp.conf

The audit log records are written with level INFO and facility LOCAL0. By default, log records of that level and facility are saved to the X-Road audit log file

    /var/log/xroad/audit.log

The default behavior can be changed by editing the *rsyslog* configuration file

    /etc/rsyslog.d/40-xroad.conf

Restart the *rsyslog* service to apply the changes made to the configuration file

    service rsyslog restart

The audit log is rotated monthly by *logrotate*. To configure the audit log rotation, edit the *logrotate* configuration file

    /etc/logrotate.d/xroad-proxy


### 12.2 Archiving the Audit Log

In order to save hard disk space and avoid loss of the audit log records during security server crash, it is recommended to archive the audit log files periodically to an external storage or a log server.

The X-Road software does not offer special tools for archiving the audit log. The `rsyslog` can be configured to redirect the audit log to an external location.


## 13 Back up and restore

It is possible to back up and later restore security server configuration. A backup archive file contains the
following configuration:

- copy of serverconf database
- user modifiable configuration files
- keys and certificates
  - security server's auth key and certificate
  - members' sign keys and certificates (that are stored in soft token)
  - security server's internal TLS key and certificate
  - security server's UI key and certificate
- database credentials

Notice that starting from X-Road v7.0, the backup archive file no longer contains the local override file `/etc/xroad/services/local.conf`, but instead `/etc/xroad/services/local.properties` file will be included.

**N.B.** Message log database encryption keys, and message log archives encryption and signing keys are included in the backups only if they are stored under the `/etc/xroad` directory. However, they should not be stored in the `/etc/xroad/gpghome` subdirectory since it is excluded from the backups.

Backups contain sensitive information that must be kept secret (for example, private keys and database credentials).
In other words, leaking this information could easily lead to full compromise of security server. Therefore, it is
highly recommended that backup archives are encrypted and stored securely. Should the information still leak for whatever
reason the security server should be considered as compromised and reinstalled from scratch.

Security server backups are signed and optionally encrypted. The GNU Privacy Guard [GnuPG] is used for encryption and signing.
Security server's backup encryption key is generated during security server initialisation. In addition to the
automatically generated backup encryption key, additional public keys can be used to encrypt backups.

### 13.1 Back up and Restore in the User Interface

**Access rights:** [System Administrator](#xroad-system-administrator)

The backup and restore view can be accessed from the **Navigation tabs** by selecting **Back Up and Restore**.

To **back up configuration**, follow these steps.

1.  Navigate to **SETTINGS** tab and in the view that opens click **BACKUP AND RESTORE** tab.

2.  Click **BACK UP CONFIG.**

3.  The configuration backup file appears in the list of configuration backup files.

4.  To save the configuration backup file to the local file system, click **DOWNLOAD** on the configuration file's row.

To **restore configuration**, follow these steps.

1.  Click **Restore** on the appropriate row in the list of configuration backup files and click **YES**.

2.  A popup notification shows after the restore whether the restoring was successful or not.

If something goes wrong while restoring the configuration it is possible to revert back to the old configuration.
Security Server stores so called pre-restore configuration automatically to `/var/lib/xroad/conf_prerestore_backup.tar`. Either move it to `/var/lib/xroad/backup/` folder and utilize the user interface to restore it or use the command line interaface described in the next chapter.

To **delete a configuration backup file**, click **Delete** on the appropriate row in the configuration backup file list and then click **YES**.

To **upload a configuration backup file** from the local file system to the security server, click **UPLOAD BACKUP**,
select a file and click **YES**. The uploaded configuration file appears in the list of configuration files. Bear in mind
that only files signed with current security server encryption key can be restored via user interface. All other archives
can be restored only from command line.

As long as original keypair is intact no additional steps are needed even when backup encryption is turned on.

### 13.2 Restore from the Command Line

To restore configuration from the command line, the following data must be available:

-   The X-Road ID of the security server

To find the X-Road ID of the security server, the following command can be used:

    tar -tf /var/lib/xroad/backup/<security server conf backup file> | head -1

It is expected that the restore command is run by the xroad user.

In order to restore configuration, the following command should be used:

    /usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh \
    -s <security server ID> -f <path + filename> [-P -N]

For example (all on one line):

    /usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh \
    -s AA/GOV/TS1OWNER/TS1 \
    –f /var/lib/xroad/backup/conf_backup_20140703-110438.gpg

In case original backup encryption and signing key is lost additional parameters can be specified to skip decryption and/or
signature verification. Use `-P` command line switch when backup archive is already decrypted externally and `-N` switch to
skip checking archive signature.

If a backup is restored on a new uninitialized (the initial configuration hasn't been completed) security server, the 
security server's gpg key must be manually created before restoring the backup:

    /usr/share/xroad/scripts/generate_gpg_keypair.sh /etc/xroad/gpghome <security server ID>

If it is absolutely necessary to restore the system from a backup made on a different security server, the forced mode
of the restore command can be used with the –F option together with unencrypted backup archive flags. For example (all on one line):

    /usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh \
    -F -P –f /var/lib/xroad/backup/conf_backup_20140703-110438.tar

In case backup archives were encrypted they have to be first unencrypted in external safe environment and then securely
transported to security server filesystem.

### 13.3 Automatic Backups

By default the Security Server backs up its configuration automatically once every day. Backups older than 30 days are
automatically removed from the server. If needed, backup removal policies can be adjusted by editing the
`/etc/cron.d/xroad-proxy` file.

Automatic backup schedule can be adjusted  in the file `/etc/xroad/conf.d/local.ini`, 
in the `[configuration-client]` section (add or edit this section).

```ini
[configuration-client]
proxy-configuration-backup-cron=0 15 3 * * ?
```

**Note:** In cases where automatic backup is not required (ex: extensions which rely on configuration-client) 
it is suggested to disable it by using cron expression that will never trigger. For example `* * * * * ? 2099`

### 13.4 Backup Encryption Configuration

Backups are always signed, but backup encryption is initially turned off. To turn encryption on, please override the
default configuration in the file `/etc/xroad/conf.d/local.ini`, in the `[proxy]` section (add or edit this section).

```ini
[proxy]

backup-encryption-enabled = true
backup-encryption-keyids = <keyid1>, <keyid2>, ...
```

To turn backup encryption on, change the `backup-encryption-enabled` property to true. Additional
encryption keys can be imported in the `/etc/xroad/gpghome` keyring and key identifiers listed using the `backup-encryption-keyids` parameter. It is recommended to set up at least one additional key, otherwise the backups will be unusable in case security servers private key is lost. It is up to security servers administrator to check that keys used are sufficiently strong, there are no automatic checks.

Warning. All keys listed in `backup-encryption-keyids` must be present in the gpg keyring or backup fails.

Additional keys for backup encryption should be generated and stored outside security server in a secure environment.
After gpg keypair has been generated, public key can be exported to a file (backupadmin@example.org is the name of the
key being exported) using this command:

    gpg --output backupadmin.publickey --armor --export backupadmin@example.org

Resulting file `backupadmin.publickey` should be moved to security server and imported to backup gpg keyring. Administrator should make sure that the key has not been changed during transfer, for example by validating the key fingerprint.

Private keys corresponding to additional backup encryption public keys must be handled safely and kept in secret. Any of
them can be used to decrypt backups and thus mount attacks on the security servers.

**Configuration example**

Generate a keypair for encryption with defaults and no expiration and export the public key:
```bash
gpg [--homedir <admin gpghome>] --quick-generate-key backupadmin@example.org default default never
gpg [--homedir <admin gpghome>] --export backupadmin@example.org >backupadmin@example.org.pgp
```

Import the public key to the gpg keyring in `/etc/xroad/gpghome` directory and take note of the key id.
```bash
gpg --homedir /etc/xroad/gpghome --import backupadmin@example.org.pgp
```

Edit the key and add ultimate trust.
```bash
gpg --homedir /etc/xroad/gpghome/ --edit-key <key id>
```

At the `gpg>` prompt, type `trust`, then type `5` for ultimate trust, then `y` to confirm, then `quit`.

Add the key id to `/etc/xroad/conf.d/local.ini` file (editing the file requires restarting X-Road services), e.g.:
```bash
[proxy]
backup-encryption-enabled = true
backup-encryption-keyids = 96F20FF6578A5EF90DFBA18D8C003019508B5637
```

To decrypt the encrypted backups, use the following syntax:

```bash
gpg --homedir /etc/xroad/gpghome --decrypt <backup name> --output <output file name> 
```

### 13.5 Verifying Backup Archive Consistency

During restore security server verifies consistency of backup archives automatically, archives are not checked during upload.
Also, it is possible to verify the consistency of the archives externally. For verifying the consistency externally,
security server's public key is needed. When backups are encrypted, then a private key for decrypting archive is also needed.
GPG uses "sign then encrypt" scheme, so it is not possible to verify encrypted archives without decrypting them.

Automatic backup verification is only possible when original security server keypair is available. Should keypair on the
security server be lost for whatever reason, automatic verification is no longer possible. Therefore, it is recommended
to export backup encryption public key and import it into separate secure environment. If backups are encrypted,
security server public key should be imported to keyrings holding additional encryption keys, so that backups can be
decrypted and verified in these separate environments.

To export security servers backup encryption public key use the following command:

    gpg --homedir /etc/xroad/gpghome --armor --output server-public-key.gpg --export <instanceIdentifier>/<memberClass>/<memberCode>/<serverCode>

where `<instanceIdentifier>/<memberClass>/<memberCode>/<serverCode>` is the security server id,
for example, `AA/GOV/TS1OWNER/TS1`.

Resulting file (`server-public-key.gpg`) should then be exported from security server and imported to GPG keystore used
for backup archive consistency verification.

## 14 Diagnostics

**Access rights:** [System Administrator](#xroad-system-administrator)

Click on **DIAGNOSTICS** in the **Navigation tabs**.

On the Diagnostics page you can view the status information of:

- security server services;
    - global configuration client;
    - timestamping operation;
    - downloading OCSP responses from the OCSP-responder;
- security server Java version;
- security server encryption configuration;
    - backup encryption;
    - message log archive encryption and grouping;
    - message log database encryption.

### 14.1 Examine security server services status information

Security server services status information covers the following services:

 Service              | Status           | Message        | Previous Update          | Next Update
--------------------- | ---------------- | -------------- | ------------------------ | ------------------------
 Global configuration | Green/yellow/red | Status message | The time of the global configuration client's last run | The estimated time of the global configuration client's next run
 Timestamping         | Green/yellow/red | Status message | The time of the last timestamping operation            | Not used                                   
 OCSP-responders      | Green/yellow/red | Status message | The time of the last contact with the OCSP-responder   | The latest possible time for the next OCSP-refresh

To refresh the service statuses, refresh the page.

The status colors indicate the following:
- **Red indicator** – service cannot be contacted or is not operational
- **Yellow indicator** – service has been contacted but is yet to have been used to verify its status
- **Green indicator** – service has been successfully contacted and used to verify it is operational

The status message offers more detailed information on the current status.

If a section of the diagnostics view appears empty, it means that there either is no configured service available or that checking the service status has failed. If sections are empty, try refreshing the diagnostics view or check the service configuration.

### 14.2 Examine security server Java version information

Security server Java version information provides the following details:

 Column                    | Description 
---------------------------|------------
Status                     | Green/red
Message                    | Status message
Vendor name                | Vendor name of Java that the security server is using
Java version               | Java version number that the security server is using
Earliest supported version | Earliest supported Java version number
Latest supported version   | Latest supported Java version number

To refresh the status, refresh the page.

The status colors indicate the following:
- **Red indicator** – security server's java version number isn't supported
- **Green indicator** – security server's java version number is supported

### 14.3 Examine security server encryption status information

**Backup encryption status**

The status shows is the backup encryption `enabled` or `disabled`. The Configured key ID list contains all the additional encryption keys that are present in the configuration. If the list is empty, only the system generated default encryption key is used. When the backup encryption is `disabled`, the list is always empty.

The status colors indicate the following:
- **Red indicator** – there's an error with checking the backup encryption status
- **Yellow indicator** – backup encryption is disabled
- **Green indicator** – backup encryption is enabled

**Message log archive encryption status**

The status shows is the message log archive encryption `enabled` or `disabled`. The Grouping rule shows the grouping level (`NONE`, `MEMBER`, `SUBSYSTEM`) of the message log archives.

The list of Member Identifier / Key ID pairs includes a list of members using the security server and the encryption key(s) associated with the member when the grouping level is `MEMBER` or `SUBSYSTEM`. When the grouping level is `NONE`, the list is always empty. If no member-specific key is associated with a member, there's a warning icon in the Key ID column. If the Key ID is missing and there's only the warning icon in the Key ID column, the member is using the system generated default encryption key. Instead, if the warning icon is after the Key ID, the member is using the user generated default encryption key (defined using the `archive-default-encryption-key` property). It's strongly recommended to use user generated member-specific encryption keys.

Each member can have multiple member-specific encryption keys configured. If multiple keys are configured for a single member, the key IDs are presented as a comma separated list.

The status colors indicate the following:
- **Red indicator** – there's an error with checking the message log archive encryption status
- **Yellow indicator** – message log archive encryption is disabled
- **Green indicator** – message log archive encryption is enabled
- 
**Message log database encryption status**

The status shows is the message log database encryption `enabled` or `disabled`.

The status colors indicate the following:
- **Red indicator** – there's an error with checking the message log database encryption status
- **Yellow indicator** – message log database encryption is disabled
- **Green indicator** – message log database encryption is enabled

## 15 Operational Monitoring

**Operational monitoring data** contains data about request exchange (such as the ID-s of the client and the service, various attributes of the message read from the message header, request and response timestamps, SOAP sizes etc.) of the X-Road security server(s).

**The operational monitoring daemon** collects and shares operational monitoring data of the X-Road security server(s) as part of request exchange, shares this data, calculates and shares health statistics (the timestamps and number of successful/unsuccessful requests, various metrics of the duration and the SOAP message size of the requests, etc.). The data fields that are stored and shared are described in \[[PR-OPMON](#Ref_PR-OPMON)\].

The security server caches operational monitoring data in the **operational monitoring buffer**. One operational data record is created for each request during the message exchange. Security server forwards operational data cached in the operational monitoring buffer to the operational monitoring daemon. Successfully forwarded records are removed from the operational monitoring buffer.

The operational monitoring daemon makes operational and health data available to the owner of the security server, regular clients and the central monitoring client via the security server. Local health data are available for external monitoring systems (e.g. Zabbix) over the JMXMP interface described in \[[PR-OPMONJMX](#Ref_PR-OPMONJMX)\].

The owner of the security server and the central monitoring client are able to query the records of all clients. For a regular client, only the records associated with that client are available. The internal IP of the security server is included in the response only for the owner of the security server and central monitoring client.

**NOTE:** All the commands in the following sections must be carried out using root permissions.


### 15.1 Operational Monitoring Buffer

In general, the operational monitoring buffer is an internal component of the security server and thus being not directly used by the end user.

The configuration parameters available for configuring the operational monitoring buffer have been documented in \[[UG-OPMONSYSPAR](#Ref_UG-OPMONSYSPAR)\].

The default values of the parameters have been chosen to be sufficient under expected average load using the minimum hardware recommended.

All overrides to the default configuration values must be made in the file `/etc/xroad/conf.d/local.ini`, in the `[op-monitor-buffer]` section.


#### 15.1.1 Stopping the Collecting of Operational Data

If, for any reason, operational data should not be collected and forwarded to the operational monitoring daemon, the parameter size can be set to 0:

    [op-monitor-buffer]
    size = 0

After the configuration change, the xroad-proxy service must be restarted:

    service xroad-proxy restart

In addition, the operational monitoring daemon should be stopped:

    service xroad-opmonitor stop

For the service to stay stopped after reboot the following command should be run:

    echo manual > /etc/init/xroad-opmonitor.override


### 15.2 Operational Monitoring Daemon

The configuration parameters available for configuring the operational monitoring daemon have been documented in \[[UG-OPMONSYSPAR](#Ref_UG-OPMONSYSPAR)\].

Similarly to the operational monitoring buffer, the default values of the parameters have been chosen to be sufficient under expected average load using the minimum recommended hardware.

All overrides to the default configuration values must be made in the file `/etc/xroad/conf.d/local.ini`, in the `[op-monitor]` section.

In the following sections, some parameters are described which may be required to be changed more likely.


#### 15.2.1 Configuring the Health Statistics Period

By default, health statistics are provided for a period of 600 seconds (10 minutes). This means that if no request exchange has taken place for 10 minutes, all the statistical metrics are reset. Please refer to \[[PR-OPMON](#Ref_PR-OPMON)\] for a detailed overview of the health metrics available.

To change the health statistics period, the value of the parameter health-statistics-period-seconds should be set or edited in the `[op-monitor]`
section of the file `/etc/xroad/conf.d/local.ini`.


#### 15.2.2 Configuring the Parameters Related to Database Cleanup

Depending on the load and resources of the system, it may be necessary to change the interval of the removal of old database records.

The following parameters must be placed in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini`.

The parameter `keep-records-for-days` should be edited, for instance if the disk fills up before cleanup occurs, or alternatively, if the default period of 7 days is too short. The parameter `clean-interval` (a Cron expression \[[CRON](#Ref_CRON)\]) defines how often the system checks whether cleanup should be done. If the default period of 12 hours is too long or short it should be edited according to your needs.


#### 15.2.3 Configuring the Parameters related to the HTTP Endpoint of the Operational Monitoring Daemon

For configuring the endpoint of the operational monitoring daemon, the following parameters are available in the `[op-monitor]` section of the configuration:

**host** – listening host of the daemon (by default the value is set to *localhost*).

**port** – listening port (by default the value is set to *2080*).

**scheme** – connection type (by default the value is set to *HTTP*).

If any of these values are changed, both the proxy and the operational monitoring daemon services must be restarted:

    service xroad-proxy restart
    service xroad-opmonitor restart


#### 15.2.4 Installing an External Operational Monitoring Daemon

Technically, the operational monitoring daemon can be installed on a separate host from the security server. It is possible to configure several security servers to use that external operational monitoring daemon, but this setup is correct *only* if the security servers are identical clones installed behind a load balancer.

**NOTE:** The setup of clustered security servers is not officially supported yet and has been implemented for future compatibility.

**NOTE:** It is **strongly advised** to use HTTPS for requests between a security server and the associated external operational monitoring daemon.

For running a separate operational monitoring daemon, the xroad-opmonitor package must be installed. Please refer to \[[IG-SS](#Ref_IG-SS)\] for general instructions on obtaining X-Road packages.

As a result of installation, the following services will be running:

    xroad-confclient
    xroad-signer
    xroad-opmonitor


#### 15.2.5 Configuring an External Operational Monitoring Daemon and the Corresponding Security Server

To make a security server communicate with an external operational monitoring daemon, it is necessary to configure both the daemon and the security server.

By default, the operational monitoring daemon listens on localhost. To make the daemon available to security servers on other hosts, the listening address must be set to the IP address that is relevant in the particular network, as described in the previous section.

As advised, the scheme parameter should be set to "https". For communication over HTTPS, the security server and the operational monitoring daemon must know each other's TLS certificates to enable the security server to authenticate to the monitoring daemon successfully.

**NOTE:** If an external operational monitoring daemon is used, the host, scheme (and optionally, port) parameters must be changed at both hosts.

The internal TLS certificate of the security server is used for authenticating the security server to the operational monitoring daemon. This certificate has been generated beforehand, during the installation process of the security server, and is available in PEM format in the file `/etc/xroad/ssl/internal.crt`. Please refer to Section [10.3](#103-changing-the-internal-tls-key-and-certificate) for the instructions on exporting the internal TLS certificate from UI. The file must be copied to the host running the operational monitoring daemon. The system user xroad must have permissions to read this file.

In the configuration of the external daemon, the corresponding path must be set in `/etc/xroad/conf.d/local.ini`:

    [op-monitor]
    client-tls-certificate = <path/to/security/server/internal/cert>

Next, a TLS key and the corresponding certificate must be generated on the host of the external monitoring daemon as well, using the command

    generate-opmonitor-certificate

The script will prompt you for standard fields for input to TLS certificates and its output (key files and the certificate) will be generated to the directory `/etc/xroad/ssl`.

The generated certificate, in the file `opmonitor.crt`, must be copied to the corresponding security server. The system user `xroad` must have permissions to read this file. Its path at the security server must be written to the configuration (note the name of the section, although it is the proxy service that will read the configuration):

    [op-monitor]
    tls-certificate = <path/to/external/daemon/tls/cert>

For the external operational daemon to be used, the proxy service at the security server must be restarted:

    service xroad-proxy restart

In addition, on the host running the corresponding security server, the operational monitoring daemon must be stopped:

    service xroad-opmonitor stop

For the service to stay stopped after reboot the following command should be run:

    echo manual > /etc/init/xroad-opmonitor.override

The configuration anchor (renamed as `configuration-anchor.xml`) file must be manually copied into the directory `/etc/xroad` of the external monitoring daemon in order for configuration client to be able to download the global configuration (by default configuration download interval is 60 seconds). The system user xroad must have permissions to read this file.


#### 15.2.6 Monitoring Health Data over JMXMP

The operational monitoring daemon makes health data available over the JMXMP protocol. The Zabbix monitoring software can be configured to gather that data periodically, using its built in JMX interface type.

By default, the operational monitoring daemon JMXMP is disabled. JMXMP must be enabled for external tools such as Zabbix to be able to access the data. Please refer to the documentation at \[[JMX](#Ref_JMX)\] for instructions on configuring access to the JMX interface of the operational monitoring daemon.

For Zabbix to be able to gather data over JMX, the Zabbix Java gateway must be installed. See \[[ZABBIX-GATEWAY](#Ref_ZABBIX-GATEWAY)\] for instructions.

The JMX interface must be configured to each host item in Zabbix, for which health data needs to be obtained. See \[[ZABBIX-JMX](#Ref_ZABBIX-JMX)\] for instructions.

Please refer to \[[PR-OPMONJMX](#Ref_PR-OPMONJMX)\] for a specification of the names and attributes of the JMX objects exposed by the operational monitoring daemon.

The xroad-opmonitor package comes with sample host data that can be imported to Zabbix, containing a JMX interface, applications related to sample services and health data items under these services. Also, a script is provided for importing health data related applications and items to several hosts using the Zabbix API. Please find the example files in the directory `/usr/share/doc/xroad-opmonitor/examples/zabbix/`. Please refer to \[[ZABBIX-API](#Ref_ZABBIX-API)\] for information on the Zabbix API.


## 16 Environmental Monitoring

Environmental monitoring provides details of the security servers such as operating system, memory, disk space, CPU load, running processes and installed packages, etc.


### 16.1 Usage via SOAP API

Environmental monitoring provides SOAP API via X-Road message protocol extension. SOAP messages are described in \[[PR-ENVMONMES](#Ref_PR-ENVMONMES)\].

Monitoring extension schema is defined in \[[MONITORING_XSD](#Ref_MONITORING_XSD)\].


### 16.2 Usage via JMX API

Environmental monitoring provides also a standard JMX endpoint which can be accessed with any JMX client (for example Java's jconsole application). See \[[ARC-ENVMON](#Ref_ARC-ENVMON)\] for details.

JMX is disabled on default. JMX is enabled by adding standard JMX-related options to the executable java process as in example by \[[ZABBIX-JMX](#Ref_ZABBIX-JMX)\].

### 16.3 Limiting environmental monitoring remote data set

It is possibility to limit what allowed non-owners can request via environmental monotiring data request by changing monitor-env limit-remote-data-set parameter. By changing flag to be true non-owners who are allowed to query environmental monitoring data will get only certificate, operating system and xroad version information. This parameters is set by default false. Security server owner will always get full data set as requested.

## 17 Logs and System Services

**To read logs**, a user must have root user's rights or belong to the `xroad` and/or `adm` system group.


### 17.1 System Services

The most important system services of a security server are as follows.

 **Service**              | **Purpose**                                             | **Log**
------------------------- | ------------------------------------------------------  | -----------------------------------------
 `xroad-addon-messagelog` | Message log archiving and cleaning of the message logs  | `/var/log/xroad/messagelog-archiver.log`
 `xroad-confclient`       | Client process for the global configuration distributor | `/var/log/xroad/configuration_client.log`
 `xroad-proxy`            | Message exchanger                                       | `/var/log/xroad/proxy.log`
 `xroad-signer`           | Manager process for key settings                        | `/var/log/xroad/signer.log`
 `xroad-proxy-ui-api`     | Management UI and REST API                              | `/var/log/xroad/proxy_ui_api.log` and <br/>`/var/log/xroad/proxy_ui_api_access.log` 
 `xroad-monitor`          | Environmental monitoring                                | `/var/log/xroad/monitor.log`
 `xroad-opmonitor`        | Operational monitoring                                  | `/var/log/xroad/op-monitor.log`
 
System services are managed through the *systemd* facility.

**To start a service**, issue the following command as a `root` user:

    service <service> start

**To stop a service**, enter:

    service <service> stop

Services use the [default unit start rate limits](https://www.freedesktop.org/software/systemd/man/systemd-system.conf.html#DefaultStartLimitIntervalSec=).
An exception to this is `xroad-proxy-ui-api`, which uses a longer start rate limit ([5 starts / 40 seconds](https://github.com/nordic-institute/X-Road/blob/master/src/packages/src/xroad/ubuntu/generic/xroad-proxy-ui-api.service#L5-6))
to prevent infinite restart-loop in some specific error situations.

### 17.2 Logging configuration

For logging, the **Logback** system is used. Logback configuration files are stored in the directory `/etc/xroad/conf.d/`.

Default settings for logging are the following:

-   logging level: INFO;

-   rolling policy: whenever the file size reaches 100 MB.


### 17.3 Fault Detail UUID

In case a security server encounters an error condition during the message exchange, the security server returns a SOAP Fault message \[[PR-MESS](#Ref_PR-MESS)\] containing a UUID (a universally unique identifier, e.g. `1328e974-4fe5-412c-a4c4-f1ac36f20b14`) as the fault detail to the service client's information system. The UUID can be used to find the details of the occurred error from the `xroad-proxy` log.

## 18 Federation

Federation allows security servers of two different X-Road instances to exchange messages with each other. The instances
are federated at the central server level. After this, security servers can be configured to opt-in to the federation.
By default, federation is disabled and configuration data for other X-Road instances will not be downloaded.

The federation can be allowed for all X-Road instances that the central server offers, or a list of specific
(comma-separated) instances. The default is to allow none. The values are case-insensitive.

To override the default value, edit the file `/etc/xroad/conf.d/local.ini` and add or change the value of the system
parameter `allowed-federations` for the server component `configuration-client`. To restore the default, either remove
the system parameter entirely or set the value to `none`. X-Road services `xroad-confclient` and `xroad-proxy` need to
be restarted (in that order) for any setting changes to take effect.

Below are some examples for `/etc/xroad/conf.d/local.ini`.

To allow federation with all offered X-Road instances:
```ini
[configuration-client]
allowed-federations=all
```

To allow federation with specific instances `xe-test` and `ee-test`:
```ini
[configuration-client]
allowed-federations=xe-test,ee-test
```

To disable federation, just remove the `allowed-federations` system parameter entirely or use:
```ini
[configuration-client]
allowed-federations=none
```

Please note that if the keyword `all` is present in the comma-separated list, it will override the single allowed
instances. The keyword `none` will override all other values. This means that the following setting will allow all
federations:
```ini
[configuration-client]
allowed-federations=xe-test, all, ee-test
```
And the following will allow none:
```ini
[configuration-client]
allowed-federations=xe-test, all, none, ee-test
```

## 19 Management REST API

Security server has a REST API that can be used to do all the same server configuration operations that can be done
using the web UI.

Management REST API is protected with an API key based authentication. To execute REST calls, API keys need to be created.

REST API is protected by TLS. Since server uses self signed certificate, the caller needs to accept this (for example
with `curl` you might use `--insecure` or `-k` option).

Requests sent to REST API have a *limit for maximum size*. If a too large request is sent
to REST API, it will not be processed, and http status 413 Payload too large will be returned.
There is a different limit for binary file uploads, and for other requests.

Limits are
- 10MB for file uploads
- 50KB for other requests

REST API is also *rate limited*. Rate limits apply per each calling IP. If the number of calls
from one IP address exceeds the limit, endpoints return http status 429 Too Many Requests.

Limits are
- 600 requests per minute
- 20 requests per second

If the default limits are too restricting (or too loose), they can be overridden with command line arguments. Limits are set with
application properties
- `request.sizelimit.regular`
- `request.sizelimit.binary.upload`
- `ratelimit.requests.per.second`
- `ratelimit.requests.per.minute`

**Note:** These properties have been deprecated since 7.3.0, please use `request-sizelimit-*` & `rate-limit-requests-per-*` [proxy-ui-api parameters](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api) instead

Size limit parameters support formats from Formats from [DataSize](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/unit/DataSize.html),
for example `5MB`.

New command line arguments can be added, not replaced, using the configuration file `local.properties`.
Example of `/etc/xroad/services/local.properties` with modifications:

```properties
XROAD_PROXY_UI_API_PARAMS=-Dratelimit.requests.per.second=100 -Drequest.sizelimit.binary.upload=1MB
```

### 19.1 API key management operations

**Access rights:** [System Administrator](#xroad-system-administrator)

An API key is linked to a role or roles, and grants access to the operations that are allowed for that role/roles.
Separate REST endpoints exist for API key management.
API key management endpoints are authenticated to with [HTTP basic authentication](https://en.wikipedia.org/wiki/Basic_access_authentication) (username and password)
or with session authentication (for admin web application).
Basic authentication access is limited to localhost by default, but this can
be changed using System Parameters \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

#### 19.1.1 Creating new API keys

A new API key is created with a `POST` request to `/api/v1/api-keys`. Message body must contain the roles to be
associated with the key. Server responds with data that contains the actual API key. After this point the key
cannot be retrieved, as it is not stored in plaintext.

```bash
curl -X POST -u <user>:<password> https://localhost:4000/api/v1/api-keys --data '["XROAD_SECURITYSERVER_OBSERVER","XROAD_REGISTRATION_OFFICER"]' --header "Content-Type: application/json" -k
{
  "roles": [
    "XROAD_REGISTRATION_OFFICER",
    "XROAD_SECURITYSERVER_OBSERVER"
  ],
  "id": 61,
  "key": "23bc57cd-b1ba-4702-9657-8d53e335c843"
}

```

In this example the created key was `23bc57cd-b1ba-4702-9657-8d53e335c843`.

#### 19.1.2 Listing API keys

Existing API keys can be listed with a `GET` request to `/api/v1/api-keys`. This lists all keys, regardless of who has created them.

```bash
curl -X GET -u <user>:<password> https://localhost:4000/api/v1/api-keys -k
[
  {
    "id": 59,
    "roles": [
      "XROAD_REGISTRATION_OFFICER",
      "XROAD_SECURITYSERVER_OBSERVER",
      "XROAD_SERVICE_ADMINISTRATOR"
    ]
  },
  {
    "id": 60,
...

```

You can also retrieve a single API key with a `GET` request to `/api/v1/api-keys/{id}`.

```bash
curl -X GET -u <user>:<password> https://localhost:4000/api/v1/api-keys/59 -k
{
  "id": 59,
  "roles": [
    "XROAD_REGISTRATION_OFFICER",
    "XROAD_SECURITYSERVER_OBSERVER",
    "XROAD_SERVICE_ADMINISTRATOR"
  ]
}

```

#### 19.1.3 Updating API keys

An existing API key is updated with a `PUT` request to `/api/v1/api-keys/{id}`. Message body must contain the roles to be
associated with the key. Server responds with data that contains the key id and roles associated with the key.

```bash
curl -X PUT -u <user>:<password> https://localhost:4000/api/v1/api-keys/60 --data '["XROAD_SECURITYSERVER_OBSERVER","XROAD_REGISTRATION_OFFICER"]' --header "Content-Type: application/json" -k
{
  "id": 60,
  "roles": [
    "XROAD_REGISTRATION_OFFICER",
    "XROAD_SECURITYSERVER_OBSERVER"
  ]
}

```

#### 19.1.4 Revoking API keys

An API key can be revoked with a `DELETE` request to `/api/v1/api-keys/{id}`. Server responds with `HTTP 200` if
revocation was successful and `HTTP 404` if key did not exist.

```bash
curl -X DELETE -u <user>:<password> https://localhost:4000/api/v1/api-keys/60  -k

```

#### 19.1.5 API key caching

API keys are cached in memory, which is typically not a problem in standard Security Server configurations.
However, if you have multiple Security Servers configured to share the same `serverconf` database
and use multiple nodes to access the REST API and execute API key management operations, the caches of different nodes can become out of sync.

For instance, revoking an API key from `node 1` may not be recognized by `node 2`, which can still grant access to REST API endpoints with the revoked API key. To address this issue, there are a few potential solutions:

- **Option A:** Consider decreasing [time-to-live](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api) value for API key cache from the default of **60 seconds** to a more lenient value. Doing so will reduce the risk of stale values being returned, thus improving security.
- **Option B:** Direct all REST API operations to the same Security Server node.
- **Option C:** Always restart REST API modules when API key operations are executed.
- **Option D:** Disable Api key cache. (See [proxy-ui-api parameters](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api) for more details). This option will degrade API throughput and should only be used when other options do not work.

### 19.2 Executing REST calls

**Access rights:** Depends on the API.

Once a valid API key has been created, it is used by providing an `Authorization: X-Road-ApiKey token=<api key>` HTTP
header in the REST calls. For example

```bash
curl --header "Authorization: X-Road-ApiKey token=ff6f55a8-cc63-4e83-aa4c-55f99dc77bbf" "https://localhost:4000/api/v1/clients" -k
[
  {
    "id": "XRD2:GOV:999:foobar",
    "member_name": Foo Name,
    "member_class": "GOV",
    "member_code": "999",
    "subsystem_code": "SUBS_1",
    "status": "saved
...
```

The available APIs are documented in OpenAPI specification \[[REST_UI-API](#Ref_REST_UI-API)\]. Access rights for different APIs follow the same rules
as the corresponding UI operations.
Access to regular APIs is allowed from all IP addresses by default, but this can
be changed using System Parameters \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

### 19.3 Correlation ID HTTP header

The REST API endpoints return an **x-road-ui-correlation-id** HTTP header. This header is also logged in `proxy_ui_api.log`, so it
can be used to find the log messages related to a specific API call.

The correlation ID header is returned for all requests, both successful and failed ones.

For example, these log messages are related to an API call with correlation ID `3d5f193102435242`:
```
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] DEBUG o.s.s.w.c.HttpSessionSecurityContextRepository - The HttpSession is currently null, and the HttpSessionSecurityContextRepository is prohibited from creating an HttpSession (because the allowSessionCreation property is false) - SecurityContext thus not stored for next request
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] WARN  o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Resolved [org.niis.xroad.restapi.exceptions.ConflictException: local group with code koodi6 already added]
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] DEBUG o.s.s.w.a.ExceptionTranslationFilter - Chain processed normally
```

### 19.4 Validation errors

An error response from the REST API can include validation errors if an unsupported parameter was provided with the request.
When 

Example request and response of adding a new subsystem with illegal characters:
```
POST https://ss1:4100/api/v1/clients

Request body:
{
  "client": {
    "member_class": "ORG",
    "member_code": "0/1234",
    "subsystem_code": "Subsystem%Code"
  },
  "ignore_warnings": false
}

Response body:
{
  "error": {
    "code": "validation_failure",
    "validation_errors": {
      "clientAdd.client.memberCode": [
        "NoForwardslashes"
      ],
      "clientAdd.client.subsystemCode": [
        "NoPercents"
      ]
    }
  },
  "status": 400
}
```

In addition to the validation messages declared in [Java Validation API](https://javaee.github.io/javaee-spec/javadocs/javax/validation/constraints/package-summary.html), the following validation errors are possible:

Error             | Explanation
------------------|-----------
`NoControlChars`    | The provided string contains [ISO control characters](https://en.wikipedia.org/wiki/Control_character) or zero-width spaces
`NoColons`          | The provided string contains colons `:`
`NoSemicolons`      | The provided string contains semicolons `;`
`NoForwardslashes`  | The provided string contains slashes `/`
`NoBackslashes`     | The provided string contains backslashes `\`
`NoPercents`        | The provided string contains percent symbol `%`

### 19.5 Warning responses

Error response from the Management API can include additional warnings that you can ignore if seen necessary. The warnings can be ignored by your decision, by executing the same operation with `ignore_warnings` boolean parameter set to `true`. *Always consider the warning before making the decision to ignore it.* 

An example case:
1. Client executes a REST request, without `ignore_warnings` parameter, to backend.
2. Backend notices warnings and responds with error message that contains the warnings. Nothing is updated at this point.
3. Client determines if warnings can be ignored.
4. If the warnings can be ignored, client resends the REST request, but with `ignore_warnings` parameter set to `true`.
5. Backend ignores the warnings and executes the operation.

Error response with warnings always contains the error code `warnings_detected`.

Like errors, warnings contain an identifier (code) and possibly some metadata.

Warning example when trying to register a WSDL that produces non-fatal validation warnings: 
```json
{
  "status": 400,
  "error": {
    "code": "warnings_detected"
  },
  "warnings": [
    {
      "code": "wsdl_validation_warnings",
      "metadata": [
        "WSDLValidator Error : Summary: Failures: 0, Warnings: 1 <<< WARNING! Operation 'someService' in PortType: {http://test.x-road.global/some-service}someService.servicePortType has no output message"
      ]
    }
  ]
}
```

Note that when you are using the admin UI and you encounter warnings, you will always be provided with a popup window with a `CONTINUE` button in it. When you click the `CONTINUE` button in the popup, the request is sent again but this time warnings will be ignored.

## 20 Migrating to Remote Database Host

Since version `6.22.0` Security Server supports using remote databases. In case you have an already running Security Server with local database, it is possible to migrate it to use remote database host instead. The instructions for this process are listed below.

1. Shutdown X-Road processes.

    ```bash
    systemctl stop "xroad*"
    ```

2. Dump the local databases to be migrated. You can find the passwords of users `serverconf_admin`, `messagelog_admin` and `opmonitor_admin` in `/etc/xroad.properties`.Notice that the versions of the local PostgreSQL client and remote PostgreSQL server must match. Also take into account that on a busy system the messagelog database can be quite large and therefore dump and restore can take considerable amount of time and disk space. Notice that the versions of the local PostgreSQL client and remote PostgreSQL server must match. Also take into account that on a busy system the messagelog database can be quite large and therefore dump and restore can take considerable amount of time and disk space.

    ```bash
    pg_dump -F t -h 127.0.0.1 -p 5432 -U serverconf_admin -f serverconf.dat serverconf
    pg_dump -F t -h 127.0.0.1 -p 5432 -U messagelog_admin -f messagelog.dat messagelog
    pg_dump -F t -h 127.0.0.1 -p 5432 -U opmonitor_admin -f op-monitor.dat op-monitor
    ```

3. Shut down and mask local `postgresql` so it won't start when `xroad-proxy` starts.

    ```bash
    systemctl stop postgresql
    systemctl mask postgresql
    ```

4. Connect to the remote database server as the superuser `postgres` and create roles, databases and access permissions as follows.

    **serverconf** (required)
    ```bash
    psql -h <remote-db-url> -p <remote-db-port> -U postgres
    CREATE DATABASE serverconf ENCODING 'UTF8';
    REVOKE ALL ON DATABASE serverconf FROM PUBLIC;
    CREATE ROLE serverconf_admin LOGIN PASSWORD '<serverconf_admin password>';
    GRANT serverconf_admin to <superuser>;
    GRANT CREATE,TEMPORARY,CONNECT ON DATABASE serverconf TO serverconf_admin;
    \c serverconf
    CREATE EXTENSION hstore;
    CREATE SCHEMA serverconf AUTHORIZATION serverconf_admin;
    REVOKE ALL ON SCHEMA public FROM PUBLIC;
    GRANT USAGE ON SCHEMA public to serverconf_admin;
    CREATE ROLE serverconf LOGIN PASSWORD '<serverconf password>';
    GRANT serverconf to <superuser>;
    GRANT TEMPORARY,CONNECT ON DATABASE serverconf TO serverconf;
    GRANT USAGE ON SCHEMA public to serverconf;
    GRANT USAGE ON SCHEMA serverconf TO serverconf;
    GRANT SELECT,UPDATE,INSERT,DELETE ON ALL TABLES IN SCHEMA serverconf TO serverconf;
    GRANT SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA serverconf TO serverconf;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA serverconf to serverconf;
    ```

    **messagelog** (required by xroad-addon-messagelog)
    ```bash
    psql -h <remote-db-url> -p <remote-db-port> -U postgres
    CREATE DATABASE messagelog ENCODING 'UTF8';
    REVOKE ALL ON DATABASE messagelog FROM PUBLIC;
    CREATE ROLE messagelog_admin LOGIN PASSWORD '<messagelog_admin password>';
    GRANT messagelog_admin to <superuser>;
    GRANT CREATE,TEMPORARY,CONNECT ON DATABASE messagelog TO messagelog_admin;
    \c messagelog
    CREATE SCHEMA messagelog AUTHORIZATION messagelog_admin;
    REVOKE ALL ON SCHEMA public FROM PUBLIC;
    GRANT USAGE ON SCHEMA public to messagelog_admin;
    CREATE ROLE messagelog LOGIN PASSWORD '<messagelog password>';
    GRANT messagelog to <superuser>;
    GRANT TEMPORARY,CONNECT ON DATABASE messagelog TO messagelog;
    GRANT USAGE ON SCHEMA public to messagelog;
    GRANT USAGE ON SCHEMA messagelog TO messagelog;
    GRANT SELECT,UPDATE,INSERT,DELETE ON ALL TABLES IN SCHEMA messagelog TO messagelog;
    GRANT SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA messagelog TO messagelog;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA messagelog to messagelog;
    ```

    **op-monitor** (optional, required by xroad-opmonitor)

    If operational monitoring is going to be installed, run additionally the following commands. Again, the database and role names can be customized to suit your environment.

    ```bash
    psql -h <remote-db-url> -p <remote-db-port> -U postgres
    CREATE DATABASE "op-monitor" ENCODING 'UTF8';
    REVOKE ALL ON DATABASE "op-monitor" FROM PUBLIC;
    CREATE ROLE opmonitor_admin LOGIN PASSWORD '<opmonitor_admin password>';
    GRANT opmonitor_admin to <superuser>;
    GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "op-monitor" TO opmonitor_admin;
    \c "op-monitor"
    CREATE SCHEMA opmonitor AUTHORIZATION opmonitor_admin;
    REVOKE ALL ON SCHEMA public FROM PUBLIC;
    GRANT USAGE ON SCHEMA public to opmonitor_admin;
    CREATE ROLE opmonitor LOGIN PASSWORD '<opmonitor password>';
    GRANT opmonitor to <superuser>;
    GRANT TEMPORARY,CONNECT ON DATABASE "op-monitor" TO opmonitor;
    GRANT USAGE ON SCHEMA public to opmonitor;
    GRANT USAGE ON SCHEMA opmonitor TO opmonitor;
    GRANT SELECT,UPDATE,INSERT,DELETE ON ALL TABLES IN SCHEMA opmonitor TO opmonitor;
    GRANT SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA opmonitor TO opmonitor;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA opmonitor to opmonitor;
    ```

5. Restore the database dumps on the remote database host.

    ```bash
    pg_restore -h <remote-db-url> -p <remote-db-port> -U serverconf_admin -O -n serverconf -1 -d serverconf serverconf.dat
    pg_restore -h <remote-db-url> -p <remote-db-port> -U messagelog_admin -O -n messagelog -1 -d messagelog messagelog.dat
    pg_restore -h <remote-db-url> -p <remote-db-port> -U opmonitor_admin -O -n opmonitor -1 -d op-monitor op-monitor.dat
    ```

6. Create properties file `/etc/xroad.properties` containing the superuser password.

    ```bash
    sudo touch /etc/xroad.properties
    sudo chown root:root /etc/xroad.properties
    sudo chmod 600 /etc/xroad.properties
    ```

7. Edit `/etc/xroad.properties`.

    ```properties
    serverconf.database.admin_user = serverconf_admin
    serverconf.database.admin_password = <serverconf_admin password>
    messagelog.database.admin_user = messagelog_admin
    messagelog.database.admin_password = messagelog_admin password>
    op-monitor.database.admin_user = opmonitor_admin
    op-monitor.database.admin_password = <opmonitor_admin password>
    ```

8. Update `/etc/xroad/db.properties` contents with correct database host URLs and passwords.

    ```properties
    serverconf.hibernate.connection.url = jdbc:postgresql://<database host>:<port>/serverconf
    serverconf.hibernate.connection.username = serverconf
    serverconf.hibernate.connection.password = <serverconf password>
    serverconf.hibernate.hikari.dataSource.currentSchema = serverconf,public

    messagelog.hibernate.connection.url = jdbc:postgresql://<database host>:<port>/messagelog
    messagelog.hibernate.connection.username = messagelog
    messagelog.hibernate.connection.password = <messagelog password>
    messagelog.hibernate.hikari.dataSource.currentSchema = messagelog,public

    op-monitor.hibernate.connection.url = jdbc:postgresql://<database host>:<port>/op-monitor
    op-monitor.hibernate.connection.username = opmonitor
    op-monitor.hibernate.connection.password = <opmonitor password>
    op-monitor.hibernate.hikari.dataSource.currentSchema = opmonitor,public
    ```

9. Start again the X-Road services.

    ```bash
    systemctl start "xroad*"
    ```

## 21 Adding command line arguments

If you need to add command line arguments for the Security Server, for example if you wish to increase Java's maximum heap size, you can do it with the properties file `/etc/xroad/services/local.properties`. The file is also included in the backup archive file when taking a backup of the Security Server's configuration.

Example of `/etc/xroad/services/local.properties` with modifications that override the default Java memory parameters:

```properties
XROAD_PROXY_PARAMS=-Xms150m -Xmx1024m
```

All possible properties to adjust in this file are:
```
XROAD_SIGNER_PARAMS
XROAD_ADDON_PARAMS
XROAD_CONFCLIENT_PARAMS
XROAD_CONFPROXY_PARAMS
XROAD_JETTY_PARAMS
XROAD_MESSAGELOG_ARCHIVER_PARAMS
XROAD_MONITOR_PARAMS
XROAD_OPMON_PARAMS
XROAD_PROXY_PARAMS
XROAD_PROXY_UI_API_PARAMS
XROAD_SIGNER_CONSOLE_PARAMS
```

## 22 Additional Security Hardening

For the guidelines on security hardening, please refer to [UG-SEC](ug-sec_x_road_security_hardening.md).
