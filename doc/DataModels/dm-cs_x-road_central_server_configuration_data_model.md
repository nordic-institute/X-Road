# X-Road: Central Server Configuration Data Model

Version: 1.12 
Doc. ID: DM-CS

| Date       | Version | Description                                                                      | Author               |
|------------|---------|----------------------------------------------------------------------------------|----------------------|
| 15.06.2015 | 0.1     | Initial version                                                                  | Martin Lind          |
| 30.06.2015 | 0.2     | Comments and revisions                                                           | Margus Freudenthal   |
| 09.07.2015 | 0.3     | Rearrangements for consistency                                                   | Martin Lind          |
| 28.08.2015 | 0.4     | Corrections according to feedback to first 7 tables                              | Martin Lind          |
| 01.09.2015 | 0.5     | Better explanations for modifications to all tables                              | Martin Lind          |
| 09.09.2015 | 0.6     | Made minor editorial changes                                                     | Margus Freudenthal   |
| 16.09.2015 | 0.7     | Added the descriptions of the fields and procedures related to high availability | Marju Ignatjeva      |
| 21.09.2015 | 1.0     | Editorial changes made                                                           | Imbi Nõgisto         |
| 16.10.2015 | 1.1     | Field cert_profile_info for approved CA-s table and one missing index            | Martin Lind          |
| 17.10.2015 | 1.2     | Clarified description of the cert_profile_info field                             | Margus Freudenthal   |
| 11.12.2015 | 1.3     | Subsystems can only be clients of security servers                               | Siim Annuk           |
| 02.02.2017 | 1.4     | Update distributed_files and convert to markdown format                          | Ilkka Seppälä        |
| 05.06.2017 | 1.5     | System parameter *confSignAlgoId* replaced with *confSignDigestAlgoId*           | Kristo Heero         |
| 02.03.2018 | 1.6     | Added uniform terms and conditions reference                                     | Tatu Repo            |
| 11.09.2019 | 1.7     | Remove Ubuntu 14.04 support                                                      | Jarkko Hyöty         |
| 11.08.2021 | 1.8     | Update chapter 1.7 about high availability support                               | Ilkka Seppälä        |
| 26.09.2022 | 1.9     | Remove Ubuntu 18.04 support                                                      | Andres Rosenthal     |
| 17.04.2023 | 1.9     | Remove security server category support                                          | Ričardas Bučiūnas    |
| 17.04.2023 | 1.10    | Remove central services support                                                  | Justas Samuolis      | 
| 30.05.2023 | 1.11    | Remove security_server_client_names table                                        | Ovidijus Narkevičius | 
| 14.06.2023 | 1.12    | New Central Server updates                                                       | Eneli Reimets        |

## Table of Contents

- [X-Road: Central Server Configuration Data Model](#x-road-central-server-configuration-data-model)
	- [Table of Contents](#table-of-contents)
	- [License](#license)
- [1 General](#1-general)
	- [1.1 Preamble](#11-preamble)
	- [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
    - [1.3 References](#13-references)
	- [1.4 Database Version](#14-database-version)
	- [1.5 Creating, Backing Up and Restoring the Database](#15-creating-backing-up-and-restoring-the-database)
	- [1.6 Saving Database History](#16-saving-database-history)
	- [1.7 High Availability Support](#17-high-availability-support)
	- [1.8 Entity-Relationship Diagram](#18-entity-relationship-diagram)
	- [1.9 List of Stored Procedures](#19-list-of-stored-procedures)
	- [1.10 List of Triggers](#110-list-of-triggers)
- [2 Description of Entities](#2-description-of-entities)
	- [2.1 ANCHOR_URL_CERTS](#21-anchor_url_certs)
		- [2.1.1 Indexes](#211-indexes)
		- [2.1.2 Attributes](#212-attributes)
	- [2.2 ANCHOR_URLS](#22-anchor_urls)
		- [2.2.1 Indexes](#221-indexes)
		- [2.2.2 Attributes](#222-attributes)
    - [2.3 APIKEY](#23-apikey)
		- [2.3.1 Attributes](#231-attributes)
    - [2.4 APIKEY_ROLES](#24-apikey_roles)
        - [2.4.1 Indexes](#241-indexes)
		- [2.4.2 Attributes](#242-attributes)
	- [2.5 APPROVED_CAS](#25-approved_cas)
		- [2.5.1 Indexes](#251-indexes)
		- [2.5.2 Attributes](#252-attributes)
	- [2.6 APPROVED_TSAS](#26-approved_tsas)
		- [2.6.1 Attributes](#261-attributes)
	- [2.7 AUTH_CERTS](#27-auth_certs)
		- [2.7.1 Indexes](#271-indexes)
		- [2.7.2 Attributes](#272-attributes)
	- [2.8 CA_INFOS](#28-ca_infos)
		- [2.8.1 Indexes](#281-indexes)
		- [2.8.2 Attributes](#282-attributes)
	- [2.9 CONFIGURATION_SIGNING_KEYS](#29-configuration_signing_keys)
		- [2.9.1 Indexes](#291-indexes)
		- [2.9.2 Attributes](#292-attributes)
	- [2.10 CONFIGURATION_SOURCES](#210-configuration_sources)
		- [2.10.1 Indexes](#2101-indexes)
		- [2.10.2 Attributes](#2102-attributes)
	- [2.11 DISTRIBUTED_FILES](#211-distributed_files)
		- [2.11.1 Attributes](#2111-attributes)
	- [2.12 GLOBAL_GROUP_MEMBERS](#212-global_group_members)
		- [2.12.1 Indexes](#2121-indexes)
		- [2.12.2 Attributes](#2122-attributes)
	- [2.13 GLOBAL_GROUPS](#213-global_groups)
		- [2.13.1 Attributes](#2131-attributes)
	- [2.14 HISTORY](#214-history)
		- [2.14.1 Attributes](#2141-attributes)
	- [2.15 IDENTIFIERS](#215-identifiers)
		- [2.15.1 Attributes](#2151-attributes)
	- [2.16 MEMBER_CLASSES](#216-member_classes)
		- [2.16.1 Attributes](#2161-attributes)
	- [2.17 OCSP_INFOS](#217-ocspinfos)
		- [2.17.1 Indexes](#2171-indexes)
		- [2.17.2 Attributes](#2172-attributes)
	- [2.18 REQUEST_PROCESSINGS](#218-request_processings)
		- [2.18.1 Attributes](#2181-attributes)
	- [2.19 REQUESTS](#219-requests)
		- [2.19.1 Indexes](#2191-indexes)
		- [2.19.2 Attributes](#2192-attributes)
	- [2.20 SECURITY_SERVER_CLIENTS](#220-security_server_clients)
		- [2.20.1 Indexes](#2201-indexes)
		- [2.20.2 Attributes](#2202-attributes)
	- [2.21 SECURITY_SERVERS](#221-security_servers)
		- [2.21.1 Indexes](#2211-indexes)
		- [2.21.2 Attributes](#2212-attributes)
	- [2.22 SERVER_CLIENTS](#222-server_clients)
		- [2.22.1 Indexes](#2221-indexes)
		- [2.22.2 Attributes](#2222-attributes)
	- [2.23 SYSTEM_PARAMETERS](#223-system_parameters)
		- [2.23.1 Attributes](#2231-attributes)
	- [2.24 TRUSTED_ANCHORS](#224-trusted_anchors)
		- [2.24.1 Attributes](#2241-attributes)
	- [2.25 UI_USERS](#225-ui_users)
		- [2.25.1 Attributes](#2251-attributes)

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# 1 General

## 1.1 Preamble

This document describes the database model of the X-Road Central Server.

## 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

## 1.4 Database Version

This database assumes PostgreSQL version 12 or later. Default settings are used in simple setup, while a custom configuration is used in HA setup.

## 1.5 Creating, Backing Up and Restoring the Database

This database is integrated into X-Road Central Server application. The database management functions are embedded into the application user interface.
The database, the database user and the data model is created by the application's installer. The database updates are packaged as application updates and are applied when the application is upgraded. From the technical point of view, the database structure is created and updated using [Liquibase](http://www.liquibase.org/) tool. The migration scripts can be found both in application source and in file system of the installed application.
Database backup functionality is built into the application. The backup operation can be invoked from the web-based user interface or from the command line. The backup contains dump of all the database structure and contents. When restoring the application, first the software is installed and then the configuration database is restored together with all the other necessary files. This produces a working Central Server.

## 1.6 Saving Database History

This section describes the general mechanism for storing the history of the database tables. All the history-aware tables have an associated trigger update_history that records all the modifications to data. All the tables of central database are history-aware, except for

- history,
- distributed_files

When a row is created, updated or deleted in one of the history-aware tables, the trigger update_history is activated and invokes the stored procedure add_history_rows. For each changed column, add_history_rows inserts a row into the history table. The details of the stored procedures are described in section 1.9.

## 1.7 High Availability Support

The High Availability (HA) solution for the X-Road Central Server relies on a shared, optionally highly available database. There can be multiple Central Server nodes each connecting to the same database instance. Furthermore, the database can be set up in high-availability mode where there is the primary node with read/write access and one or more secondary read-only nodes replicating the primary data as it changes.

In order to support high availability (HA) setup of the X-Road Central Server, some database tables have the ha_node_name field. In an HA setup, the name of the node of the cluster that initiated the insertion of a given record, is stored in that field. In ordinary setup, a default value is used. In both cases, this is done at the level of stored procedures as described in section 1.9.

The logic of taking into account the value of ha_node_name where applicable, has been implemented at the application level.

Database history records are aware of the node name in an HA setup and are replicated just like other records. Thus each node contains the full history of database changes. Because replication events happen at a lower level than insertions of records, the replication of history records themselves does not trigger any subsequent insertions of history records on target nodes.

## 1.8 Entity-Relationship Diagram

The data model is described in two entity relationship diagrams (ERD). The first diagram contains tables related to Security Servers and Security Server clients. The second diagram contains the rest of the tables.

![Entity-Relationship Diagram](img/dm-cs-central-server-database-diagram.svg)

Figure 1. ERD describing the database tables in the Central Server database

## 1.9 List of Stored Procedures

The following stored procedures are present in the database, regardless of whether a given Central Server has been installed in standalone or HA setup.

1. add_history_rows: Detects the changes made as a result of the operation it is invoked on, and calls the insert_history_row stored procedure to insert a row to the history table, for each changed field. For insertions and deletions, a history record is inserted for each field of the original table.
2. insert_history_row: Inserts a single row with values corresponding to a changed field in one of the database tables. Invoked by the add_history_rows stored procedure.
3. insert_node_name: For each record inserted to a table with the field ha_node_name, sets the value of this field to
- the default value in standalone systems
- the name of the cluster node that initiated the insertion, in an HA setup.

## 1.10 List of Triggers

The following triggers are present in the database, regardless of whether a given Central Server has been installed in standalone or HA setup.

1. `update_history`: Invokes the `add_history_rows` stored procedure upon insertions, updates and deletions of records. Created for each history-aware table.
2. `insert_node_name`: Invokes the `insert_node_name` stored procedure upon insertions. Created for each table with the ha_node_name field.

# 2 Description of Entities

## 2.1 ANCHOR_URL_CERTS

Certificate belonging to a configuration source that is represented in database by an anchor URL. The certificates are used to verify signed configuration downloaded from a given URL.

The record is created when an X-Road security officer has received trusted anchor from federation partner and uploads it in the user interface. The record is deleted when the federation contract between two X-Road instances has come to an end and an X-Road security officer deletes the anchor associated with the record in the user interface. The record is never modified. Records in tables trusted_anchors and anchor_urls are created and deleted in exactly the same way. See also documentation of these tables.

### 2.1.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_anchor_url_certs_on_anchor_url_id | anchor_url_id |

### 2.1.2 Attributes

| Name               |  Type   | Modifiers        | Description          |
|:-------------------|:-------:|:----------- |:-----------------:|
| id [PK]            | integer | NOT NULL | Primary key |
| anchor_url_id [FK] | integer |  | ID of the configuration anchor URL the certificate belongs to. References id attribute of anchor_urls entity. As every anchor URL certificate must belong to particular anchor URL, the column cannot be NULL (currently set in the data model layer of the user interface). |
| cert               |  bytea  |  |                                                                                                                                                                                                                                                                              |

## 2.2 ANCHOR_URLS

URL pointing to a configuration source that is described by a trusted anchor. Anchor URL is HTTP URL that can be used to download signed configuration.

The record is created or modified exactly the same way as described in the documentation of table anchor_url_certs. The record is never modified.

### 2.2.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_anchor_urls_on_trusted_anchor_id | trusted_anchor_id |

### 2.2.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| trusted_anchor_id [FK] | integer |  | ID of the trusted anchor that contains this anchor URL. References id attribute of trusted_anchors entity. Cannot be NULL. |
| url | character varying(255) | | The URL that can be used by the configuration client to download the configuration from the configuration source. Must correspond to the URL format (See also URL specification: http://www.w3.org/Addressing/URL/url-spec.txt). Cannot be NULL. |

## 2.3 APIKEY

API key which grants access to REST API operations.

### 2.3.1 Attributes

| Name        | Type           | Modifiers |   Description    |
|:----------- |:-----------------:|:----------|:----------------:|
| id [PK] | bigint | NOT NULL  |   Primary key    |
| encodedkey | character varying(255) | NOT NULL  | Encoded API key. |


## 2.4 APIKEY_ROLES

Roles linked to one API key.

### 2.4.1 Indexes

| Name               |     Columns     |
|:-------------------|:---------------:|
| unique_apikey_role | apikey_id, role |

### 2.4.2 Attributes

| Name           |          Type          | Modifiers |                             Description                              |
|:---------------|:----------------------:|:----------|:--------------------------------------------------------------------:|
| id [PK]        |         bigint         | NOT NULL  |                             Primary key                              |
| apikey_id [FK] |         bigint         | NOT NULL  |                    Links one role to an API key.                     |
| role           | character varying(255) | NOT NULL  | Role name. Check constraint `valid_role` limits value to valid ones. |


## 2.5 APPROVED_CAS

Approved certification authority (CA) that is used when verifying authentication and signing certificates. Exactly one top-level CA certificate is associated with each approved CA. Multiple intermediate CA certificates can be associated with each approved CA. The intermediate CA certificates form a hierarchy with top-level CA used as a trust anchor. The intermediate CAs are used for certificate path building and for finding OCSP responders.

New record creation process starts when an X-Road system administrator receives a certificate from a CA which is going to be trusted by the X-Road instance. Having received the certificate, an X-Road system administrator uploads it in the user interface. The record is deleted when for any reason the governing authority of the X-Road instance does not trust the CA any more. Then an X-Road system administrator deletes the approved CA in the user interface. The record is modified when the user changes parameters of the approved CA. Parameters that can be changed, are following:

1. Flag “authentication only”, see also documentation of the column authentication_only of this table.
2. Certificate profile info class name, see also documentation of the column cert_profile_info of this table.

### 2.5.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_approved_cas_on_top_ca_id | top_ca_id |

### 2.5.2 Attributes

| Name                                              | Type           | Modifiers        | Description           |
|:--------------------------------------------------|:-----------------:|:----------- |:-----------------:|
| id [PK]                                           | integer | NOT NULL | Primary key |
| name                                              | character varying(255) |  | Name of the CA, used in user interfaces. Technically this is the subject name of the top level certification authority certificate. |
| top_ca_id [FK]                                    | integer |  | ID of the top level CA certificate entry of the record. See also documentation of the table ca_infos. Cannot be NULL. |
| authentication_only                               | boolean |  | If true, this CA can only issue authentication certificates. If false, this CA can issue all certificates. |                                                                                                          |
| cert_profile_info                                 | character varying(255) |  | Fully qualified Java class name that implements the CertificateProfileInfoProvider interface. The implementing class is used for extracting subject information from certificates. The implementing class must be present in classpath of both Central Server and securitys servers. Cannot be NULL. |
| created_at                                        | timestamp without time zone | NOT NULL | Record creation time, managed automatically. |
| updated_at                                        | timestamp without time zone | NOT NULL | Record last modified time, managed automatically. |

## 2.6 APPROVED_TSAS

Approved time-stamping authority (TSA). The certificate of the approved CA is used for time-stamping signed messages.

New record creation process starts when an X-Road system administrator receives a certificate from a TSA which is going to be trusted by the X‑Road instance. Having received the certificate, an X-Road system administrator uploads it in the user interface. The record is created when the user adds new approved TSA in the user interface. The record is deleted when for any reason the governing authority of the X-Road instance does not trust the TSA any more. Then an X-Road system administrator deletes the approved TSA in the user interface. The record is modified when the user changes URL or uploads new certificate.

### 2.6.1 Attributes

| Name        | Columns           | Name        | Columns           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| name | character varying(255) |  | Name of the TSA, used in user interfaces. Technically, this is the subject name of the TSA certificate. |
| url | character varying(255) |  | URL that is used for sending time-stamping requests. Must correspond to the URL format. Cannot be NULL. |
| cert | bytea |  | TSA certificate that is used to verify issued time stamps. Stored in DER-encoded form. Cannot be NULL. |
| valid_from | timestamp without time zone |  | Start of validity period of the TSA's certificate. Extracted from the uploaded certificate. |
| valid_to | timestamp without time zone |  | End of validity period of the TSA's certificate. Extracted from the uploaded certificate. |
| created_at | timestamp without time zone | NOT NULL | Record creation time, managed automatically. |
| updated_at | timestamp without time zone | NOT NULL | Record last modified time, managed automatically. |

## 2.7 AUTH_CERTS

Authentication certificate that is used by a Security Server to establish secure connection. Each authentication certificate belongs to a particular Security Server.

The record is created when X-Road registration officer approves the request in the user interface. The record is removed whenever there is need to remove the Security Server the record belongs to or when the authentication certificate cannot be used any more. An X-Road registration officer can either remove Security Server or send authentication certificate deletion request for the Security Server in the user interface. The latter is done when only authentication certificate (without Security Server) is going to be deleted. The record is never modified. See also documentation of table security_servers.

### 2.7.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_auth_certs_on_security_server_id | security_server_id |

### 2.7.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| security_server_id [FK] | integer |  | ID of the Security Server the authentication certificate belongs to. References id attribute of security_servers entity. Cannot be NULL. |
| cert | bytea |  | Authentication certificate contents (in DER encoding). Cannot be NULL. |
| created_at | timestamp without time zone | NOT NULL | Record creation time, managed automatically. |
| updated_at | timestamp without time zone | NOT NULL | Record last modified time, managed automatically. |

## 2.8 CA_INFOS

CA certificates with additional data that is displayed in the user interface. The CA info can describe either certificate of a top-level CA or an intermediate CA. The record is created when a new top-level CA or an intermediate CA is added in the user interface.
The record is created on two occasions:

1. When a new approved CA is added in the user interface (for details, see documentation of table approved_cas), CA info corresponding to top CA is added.
2. When the certification chain of the approved CA includes intermediate CA-s. Then an X-Road system administrator adds intermediate CA certificate(s) in the user interface.

Accordingly, the record is deleted when either the approved CA is deleted (see also documentation of table approved_cas) or the intermediate CA is deleted in the user interface. The latter can happen when certification chain for approved CA changes. The record is never modified.

### 2.8.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_ca_infos_on_intermediate_ca_id | intermediate_ca_id |

### 2.8.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| cert | bytea |  | Contents of the CA certificate (in DER encoding). Cannot be NULL. |
| intermediate_ca_id [FK] | integer |  | Used to associate the ca_info record with a top-level CA record. This field is present only for intermediate-level CAs (top-level CA is referenced directly by the ca_info table. References to id attribute of approved_cas entity. |
| valid_from | timestamp without time zone |  | Start of validity period of the CA's certificate. Extracted from the certificate. |
| valid_to | timestamp without time zone |  | End of validity period of the CA's certificate. Extracted from the certificate. |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |

## 2.9 CONFIGURATION_SIGNING_KEYS

Signing context (key identifier used by the signer and signing certificate) for signing the global configuration. A signing key belongs to a configuration source. A configuration signing key is used when it is marked as active in the user interface. Technically it is done by designating the key as active key in the configuration_sources table, see also documentation of table configuration_sources.

The record is created when a new key for signing global configuration is needed (either no keys are present or any of present ones cannot be used). Then an X-Road security officer generates a new signing key in the user interface. Non-active configuration signing keys that are no longer necessary can be deleted by an X-Road security officer in the user interface. The record is never modified.

### 2.9.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_configuration_signing_keys_on_configuration_source_id | configuration_source_id |

### 2.9.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| configuration_source_id [FK] | integer |  | ID of the configuration source that uses this signing key. References id attribute of configuration_sources entity. Cannot be NULL. |
| key_identifier  | character varying(255) |  | Contents of the configuration signing certificate (in DER encoding).  |
| cert | bytea |  |  |
| key_generated_at  | timestamp without time zone |  | The signing key generation time.  |
| token_identifier  | character varying(255) |  | Unique identifier of hardware or software token used for signing the configuration.  |

## 2.10 CONFIGURATION_SOURCES

Configuration source that the Central Server uses to distribute the global configuration. Stores (with associated configuration_signing_keys table) all the data necessary to generate configuration anchors for the Central Server. The configuration distributed by the source can be either internal configuration or external configuration. The internal configuration is distributed to Security Servers of this X-Road instance. The external configuration is distributed to the other X-Road instances (federation partners).

The configuration source is associated with several configuration signing keys. When generating a configuration anchor for the source, all the keys are included. One of the keys is marked active. Technically, the active key (in configuration_signing_keys table) is referred by the attribute active_key_id. The active key is used for signing configuration distributed by this source.

In an HA setup, each node of the cluster uses separate keys for signing configuration, and configuration anchors contain entries for each node of the cluster.
The record is created when the configuration source tab (either for internal or external configuration) is opened in the UI for the first time. The configuration source tab can be opened for viewing or editing configuration anchor or signing keys information for the configuration source. The record is modified when signing keys information of the configuration source is changed and a new configuration anchor is generated by the system. Also, the record is modified when an X-Road security officer generates a new configuration anchor in the user interface. The record is never deleted.

### 2.10.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_configuration_sources_on_active_key_id | active_key_id |

### 2.10.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| source_type  | character varying(255) |  | Type of the configuration source, can be either 'internal' or 'external'.  |
| active_key_id [FK] | integer |  | ID of the active key that is used to sign the distributed configuration (all the other keys are only included in the generated configuration anchor).References id attribute of configuration_signing_keys entity.  |
| anchor_file  | qqbytea |  | Configuration anchor file (in XML format). The anchor is re-generated if any information contained in the anchor is saved. |
| anchor_file_hash  | text |  | Configuration anchor file hash (for displaying in user interface). Updated when the configuration anchor is re-generated. |
| anchor_generated_at  | timestamp without time zone |  | Configuration anchor generation time. Updated when the configuration anchor is re-generated. |
| ha_node_name | character varying(255) |  | Name of the cluster node that initiated the insertion in an HA setup; the default value in standalone setup. |

## 2.11 DISTRIBUTED_FILES

Stores global configuration files that are distributed to the X-Road members. There are three kinds of distributed files:

- private parameters (distributed to only members of this X-Road instance),
- shared parameters (distributed to members of this X-Road instance and to members of federation partners),
- other configuration files (optional, depends on the configuration of the instance. The supported optional configuration files are described in the system configuration).

The record can be created in two different ways:

- The record corresponding to either private or shared parameters is created when a new global configuration is generated. Global configuration is triggered periodically (every minute) by a cron job.
- The record corresponding to other configuration file is created when there is a need to distribute new version of a configuration file specific to the X-Road instance. Then an X-Road security officer on an X-Road registration officer uploads a new configuration part file in the user interface.

The record is always deleted before new record with particular file name is created. The record is never modified.

### 2.11.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| file_name  | character varying(255) |  | Name of the distributed file. Any valid file name. Cannot be NULL. |
| file_data  | bytea |  | Contents of the distributed file. Cannot be NULL. |
| content_identifier  | character varying(255) |  | Content identifier of the distributed file. The content identifier is used by Security Server to determine the exact type of the file. Must be unique inside an X-Road instance. Cannot be NULL. |
| file_updated_at  | timestamp without time zone |  | Time when the distributed file was last updated.  |
| ha_node_name | character varying(255) |  | Name of the cluster node that initiated the insertion in an HA setup; the default value in standalone setup. |
| version | integer | NOT NULL | Version of the distributed file. Cannot be NULL. Default is 0 which means it is not versioned and belongs to all versions of global configuration. |

## 2.12 GLOBAL_GROUP_MEMBERS

Join table that associates global group member identifier with the global group the member belongs to. See also documentation of the table global_groups.

The record is created when a new member needs to be added to a global group. Then an X-Road registration officer adds global group member in the user interface. The record is deleted when a global group member or the group where the member belongs to is deleted in the user interface. The record is never modified.

### 2.12.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_global_group_members_on_global_group_id | global_group_id |
| index_global_group_members_on_group_member_id | group_member_id |

### 2.12.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| group_member_id [FK] | integer |  | ID of the member identifier that belongs to this global group. References id attribute of identifiers entity. Cannot be NULL. |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |
| global_group_id [FK] | integer |  | ID of the global group the member referenced by group_member_id belongs to. References id attribute of global_groups entity. Cannot be NULL. |

## 2.13 GLOBAL_GROUPS

Global group of access rights subjects that can be added to access control lists at Security Servers.

The record is created when a new global group needs to be added to the X-Road instance. Then an X-Road registration officer adds new global group in the user interface. The record is modified when the group description is edited or members are added to or removed from the group by an X‑Road registration officer in the user interface. The record is deleted when the global group is deleted in the user interface by an X-Road registration officer.

### 2.13.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| group_code  | character varying(255) |  | Global group code that is unique inside the X-Road instance. Cannot be modified after the record is created. Cannot be NULL. |
| description  | character varying(255) |  | Longer, human-readable description of the group. Can be modified after the record is created. |
| created_at | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at | timestamp without time zone | NOT NULL | Record last modified time, managed automatically. |

## 2.14 HISTORY

Operation (insertion, update or deletions of a record) on the tables of this database, for the purpose of auditing. Each row corresponds to the change of a single field.

The record is created in the manner described above in this document. The record can be neither modified nor deleted.

### 2.14.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| operation  | character varying(255) | NOT NULL | Name of the database operation (possible values are 'INSERT', 'UPDATE' and 'DELETE').  |
| table_name  | character varying(255) | NOT NULL | Name of the table the operation was made on.  |
| record_id  | integer | NOT NULL | ID of the record that was inserted, updated or deleted, in the original table.  |
| field_name  | character varying(255) | NOT NULL | Name of the column that was inserted, updated or deleted.   |
| old_value  | text |  | Previous value of the column if applicable (NULL for INSERT operations).  |
| new_value  | text |  | New value of the column if applicable (NULL for DELETE operations).  |
| user_name  | character varying(255) | NOT NULL | Name of either the logged in user of the UI or the database user behind the connection, that initiated the operation.  |
| timestamp  | timestamp without time zone | NOT NULL | Date and time of the operation.  |
| ha_node_name | character varying(255) |  | Name of the cluster node that initiated the insertion in an HA setup; the default value in standalone setup. |

## 2.15 IDENTIFIERS

Identifier that can be used to identify various objects on X-Road. An identifier record is only created together with records of other entities. There is no check of duplicates when new identifier record is added. The record is deleted when any record associated with the identifier is deleted. For example, when an entity of global_group_members is deleted, respective identifier is deleted as well. The record is never modified.

### 2.15.1 Attributes

| Name            | Type                        | Modifiers | Description           |
|:----------------|:---------------------------:|:--------- |:-----------------:|
| id [PK]         | integer                     | NOT NULL  | Primary key |
| object_type     | character varying(255)      |           | Specifies the type of the object that the identifier identifies. Possible values, defined in enum ee.ria.xroad.common.identifier.XroadObjectType, are 'MEMBER', 'SUBSYSTEM', 'SERVICE', 'CENTRALSERVICE', 'SERVER'. |
| xroad_instance  | character varying(255)      |           | X-Road instance identifier. Present (otherwise NULL) in identifiers of all types. |
| member_class    | character varying(255)      |           | Member class. Present in identifiers of 'MEMBER', 'SUBSYSTEM', 'SERVER' and 'SERVICE' type.  |
| member_code     | character varying(255)      |           | Member code. Present in identifiers of 'MEMBER', 'SUBSYSTEM, SERVER' and 'SERVICE' type.  |
| subsystem_code  | character varying(255)      |           | Subsystem code. Present in identifiers of 'SUBSYSTEM' and 'SERVICE' type. |
| service_code    | character varying(255)      |           | Service code. Present in identifiers of 'SERVICE' type.  |
| server_code     | character varying(255)      |           | Security Server code. Present in identifiers of 'SERVER' type.  |
| created_at      | timestamp without time zone | NOT NULL  | Record creation time, managed automatically.  |
| updated_at      | timestamp without time zone | NOT NULL  | Record last modified time, managed automatically.  |
| service_version | character varying(255)      |           | X-Road service version. May be present in identifiers of 'SERVICE' type. |

## 2.16 MEMBER_CLASSES

Member class supported by this X-Road instance. Member class has the purpose of grouping members with similar properties. A member class must have unique code inside the X-Road instance.

The record is added when the X-Road instance needs new member class. Then an X-Road system administrator adds a new member class in the user interface. The record is deleted when the member class is no longer necessary for this X-Road instance. Then an X-Road system administrator deletes the member class in the user interface. The description of the member class can be edited in the user interface.

### 2.16.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| code  | character varying(255) |  | Member class code, unique inside an X-Road instance. Cannot be NULL. |
| description  | character varying(255) |  | Member class description.  |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically. |

## 2.17 OCSP_INFOS

Information about OCSP service that is offered by a particular CA. See also documentation of table approved_cas.

The record is created when a new OCSP responder needs to be registered for either top CA or intermediate CA of approved CA (see also documentation of tables approved_cas and ca_infos). Then an X-Road system administrator adds new OCSP info in the user interface. The record can be modified or deleted in the user interface.

### 2.17.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_ocsp_infos_on_ca_info_id | ca_info_id |

### 2.17.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key. |
| url  | character varying(255) |  | URL of the OCSP server. Must correspond to the URL format. Cannot be NULL. |
| cert  | bytea |  | Certificate used by the OCSP server to sign OCSP responses (in DER encoding). |
| ca_info_id [FK] | integer |  | ID of the CA info record this OCSP info belongs to. References id attribute of ca_infos entity. Cannot be NULL. |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |

## 2.18 REQUEST_PROCESSINGS

Processing status of the management request. Management requests are means of managing clients and authentication certificates of Security Servers. See also documentation of the table requests. 
- In older version request processing binds together two management requests that refer to the same data but have different origin (Security Server or user interface of the Central Server). If one request associated with the processing is from Central Server, the other one must be from Security Server and vice versa. 
- Starting in X-Road version 7.3.0 request_processing table contain only one record per request, there is no complementary request anymore.

Request processing can have one of following statuses:

- WAITING – Central Server has received a request. From this state, the user must either approve or decline the request.
- SUBMITTED FOR APPROVAL – Central Server has received two complementary requests from different sources. From this state, the user must either approve or decline the request. Starting in X-Road version 7.3.0 not used anymore.
- APPROVED – when the user approves the request, the processing enters APPROVED state. When entering this state the requested action (such as adding a client to a Security Server) is performed.
- REVOKED – when the processing is in WAITING state, respective deletion request can be sent to revoke the request. Deletion request can be sent from Security Server.
- DECLINED – when the processing is in WAITING state, it can be declined from the user interface if X-Road registration officer decides so.

Request processing record is created (registration requests for X-Road client and Security Server authentication certificate) from Security Server. Modifications to the record are related to changes of the request processing status and are described above in this section. The record is never deleted.

### 2.18.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| type  | character varying(255) |  | Application model class type, managed automatically.  Possible values are 'ClientRegProcessing' and 'AuthCertRegProcessing'. |
| status | character varying(255) |  | Current status of the request processing. Possible values are 'NEW', 'WAITING', 'EXECUTING', 'SUBMITTED FOR APPROVAL', 'APPROVED', 'DECLINED' and 'REVOKED'.  |
| created_at | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |

## 2.19 REQUESTS

Management request for creating or deleting association between X-Road member and Security Server. Management requests are divided into registration and deletion requests.

- Registration requests are submitted through X-Road Security Server. The request can be either approved or declined in the user interface of the Central Server. There are two types of registration requests: registration of a Security Server client and registration of Security Server's authentication certificate.
- Deletion requests are there to delete associations between X-Road clients, Security Servers and authentication certificates. Deletion requests are not associated with request processing. There are two types of deletion requests: deletion of Security Server's authentication certificate and  deletion of Security Server' client. Deletion request can be sent for following purposes:
  - if a registration request is mistakenly sent (from user interface of the Security Server), respective (with the same client, Security Server and/or authentication certificate data) deletion request can be sent to delete the bad registration request;
  - if authentication certificate of Central Server needs to be deleted, respective authentication certificate deletion request is sent either from user interface of the Central Server or Security Server;
  - if client of a Security Server needs to be removed, respective deletion request can be sent.

The record is created in the manner described above in this section. Starting in X-Road version 7.3.0 the record is never modified.
The record is never deleted.

### 2.19.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_requests_on_request_processing_id | request_processing_id |
| index_requests_on_sec_serv_user_id | sec_serv_user_id |
| index_requests_on_security_server_id | security_server_id |

### 2.19.2 Attributes

| Name        | Type           | Modifiers        |                                                                                                                                                                              Description                                                                                                                                                                              |
|:----------- |:-----------------:|:----------- |:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| id [PK] | integer | NOT NULL |                                                                                                                                                                              Primary key                                                                                                                                                                              |
| request_processing_id [FK] | integer |  |                                                                          ID of the registration processing object that manages this request. Applicable if type has value 'AuthCertRegRequest' or 'ClientRegRequest', otherwise NULL. References id attribute of request_processings entity.                                                                          |
| type  | character varying(255) |  |                                                                                               Application model class type, managed automatically. Possible values are 'AuthCertRegRequest', 'ClientRegRequest', 'AuthCertDeletionRequest' and 'ClientDeletionRequest'.                                                                                               |
| security_server_id [FK] | integer |  |                                                                                                                           ID of the Security Server related to the request. References id attribute of identifiers entity. Cannot be NULL.                                                                                                                            |
| sec_serv_user_id [FK] | integer |  |                                                                                ID of the Security Server client related to the request. Applicable when type is client registration request or client deletion request, otherwise NULL. References id attribute of identifiers entity.                                                                                |
| auth_cert  | bytea |  |                                                                                                       Applicable for authentication certificate registration or deletion request, otherwise NULL. Contents of the authentication certificate (in DER encoding).                                                                                                       |
| address | character varying(255) |  |                                                  Security Server address for helping X-Road clients to locate the Security Server. Added into the global configuration when authentication certificate registration request is approved. Applicable only for requests of type 'AuthCertRegRequest', otherwise NULL.                                                   |
| origin | character varying(255) |  |                                                                                                                                              Specifies where the request is from either CENTER or from SECURITY_SERVER.                                                                                                                                               |
| comments  | text |  |                                                                                                                                                   Comments related to creating this request. Currently not in use.                                                                                                                                                    |
| created_at  | timestamp without time zone | NOT NULL |                                                                                                                                                             Record creation time, managed automatically.                                                                                                                                                              |
| updated_at   | timestamp without time zone | NOT NULL |                                                                                                                                                           Record last modified time, managed automatically.                                                                                                                                                           |

## 2.20 SECURITY_SERVER_CLIENTS

Contains X-Road members or subsystems. The subject that can be associated with a Security Server. There are two types of associations:

- X-Road members can be Security Server owners. Members cannot provide and consume regular X-Road services (but can consume the X-Road management services).
- X-Road subsystems can be Security Server clients and can provide or consume X-Road services.

The way records are added depends on whether the record holds a member or a subsystem:

- members can be added in the user interface by the X-Road registration officer;
- subsystem can be added in the user interface by the X-Road registration officer and are also added when a client registration request for registering a subsystem that does not previously exist in this table is approved. See also documentation for tables requests and request_processings.

The record is modified when the X-Road registration officer edits the member's name in the user interface.

The record can be deleted in the user interface by an X-Road registration officer.

### 2.20.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_security_server_clients_on_member_class_id | member_class_id |
| index_security_server_clients_on_server_client_id | server_client_id |
| index_security_server_clients_on_xroad_member_id | xroad_member_id |

### 2.20.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| member_code  | character varying(255) |  | Member code, unique inside member class. NULL if type is 'Subsystem'. |
| subsystem_code  | character varying(255) |  | Subsystem code, unique inside member, filled if type is 'Subsystem'. NULL if type is 'XroadMember'. |
| name  | character varying(255) |  | Member (human-readable) name.  |
| xroad_member_id [FK] | integer |  | ID of the member the subsystem record belongs to. Filled if type is 'Subsystem'. NULL if type is 'XroadMember'. References id attribute of security_server_clients entity.  |
| member_class_id [FK] | integer |  | ID of the the member record belongs to. Filled if type is 'XroadMember'.  References id attribute of member_classes entity. Cannot be NULL. |
| server_client_id [FK] | integer |  | Full identifier of the client. References id attribute of identifiers entity. Cannot be NULL. |
| type  | character varying(255) |  | Application model class type, managed automatically. Possible values are 'XroadMember' and 'Subsystem'. |
| administrative_contact  | character varying(255) |  | Administrative contact of the member, may be e-mail, phone etc. NB! Not used at the moment! |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |

## 2.21 SECURITY_SERVERS

Information about a Security Server registered in this X-Road instance. Security Server always belongs to a particular X-Road member. For Security Server to function properly, it needs at least one authentication certificate. Security Server may have clients (subsystems).

A prerequisite for creating the record is that the authentication certificate registration request for not yet existing Security Server are approved (see also documentation of tables requests and request_processings). The record is created when request is approved by an X-Road registration officer in the user interface. The record is modified when an X-Road registration officer edits Security Server address in the user interface. The record can be deleted in the user interface by an X-Road registration officer.

### 2.21.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_security_servers_on_xroad_member_id | xroad_member_id |

### 2.21.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| server_code  | character varying(255) |  | Security Server code, unique between Security Servers belonging to the same owner. Cannot be NULL. |
| owner_id [FK] | integer |  | ID of the X-Road member that owns the Security Server. References id attribute of security_server_clients entity. Cannot be NULL. |
| address  | character varying(255) |  | DNS name or IP-address of the Security Server.  |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |


## 2.22 SERVER_CLIENTS

Join table enabling many-to-many relationship between Security Servers and Security Server clients. In other words, associates Security Servers with its clients.

The record is created when a new client is added to the Security Server. It requires approval of a client registration request (see documentation of tables requests and request_processings for details). An X-Road registration officer can do it in the user interface. The record is deleted when a client of a Security Server is deleted in the user interface by an X-Road registration officer. The record is never modified.

### 2.22.1 Indexes

| Name        | Columns           |
|:----------- |:-----------------:|
| index_server_clients_on_security_server_client_id | security_server_client_id |
| index_server_clients_on_security_server_id | security_server_id |

### 2.22.2 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| security_server_id [FK] | integer | NOT NULL | ID of the Security Server. References id attribute of security_servers entity. |
| security_server_client_id [FK] | integer | NOT NULL | ID of the client the Security Server has. References id attribute of security_server_clients entity. |

## 2.23 SYSTEM_PARAMETERS

System configuration parameter necessary for proper functioning of Central Server and entire X-Road for that matter. System parameters are stored as key-value pairs. Following is the list of supported system parameters. In an HA setup, the name of the node that initiated a particular insertion, is not significant, except for where stated explicitly.

1. managementServiceProviderClass – Member class part of the identifier pointing to the Security Server client that provides management services. The value can be changed in the user interface.
2. managementServiceProviderCode – Member code part of the identifier pointing to the Security Server client that provides management services. The value can be changed in the user interface.
3. managementServiceProviderSubsystem – Subsystem code part of the identifier pointing to the Security Server client that provides management services. The value can be changed in the user interface.
4. centralServerAddress – the DNS name of this Central Server. The value can be changed in the user interface. In an HA setup, the value is local to each node of the cluster.
5. instanceIdentifier – the instance identifier of this X-Road instance. Must be globally unique. The value is assigned during the initialization of the Central Server in the user interface.
6. authCertRegUrl – URL where Security Servers can send their authentication certificate registration requests. May contain placeholder %{centralServerAddress} which will be replaced with value of the centralServerAddress system parameter.
7. confSignDigestAlgoId  – identifier of the digest algorithm that is used for signing the global configuration. Supported values are 'SHA-512', 'SHA-384' and 'SHA-256'.
8. confHashAlgoUri – URI of the algorithm that is used to hash distributable global configuration files. Supported values are http://www.w3.org/2001/04/xmlenc#sha512 and http://www.w3.org/2001/04/xmlenc#sha256.
9. confSignCertHashAlgoUri – URI of the algorithm that is used to hash global configuration signing certificate. Supported values are http://www.w3.org/2001/04/xmlenc#sha512 and http://www.w3.org/2001/04/xmlenc#sha256.
10. securityServerOwnersGroup – name of the global group where all the members that get ownership of any Security Server are automatically added.
11. confExpireIntervalSeconds – time (in seconds)  during which generated global configuration is considered valid.
12. ocspFreshnessSeconds – time (in seconds) during which Security Servers should consider validity information to be usable. After that time, cached OCSP responses must be discarded. This configuration parameter is distributed to Security Servers as part of global configuration.

Some system parameters can be modified by an X-Road security officer in the user interface. All the system parameters that cannot be changed in the user interface, are assigned default values during the initialization of the Central Server. Later these can only be changed from the database. As these parameters are critical for functioning of entire X-Road instance, these must be modified with extreme care.

### 2.23.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| key | character varying(255) |  | System parameter key. Cannot be NULL. |
| value  | character varying(255) |  | System parameter value corresponding to the key.  |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |
| ha_node_name | character varying(255) |  | Name of the cluster node that initiated the insertion in an HA setup; the default value in standalone setup. |

## 2.24 TRUSTED_ANCHORS

Trusted anchor of a federation partner. A trusted anchor is the configuration anchor of the configuration source distributing the external configuration of a federation partner.

The record is created or modified exactly the same way as described in the documentation of table anchor_url_certs. The record is never modified.

### 2.24.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK]  | integer | NOT NULL | Primary key |
| instance_identifier | character varying(255) |  | Instance identifier of the trusted anchor. Cannot be NULL. |
| trusted_anchor_file  | bytea |  | Trusted anchor file (in XML format). Cannot be NULL. |
| trusted_anchor_hash  | text |  | Hash of the trusted anchor file. Cannot be NULL. |
| created_at  | timestamp without time zone |  | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone |  | Record last modified time, managed automatically.  |
| generated_at  | timestamp without time zone |  | Anchor generation time (read from the anchor file).  |

## 2.25 UI_USERS

UI user name with its last used locale. Maps possible user interface (UI) user names with locales so that when UI user is logged in next time, the locale it has been used is remembered. If a user with no assigned locale logs in, the first available locale is selected to this user. Later user can change its locale in the user interface.

The record is created when the user is logged in the user interface for the first time. The record is modified when the user logged in changes its locale in the user interface. The record is never deleted.

### 2.25.1 Attributes

| Name        | Type           | Modifiers        | Description           |
|:----------- |:-----------------:|:----------- |:-----------------:|
| id [PK] | integer | NOT NULL | Primary key |
| username | character varying(255) |  | User name of the UI user.  |
| locale  | character varying(255) |  | Current locale of the UI user.  |
| created_at  | timestamp without time zone | NOT NULL | Record creation time, managed automatically.  |
| updated_at  | timestamp without time zone | NOT NULL | Record last modified time, managed automatically.  |
