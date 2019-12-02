declare namespace Components {
    namespace Schemas {
        /**
         * access right for clients and services
         */
        export interface AccessRight {
            /**
             * access right id
             * example:
             * 123
             */
            readonly id: string; // text
            /**
             * <instance_id>:<member_class>:<member_code>:<subsystem>(optional)
             * example:
             * FI:GOV:123:ABC
             */
            client_id: string; // text
            /**
             * service code
             * example:
             * clientDeletion
             */
            service_code: string; // text
            /**
             * service title
             * example:
             * client deletion
             */
            service_title: string; // text
        }
        /**
         * security server anchor
         */
        export interface Anchor {
            /**
             * anchor hash
             * example:
             * 42:34:C3:22:55:42:34:C3:22:55:42:34:C3:22:55:42:34:C3:22:55:42:34:C3:22:55:42:34:C3
             */
            hash: string; // hash
            /**
             * anchor created at
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            created_at: string; // date-time
        }
        /**
         * security server backup
         */
        export interface Backup {
            /**
             * backup filename
             * example:
             * configuration_backup_20181224.tar
             */
            filename: string; // filename
            /**
             * backup created at
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            created_at: string; // date-time
        }
        /**
         * certificate authority
         */
        export interface CertificateAuthority {
            /**
             * distinguished name
             * example:
             * /C=FI/O=X-Road Test/OU=X-Road Test CA OU/CN=X-Road Test CA CN
             */
            name: string; // text
            /**
             * certificate authority response
             * example:
             * N/A
             */
            response: string; // text
            /**
             * certificate authority expires at
             * example:
             * 2099-12-15T00:00:00.001Z
             */
            expires_at: string; // date-time
        }
        /**
         * certificate details for any kind of certificate (TLS, auth, sign)
         */
        export interface CertificateDetails {
            /**
             * certificate issuer distinguished name
             * example:
             * issuer123
             */
            issuer_distinguished_name: string; // text
            /**
             * certificate issuer common name
             * example:
             * domain.com
             */
            issuer_common_name: string; // text
            /**
             * certificate subject distinguished name
             * example:
             * subject123
             */
            subject_distinguished_name: string; // text
            /**
             * certificate subject common name
             * example:
             * domain.com
             */
            subject_common_name: string; // text
            /**
             * certificate validity not before
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            not_before: string; // date-time
            /**
             * certificate validity not after
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            not_after: string; // date-time
            /**
             * serial number
             * example:
             * 123456789
             */
            serial: string; // text
            /**
             * version
             * example:
             * 3
             */
            version: number; // int32
            /**
             * certificate signature algorithm
             * example:
             * sha256WithRSAEncryption
             */
            signature_algorithm: string; // sha-256
            /**
             * hex encoded certificate signature
             * example:
             * 30af2fdc1780...
             */
            signature: string; // text
            /**
             * certificate public key algorithm
             * example:
             * sha256WithRSAEncryption
             */
            public_key_algorithm: string; // sha-256
            /**
             * hex encoded RSA public key modulus (if RSA key)
             * example:
             * c44421d601...
             */
            rsa_public_key_modulus: string; // hex
            /**
             * RSA public key exponent (if RSA key) as an integer
             * example:
             * 65537
             */
            rsa_public_key_exponent: number; // int32
            /**
             * certificate SHA-1 hash
             * example:
             * 1234567890ABCDEF
             */
            hash: string; // text
            /**
             * certificate key usage array
             */
            key_usages: KeyUsage /* enum */ [];
        }
        /**
         * certificate status
         * example:
         * IN_USE
         */
        export type CertificateOcspStatus = "DISABLED" | "EXPIRED" | "OCSP_RESPONSE_UNKNOWN" | "OCSP_RESPONSE_GOOD" | "OCSP_RESPONSE_SUSPENDED" | "OCSP_RESPONSE_REVOKED"; // enum
        /**
         * certificate status
         * example:
         * IN_USE
         */
        export type CertificateStatus = "SAVED" | "REGISTRATION_IN_PROGRESS" | "REGISTERED" | "DELETION_IN_PROGRESS" | "GLOBAL_ERROR"; // enum
        /**
         * x-road client
         */
        export interface Client {
            /**
             * <instance_id>:<member_class>:<member_code>:<subsystem>(optional)
             * example:
             * FI:GOV:123:ABC
             */
            readonly id?: string; // text
            /**
             * member name
             * example:
             * FI
             */
            member_name?: string; // text
            /**
             * member class
             * example:
             * GOV
             */
            member_class: string; // text
            /**
             * member code
             * example:
             * 123
             */
            member_code: string; // text
            /**
             * subsystem code
             * example:
             * ABC
             */
            subsystem_code: string; // text
            connection_type?: ConnectionType; // enum
            status?: ClientStatus; // enum
        }
        /**
         * request to add client. Carries a Client and ignore warnings parameter
         */
        export interface ClientAdd {
            client: Client;
            /**
             * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
             */
            ignore_warnings?: boolean;
        }
        /**
         * client status
         * example:
         * REGISTERED
         */
        export type ClientStatus = "REGISTERED" | "SAVED" | "GLOBAL_ERROR" | "REGISTRATION_IN_PROGRESS" | "DELETION_IN_PROGRESS"; // enum
        /**
         * object that contains a code identifier and possibly collection of associated metadata. Used to relay error and warning information.
         */
        export interface CodeWithMetadata {
            /**
             * identifier of the item (for example errorcode)
             * example:
             * adding_services
             */
            code: string; // text
            /**
             * array containing metadata associated with the item. For example names of services were attempted to add, but failed
             */
            metadata?: string /* text */ [];
        }
        /**
         * configuration status
         * example:
         * SUCCESS
         */
        export type ConfigurationStatus = "SUCCESS" | "CONFCLIENT_STATUS_FAILED" | "ERROR_CODE_INTERNAL" | "ERROR_CODE_INVALID_SIGNATURE_VALUE" | "ERROR_CODE_EXPIRED_CONF" | "ERROR_CODE_CANNOT_DOWNLOAD_CONF" | "ERROR_CODE_MISSING_PRIVATE_PARAM"; // enum
        /**
         * connection type
         * example:
         * HTTP
         */
        export type ConnectionType = "HTTP" | "HTTPS" | "HTTPS_NO_AUTH"; // enum
        /**
         * csr format
         * example:
         * PEM
         */
        export type CsrFormat = "PEM" | "DER"; // enum
        /**
         * object returned in error cases
         */
        export interface ErrorInfo {
            /**
             * http status code
             * example:
             * 400
             */
            status: number; // int32
            error?: CodeWithMetadata;
            /**
             * warnings that could be ignored
             */
            warnings?: CodeWithMetadata[];
        }
        /**
         * global configuration
         */
        export interface GlobalConfiguration {
            status: ConfigurationStatus; // enum
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            updated_at: string; // date-time
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            next_update_at: string; // date-time
        }
        /**
         * group member
         */
        export interface GroupMember {
            /**
             * group member id
             * example:
             * FI:GOV:123:SS1
             */
            id: string; // text
            /**
             * group member name
             * example:
             * Member123
             */
            name: string; // text
            /**
             * group member created at
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            created_at: string; // date-time
        }
        /**
         * key for the certificate
         */
        export interface Key {
            /**
             * key id
             * example:
             * 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
             */
            readonly id: string; // text
            /**
             * key name
             * example:
             * friendly name
             */
            name: string; // text
            /**
             * key label
             * example:
             * key label
             */
            label: string; // text
            /**
             * list of certificates for the key
             */
            certificates: TokenCertificate[];
            /**
             * list of CSRs for the key
             */
            certificate_signing_requests: TokenCertificateSigningRequest[];
            usage: KeyUsageType; // enum
            /**
             * if the key is available
             * example:
             * true
             */
            available?: boolean;
            /**
             * if the key is saved to configuration
             * example:
             * true
             */
            saved_to_configuration?: boolean;
        }
        /**
         * certificate key usage
         * example:
         * NON_REPUDIATION
         */
        export type KeyUsage = "DIGITAL_SIGNATURE" | "NON_REPUDIATION" | "KEY_ENCIPHERMENT" | "DATA_ENCIPHERMENT" | "KEY_AGREEMENT" | "KEY_CERT_SIGN" | "CRL_SIGN" | "ENCIPHER_ONLY" | "DECIPHER_ONLY"; // enum
        /**
         * intended usage for the key
         * example:
         * AUTHENTICATION
         */
        export type KeyUsageType = "AUTHENTICATION" | "SIGNING"; // enum
        /**
         * key-value pair of strings
         */
        export interface KeyValuePair {
            /**
             * key
             */
            key: string;
            /**
             * value
             */
            value: string;
        }
        /**
         * language
         */
        export interface Language {
            /**
             * language code
             * example:
             * en
             */
            readonly id: string; // text
        }
        /**
         * group
         */
        export interface LocalGroup {
            /**
             * unique identifier
             * example:
             * 123
             */
            readonly id?: string; // text
            /**
             * group code
             * example:
             * groupcode
             */
            code: string; // text
            /**
             * group description
             * example:
             * description
             */
            description: string; // text
            /**
             * member count
             * example:
             * 10
             */
            member_count?: number; // uint
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            updated_at?: string; // date-time
            /**
             * group members
             */
            members?: GroupMember[];
        }
        /**
         * request object containing an array of member ids
         */
        export interface Members {
            /**
             * array of members to be added
             */
            items?: string /* text */ [];
        }
        /**
         * ocsp responce
         */
        export interface OcspResponders {
            /**
             * service url
             * example:
             * https://domain.com/service
             */
            url: string; // url
            status: OcspStatus; // enum
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            updated_at: string; // date-time
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            next_update_at: string; // date-time
        }
        /**
         * OCSP responder status
         * example:
         * SUCCESS
         */
        export type OcspStatus = "SUCCESS" | "ERROR_CODE_OCSP_CONNECTION_ERROR" | "ERROR_CODE_OCSP_FAILED" | "ERROR_CODE_OCSP_RESPONSE_INVALID" | "ERROR_CODE_OCSP_UNINITIALIZED"; // enum
        /**
         * security server information
         */
        export interface SecurityServer {
            /**
             * <instance_id>:<member_class>:<member_code>:<security_server_code>
             * example:
             * FI:GOV:123:sserver1
             */
            id: string; // text
            /**
             * xroad instance id
             * example:
             * FI
             */
            instance_id?: string; // text
            /**
             * member class
             * example:
             * GOV
             */
            member_class?: string; // text
            /**
             * member code
             * example:
             * 123
             */
            member_code?: string; // text
            /**
             * security server code
             * example:
             * server123
             */
            server_code?: string; // text
        }
        /**
         * service for the service description
         */
        export interface Service {
            /**
             * encoded service id, including client id
             * example:
             * CS:ORG:Client:myService.v1
             */
            id: string; // text
            /**
             * encoded service code and version
             * example:
             * myService.v1
             */
            service_code: string; // text
            /**
             * service time out value
             * example:
             * 60
             */
            timeout: number; // uint
            /**
             * service ssl auth
             * example:
             * true
             */
            ssl_auth?: boolean;
            /**
             * count of acl subjects
             * example:
             * 5
             */
            subjects_count?: number; // uint
            /**
             * service url
             * example:
             * https://domain.com/service
             */
            url: string; // url
        }
        /**
         * service client
         */
        export interface ServiceClient {
            subject: Subject;
            /**
             * access right given at
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            rights_given_at: string; // date-time
            /**
             * list of access rights - this will be null when requested via services/{id}/access-rights endpoint
             */
            access_rights: AccessRight[];
        }
        /**
         * WSDL/REST service
         */
        export interface ServiceDescription {
            /**
             * unique identifier
             * example:
             * 123
             */
            id: string;
            /**
             * service url
             * example:
             * http://dev.xroad.rocks/services.wsdl
             */
            url: string; // url
            type: ServiceType; // text
            /**
             * service disabled
             * example:
             * true
             */
            disabled: boolean;
            /**
             * disabled notice
             * example:
             * default_disabled_service_notice
             */
            disabled_notice: string; // text
            /**
             * time for service refresh
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            refreshed_at: string; // date-time
            /**
             * service description services
             */
            services: Service[];
            /**
             * <instance_id>:<member_class>:<member_code>:<subsystem>(optional)
             * example:
             * FI:GOV:123:ABC
             */
            client_id: string; // text
        }
        /**
         * request object containing service description url, service code and type
         */
        export interface ServiceDescriptionAdd {
            /**
             * path for the service description file
             * example:
             * https://domain.com/service
             */
            url: string; // text
            /**
             * service code for REST service
             * example:
             * exampleServiceCode
             */
            rest_service_code?: string; // text
            /**
             * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
             */
            ignore_warnings?: boolean;
            type: ServiceType; // text
        }
        /**
         * request object for updating a service description url or service code
         */
        export interface ServiceDescriptionUpdate {
            /**
             * path for the service description file
             * example:
             * https://domain.com/service
             */
            url?: string; // text
            /**
             * service code for REST service
             * example:
             * exampleServiceCode
             */
            rest_service_code?: string; // text
            /**
             * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
             */
            ignore_warnings?: boolean;
            type?: ServiceType; // text
        }
        /**
         * service type
         * example:
         * WSDL
         */
        export type ServiceType = "WSDL" | "REST"; // text
        /**
         * object for updating a service or all services within service description
         */
        export interface ServiceUpdate {
            service: Service;
            /**
             * url is applied for all services
             * example:
             * false
             */
            url_all?: boolean;
            /**
             * timeout value is applied for all services
             * example:
             * false
             */
            timeout_all?: boolean;
            /**
             * ssl authentication is applied for all services
             * example:
             * false
             */
            ssl_auth_all?: boolean;
        }
        /**
         * subject
         */
        export interface Subject {
            /**
             * subject id - can be a subsystem id <instance_id>:<member_class>:<member_code>:<subsystem> | globalgroup id <instance_id>:<group_code> | localgroup resource id in number format <id>
             * example:
             * DEV:ORG:1234:Subsystem | DEV:security-server-owners | 123
             */
            id: string; // text
            /**
             * name of the subject - can be the name of a member or the description of a group
             * example:
             * Security server owners
             */
            readonly member_name_group_description?: string; // text
            /**
             * group code in case the object is a local group
             * example:
             * My own Local group code
             */
            readonly local_group_code?: string; // text
            subject_type: SubjectType; // text
        }
        /**
         * subject type
         * example:
         * GLOBALGROUP
         */
        export type SubjectType = "GLOBALGROUP" | "LOCALGROUP" | "SUBSYSTEM"; // text
        /**
         * object containing and array of subject ids
         */
        export interface Subjects {
            /**
             * array of subject ids
             */
            items?: Subject[];
        }
        /**
         * system parameters
         */
        export interface System {
            anchor: Anchor;
            configuration: GlobalConfiguration;
            timestamping_services: TimestampingService[];
            ocsp_responders: OcspResponders;
            /**
             * system certificate authorities
             */
            certificate_authorities: CertificateAuthority[];
            tls_certificate: CertificateDetails;
            version: Version;
        }
        /**
         * timestamping services
         */
        export interface TimestampingService {
            /**
             * name of the time stamping service
             * example:
             * X-Road Test TSA CN
             */
            name: string; // text
            /**
             * url of the time stamping service
             * example:
             * http://dev.xroad.rocks:123
             */
            url: string; // url
            /**
             * last time updated
             * example:
             * 2018-12-15T00:00:00.001Z
             */
            updated_at: string; // date-time
            /**
             * timestamping service message
             * example:
             * ok
             */
            message: string; // text
            status: TimestampingStatus; // enum
        }
        /**
         * timestamping status
         * example:
         * SUCCESS
         */
        export type TimestampingStatus = "SUCCESS" | "ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT" | "ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL" | "ERROR_CODE_UNKNOWN" | "ERROR_CODE_UNINITIALIZED" | "ERROR_CODE_TIMESTAMP_UNINITIALIZED" | "ERROR_CODE_CONNECTION_FAILED"; // enum
        /**
         * token
         */
        export interface Token {
            /**
             * token id
             * example:
             * 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
             */
            readonly id: string; // text
            /**
             * token name
             * example:
             * softToken-0
             */
            name: string; // text
            type: TokenType; // text
            /**
             * token keys
             */
            keys: Key[];
            status: TokenStatus; // text
            /**
             * if the token has been logged in to
             * example:
             * true
             */
            logged_in: boolean;
            /**
             * if the token is available
             * example:
             * true
             */
            available: boolean;
            /**
             * if the token is saved to configuration
             * example:
             * true
             */
            saved_to_configuration: boolean;
            /**
             * if the token is read-only
             * example:
             * true
             */
            read_only: boolean;
            /**
             * serial number of the token
             * example:
             * 12345
             */
            serial_number?: string; // text
            /**
             * Contains label-value pairs of information
             */
            token_infos?: KeyValuePair[];
        }
        /**
         * certificate that is stored in a Token (auth or sign cert)
         */
        export interface TokenCertificate {
            ocsp_status: CertificateOcspStatus; // enum
            /**
             * client id of the owner member, <instance_id>:<member_class>:<member_code>
             * example:
             * FI:GOV:123
             */
            readonly owner_id: string; // text
            /**
             * if the certificate is active
             * example:
             * true
             */
            active: boolean;
            /**
             * if the certificate is saved to configuration
             * example:
             * true
             */
            saved_to_configuration: boolean;
            certificate_details: CertificateDetails;
            status: CertificateStatus; // enum
        }
        /**
         * CSR for certificate that is stored in a Token
         */
        export interface TokenCertificateSigningRequest {
            /**
             * CSR id
             * example:
             * 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
             */
            readonly id: string; // text
            /**
             * client id of the owner member, <instance_id>:<member_class>:<member_code>
             * example:
             * FI:GOV:123
             */
            readonly owner_id: string; // text
        }
        /**
         * token type
         * example:
         * OK
         */
        export type TokenStatus = "OK" | "USER_PIN_LOCKED" | "USER_PIN_INCORRECT" | "USER_PIN_INVALID" | "USER_PIN_EXPIRED" | "USER_PIN_COUNT_LOW" | "USER_PIN_FINAL_TRY" | "NOT_INITIALIZED"; // text
        /**
         * token type
         * example:
         * SOFTWARE
         */
        export type TokenType = "SOFTWARE" | "HARDWARE"; // text
        /**
         * x-road user
         */
        export interface User {
            /**
             * user username
             * example:
             * Guest
             */
            username: string; // text
            /**
             * user roles
             */
            roles: string /* text */ [];
            /**
             * user permissions
             */
            permissions: string /* text */ [];
        }
        /**
         * version information
         */
        export interface Version {
            /**
             * information about the security server
             * example:
             * Security Server version 6.21.0-SNAPSHOT-20190411git32add470
             */
            info: string; // text
        }
    }
}
declare namespace Paths {
    namespace ActivateCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
    }
    namespace AddBackup {
        namespace Responses {
            export type $201 = Components.Schemas.Backup;
        }
    }
    namespace AddCertificate {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $201 = Components.Schemas.CertificateDetails;
        }
    }
    namespace AddClient {
        export type RequestBody = Components.Schemas.ClientAdd;
        namespace Responses {
            export type $201 = Components.Schemas.Client;
            export type $400 = Components.Schemas.ErrorInfo[];
        }
    }
    namespace AddClientGroup {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.LocalGroup;
        namespace Responses {
            export type $201 = Components.Schemas.LocalGroup;
        }
    }
    namespace AddClientServiceClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * ServiceClientId
         */
        export interface RequestBody {
            /**
             * id of the service client
             */
            id?: string; // text
        }
        namespace Responses {
            export type $201 = Components.Schemas.ServiceClient;
        }
    }
    namespace AddClientServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.ServiceDescriptionAdd;
        namespace Responses {
            export type $201 = Components.Schemas.ServiceDescription;
            export type $400 = Components.Schemas.ErrorInfo[];
        }
    }
    namespace AddClientTlsCertificate {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $201 = Components.Schemas.CertificateDetails;
        }
    }
    namespace AddGroupMember {
        namespace Parameters {
            export type GroupId = string; // text
        }
        export interface PathParameters {
            group_id: Parameters.GroupId; // text
        }
        export type RequestBody = Components.Schemas.Members;
        namespace Responses {
            export type $201 = Components.Schemas.Members;
        }
    }
    namespace AddKey {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * KeyLabel
         * example:
         * {
         *   "label": "My new key"
         * }
         */
        export interface RequestBody {
            /**
             * label for the new key
             */
            label?: string; // text
        }
        namespace Responses {
            export type $201 = Components.Schemas.Key;
        }
    }
    namespace AddServiceAccessRight {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.Subjects;
        namespace Responses {
            /**
             * array of added service client objects
             */
            export type $201 = Components.Schemas.ServiceClient[];
        }
    }
    namespace AddServiceClientAccessRight {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.Subjects;
        namespace Responses {
            /**
             * array of added access right objects
             */
            export type $201 = Components.Schemas.AccessRight[];
        }
    }
    namespace AddTimestampingService {
        export type RequestBody = Components.Schemas.TimestampingService;
        namespace Responses {
            export type $201 = Components.Schemas.TimestampingService;
        }
    }
    namespace DeactivateCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
    }
    namespace DeleteBackup {
        namespace Parameters {
            export type Filename = string; // filename
        }
        export interface PathParameters {
            filename: Parameters.Filename; // filename
        }
    }
    namespace DeleteCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
    }
    namespace DeleteClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace DeleteClientTlsCertificate {
        namespace Parameters {
            export type Hash = string; // text
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
            hash: Parameters.Hash; // text
        }
    }
    namespace DeleteCsr {
        namespace Parameters {
            export type CsrId = string; // text
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export interface QueryParameters {
            csr_id: Parameters.CsrId; // text
        }
    }
    namespace DeleteGroup {
        namespace Parameters {
            export type GroupId = string; // text
        }
        export interface PathParameters {
            group_id: Parameters.GroupId; // text
        }
    }
    namespace DeleteGroupMember {
        namespace Parameters {
            export type GroupId = string; // text
        }
        export interface PathParameters {
            group_id: Parameters.GroupId; // text
        }
        export type RequestBody = Components.Schemas.Members;
    }
    namespace DeleteKey {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace DeleteServiceAccessRight {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.Subjects;
    }
    namespace DeleteServiceClientAccessRight {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.Subjects;
    }
    namespace DeleteServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace DeleteTimestampingService {
        namespace Parameters {
            export type Url = string; // text
        }
        export interface PathParameters {
            url: Parameters.Url; // text
        }
    }
    namespace DisableServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * ServiceDescriptionDisabledNotice
         */
        export interface RequestBody {
            /**
             * disabled service notice
             */
            disabled_notice?: string; // text
        }
    }
    namespace DownloadBackup {
        namespace Parameters {
            export type Filename = string; // filename
        }
        export interface PathParameters {
            filename: Parameters.Filename; // filename
        }
    }
    namespace EnableServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace FindClients {
        namespace Parameters {
            export type Instance = string; // text
            export type InternalSearch = boolean;
            export type MemberClass = string; // text
            export type MemberCode = string; // text
            export type Name = string; // text
            export type ShowMembers = boolean;
            export type SubsystemCode = string; // text
        }
        export interface QueryParameters {
            name?: Parameters.Name; // text
            instance?: Parameters.Instance; // text
            member_class?: Parameters.MemberClass; // text
            member_code?: Parameters.MemberCode; // text
            subsystem_code?: Parameters.SubsystemCode; // text
            show_members?: Parameters.ShowMembers;
            internal_search?: Parameters.InternalSearch;
        }
        namespace Responses {
            /**
             * array of client objects
             */
            export type $200 = Components.Schemas.Client[];
        }
    }
    namespace FindSubjects {
        namespace Parameters {
            export type Id = string; // text
            export type Instance = string; // text
            export type MemberClass = string; // text
            export type MemberGroupCode = string; // text
            export type MemberNameGroupDescription = string; // text
            export type SubjectType = Components.Schemas.SubjectType; // text
            export type SubsystemCode = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export interface QueryParameters {
            member_name_group_description?: Parameters.MemberNameGroupDescription; // text
            subject_type?: Parameters.SubjectType;
            instance?: Parameters.Instance; // text
            member_class?: Parameters.MemberClass; // text
            member_group_code?: Parameters.MemberGroupCode; // text
            subsystem_code?: Parameters.SubsystemCode; // text
        }
        namespace Responses {
            /**
             * array of Subjects
             */
            export type $200 = Components.Schemas.Subject[];
        }
    }
    namespace GenrerateCsr {
        namespace Parameters {
            export type ApprovedCa = string; // text
            export type CsrFormat = Components.Schemas.CsrFormat; // enum
            export type Id = string; // text
            export type KeyUsage = Components.Schemas.KeyUsageType; // enum
            export type MemberId = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export interface QueryParameters {
            key_usage?: Parameters.KeyUsage;
            approved_ca?: Parameters.ApprovedCa; // text
            csr_format?: Parameters.CsrFormat;
            member_id?: Parameters.MemberId; // text
        }
    }
    namespace GetAnchor {
        namespace Responses {
            export type $200 = Components.Schemas.Anchor;
        }
    }
    namespace GetBackups {
        namespace Responses {
            /**
             * array of backup objects
             */
            export type $200 = Components.Schemas.Backup[];
        }
    }
    namespace GetCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.CertificateDetails;
        }
    }
    namespace GetClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Client;
        }
    }
    namespace GetClientGroups {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of local group objects
             */
            export type $200 = Components.Schemas.LocalGroup[];
        }
    }
    namespace GetClientServiceClients {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of service client objects
             */
            export type $200 = Components.Schemas.ServiceClient[];
        }
    }
    namespace GetClientServiceDescriptions {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of service description objects
             */
            export type $200 = Components.Schemas.ServiceDescription[];
        }
    }
    namespace GetClientSignCertificates {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of certificate (details) objects
             */
            export type $200 = Components.Schemas.TokenCertificate[];
        }
    }
    namespace GetClientTlsCertificate {
        namespace Parameters {
            export type Hash = string; // text
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
            hash: Parameters.Hash; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.CertificateDetails;
        }
    }
    namespace GetClientTlsCertificates {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of certificate (details) objects
             */
            export type $200 = Components.Schemas.CertificateDetails[];
        }
    }
    namespace GetGroup {
        namespace Parameters {
            export type GroupId = string; // text
        }
        export interface PathParameters {
            group_id: Parameters.GroupId; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.LocalGroup;
        }
    }
    namespace GetKey {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Key;
        }
    }
    namespace GetMemberClasses {
        namespace Parameters {
            export type CurrentInstance = boolean;
        }
        export interface QueryParameters {
            current_instance?: Parameters.CurrentInstance;
        }
        namespace Responses {
            /**
             * array of member classes
             */
            export type $200 = string /* text */ [];
        }
    }
    namespace GetMemberClassesForInstance {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of member classes
             */
            export type $200 = string /* text */ [];
        }
    }
    namespace GetSecurityServer {
        namespace Parameters {
            /**
             * <instance_id>:<member_class>:<member_code>:<security_server_code>
             * example:
             * FI:GOV:123:sserver1
             */
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.SecurityServer;
        }
    }
    namespace GetService {
        namespace Parameters {
            /**
             * example:
             * CS:ORG:Client:myService.v1
             */
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Service;
        }
    }
    namespace GetServiceAccessRights {
        namespace Parameters {
            /**
             * example:
             * CS:ORG:Client:myService.v1
             */
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of service client objects
             */
            export type $200 = Components.Schemas.ServiceClient[];
        }
    }
    namespace GetServiceClientAccessRights {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of access right objects
             */
            export type $200 = Components.Schemas.AccessRight[];
        }
    }
    namespace GetServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.ServiceDescription;
        }
    }
    namespace GetServiceDescriptionServices {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            /**
             * array of service objects
             */
            export type $200 = Components.Schemas.Service[];
        }
    }
    namespace GetSystem {
        namespace Responses {
            export type $200 = Components.Schemas.System;
        }
    }
    namespace GetSystemCertificate {
        namespace Responses {
            export type $200 = Components.Schemas.CertificateDetails;
        }
    }
    namespace GetTimestampingServices {
        namespace Responses {
            /**
             * array of timestamping service objects
             */
            export type $200 = Components.Schemas.TimestampingService[];
        }
    }
    namespace GetToken {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Token;
        }
    }
    namespace GetTokens {
        namespace Responses {
            /**
             * array of token objects
             */
            export type $200 = Components.Schemas.Token[];
        }
    }
    namespace GetXroadInstances {
        namespace Responses {
            /**
             * array of xroad instance identifiers
             */
            export type $200 = string /* text */ [];
        }
    }
    namespace ImportSystemCertificate {
        namespace Responses {
            export type $201 = Components.Schemas.CertificateDetails;
        }
    }
    namespace Language {
        namespace Parameters {
            export type Code = string; // text
        }
        export interface PathParameters {
            code: Parameters.Code; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Language;
        }
    }
    namespace LoginToken {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * TokenPassword
         * example:
         * {
         *   "password": "sm3!!ycat"
         * }
         */
        export interface RequestBody {
            /**
             * password for logging in to the token
             */
            password?: string; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Token;
        }
    }
    namespace LogoutToken {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Token;
        }
    }
    namespace RefreshServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * IgnoreWarnings
         */
        export interface RequestBody {
            /**
             * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
             */
            ignore_warnings?: boolean;
        }
        namespace Responses {
            export type $200 = Components.Schemas.ServiceDescription;
            export type $400 = Components.Schemas.ErrorInfo[];
        }
    }
    namespace RegisterCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
    }
    namespace RegisterClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace RestoreBackup {
        namespace Parameters {
            export type Filename = string; // filename
        }
        export interface PathParameters {
            filename: Parameters.Filename; // filename
        }
    }
    namespace SystemVersion {
        namespace Responses {
            export type $200 = Components.Schemas.Version;
        }
    }
    namespace UnregisterCertificate {
        namespace Parameters {
            export type Hash = string; // text
        }
        export interface PathParameters {
            hash: Parameters.Hash; // text
        }
    }
    namespace UnregisterClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
    }
    namespace UpdateClient {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * ConnectionTypeWrapper
         * connection type
         */
        export interface RequestBody {
            connection_type?: Components.Schemas.ConnectionType; // enum
        }
        namespace Responses {
            export type $200 = Components.Schemas.Client;
        }
    }
    namespace UpdateGroup {
        namespace Parameters {
            export type Description = string; // text
            export type GroupId = string; // text
        }
        export interface PathParameters {
            group_id: Parameters.GroupId; // text
        }
        export interface QueryParameters {
            description?: Parameters.Description; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.LocalGroup;
        }
    }
    namespace UpdateKey {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * KeyName
         * example:
         * {
         *   "name": "my-key-0"
         * }
         */
        export interface RequestBody {
            /**
             * Friendly name of a key
             */
            name: string; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Key;
        }
    }
    namespace UpdateService {
        namespace Parameters {
            /**
             * example:
             * CS:ORG:Client:myService.v1
             */
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.ServiceUpdate;
        namespace Responses {
            export type $200 = Components.Schemas.Service;
        }
    }
    namespace UpdateServiceDescription {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        export type RequestBody = Components.Schemas.ServiceDescriptionUpdate;
        namespace Responses {
            export type $200 = Components.Schemas.ServiceDescription;
            export type $400 = Components.Schemas.ErrorInfo[];
        }
    }
    namespace UpdateToken {
        namespace Parameters {
            export type Id = string; // text
        }
        export interface PathParameters {
            id: Parameters.Id; // text
        }
        /**
         * TokenName
         * example:
         * {
         *   "name": "my-token-0"
         * }
         */
        export interface RequestBody {
            /**
             * friendly name of the token
             */
            name: string; // text
        }
        namespace Responses {
            export type $200 = Components.Schemas.Token;
        }
    }
    namespace UploadAnchor {
        namespace Responses {
            export type $201 = Components.Schemas.Anchor;
        }
    }
    namespace UploadBackup {
        namespace Responses {
            export type $201 = Components.Schemas.Backup;
        }
    }
    namespace User {
        namespace Responses {
            export type $200 = Components.Schemas.User;
        }
    }
}
