# X-Road: Central Server User Guide <!-- omit in toc --> 

Version: 2.41  
Doc. ID: UG-CS

## Version history <!-- omit in toc --> 

| Date       | Version | Description                                                                                                                                                                                                                                                                                                                                                                                                                             | Author              |
|------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------|
| 28.08.2014 | 0.1     | Initial version                                                                                                                                                                                                                                                                                                                                                                                                                         |                     |
| 28.09.2014 | 0.2     | Translation to English                                                                                                                                                                                                                                                                                                                                                                                                                  |                     |
| 09.10.2014 | 0.3     | Minor updates and corrections. Security Categories removed.                                                                                                                                                                                                                                                                                                                                                                             |                     |
| 09.10.2014 | 0.4     | Add service CA OCSP responder changed to Add top CA OCSP responder                                                                                                                                                                                                                                                                                                                                                                      |                     |
| 14.10.2014 | 0.5     | Title page, header, footer modified                                                                                                                                                                                                                                                                                                                                                                                                     |                     |
| 28.11.2014 | 0.6     | Logback information added (Chapter 17). Introduction added (Chapter 1). Security Officer user role added (Section 2.1). System Settings added (Chapter 4). Configuration Management added (Chapter 5). Database Management Chapter deleted.                                                                                                                                                                                             |                     |
| 1.12.2014  | 1.0     | Minor corrections                                                                                                                                                                                                                                                                                                                                                                                                                       |                     |
| 23.01.2015 | 1.1     | License information. Certification services management and time stamping services management chapters updated (Chapters 11 and 12).                                                                                                                                                                                                                                                                                                     |                     |
| 30.04.2015 | 1.2     | “sdsb” changed to “xroad”                                                                                                                                                                                                                                                                                                                                                                                                               |                     |
| 30.06.2015 | 1.3     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                                                  |                     |
| 3.07.2015  | 1.4     | Audit Log chapter added (Chapter 14)                                                                                                                                                                                                                                                                                                                                                                                                    |                     |
| 31.08.2015 | 1.5     | Information about high availability added (Chapter 3)                                                                                                                                                                                                                                                                                                                                                                                   |                     |
| 15.09.2015 | 1.6     | Reference to the audit log events added                                                                                                                                                                                                                                                                                                                                                                                                 |                     |
| 17.09.2015 | 1.7     | Corrections related to high availability added                                                                                                                                                                                                                                                                                                                                                                                          |                     |
| 18.09.2015 | 1.8     | Minor corrections done                                                                                                                                                                                                                                                                                                                                                                                                                  |                     |
| 21.09.2015 | 1.9     | References fixed                                                                                                                                                                                                                                                                                                                                                                                                                        |                     |
| 22.10.2015 | 1.10    | Corrections in Chapter 17                                                                                                                                                                                                                                                                                                                                                                                                               |                     |
| 04.11.2015 | 1.11    | Updates related to backup and restore (Chapter 13)                                                                                                                                                                                                                                                                                                                                                                                      |                     |
| 30.11.2015 | 2.0     | Management service provider configuration updated (Section 4.2); management requests system updated (Chapter 6); key label added to configuration signing key generation (Section 5.4.1); section about adding a subsystem to an X-Road member added (Section 7.3); only subsystems can be registered as Security Server clients or be members of global groups; certification service settings updated (11.1). Editorial changes made. |                     |
| 17.12.2015 | 2.1     | Added user instructions for monitoring.                                                                                                                                                                                                                                                                                                                                                                                                 |                     |
| 14.4.2016  | 2.2     | Added chapter for additional configuration options.                                                                                                                                                                                                                                                                                                                                                                                     |                     |
| 5.9.2016   | 2.3     | Added instructions for configuring OCSP fetch interval.                                                                                                                                                                                                                                                                                                                                                                                 |                     |
| 20.01.2017 | 2.4     | Added license text and version history                                                                                                                                                                                                                                                                                                                                                                                                  | Sami Kallio         |
| 05.03.2018 | 2.5     | Added terms and abbreviations reference and document links                                                                                                                                                                                                                                                                                                                                                                              | Tatu Repo           |
| 18.08.2018 | 2.6     | Corrected `ocspFetchInterval` default value (Chapter 16.2)                                                                                                                                                                                                                                                                                                                                                                              | Petteri Kivimäki    |
| 15.11.2018 | 2.7     | Minor corrections for Ubuntu 18                                                                                                                                                                                                                                                                                                                                                                                                         | Jarkko Hyöty        |
| 23.01.2019 | 2.8     | Information about automatic approval of auth cert registration requests added. Updates in Chapters 6-8.                                                                                                                                                                                                                                                                                                                                 | Petteri Kivimäki    |
| 06.02.2019 | 2.9     | Information about automatic approval of Security Server client registration requests added. Updates in Chapters 6-8.                                                                                                                                                                                                                                                                                                                    | Petteri Kivimäki    |
| 02.07.2019 | 2.10    | Security Server owner change added (Chapter 7.10)                                                                                                                                                                                                                                                                                                                                                                                       | Petteri Kivimäki    |
| 14.08.2019 | 2.11    | Added automatic backups                                                                                                                                                                                                                                                                                                                                                                                                                 | Ilkka Seppälä       |
| 11.09.2019 | 2.12    | Remove Ubuntu 14.04 support                                                                                                                                                                                                                                                                                                                                                                                                             | Jarkko Hyöty        |
| 26.11.2019 | 2.13    | Update Chapter 3 with remote database support possiblity                                                                                                                                                                                                                                                                                                                                                                                | Ilkka Seppälä       |
| 03.12.2019 | 2.14    | Remove HA setup dependency on BDR                                                                                                                                                                                                                                                                                                                                                                                                       | Jarkko Hyöty        |
| 13.03.2020 | 2.15    | Add instructions for migrating to remote database                                                                                                                                                                                                                                                                                                                                                                                       | Ilkka Seppälä       |
| 30.03.2020 | 2.16    | Added description of pre-restore backups                                                                                                                                                                                                                                                                                                                                                                                                | Ilkka Seppälä       |
| 04.08.2021 | 2.17    | Add more details about restoring configuration from the command line                                                                                                                                                                                                                                                                                                                                                                    | Ilkka Seppälä       |
| 11.08.2021 | 2.18    | Update chapter 3.2 about checking the cluster status.                                                                                                                                                                                                                                                                                                                                                                                   | Ilkka Seppälä       |
| 25.08.2021 | 2.19    | Update X-Road references from version 6 to 7                                                                                                                                                                                                                                                                                                                                                                                            | Caro Hautamäki      |
| 23.09.2022 | 2.20    | Added new Registration Web Service                                                                                                                                                                                                                                                                                                                                                                                                      | Eneli Reimets       |
| 26.09.2022 | 2.21    | Remove Ubuntu 18.04 support                                                                                                                                                                                                                                                                                                                                                                                                             | Andres Rosenthal    |
| 17.04.2023 | 2.22    | Remove central services support                                                                                                                                                                                                                                                                                                                                                                                                         | Justas Samuolis     |
| 19.04.2023 | 2.23    | Removed unused properties from db.properties                                                                                                                                                                                                                                                                                                                                                                                            | Mikk-Erik Bachmannn |
| 19.05.2023 | 2.24    | New Central Server updates                                                                                                                                                                                                                                                                                                                                                                                                              | Eneli Reimets       |
| 01.06.2023 | 2.25    | Update references                                                                                                                                                                                                                                                                                                                                                                                                                       | Petteri Kivimäki    |
| 31.05.2023 | 2.26    | Added 3.3 API key considerations in High-Availability setup  paragraph                                                                                                                                                                                                                                                                                                                                                                  | Ričardas Bučiūnas   |
| 05.06.2023 | 2.27    | Update HA cluster status endpoint path                                                                                                                                                                                                                                                                                                                                                                                                  | Andres Rosenthal    |
| 02.06.2023 | 2.28    | Added security hardening paragraph                                                                                                                                                                                                                                                                                                                                                                                                      | Ričardas Bučiūnas   |
| 09.06.2023 | 2.29    | Added REST API paragraph                                                                                                                                                                                                                                                                                                                                                                                                                | Vytautas Paliliūnas |
| 19.06.2023 | 2.30    | Remove table schema_migrations                                                                                                                                                                                                                                                                                                                                                                                                          | Eneli Reimets       |
| 28.06.2023 | 2.31    | Update database properties to match new Spring datasource style                                                                                                                                                                                                                                                                                                                                                                         | Raido Kaju          |
| 10.07.2023 | 2.32    | Update system services                                                                                                                                                                                                                                                                                                                                                                                                                  | Petteri Kivimäki    |
| 11.07.2023 | 2.33    | Minor updates                                                                                                                                                                                                                                                                                                                                                                                                                           | Petteri Kivimäki    |
| 20.11.2023 | 2.34    | Security server address change management request                                                                                                                                                                                                                                                                                                                                                                                       | Justas Samuolis     |
| 09.12.2023 | 2.35    | Minor updates                                                                                                                                                                                                                                                                                                                                                                                                                           | Petteri Kivimäki    |
| 09.12.2023 | 2.36    | Management service TLS certificate                                                                                                                                                                                                                                                                                                                                                                                                      | Eneli Reimets       |
| 12.12.2023 | 2.37    | Add a reference to LDAP configuration in Security Server guide                                                                                                                                                                                                                                                                                                                                                                          | Ričardas Bučiūnas   |
| 12.12.2023 | 2.38    | Client subsystem disabling and enabling management requests                                                                                                                                                                                                                                                                                                                                                                             | Madis Loitmaa       | 
| 15.12.2023 | 2.39    | Publishing global configuration over HTTPS                                                                                                                                                                                                                                                                                                                                                                                              | Eneli Reimets       |
| 20.12.2023 | 2.40    | Automatic configuration signing key rotation                                                                                                                                                                                                                                                                                                                                                                                            | Andres Rosenthal    |
| 19.01.2024 | 2.41    | Minor updates                                                                                                                                                                                                                                                                                                                                                                                                                           | Eneli Reimets       |
## Table of Contents <!-- omit in toc --> 

<!-- toc -->
- [License](#license)
- [1. Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
  - [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
  - [1.3 References](#13-references)
- [2. User and Role Management](#2-user-and-role-management)
  - [2.1 User Roles](#21-user-roles)
  - [2.2 Managing the Users](#22-managing-the-users)
  - [2.3 LDAP User Authentication](#23-ldap-user-authentication)
  - [2.4 Managing API Keys](#24-managing-api-keys)
- [3. Standalone and High-Availability Systems](#3-standalone-and-high-availability-systems)
  - [3.1 Detecting the Type of Deployment in the User Interface](#31-detecting-the-type-of-deployment-in-the-user-interface)
  - [3.2 Checking the Status of the Nodes of the Cluster](#32-checking-the-status-of-the-nodes-of-the-cluster)
  - [3.3 API key considerations in High-Availability setup](#33-api-key-considerations-in-high-availability-setup)
- [4. System Settings](#4-system-settings)
  - [4.1 Managing the Member Classes](#41-managing-the-member-classes)
  - [4.2 Configuring the Management Service Provider](#42-configuring-the-management-service-provider)
    - [4.2.1 Appointing the Management Service Provider](#421-appointing-the-management-service-provider)
    - [4.2.2 Registering the Management Service Provider as a Security Server Client](#422-registering-the-management-service-provider-as-a-security-server-client)
    - [4.2.3 Configuring the Management Services in the Management Services’ Security Server](#423-configuring-the-management-services-in-the-management-services-security-server)
  - [4.3 Configuring the Central Server Address](#43-configuring-the-central-server-address)
    - [4.3.1 Notes on HA Setup](#431-notes-on-ha-setup)
    - [4.3.2 Changing the Central Server Address](#432-changing-the-central-server-address)
  - [4.4 Managing the TLS certificates](#44-managing-the-tls-certificates)
    - [4.4.1 Registration and Management Service TLS certificate](#441-registration-and-management-service-tls-certificate)
      - [4.4.1.1 Necessary activities after changing certificate](#4411-necessary-activities-after-changing-certificate) 
- [5. Configuration Management](#5-configuration-management)
  - [5.1 Viewing the Configuration Settings](#51-viewing-the-configuration-settings)
  - [5.2 Downloading the Configuration Anchor](#52-downloading-the-configuration-anchor)
  - [5.3 Re-Creating the Configuration Anchor](#53-re-creating-the-configuration-anchor)
  - [5.4 Changing the Configuration Signing Keys](#54-changing-the-configuration-signing-keys)
    - [5.4.1 Generating a Configuration Signing Key](#541-generating-a-configuration-signing-key)
    - [5.4.2 Activating a Configuration Signing Key](#542-activating-a-configuration-signing-key)
    - [5.4.3 Deleting a Configuration Signing Key](#543-deleting-a-configuration-signing-key)
  - [5.5 Managing the Contents of a Configuration Part](#55-managing-the-contents-of-a-configuration-part)
  - [5.6 Uploading a Trusted Anchor](#56-uploading-a-trusted-anchor)
  - [5.7 Viewing the Contents of a Trusted Anchor](#57-viewing-the-contents-of-a-trusted-anchor)
  - [5.8 Deleting a Trusted Anchor](#58-deleting-a-trusted-anchor)
  - [5.9 Publishing global configuration over HTTPS](#59-publishing-global-configuration-over-https)
- [6. The Management Requests System](#6-the-management-requests-system)
  - [6.1 Registration Requests](#61-registration-requests)
    - [6.1.1 State Model for Registration Requests](#611-state-model-for-registration-requests)
  - [6.2 Deletion Requests](#62-deletion-requests)
  - [6.3 Address Change Request](#63-address-change-request)
  - [6.4 Temporarily Disabling Client Requests](#64-temporarily-disabling-client-requests)
  - [6.5 Viewing the Management Request Details](#65-viewing-the-management-request-details)
- [7 Managing the X-Road Members](#7-managing-the-x-road-members)
  - [7.1 Adding a Member](#71-adding-a-member)
  - [7.2 Viewing the Member Details](#72-viewing-the-member-details)
  - [7.3 Adding a Subsystem to an X-Road Member](#73-adding-a-subsystem-to-an-x-road-member)
  - [7.4 Registering a Member's Security Server](#74-registering-a-members-security-server)
  - [7.5 Registering a Client to a Security Server](#75-registering-a-client-to-a-security-server)
  - [7.6 Removing a Client from a Security Server](#76-removing-a-client-from-a-security-server)
  - [7.7 Changing the Owner of Security Server](#77-changing-the-owner-of-security-server)
  - [7.8 Deleting a Subsystem](#78-deleting-a-subsystem)
  - [7.9 Deleting an X-Road Member](#79-deleting-an-x-road-member)
- [8. Managing the Security Servers](#8-managing-the-security-servers)
  - [8.1 Viewing the Security Server Details](#81-viewing-the-security-server-details)
  - [8.2 Changing the Security Server Address](#82-changing-the-security-server-address)
  - [8.3 Registering a Security Server's Authentication Certificate](#83-registering-a-security-servers-authentication-certificate)
  - [8.4 Deleting a Security Server's Authentication Certificate](#84-deleting-a-security-servers-authentication-certificate)
  - [8.5 Deleting a Security Server](#85-deleting-a-security-server)
- [9. Managing the Global Groups](#9-managing-the-global-groups)
  - [9.1 Adding a Global Group](#91-adding-a-global-group)
  - [9.2 Viewing the Global Group Details](#92-viewing-the-global-group-details)
  - [9.3 Changing the Description of a Global Group](#93-changing-the-description-of-a-global-group)
  - [9.4 Changing the Members of a Global Group](#94-changing-the-members-of-a-global-group)
  - [9.5 Deleting a Global Group](#95-deleting-a-global-group)
- [10. Managing the Approved Certification Services](#10-managing-the-approved-certification-services)
  - [10.1 Adding an Approved Certification Service](#101-adding-an-approved-certification-service)
  - [10.2 Changing an Approved Certification Service](#102-changing-an-approved-certification-service)
  - [10.3 Deleting an Approved Certification Service](#103-deleting-an-approved-certification-service)
- [11. Managing the Approved Timestamping Services](#11-managing-the-approved-timestamping-services)
  - [11.1 Adding an Approved Timestamping Service](#111-adding-an-approved-timestamping-service)
  - [11.2 Changing an Approved Timestamping Service](#112-changing-an-approved-timestamping-service)
  - [11.3 Deleting an Approved Timestamping Service](#113-deleting-an-approved-timestamping-service)
- [12. Configuration Backup and Restore](#12-configuration-backup-and-restore)
  - [12.1 Backing Up the System Configuration](#121-backing-up-the-system-configuration)
  - [12.2 Restoring the System Configuration in the User Interface](#122-restoring-the-system-configuration-in-the-user-interface)
  - [12.3 Restoring the Configuration from the Command Line](#123-restoring-the-configuration-from-the-command-line)
  - [12.4 Downloading, Uploading and Deleting Configuration Backup Files](#124-downloading-uploading-and-deleting-configuration-backup-files)
  - [12.5 Automatic Backups](#125-automatic-backups)
  - [12.6 Backup Encryption Configuration](#126-backup-encryption-configuration)
  - [12.7 Verifying Backup Archive Consistency](#127-verifying-backup-archive-consistency)
- [13. Audit Log](#13-audit-log)
  - [13.1 Changing the Configuration of the Audit Log](#131-changing-the-configuration-of-the-audit-log)
  - [13.2 Archiving the Audit Log](#132-archiving-the-audit-log)
- [14. Monitoring](#14-monitoring)
- [15. Additional configuration options](#15-additional-configuration-options)
  - [15.1 Verify next update](#151-verify-next-update)
  - [15.2 OCSP fetch interval](#152-ocsp-fetch-interval)
- [16. Logs and System Services](#16-logs-and-system-services)
- [17 Management REST API](#17-management-rest-api)
  - [17.1 API key management operations](#171-api-key-management-operations)
    - [17.1.1 Creating new API keys](#1711-creating-new-api-keys)
    - [17.1.2 Listing API keys](#1712-listing-api-keys)
    - [17.1.3 Updating API keys](#1713-updating-api-keys)
    - [17.1.4 Revoking API keys](#1714-revoking-api-keys)
  - [17.2 Executing REST calls](#172-executing-rest-calls)
  - [17.3 Correlation ID HTTP header](#173-correlation-id-http-header)
  - [17.4 Data Integrity errors](#174-data-integrity-errors)
  - [17.5 Warning responses](#175-warning-responses)
- [18. Migrating to Remote Database Host](#18-migrating-to-remote-database-host)
  - [19 Additional Security Hardening](#19-additional-security-hardening)
<!-- tocstop -->

# License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# 1. Introduction

## 1.1 Target Audience

The intended audience of this User Guide are X-Road Central Server administrators who are responsible for everyday management of the X-Road Central Server.

Instructions for the installation and initial configuration of the Central Server can be found in the Central Server Installation Guide [CSI](#13-references). Instructions for installing the Central Server in a cluster for achieving high availability can be found in the Central Server High Availability Installation Guide [IG-CSHA](#13-references).

## 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

## 1.3 References

1. [CSI] X-Road 7. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md)
2. [IG-CSHA] X-Road 7. Central Server High Availability Installation Guide. Document ID: [IG-CSHA](ig-csha_x-road_6_ha_installation_guide.md)
3. [JSON] Introducing JSON, [http://json.org/](http://json.org/)
4. [SPEC-AL] X-Road: Audit log events. Document ID: SPEC-AL
5. [SSI] X-Road 7. Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)
6. [IG-CS] X-Road 7. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md)
7. [UC-GCONF] X-Road 7: Use Case Model for Global Configuration Distribution. Document ID: [UC-GCONF](../UseCases/uc-gconf_x-road_use_case_model_for_global_configuration_distribution_1.4_Y-883-8.md)
8. [RFC-OCSP] Online Certificate Status Protocol – OCSP, [https://tools.ietf.org/html/rfc6960](https://tools.ietf.org/html/rfc6960)
9. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
10. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR](../Manuals/ug-syspar_x-road_v6_system_parameters.md).
11. <a id="Ref_REST_UI-API" class="anchor"></a>\[REST_UI-API\] X-Road Central Server Admin API OpenAPI Specification: <https://github.com/nordic-institute/X-Road/blob/develop/src/central-server/openapi-model/src/main/resources/openapi-definition.yaml>.
12. [UG-SS] X-Road 7. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)

# 2. User and Role Management

## 2.1 User Roles

The Central Server supports the following user roles:

- <a id="xroad-registration-officer" class="anchor"></a>**Registration Officer** (`xroad-registration-officer`) is responsible for handling the information about X-Road members.
- <a id="xroad-system-administrator" class="anchor"></a>**System Administrator** (`xroad-system-administrator`) is responsible for the installation, configuration, and maintenance of the Central Server.
- <a id="xroad-security-officer" class="anchor"></a>**Security Officer** (`xroad-security-officer`) is responsible for the application of the security policy and security requirements.

One user can have multiple roles, and multiple users can fulfill the same role. Each role has a corresponding system group, created upon the installation of the system. The system user names are used for logging in to the user interface of the Central Server.

The document indicates in sections similar to the following example, which user role is required for performing a particular action in the user interface. For example

`Access rights: System Administrator`

Caution: If the logged-in user does not have a permission to carry out a task, the button that initiates the action is hidden (and neither is it possible to run the task using its corresponding keyboard combinations or mouse actions). Only the permitted information and actions are visible and available to the user.

## 2.2 Managing the Users

During the installation, a super user equipped with all the roles is created. You can create additional users that have restricted rights. User management is carried out in root user's permissions using the command line.

To add a new user, issue the command:

`adduser username`

To grant permissions to the user you created, add it to the corresponding system groups, for example:

`adduser username xroad-registration-officer`<br>
`adduser username xroad-system-administrator`<br>
`adduser username xroad-security-officer`

To remove a user’s permission, remove the user from the corresponding system group, for example:

`deluser username xroad-registration-officer`

To remove a user, enter:

`deluser username`

## 2.3 LDAP user authentication

X-Road leverages PAM (Pluggable Authentication Modules) for user authentication, which facilitates LDAP integration.

A detailed setup guide can be found under security server user guide [2.3 LDAP user authentication](ug-ss_x-road_6_security_server_user_guide.md#23-ldap-user-authentication).
Please note that x-road property path will be different in case of Central Server. Refer to [ug-syspar_x-road_v6_system_parameters.md](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service) for relevant properties.

## 2.4 Managing API Keys

API keys are used to authenticate API calls to Central Server's management REST API. API keys are associated with roles that define the permissions granted to the API key. If an API key is lost, it can be revoked.

In addition to the user roles, an API key can be added to the Management Services role. The Management Services role is used for registration and management services to authenticate and successfully communicate with the management REST API. Registration and management services are automatically configured with valid API keys during installation.

Access rights: System Administrator

To create API key, follow these steps.

1. In the Navigation tabs, select Settings --> API Keys, click Create API key.
2. In the window that opens, check selected roles checkbox and then click Next.
3. In the next window click Create Key.
4. Copy and save the API key in a secure place. _The API key is visible only at the time of key generation. It will not be presented again and cannot be retrieved later_.
5. Click Finish.

To edit API key related roles, follow these steps.

1. In the Navigation tabs, select Settings --> API Keys.
2. Select a API key and click Edit.
3. In the window that opens, check selected roles checkbox and click Save.

To revoke API key from roles, follow these steps.

1. In the Navigation tabs, select Settings --> API Keys.
2. Select a API key and click Revoke key. 
3. Confirm the revoking by clicking Yes.

# 3. Standalone and High-Availability Systems

The Central Server can be installed and configured in several ways:
- A standalone server with local database
- A standalone server with remote database
- A cluster of Central Servers (nodes) using a remote database. The system continues to function if one or more of the Central Server nodes are experiencing problems or are down for maintenance. If the database is highly available (e.g using hot-standby with automatic fail-over or multi-master replication), the system can also recover from database problems with minimal downtime.

When the system is configured with the most basic option standalone server with local database, there is no high-availability support. If either the web server or the database server break, the system goes down.

In the case of an HA setup, the Central Servers share configuration via an optionally highly-available database. While most of the system settings described in this document apply to the whole cluster, some have a meaning that is local to each node. In addition, all the configuration signing keys are local to each node and must be generated separately. This distinction will be stated explicitly throughout this document, where necessary.

In an HA setup, if the system is configured using different nodes in parallel, the effect will be similar to several people updating the configuration of a standalone server at the same time.

## 3.1 Detecting the Type of Deployment in the User Interface

In order to detect the type of deployment and the name of the node in the cluster in the case of HA setup, the logged-in user should check the instance identifier displayed in the left upper corner of the user interface. In the case of an HA setup, the name of the node is displayed in the right upper corner of the user interface.

## 3.2 Checking the Status of the Nodes of the Cluster

Access rights: Registration Office, System Administrator, Security Officer

In order to check the status of the nodes in an HA setup, execute the following command on the Central Server node's command line:

```bash
curl --header "Authorization: X-Road-ApiKey token=<api key>" -k https://localhost:4000/api/v1/system/high-availability-cluster/status`
```

**Note:** This endpoint requires authentication which can be provided with a valid API KEY (with at least one of the aforementioned roles) in the `Authorization` header of the request. See [Managing API Keys](#23-managing-api-keys) for instructions regarding setting up an API KEY.

## 3.3 API key considerations in High-Availability setup

API keys are cached in memory, which is typically not a problem in non-clustered Central Server configuration.
However, in case of High-Availability setup, the caches of different nodes can become out of sync.

For instance, revoking an API key from `node 1` may not be recognized by `node 2`, which can still grant access to REST API endpoints with the revoked API key. To address this issue, there are a few potential solutions:

- **Option A:** Consider decreasing [time-to-live](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service) value for API key cache from the default of **60 seconds** to a more lenient value. Doing so will reduce the risk of stale values being returned, thus improving security.
- **Option B:** Direct all REST API operations to the same Central Server node.
- **Option C:** Always restart REST API modules when API key operations are executed.
- **Option D:** Disable Api key cache. (See [admin-service parameters](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service) for more details). This option will degrade API throughput and should only be used when other options do not work.

# 4. System Settings
## 4.1 Managing the Member Classes

Access rights: Security Officer

To add a member class, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the Member Classes section and click Add.
3. In the window that opens, enter the member class code and description. Click Save.

To edit the description of a member class, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the Member Classes section, select a member class and click Edit.
3. In the window that opens, enter the member class description and click Save.

To delete a member class, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the Member Classes section, select a member class and click Yes.

Only the member classes that are used by none of the X-Road members can be deleted.

## 4.2 Configuring the Management Service Provider

The Central Server provides management services to the Security Servers that are part of the (local) X-Road infrastructure (see Chapter 6).

A subsystem of an X-Road member acting as a service provider for the management services must be appointed in the Central Server (see 4.2.1), registered as a client of the management services’ Security Server (see 4.2.2) and configured to provide the services in the management services’ Security Server (see 4.2.3).

The management services’ Security Server must be installed and registered in the Central Server before the management service provider can be registered as a client of the Security Server and the management services can be configured (see [SSI](#13-references)).

### 4.2.1 Appointing the Management Service Provider

Access rights: Security Officer

To appoint the management service provider in the Central Server, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the Management Services section and click Edit on the Service Provider Identifier field.
3. In the window that opens, find the subsystem of an X-Road member to be appointed as the management service provider and check the subsystem checkbox and then click Select.

### 4.2.2 Registering the Management Service Provider as a Security Server Client

The management service provider can be registered as a Security Server client as described in this section only if the management service provider is not registered as a client of any Security Servers. In case the management service provider is already a client of a Security Server then the Edit button is not shown next to the identifier of the Management Services' Security Server.

To register the appointed management service provider as a Security Server client to the management services’ Security Server, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the Management Services section and click Edit button next to the identifier of the Management Services' Security Server.
3. In the window that opens, find the Security Server that will be used as the management services’ Security Server and check checkbox.
4. Click Select button to submit the registration request.

On successful registration the identifier of the management services’ Security Server is displayed and Edit button should hide.

### 4.2.3 Configuring the Management Services in the Management Services’ Security Server

Access rights: Security server’s Service Administrator

The data necessary for configuring the management services in the Security Server can be found at the Central Server Settings tab -> System Settings -> Management Services section.

To configure management services in the management services’ Security Server, follow these steps.

1. In the Clients tab of the Security Server, select the client who will provide the management services. On the details view click Services sub-tab.
2. Click Add WSDL, enter the management services WSDL address in the window that opens and click Add.
3. Expand the WSDL, by clicking the > icon, select a service by click Service Code.
4. In the window that opens, enter the management services address. If necessary, edit other service parameters. Check the Apply to All in WSDL checkbox and click Save. Ensure that the parameters of all the management services have changed.
5. Activate the management service’s WSDL by selecting the row of the WSDL and clicking Enable.
6. Navigate to the Service Clients tab.
7. Click Add subject and search for the global group security-server-owners. Select the group and click Next.
8. In the window that opens, check management services checkboxes (authCertDeletion, clientDeletion, clientReg, ownerChange) and click Add Selected to add the security-server-owners group’s access rights list.

## 4.3 Configuring the Central Server Address

Access rights: Security Officer

In the System Settings view (Settings tab --> System Settings), the Central Server's public DNS name or its external IP address is displayed. This address is used by the Security Servers to access the services provided by the Central Server (configuration download, management services).

**ATTENTION!** When the Central Server address is changed,

- the management services address in the management services’ Security Server needs to be reconfigured,
- the internal configuration anchor need to be redistributed to the Security Server administrators and
- the external configuration anchor needs to be redistributed to the federation partners.

The services provided by the Central Server must be available from both the new and old address, until all servers using the services have uploaded the configuration anchor containing the new address.

### 4.3.1 Notes on HA Setup

In an HA setup, the address of the Central Server is local to the node that is being configured.

In an HA setup, internal and external configuration anchors contain information about each Central Server that is part of the cluster. If the address of one of the servers is changed, configuration anchors will be re-generated automatically on all the nodes.

### 4.3.2 Changing the Central Server Address

To change the Central Server address, follow these steps.

1. In the Navigation tabs, select Settings --> System Settings.
2. Locate the System Parameters section and click Edit.
3. Enter the Central Server’s address and click Save. When the address is changed, the system:
  - changes the management services WSDL address,
  - changes the management services address,
  - changes the configuration source addresses,
  - generates new configuration anchors for the internal and external configuration sources.
4. After the Central Server address is changed, act as follows.
  - Download the internal configuration source anchor and distribute the anchor along with the anchor’s hash value to the Security Server administrators of the local X-Road infrastructure.
  - In case of federated X-Road systems, download the external configuration source anchor and distribute the anchor along with the anchor’s hash value to the federation partners.
  - Reconfigure the management services addresses in the management service Security Server.

## 4.4 Managing the TLS certificates

Access rights: Security Officer

### 4.4.1 Registration and Management Service TLS certificate

Registration and Management Service TLS certificate is used to secure the communication between:
- the management Security Server and the member management web service;
- a Security Server and the registration web service.

To see Registration and Management Service TLS certificate info, follow these steps.

1. In the Navigation tabs, select Settings --> TLS certificates.
2. Locate the Management service TLS certificate section and click certificate hash.

To download Management Service TLS certificate, follow these steps.

1. In the Navigation tabs, select Settings --> TLS certificates.
2. Locate the Management service TLS certificate section.
3. Click button Download certificate and save the prompted file.

To re-create Management Service key and self-signed certificate, follow these steps.

1. In the Navigation tabs, select Settings --> TLS certificates.
2. Locate the Management service TLS certificate section and click button Re-create key.
3. Confirm the re-creating by clicking Confirm.
4. Complete the activities defined in section [4.4.1.1 Necessary activities after changing certificate](#4411-necessary-activities-after-changing-certificate)

To generate Management Service certificate signing request, follow these steps.

1. In the Navigation tabs, select Settings --> TLS certificates.
2. Locate the Management service TLS certificate section and click button Generate CSR.
3. Read the information and enter Distinguished name and click button Generate CSR.
4. Save prompted file into a safe place.
5. Apply for a TSL/SSL certificate from a trusted Certificate Authority (CA) using the CSR file.

To upload Management Service certificate, follow these steps.

1. In the Navigation tabs, select Settings --> TLS certificates.
2. Locate the Management service TLS certificate section and click button Upload certificate.
3. Find the proper certificate file and click Open, to finish certificate uploading click button Upload.
4. Complete the activities defined in section [4.4.1.1 Necessary activities after changing certificate](#4411-necessary-activities-after-changing-certificate)

#### 4.4.1.1 Necessary activities after changing certificate

When the key and certificate are rotated, and mTLS is enabled between the management Security Server and the management services, the new certificate must be updated to the management Security Server. To add new certificate follow Security Server User Guide [UG-SS](#13-references) instruction in section "Managing Information System TLS Certificates".

**ATTENTION!** 
- The changed TLS certificate is added in the global configuration `private-params.xml` part. The global configuration generation interval on the Central Server and the global configuration fetching interval on the Security Server depend on the system parameters. The system parameters are specified in the [UG-SYSPAR](#13-references) section "Center parameters: [admin-service]" and "Configuration Client parameters: [configuration-client]". With the default values, a new Registration and Management service TLS certificate is usable for the authentication certificate registration request on the Security Server side after ~1.5 min.
- The changed TLS certificate is automatically detected by Nginx within five minutes after the change.

# 5. Configuration Management

## 5.1 Viewing the Configuration Settings

Access rights: Security Officer, System Administrator

The Global Configuration view consists of three sub-tabs.
- The Internal Configuration view. The internal configuration is distributed by the Central Server to the Security Servers of the local X-Road infrastructure. The information needed to download and verify the internal configuration is included in the internal configuration anchor, which must be distributed to the Security Server administrators and uploaded to the Security Servers. Along with the internal configuration anchor, the anchor file hash value must be distributed. The hash value is used by the Security Server administrators to verify the integrity of the anchor file.
- The External Configuration view. The external configuration is distributed by the Central Server to the federation partners (either to the Security Servers directly or through a configuration proxy). The information needed to download and verify the external configuration is included in the external configuration anchor, which must be distributed to the federation partner’s Central Server (or configuration proxy) administrators and uploaded to the Central Server (or configuration proxy). Along with the external configuration anchor, the anchor file hash value must be distributed. The hash value is used by the federation partners to verify the integrity of the anchor file.
- The Trusted Anchors view. A trusted anchor is the configuration anchor of the configuration source(s) distributing the external configuration of a federation partner. Upon loading the trusted anchor into the Central Server, the anchor is included into the internal configuration, allowing the Security Servers to download the external configuration of a federation partner as well as the internal configuration of the local X-Road infrastructure.

## 5.2 Downloading the Configuration Anchor

Access rights: Security Officer

To download a configuration anchor, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate.
2. In the Anchor section, click Download and save the prompted file.

## 5.3 Re-Creating the Configuration Anchor

Access rights: Security Officer

Normally, the configuration anchors are generated (and in an HA setup, distributed to every node) automatically by the system upon changes in the data included in the anchor (one or more Central Server addresses, signing keys). The re-creation of an anchor is necessary mostly for recovering from error situations.

To re-create an anchor, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate.
2. In the Anchor section, click Re-create.

## 5.4 Changing the Configuration Signing Keys

Access rights: Security Officer

Key change can be either
- regular change – the key is changed periodically (for example, annually) to minimize the risk of exposure;
- emergency change – the key and all its back-ups have been destroyed or the key has been exposed.

As the key change must be carried out efficiently without disrupting the operation of X-Road, the procedure is completed in two stages, wherein the old key and the new key can exist in parallel.

Note that in an HA setup, each node has its own set of configuration signing keys. The old and new key can exist in parallel on each node. Regular key change should cover all the nodes in a cluster and the new configuration anchor should be distributed after the keys have been changed on each node.

The steps of key change are as follows:

- First, a new key is generated (on each node in HA setups) and the configuration anchor containing the public key part(s) of the key(s) is distributed to X-Road participants. Until all participants have received the public key(s), the old (i.e. current) key(s) is/are used for signing configuration.
- Then, after all participants have received & applied the new public key(s), the old key(s) is/are removed and the new key(s) is/are used to sign configuration.

To perform a regular key change, follow these steps.

1. Generate, but do not activate a new configuration signing key (see 5.4.1) (in an HA setup, for each node). The system uses the old (active) key(s) to sign the configuration. Upon the generation of a new key, the system generates a new anchor for the corresponding configuration sources.
2. If there are pre 7.4.0 Security Servers within the ecosystem (including federations) then download the anchor (see 5.2) containing the public key part(s) of the new signing key(s) and distribute the anchor along with the anchor file hash value either to these Security Servers' administrators (in case of internal configuration anchor) or to the federation partners (in case of external configuration anchor).
   > **NOTE**: Starting from version 7.4.0 new configuration signing keys are automatically distributed to Security Servers within the global configuration. Distribution will take place within two global configuration refresh cycles.
3. Activate the new signing key(s) (see 5.4.2).
The new signing key(s) should only be activated after all the affected Security Servers have received & applied the new public key (either through automatic configuration signing key rotation or uploading the distributed anchor manually). The Central Servers use the active key to sign configuration. If a server administrator has not applied the new public key before the key is activated, the verification of the downloaded configuration in the Security Servers will fail and the services exchange with the X-Road participants described in the configuration will be discontinued.
4. Delete the old signing key (in an HA setup, delete the old keys on all the nodes) (see 5.4.3). Upon the deletion of a key, the system generates a new configuration anchor.
5. Download the generated anchor (it does not contain the public key part(s) of the old signing key(s)) and distribute the anchor along with the anchor file hash value either to the Security Server administrators (in case of internal configuration anchor) or to the federation partners (in case of external configuration anchor).

To perform an emergency key change, the new key must be activated and the old key deleted immediately after the generation of the new key (in the steps described above, step 2 is skipped). The configuration anchor distributed to the Security Server administrators (in case of internal configuration anchor) or to the federation partners (in case of external configuration anchor) must only contain the public key part of the new signing key.

### 5.4.1 Generating a Configuration Signing Key

Access rights: Security Officer

To generate a configuration signing key, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate.
2. In the Signing Keys section, expand the token's information by clicking the caret next to the token name and then click Add key.
3. In a window that opens, insert signing key friendly name, and click Add.
4. Add key button is only active when the token has been logged in to.

The system will automatically generate the corresponding configuration anchor containing the public key part of the generated key.
If the generated key is the only signing key for the configuration source, the key will automatically be set as active.

### 5.4.2 Activating a Configuration Signing Key

Access rights: Security Officer

To activate a configuration signing key, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate.
2. In the Signing Keys section, expand the token's information by clicking the caret next to the token name and select an inactive key (only for an inactive key are the Activate and Delete buttons displayed) and click Activate.

### 5.4.3 Deleting a Configuration Signing Key

Access rights: Security Officer

To delete a configuration signing key, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate.
2. In the Signing Keys section,expand the token's information by clicking the caret next to the token name and select an inactive key (only for an inactive key are the Activate and Delete buttons displayed) and click Delete.
3. Confirm the deletion by clicking Confirm.

## 5.5 Managing the Contents of a Configuration Part

Access rights: Security Officer, System Administrator

The contents of a configuration part can be viewed by downloading the configuration part file. Also, configuration file can be uploaded.

To download or upload a configuration file, follow these steps.

1. In the Navigation tabs, select Global Configuration and select either the Internal Configuration or External Configuration sub-tab, as appropriate
2. In the Configuration Parts section, select a configuration part file and click Download or Upload

## 5.6 Uploading a Trusted Anchor

Access rights: Security Officer

To upload a trusted anchor, follow these steps.

1. In the Navigation tabs, select Global Configuration and select the Trusted Anchors sub-tab.
2. Click Upload button, find the external configuration anchor received from a federation partner and click Open.
3. Verify the integrity of the anchor file by comparing the displayed anchor file hash value with the hash value provided by the federation partner and confirm the anchor upload by clicking Confirm.

In case a previous anchor from the same federation partner has been uploaded to the system, the new anchor will replace the old one.

## 5.7 Viewing the Contents of a Trusted Anchor

Access rights: Security Officer, System Administrator

The contents of a trusted anchor can be viewed by downloading the anchor file.
To download an anchor file, follow these steps.

1. In the Navigation tabs, select Global Configuration and select the Trusted Anchors sub-tab.
2. In an anchor section, click Download.
3. Save or open the prompted file.

## 5.8 Deleting a Trusted Anchor

Access rights: Security Officer

To delete an anchor file, follow these steps.
1. In the Navigation tabs, select Global Configuration and select the Trusted Anchors sub-tab.
2. In the anchor section, click Delete.
3. Confirm the deletion by clicking Yes.

## 5.9 Publishing global configuration over HTTPS

Starting from version 7.4.0, the Central Server supports publishing global configuration over HTTP and HTTPS. Instead, before version 7.4.0, only HTTP was supported.

Starting from version 7.4.0, a new private key and a self-signed TLS certificate are created automatically when installing a new Central Server or upgrading an existing installation from an older version. After the installation or upgrade, the Central Server Administrator must manually apply for a TLS certificate from a trusted Certificate Authority (CA) and then configure the certificate. The CA must be trusted by the Security Server's Java installation. More information about configuring the TLS certificate on the Central Server is available in the Central Server Installation Guide [CSI](#13-references).

Applying for a TLS certificate issued by a trusted CA is required, because the Security Server does not trust the new automatically generated self-signed certificate by default. The Security Server supports disabling certificate verification, but disabling it in production environments is not recommended. More information is available in the `[configuration-client]` section of the System Parameters User Guide [UG-SYSPAR](#13-references).

When upgrading from a version < 7.4.0 to a version >= 7.4.0, the configuration anchor must be re-generated and imported to all the Security Servers to enable downloading global configuration over HTTPS.

# 6. The Management Requests System
## 6.1 Registration Requests

As the registration of associations in the X-Road governing authority is security-critical, the following measures are applied to increase security by default:

- The registration request must be submitted to the X-Road Central Server through the Security Server. Manual approval is still required by default.
- The association must be approved by the X-Road governing authority.

There are three types of registration requests:

- authentication certificate registration request (see Sections 7.4 and 8.3);
- Security Server client registration request (see Section 7.5);
- Security Server owner change request (see Section 7.7)

It is possible to streamline the registration process of authentication certificates and Security Server clients by enabling automatic approval.
 
- authentication certificate registration requests
  - When automatic approval is enabled, it is enough to submit an authentication certificate registration request to the X-Road Central Server through the Security Server, and the request will be automatically approved immediately.
  - Automatic approval is applied to existing members only.
  - By default, automatic approval of authentication certificate registration requests is disabled. It can be enabled by setting the `auto-approve-auth-cert-reg-requests` property value to `true` on Central Server.
- Security Server client registration requests
  - When automatic approval is enabled, it is enough to submit a Security Server client registration request to the X-Road Central Server through the Security Server, and the request will be automatically approved immediately.
  - Automatic approval is applied to existing members only. In addition, automatic approval is applied only if the client registration request has been signed by the member owning the subsystem to be registered as a Security Server client.
  - By default, automatic approval of Security Server client registration requests is disabled. It can be enabled by setting the `auto-approve-client-reg-requests` property value to `true` on Central Server.
- Security Server owner change requests
  - When automatic approval is enabled, it is enough to submit a Security Server owner change request to the X-Road Central Server through the Security Server, and the request will be automatically approved immediately.
  - Automatic approval is applied to existing members only.
  - By default, automatic approval of Security Server owner change requests is disabled. It can be enabled by setting the `auto-approve-owner-change-requests` property value to `true` on Central Server.
    
### 6.1.1 State Model for Registration Requests

A registration request can be in one of the following states. See Figure 1 for the state diagram.

![State diagram for registration requests](img/ug-cs_state_diagram_for_registration_requests.svg)

Figure 1. State diagram for registration requests

Pending – a registration request has been submitted from a Security Server. From this state, the request can move to the following states.
- “Approved”, if the registration request is approved in the Central Server (see 7.4, 7.5 and 8.3). The association between the objects of the registration request has been created.
- “Rejected”, if the registration request is declined in the Central Server (see 7.4, 7.5 and 8.3).
- “Revoked”.
  - Registration request received from a Security Server are automatically revoked by deletion requests sent from the Security Server for the same object that was submitted for registration with the registration request.

If automatic approval of authentication certificate registration requests, Security Server client registration requests and/or Security Server owner change requests is enabled, the request is approved automatically. Therefore, the request moves directly to Approved state.

## 6.2 Deletion Requests

Deleted requests is submitted through a Security Server or formalized in the Central Server.

Deletion requests are
- authentication certificate deletion request (see Section 8.4);
- Security Server client deletion request (see Section 7.6).

## 6.3 Address Change Request

Address change request is submitted through a Security Server to change its address. The request does not require any additional approvals on the Central Server.

## 6.4 Temporarily Disabling Client Requests

Security Server can disable client subsystem temporarily by issuing "Disable client" request. Disabled client can be enabled again to with "Enable client" request.
These requests do not require any additional approvals on Central Server.

## 6.5 Viewing the Management Request Details

Access rights: Registration Officer

To open the detail view, follow these steps.
1. In the Management Requests tab.
2. Select from the table a request and click Id field.
3. Uncheck "Show only pending requests" checkbox, if you want to see all requests.

There are three data sections in the view.

1. Information about the request.
  - Request ID – the identifier of the request;
  - Received – the date and time of saving the request in the Central Server;
  - Source – the source of the request. The request can be either submitted through a Security Server (SECURITY_SERVER) or automatically generated in the Central Server (CENTER);
  - Status (only for registration requests) – the state of the request, see Figure 1;
  - Comments – the source event for the automatic generation of the request. For example, when a Security Server is deleted from the Central Server, deletion requests are automatically generated for all the clients and authentication certificates registered for this Security Server. In the "Comments" field of the generated requests, a comment with the server identifier is added in such case. This field is left empty for requests that are not automatically generated by the Central Server.
2. Information about the Security Server associated with the request.
  - Owner Name – the name of the Security Server owner (X-Road member);
  - Owner Class – the member class of the Security Server owner;
  - Owner Code – the member code of the Security Server owner;
  - Server Code – the code of the Security Server;
  - Address – the address of the Security Server. The field is filled only for authentication certificate registration and Security Server address change requests.
3. Information about the request object – that is, the client or the authentication certificate being registered or deleted.

  For the authentication certificate:
  - CA – the name of the certification authority that issued the certificate;
  - Serial Number – the serial number of the certificate;
  - Subject – all attributes of the certificate's Subject field;
  - Expires – the expiration date of the certificate;

  For the Security Server client:

  - Name – the name of the X-Road member managing the subsystem;
  - Class – the member class of the X-Road member managing the subsystem;
  - Code – the member code of the X-Road member managing the subsystem;
  - Subsystem – the code of the subsystem.

# 7 Managing the X-Road Members
## 7.1 Adding a Member

Access rights: Registration Officer

To add a new X-Road member, follow these steps.
1. In the Members tab, click Add member.
2. In the window that opens, enter the member's information and click Add. The new member appears in the list of members.

## 7.2 Viewing the Member Details

Access rights: Registration Officer

To open the detail view, follow these steps.
1. In the Members tab.
2. Select from the table an X-Road member and click members name.

The view consists of five sections and a tab Subsystems.
1. "Member name"
2. "Member class"
3. "Member code"
4. "Owned Servers" – displays the codes of servers owned by this member.
5. "Global Groups" – displays information about the group membership of the member or its subsystems.
6. "Subsystems" tab – displays a list of member's subsystems. If a subsystem is not a client of any Security Servers, then subsystem status is UNREGISTERED.

## 7.3 Adding a Subsystem to an X-Road Member

Access rights: Registration Officer

To add a subsystem to an X-Road member, follow these steps.
1. In the Members tab, select the member to whom you wish to add a subsystem and click members name.
2. In the view that opens, locate the Subsystems tab and click Add new subsystem to database.
3. Enter the code of the subsystem and click Add.

## 7.4 Registering a Member's Security Server

Access rights: Registration Officer

The actions required to register an X-Road member's Security Server depend on whether automatic approval of authentication certificate registration requests is enabled or disabled (_default_).

When automatic approval of authentication certificate registration requests is enabled, the following action must be taken:
- An authentication certificate registration request must be sent from the Security Server to the Central Server by the Security Server administrator.

Automatic approval of authentication certificate registration requests is disabled by default. In that case, to register an X-Road member's Security Server, the following actions must be taken.
- An authentication certificate registration request must be sent from the Security Server to the Central Server by the Security Server administrator;
- The requests must be approved or declined by the Central Server administrator.

To approve a request, it can be done either through in the Management request view list or in the Management request details view.

On the approval of the request
- the request moves to the "Approved" state;
- the registered Security Server appears both in the "Owned Servers" section of its owner’s detail view and in the list of Security Servers (in the Security Servers tab);
- the Security Server's owner is added to the global "security-server-owners" group.

To decline a request, it can be done either through in the Management request view list or in the Management request details view.
On the decline of the request
- the request moves to the "Rejected" state.

## 7.5 Registering a Client to a Security Server

Access rights: Registration Officer

The actions required to register a subsystem of an X-Road member as a Security Server client depend on whether automatic approval of Security Server client registration requests is enabled or disabled (_default_).

When automatic approval of Security Server client registration requests is enabled, the following action must be taken:
- A Security Server client registration request must be sent from the Security Server to the Central Server by the Security Server administrator.

Automatic approval of Security Server client registration requests is disabled by default. In that case, to register a subsystem of an X-Road member as a Security Server client, the following actions must be taken.
- A Security Server client registration request must be sent from the Security Server to the Central Server by the Security Server administrator;
- The requests must be approved or declined by the Central Server administrator.

To approve a request, it can be done either through in the Management request view list or in the Management request details view.

On the approval of the request, follow these steps.
- The request moves to the "Approved" state.
- The client's information is displayed in the "Clients" section of the detailed view of the Security Server to which the client was registered.

To decline a request, it can be done either through in the Management request view list or in the Management request details view.
On the decline of the request
- the request moves to the "Rejected" state.

## 7.6 Removing a Client from a Security Server

Access rights: Registration Officer

The association between an X-Road member and a Security Server is deleted by the corresponding Security Server's client deletion request. The request can be submitted through the Security Server or in the Central Server.

The association between the Security Server's owner and the Security Server cannot be deleted.

Removing a client from the Security Server clients can be carried out through a member's detail view.

To submit a Security Server client deletion request through a member's detail view, follow these steps.
1. In the Members tab, select the member whose subsystem is to be removed from a Security Server and click members name.
2. In the window that opens, select Subsystems tab and select the client subsystem, and click Unregister.
3. Review the information displayed on the client deletion request and click Delete to submit the request.

## 7.7 Changing the Owner of Security Server

Access rights: Registration Officer

The actions required to change a Security Server's owner depend on whether automatic approval of Security Server owner change requests is enabled or disabled (_default_).

When automatic approval of Security Server owner change requests is enabled, the following action must be taken:
- A Security Server owner change request must be sent from the Security Server to the Central Server by the Security Server administrator.

Automatic approval of Security Server owner change requests is disabled by default. In that case, to change the owner of a Security Server, the following action must be taken.
- A Security Server owner change request must be sent from the Security Server to the Central Server by the Security Server administrator.
- The requests must be approved or declined by the Central Server administrator.

To approve/decline a request, it can be done either through in the Management request view list or in the Management request details view.

## 7.8 Deleting a Subsystem

Access rights: Registration Officer

In the Central Server, the X-Road member's subsystem can be deleted only if the subsystem is not associated with any Security Servers, that is, not registered as a client of any Security Servers.

To delete an X-Road member's subsystem, follow these steps.
1. In the Members tab, select the member whose subsystem you wish to delete and click members name.
2. In the window that opens, select Subsystems tab and select the client subsystem, and click Delete. Note: The "Delete" button is displayed only if the subsystem is not a client of any Security Servers.

## 7.9 Deleting an X-Road Member

Access rights: Registration Officer

When an X-Road member is deleted, information about all Security Servers in its ownership will be deleted as well.

To delete an X-Road member, follow these steps.
1. In the Members tab, select a member that you wish to delete, and click members name.
2. In the view that opens, click Delete member "\<member name\>". In the confirmation window that opens, enter member code and click Delete.

# 8. Managing the Security Servers
## 8.1 Viewing the Security Server Details

Access rights: Registration Officer

To open the detail view, follow these steps. In the Members tab, select a member that you wish to delete, and click members name.
1. In the Security Servers tab.
2. Choose from the table a Security Server and click server code.

The view contains three sections.
- "Details" – information about the server and its owner.
- "Clients" – information about clients registered for this Security Server.
Hint: Click a client's member name to open the client's detail view.
- "Authentication Certificates" – information about the Security Server's registered authentication certificates.
Hint: Click a certificate's certification authority to open the certificate's detail view.

## 8.2 Changing the Security Server Address

Access rights: Registration Officer

By default, the Security Server's address is provided in the registration request of the authentication certificate sent from the Security Server. The address must be changed if it was not set when the request was submitted or if it is no longer valid.

There are several reasons why setting the Security Server’s address matters.
- The services that are relayed through a Security Server become available once the Security Server’s address is set.
- By registering the addresses of Security Servers, the service clients are certain to receive a response to their queries in a reasonable time, even if the relaying Security Server is overloaded with service requests (e.g., the requests from addresses belonging to registered Security Servers are served before requests coming from unknown addresses).

The request can be submitted through the Security Server or changed in the Central Server.

To change the Security Server address from the Central Server, follow these steps.
1. In the Security Servers tab, select the Security Server whose address you wish to change and click server code.
2. In the view that opens, locate the "Address" section and click Edit adjacent to the "Address" field.
3. Enter the Security Server's address and click Save.

## 8.3 Registering a Security Server's Authentication Certificate

Access rights: Registration Officer

The actions required to register a Security Server's authentication certificate depend on whether automatic approval of authentication certificate registration requests is enabled or disabled (_default_).

When automatic approval of authentication certificate registration requests is enabled, the following action must be taken:
- An authentication certificate registration request must be sent from the Security Server to the Central Server by the Security Server administrator.

Automatic approval of authentication certificate registration requests is disabled by default. In that case, to register a Security Server's authentication certificate, the following actions must be taken.
- An authentication certificate registration request must be sent from the Security Server to the Central Server by the Security Server administrator;
- The requests must be approved or declined by the Central Server administrator.

To approve/decline a request, it can be done either through in the Management request view list or in the Management request details view.

Upon approving the request
- the request moves to the "Approved" state;
- the registered authentication certificate appears in the Security Server's detail view, in the "Authentication Certificates" section.

To decline the request
- the request moves to the "Rejected" state;

## 8.4 Deleting a Security Server's Authentication Certificate

Access rights: Registration Officer

The authentication certificate registered for a Security Server is deleted when an authentication certificate deletion request is received for that certificate. The request can be submitted through the Security Server or in the Central Server.

To submit an authentication certificate deletion request in the Central Server, follow these steps.
1. In the Security Servers tab, select the Security Server whose certificate you wish to delete and click server code.
2. In the view that opens, locate the Authentication Certificates section, find the correct authentication certificate and click Delete.
3. Review the information displayed on the deletion request and enter Security Server code and click Delete to submit the request.
4. The submitted request appears in the management requests view (Management Requests tab).

## 8.5 Deleting a Security Server

Access rights: Registration Officer

To delete a Security Server, follow these steps.
1. In the Security Servers tab, select the Security Server that you wish to delete and click server code.
2. In the view that opens, on the bottom left, click Delete Security Server "\<server code\>". Confirm the action by entering Security Server code and clicking Delete.

If the Security Server being deleted has registered clients or authentication certificates, deletion requests for those associations are automatically generated.

# 9. Managing the Global Groups
## 9.1 Adding a Global Group

Access rights: Registration Officer

To add a new global group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources and click Add Global Group.
2. In the window that opens, enter the new group's code and description, and click Add. The new group is added to the list of global groups.

## 9.2 Viewing the Global Group Details

Access rights: Registration Officer

To see the details of a global group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources.
2. Select a global group from the table and click code.

In the global group detail view, a list of the group's members is displayed. The detail view allows you to change the group's description, delete the group, and add or remove its members.

## 9.3 Changing the Description of a Global Group

Access rights: Registration Officer

To change the description of a global group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources.
2. Select a global group from the table and click its Code.
3. In the view that opens, click Edit, change the group’s description and click Save.

## 9.4 Changing the Members of a Global Group

Access rights: Registration Officer

Note that the members of the global group security-server-owners are managed automatically by the Central Server and cannot be added or removed manually.

To add members (can be X-Road members or subsystems) to a global group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources.
2. Select a global group from the table and click its Code.
3. In the view that opens, click Add Members.
4. Select one or more subsystems from the list and click Add. Or filter a selection of subsystems with the search function.

To remove members from a group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources.
2. Select a global group from the table and click its Code.
3. Click Remove button on the selected subsystem row.
4. In the confirmation window that opens, enter the member code and then click Delete.

## 9.5 Deleting a Global Group

Access rights: Registration Officer

To delete a global group, follow these steps.
1. In the Navigation tabs, select Settings --> Global Resources.
2. Select a global group from the table and click its Code.
3. In the view that opens, click Delete Group and in the confirmation window click Yes.

# 10. Managing the Approved Certification Services
## 10.1 Adding an Approved Certification Service

Access rights: System Administrator

To add a certification service, follow these steps.
1. In the Trust Services tab, click Add certification service
2. Locate the certification service CA certificate file and click Upload.
3. Set the certification service settings as follows.
  - If the certification service issues only authentication certificates, check the This CA can only be used for TLS authentication checkbox. However, if the certification service issues additionally or only signing certificates, leave the checkbox empty.
  - Enter the fully qualified class name that implements the ee.ria.xroad.common.certificateprofile.CertificateProfileInfo interface to the field Certificate profile info (for example: ee.ria.xroad.common.certificateprofile.impl.SkKlass3CertificateProfileInfoProvider).
  - If the CA certificate contains the certification service CA’s OCSP service information, and the PKI does not have intermediate CAs, the procedure is complete.
4. If necessary, enter the certification service CA’s OCSP service URL and certificate in the OCSP Responders tab by clicking Add.
5. Information about intermediate CAs can be added in the Intermediate CAs tab.
To add a new intermediate CA
  - click Add;
  - in the window that opens, locate the certificate file of the intermediate CA and click Save;
  - to add OCSP service information to the new intermediate CA, click on the added intermediate CA, in the window that opens, locate OCSP Responders tab and click Add.

## 10.2 Changing an Approved Certification Service

Access rights: System Administrator

While it is not possible to change the certification service's CA certificate, it is possible to
- change the service settings;
- add, change, and delete the certificate service CA’s OCSP services;
- add, change, and delete the certificates and OCSP service information of intermediate CAs.

To edit a certification service, follow these steps.
1. In the Trust Services tab, select Certification Services.
2. Select from the list the certification service you want to edit and click on the approved certification service field.

## 10.3 Deleting an Approved Certification Service

Access rights: System Administrator

To delete a certification service from the list of approved services, follow these steps.
1. In the Trust Services tab, select Certification Services.
2. Select from the list the approved certification service you wish to remove and click on the approved certification service field.
3. In the window that opens, click Delete trust service "\<certification service name\>" and in the confirmation window click Yes.

# 11. Managing the Approved Timestamping Services
## 11.1 Adding an Approved Timestamping Service

Access rights: System Administrator

To add an approved timestamping service, follow these steps.
1. In the Trust Services tab, click Add timestamping service.
2. In the window that opens, enter the timestamping service URL and locate the certificate file of the timestamping service and click Add.
3. Information about the new timestamping service appears in the list.

## 11.2 Changing an Approved Timestamping Service

Access rights: System Administrator

To change the timestamping service, follow these steps.
1. In the Trust Services tab, select Timestamping Services, select a timestamping service from the list and click Edit.
2. In the window that opens, edit the URL and/or upload new certificate. Click Save.

## 11.3 Deleting an Approved Timestamping Service

Access rights: System Administrator

To remove a timestamping service, follow these steps.
1. In the Trust Services tab, select Timestamping Services, select a timestamping service from the list and click Delete.
2. In the window that opens, click Yes.

# 12. Configuration Backup and Restore

Access rights: System Administrator

The Central Server backs up
the database (excluding the database schema) and
the directories `/etc/xroad/` and `/etc/nginx/sites-enabled/`.

Backups contain sensitive information that must be kept secret (for example, private keys and database credentials). In other words, leaking this information could easily lead to full compromise of Central Server. Therefore, it is highly recommended that backup archives are encrypted and stored securely. Should the information still leak for whatever reason the Central Server should be considered as compromised and reinstalled from scratch.

Central Server backups are signed and optionally encrypted. The GNU Privacy Guard [GnuPG] is used for encryption and signing. Central Server's backup encryption key is generated during Central Server initialisation. In addition to the automatically generated backup encryption key, additional public keys can be used to encrypt backups.

## 12.1 Backing Up the System Configuration

To back up the configuration, follow these steps.
1. In the Settings tab, select Back Up and Restore sub-tab.
2. Click Back up config. to start the backup process.
3. When done, the configuration backup file appears in the list of configuration backup files.

## 12.2 Restoring the System Configuration in the User Interface

To restore configuration, follow these steps.
1. In the Settings tab, select Back Up and Restore sub-tab.
2. Select a file from the list of configuration backup files and click Restore.
3. Confirm to proceed.
4. A popup notification shows after the restore whether the restoring was successful or not.

If something goes wrong while restoring the configuration it is possible to revert back to the old configuration. Central Server stores so called pre-restore configuration automatically to `/var/lib/xroad/conf_prerestore_backup.tar`. Move it to `/var/lib/xroad/backup/` folder and use the command line interface described in the next chapter (some specific switches with the restore command is required).

## 12.3 Restoring the Configuration from the Command Line

To restore configuration from the command line, the following data must be available:
- the instance ID of the Central Server and,
- in HA setup, the node name of the Central Server.

It is expected that the restore command is run by the xroad user.

Use the following command to restore configuration in non-HA setup:
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i <instance_ID> -f <path + filename> [-P -N]
```

In HA setup, this command has an additional mandatory parameter, the node name:
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i <instance_ID> -n <node_name> -f <path + filename> [-P -N]
```

For example (all in one line, non-HA setup):
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i EE -f /var/lib/xroad/backup/conf_backup_20230515-114736.gpg
```

For example (all in one line, HA setup):
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i EE -n node_0 -f /var/lib/xroad/backup/conf_backup_20230515-114736.gpg
```

In case original backup encryption and signing key is lost additional parameters can be specified to skip decryption and/or signature verification. Use `-P` command line switch when backup archive is already decrypted externally and `-N` switch to skip checking archive signature.

If a backup is restored on a new uninitialized (the initial configuration hasn't been completed) Central Server, the Central Server's gpg key must be manually created before restoring the backup:
```bash
/usr/share/xroad/scripts/generate_gpg_keypair.sh /etc/xroad/gpghome <instance_ID>
```

If it is absolutely necessary to restore the system from a backup made on a different Central Server, the forced mode of the restore command can be used with the –F option. For example:
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -F -P -f /var/lib/xroad/backup/conf_backup_20230515-114736.tar
```

In case backup archives were encrypted they have to be first unencrypted in external safe environment and then securely transported to Central Server filesystem.

It is possible to restore the configuration while skipping the database restoration by appending the -S switch, e.g.
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i <instance_ID> -f <path + filename> -S
```

To see all the possible parameters use the -h switch, e.g.
```bash
/usr/share/xroad/scripts/restore_xroad_center_configuration.sh -h
```

## 12.4 Downloading, Uploading and Deleting Configuration Backup Files

The following actions can be performed in the Backup And Restore view.

To save the configuration backup file locally:
- click Download on the respective row and save the prompted file.

To delete the configuration backup file:
- click Delete on the respective row and confirm the action by clicking Yes.

To upload a configuration file from the local file system to the Central Server:
- click Upload backup, select a file to be uploaded and click Open. The uploaded configuration file appears in the list of configuration files.

## 12.5 Automatic Backups

By default the Central Server backs up its configuration automatically once every day. Backups older than 30 days are automatically removed from the server. If needed, the automatic backup policies can be adjusted by editing the `/etc/cron.d/xroad-center` file.

## 12.6 Backup Encryption Configuration

Backups are always signed, but backup encryption is initially turned off. To turn encryption on, please override the
default configuration in the file `/etc/xroad/conf.d/local.ini`, in the `[center]` section (add or edit this section).

```ini
[center]
backup-encryption-enabled = true
backup-encryption-keyids = <keyid1>, <keyid2>, ...
```

To turn backup encryption on, change the `backup-encryption-enabled` property to true. Additional
encryption keys can be imported in the `/etc/xroad/gpghome` keyring and key identifiers listed using the `backup-encryption-keyids` parameter. It is recommended to set up at least one additional key, otherwise the backups will be unusable in case Central Server private key is lost. It is up to Central Server administrator to check that keys used are sufficiently strong, there are no automatic checks.

Warning. All keys listed in `backup-encryption-keyids` must be present in the gpg keyring or backup fails.

Additional keys for backup encryption should be generated and stored outside Central Server in a secure environment.
After gpg keypair has been generated, public key can be exported to a file (backupadmin@example.org is the name of the
key being exported) using this command:

    gpg --output backupadmin.publickey --armor --export backupadmin@example.org

Resulting file `backupadmin.publickey` should be moved to Central Server and imported to back up gpg keyring. Administrator should make sure that the key has not been changed during transfer, for example by validating the key fingerprint.

Private keys corresponding to additional backup encryption public keys must be handled safely and kept in secret. Any of
them can be used to decrypt backups and thus mount attacks on the Central Servers.

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
[center]
backup-encryption-enabled = true
backup-encryption-keyids = 87268CC66939CFF3
```

To decrypt the encrypted backups, use the following syntax:

```bash
gpg --homedir /etc/xroad/gpghome --output <output file name> --decrypt <backup name>  
```

## 12.7 Verifying Backup Archive Consistency

During restore Central Server verifies consistency of backup archives automatically, archives are not checked during upload.
Also, it is possible to verify the consistency of the archives externally. For verifying the consistency externally,
Central Server's public key is needed. When backups are encrypted, then a private key for decrypting archive is also needed.
GPG uses "sign then encrypt" scheme, so it is not possible to verify encrypted archives without decrypting them.

Automatic backup verification is only possible when original Central Server keypair is available. Should keypair on the
Central Server be lost for whatever reason, automatic verification is no longer possible. Therefore, it is recommended
to export backup encryption public key and import it into separate secure environment. If backups are encrypted,
Central Server public key should be imported to keyrings holding additional encryption keys, so that backups can be
decrypted and verified in these separate environments.

To export Central Servers backup encryption public key use the following command:

    gpg --homedir /etc/xroad/gpghome --armor --output server-public-key.gpg --export <instanceId>

where `<instanceId>` is the Central Server instance id,
for example, `EE`.

Resulting file (`server-public-key.gpg`) should then be exported from Central Server and imported to GPG keystore used
for backup archive consistency verification.

# 13. Audit Log

The Central Server keeps an audit log of the events performed by the Central Server administrator. The audit log events are generated by the user interface and the management REST API when the user changes the system’s state or configuration. The user actions are logged regardless of whether the outcome of the action was a success or a failure. The complete list of the audit log events is described in [SPEC-AL](#13-references).

Actions that change the system’s state or configuration but are not carried out using the user interface or the management REST API are not logged (for example, X-Road software installation and upgrade, user creation and permission granting, and changing of the configuration files in the file system).

An audit log record contains correlation-id, which can be used to link the record to other log messages about the same request.

An audit log record contains:

- the description of the user action,
- the date and time of the event,
- the username of the user that performed the action,
- the authentication type used for this request (Session, ApiKey or HttpBasicPam)
  - `Session` – session based authentication (web application)
  - `ApiKey` - direct API call using API key authentication
  - `HttpBasicPam` – HTTP basic authentication with PAM login (for api key management API operations),
- the API url for this request, 
- the IP address of the user, and
- the data related to the event.

For example, adding a new member in the Central Server produces the following log record:

`2023-05-21T16:20:06+03:00 my-central-server-host correlation-id: [655a2150c4688558] INFO  [X-Road Central Server Admin Service] 2023-05-21T16:20:06.267+03:00 - {"event":"Add member","user":"xrd","ipaddress":"192.0.2.1","auth":"Session","url":"/api/v1/members","data":{"memberName":"SS2 OWNER","memberClass":"TEST","memberCode":"SS2_OWNER"}}`

The event is present in JSON [JSON](#13-references) format, in order to ensure machine processability. The field event represents the description of the event, the field user represents the user name of the performer, and the field data represents data related with the event. The failed action event record contains an additional field reason for the error message. For example:

`2023-05-21T12:16:11+03:00 my-central-server-host correlation-id: [f9ee1a7bdf3e3d19] INFO  [X-Road Central Server Admin Service] 2023-05-21T12:16:11.232+03:00 - {"event":"Log in to token failed","user":"xrd","ipaddress":"192.0.2.1","reason":"Token action not possible","warning":false,"auth":"Session","url":"/api/v1/tokens/0/login","data":{"tokenId":"0","tokenSerialNumber":null,"tokenFriendlyName":"softToken-0"}}`

By default, audit log is located in the file

`/var/log/xroad/audit.log`

## 13.1 Changing the Configuration of the Audit Log

The X-Road software writes the audit log to the syslog (rsyslog) using UDP interface (default port is 514). Corresponding configuration is located in the file

`/etc/rsyslog.d/90-udp.conf`

The audit log records are written with level INFO and facility LOCAL0. By default, log records of that level and facility are saved to the X-Road audit log file

`/var/log/xroad/audit.log`

The default behavior can be changed by editing the rsyslog configuration file

`/etc/rsyslog.d/40-xroad.conf`

Restart the rsyslog service to apply the changes made to the configuration file

`service rsyslog restart`

The audit log is rotated monthly by logrotate. To configure the audit log rotation, edit the logrotate configuration file

`/etc/logrotate.d/xroad-center`

## 13.2 Archiving the Audit Log

In order to save hard disk space and avoid loss of the audit log records during Central Server crash, it is recommended to archive the audit log files periodically to an external storage or a log server.

The X-Road software does not offer special tools for archiving the audit log. The rsyslog can be configured to redirect the audit log to an external location.

# 14. Monitoring

Monitoring is taken to use by installing the monitoring support (see [IG-CS](#13-references) and appointing the central monitoring client as specified below.

Identity of central monitoring client (if any) is configured using Central Server's admin user interface. Configuration is done by updating a specific optional configuration file (see [UC-GCONF](#13-references)) monitoring-params.xml. This configuration file is distributed to all Security Servers through the global configuration distribution process (see [UC-GCONF](#13-references)).

```xml
<tns:conf xmlns:id="http://x-road.eu/xsd/identifiers" xmlns:tns="http://x-road.eu/xsd/xroad.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://x-road.eu/xsd/xroad.xsd">
    <monitoringClient>
        <monitoringClientId id:objectType="SUBSYSTEM">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:subsystemCode>LIPPIS</id:subsystemCode>
        </monitoringClientId>
    </monitoringClient>
</tns:conf>
```

One can configure either one member or a member's subsystem to be the central monitoring client. Permission to execute monitoring queries is strictly limited to that single member or subsystem - defining one subsystem to be a monitoring client does not grant the corresponding member access to querying monitoring data (and vice versa).

To disable central monitoring client altogether, update configuration to one which has no client:

```xml
<tns:conf xmlns:id="http://x-road.eu/xsd/identifiers" xmlns:tns="http://x-road.eu/xsd/xroad.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://x-road.eu/xsd/xroad.xsd">
    <monitoringClient>
    </monitoringClient>
</tns:conf>
```

# 15. Additional configuration options
## 15.1 Verify next update

For additional robustness the OCSP [RFC-OCSP](#13-references) response verifier can be configured to skip checking of nextUpdate parameter. By default the checking is turned on and to turn it off the user has to take action.

Configuration is done by updating a specific optional configuration file (see [UC-GCONF](#13-references)) nextupdate-params.xml. This configuration file is distributed to all Security Servers through the global configuration distribution process (see [UC-GCONF](#13-references)).

```xml
<xro:conf xmlns:xro="http://x-road.eu/xsd/xroad.xsd">
  <verifyNextUpdate>true</verifyNextUpdate>
</xro:conf>
```

With verifyNextUpdate element value “false” the nextUpdate parameter checking is switched off.

## 15.2 OCSP fetch interval

The xroad-signer component has a specific interval how often it downloads new OCSP [RFC-OCSP](#13-references) responses. By default the fetch interval is configured to 1200 seconds. To use something else than the default value a global configuration extension part (see [UC-GCONF](#13-references)) of specific format can be uploaded to Central Server.

```xml
<xro:conf xmlns:xro="http://x-road.eu/xsd/xroad.xsd">
    <ocspFetchInterval>1200</ocspFetchInterval>
</xro:conf>
```

The value is the fetch interval in seconds for new OCSP responses.

# 16. Logs and System Services

Most significant Central Server services are the following:

| Service                           | Purpose                                                |                                                     Log |
|-----------------------------------|--------------------------------------------------------|--------------------------------------------------------:|
| xroad-center                      | X-Road Central Server admin UI and REST management API |        `/var/log/xroad/centralserver-admin-service.log` |
| xroad-center-registration-service | X-Road Central Server Registration Service             | `/var/log/xroad/centralserver-registration-service.log` |
| xroad-center-management-service   | X-Road Central Server Management Service               |   `/var/log/xroad/centralserver-management-service.log` |
| xroad-signer                      | The service that manages key settings                  |                             `/var/log/xroad/signer.log` |
| nginx                             | The Web server that distributes global configuration   |                                       `/var/log/nginx/` |

System services can be managed using the systemd facility.
To start a service, issue the following command as a root user:

`systemctl start <service>`

To stop a service, enter:

`systemctl stop <service>`

To read logs, a user must have root user's rights or belong to the xroad system group.

For logging, the Logback system is used.

Logback configuration files are stored in the `/etc/xroad/conf.d/` directory.

Default settings for logging are the following:
- logging level: INFO;
- rolling policy: whenever file size reaches 100 MB.

# 17 Management REST API

Central Server has a REST API that can be used to do all the same server configuration operations that can be done
using the web UI.

Management REST API is protected with an API key based authentication. To execute REST calls, API keys need to be created.

REST API is protected by TLS. Since server uses self-signed certificate, the caller needs to accept this (for example
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

If the default limits are too restricting (or too loose), they can be overridden with [admin-service](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service) parameters:
- `request-sizelimit-regular`
- `request-sizelimit-binary-upload`
- `rate-limit-requests-per-second`
- `rate-limit-requests-per-minute`

Size limit parameters support formats from Formats from [DataSize](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/unit/DataSize.html),
for example `5MB`.

## 17.1 API key management operations

**Access rights:** [System Administrator](#xroad-system-administrator)

An API key is linked to a role or roles, and grants access to the operations that are allowed for that role/roles.
Separate REST endpoints exist for API key management.
API key management endpoints are authenticated to with [HTTP basic authentication](https://en.wikipedia.org/wiki/Basic_access_authentication) (username and password)
or with session authentication (for admin web application).
Basic authentication access is limited to localhost by default, but this can
be changed using System Parameters \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

### 17.1.1 Creating new API keys

A new API key is created with a `POST` request to `/api/v1/api-keys`. Message body must contain the roles to be
associated with the key. Server responds with data that contains the actual API key. After this point the key
cannot be retrieved, as it is not stored in plaintext.

```bash
curl -X POST -u <user>:<password> https://localhost:4000/api/v1/api-keys --data '["XROAD_REGISTRATION_OFFICER"]' --header "Content-Type: application/json" -k
{
    "id": 5,
    "key": "68117d38-8613-40b4-a9ff-5afe5ea4d27b",
    "roles": [
        "XROAD_REGISTRATION_OFFICER"
    ]
}

```

In this example the created key was `68117d38-8613-40b4-a9ff-5afe5ea4d27b`.

### 17.1.2 Listing API keys

Existing API keys can be listed with a `GET` request to `/api/v1/api-keys`. This lists all keys, regardless of who has created them.

```bash
curl -X GET -u <user>:<password> https://localhost:4000/api/v1/api-keys -k
[
...
    {
        "id": 5,
        "roles": [
            "XROAD_REGISTRATION_OFFICER"
        ]
    },
    {
        "id": 6,
...
]

```

You can also retrieve a single API key with a `GET` request to `/api/v1/api-keys/{id}`.

```bash
curl -X GET -u <user>:<password> https://localhost:4000/api/v1/api-keys/59 -k
{
    "id": 5,
    "roles": [
        "XROAD_REGISTRATION_OFFICER"
    ]
}

```

### 17.1.3 Updating API keys

An existing API key is updated with a `PUT` request to `/api/v1/api-keys/{id}`. Message body must contain the roles to be
associated with the key. Server responds with data that contains the key id and roles associated with the key.

```bash
curl -X PUT -u <user>:<password> https://localhost:4000/api/v1/api-keys/5 --data '["XROAD_SECURITY_OFFICER”,”XROAD_REGISTRATION_OFFICER"]' --header "Content-Type: application/json" -k
{
  "id": 5,
  "roles": [
    "XROAD_SECURITY_OFFICER",
    "XROAD_REGISTRATION_OFFICER"
  ]
}

```

### 17.1.4 Revoking API keys

An API key can be revoked with a `DELETE` request to `/api/v1/api-keys/{id}`. Server responds with `HTTP 200` if
revocation was successful and `HTTP 404` if key did not exist.

```bash
curl -X DELETE -u <user>:<password> https://localhost:4000/api/v1/api-keys/5  -k

```

## 17.2 Executing REST calls

**Access rights:** Depends on the API.

Once a valid API key has been created, it is used by providing an `Authorization: X-Road-ApiKey token=<api key>` HTTP
header in the REST calls. For example

```bash
curl --header "Authorization: X-Road-ApiKey token=ff6f55a8-cc63-4e83-aa4c-55f99dc77bbf" "https://localhost:4000/api/v1/clients" -k
{
    "clients": [
        {
            "client_id": {
                "instance_id": "CS",
                "type": "MEMBER",
                "member_class": "ORG",
                "member_code": "999",
                "encoded_id": "CS:ORG:999"
            },
            "member_name": "Foo Name"
        },
...
```

The available APIs are documented in OpenAPI specification \[[REST_UI-API](#Ref_REST_UI-API)\]. Access rights for different APIs follow the same rules
as the corresponding UI operations.
Access to regular APIs is allowed from all IP addresses by default, but this can
be changed using System Parameters \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

## 17.3 Correlation ID HTTP header

The REST API endpoints return an **x-road-ui-correlation-id** HTTP header. This header is also logged in `centralserver-admin-service.log`, so it
can be used to find the log messages related to a specific API call.

The correlation ID header is returned for all requests, both successful and failed ones.

For example, these log messages are related to an API call with correlation ID `3d5f193102435242`:
```
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] DEBUG o.s.s.w.c.HttpSessionSecurityContextRepository - The HttpSession is currently null, and the HttpSessionSecurityContextRepository is prohibited from creating an HttpSession (because the allowSessionCreation property is false) - SecurityContext thus not stored for next request
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] WARN  o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Resolved [org.niis.xroad.restapi.exceptions.ConflictException: local group with code koodi6 already added]
2019-08-26 13:16:23,611 [https-jsse-nio-4000-exec-10] correlation-id:[3d5f193102435242] DEBUG o.s.s.w.a.ExceptionTranslationFilter - Chain processed normally
```

## 17.4 Data Integrity errors

An error response from the REST API can include data integrity errors if incorrect data was provided with the request.
When

Example request and response of adding a new member when member already exist:
```
POST https://cs:4000/api/v1/members

Request body:
{
    "member_name": "Member",
    "member_id": {
        "member_class": "ORG",
        "member_code": "MemberCode"
    }
}

Response body:
{
    "status": 409,
    "error": {
        "code": "member_exists",
        "metadata": [
            "CS/ORG/MemberCode"
        ]
    }
}
```

Possible data integrity error codes and messages declared in [Central Server ErrorMessage](https://github.com/nordic-institute/X-Road/blob/develop/src/central-server/admin-service/core-api/src/main/java/org/niis/xroad/cs/admin/api/exception/ErrorMessage.java)

## 17.5 Warning responses

Error response from the Management API can include additional warnings that you can ignore if seen necessary. The warnings can be ignored by your decision, by executing the same operation with `ignore_warnings` boolean parameter set to `true`. *Always consider the warning before making the decision to ignore it.*

An example case:
1. Client executes a REST request, without `ignore_warnings` parameter, to backend.
2. Backend notices warnings and responds with error message that contains the warnings. Nothing is updated at this point.
3. Client determines if warnings can be ignored.
4. If the warnings can be ignored, client resends the REST request, but with `ignore_warnings` parameter set to `true`.
5. Backend ignores the warnings and executes the operation.

Error response with warnings always contains the error code `warnings_detected`.

Like errors, warnings contain an identifier (code) and possibly some metadata.

Warning example when trying to upload backup file which already exist produces non-fatal validation warnings:
```
POST https://cs:4000/api/v1/backups/upload?ignore_warnings=false

Response: 
{
  "status": 400,
  "error": {
    "code": "warnings_detected"
  },
  "warnings": [
    {
      "code": "warning_file_already_exists",
      "metadata": [
        "backup.gpg"
      ]
    }
  ]
}
```

Note that when you are using the admin UI and you encounter warnings, you will always be provided with a popup window with a `CONTINUE` button in it. When you click the `CONTINUE` button in the popup, the request is sent again but this time warnings will be ignored.

# 18. Migrating to Remote Database Host

Since version 6.23.0 Central Server supports using remote databases. In case you have an already running standalone Central Server with local database, it is possible to migrate it to use remote database host instead. The instructions for this process are listed below.

Prerequisites

* Same version (12 or later) of PostgreSQL installed on the remote database host.
* Network connections to PostgreSQL port (tcp/5432) are allowed from the Central Server to the remote database server.

1. Shutdown X-Road processes.

```bash
systemctl stop "xroad*"
```

2. Dump the local database centerui_production to be migrated. The credentials of the database admin user can be found in `/etc/xroad.properties`. Notice that the versions of the local PostgreSQL client and remote PostgreSQL server must match.

```bash
pg_dump -F t -h 127.0.0.1 -p 5432 -U centerui_admin -f centerui_production.dat centerui_production
```

3. Shut down and mask local postgresql so it won't start when xroad-proxy starts.

```bash
systemctl stop postgresql
```

```bash
systemctl mask postgresql
```

4. Connect to the remote database server as the superuser postgres and create roles, databases and access permissions as follows.

```bash
    psql -h <remote-db-url> -p <remote-db-port> -U postgres
    CREATE DATABASE centerui_production ENCODING 'UTF8';
    REVOKE ALL ON DATABASE centerui_production FROM PUBLIC;
    CREATE ROLE centerui_admin LOGIN PASSWORD '<centerui_admin password>';
    GRANT centerui_admin TO postgres;
    GRANT CREATE,TEMPORARY,CONNECT ON DATABASE centerui_production TO centerui_admin;
    \c centerui_production
    CREATE EXTENSION hstore;
    CREATE SCHEMA centerui AUTHORIZATION centerui_admin;
    REVOKE ALL ON SCHEMA public FROM PUBLIC;
    GRANT USAGE ON SCHEMA public TO centerui_admin;
    CREATE ROLE centerui LOGIN PASSWORD '<centerui password>';
    GRANT centerui TO postgres;
    GRANT TEMPORARY,CONNECT ON DATABASE centerui_production TO centerui;
    GRANT USAGE ON SCHEMA public TO centerui;
    GRANT USAGE ON SCHEMA centerui TO centerui;
    GRANT SELECT,UPDATE,INSERT,DELETE ON ALL TABLES IN SCHEMA centerui TO centerui;
    GRANT SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA centerui TO centerui;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA centerui to centerui;
```

5. Restore the database dumps on the remote database host.

```bash
pg_restore -h <remote-db-url> -p <remote-db-port> -U centerui_admin -O -n centerui -1 -d centerui_production centerui_production.dat
```

6. Create properties file `/etc/xroad.properties` if it does not exist.

```bash
    sudo touch /etc/xroad.properties
    sudo chown root:root /etc/xroad.properties
    sudo chmod 600 /etc/xroad.properties
```

7. Make sure `/etc/xroad.properties` is containing the admin user & its password.

```properties
    centerui.database.admin_user = centerui_admin
    centerui.database.admin_password = <centerui_admin password>
```

8. Update `/etc/xroad/db.properties` contents with correct database host URLs and passwords.

```properties
    spring.datasource.username=<database_username>
    spring.datasource.password=<database_password>
    spring.datasource.hikari.data-source-properties.currentSchema=<database_schema>
    spring.datasource.url=jdbc:postgresql://<database_host>:<database_port>/<database>
```

9. Start again the X-Road services.

```bash
systemctl start "xroad*"
```

## 19 Additional Security Hardening

For the guidelines on security hardening, please refer to [UG-SEC](ug-sec_x_road_security_hardening.md).
