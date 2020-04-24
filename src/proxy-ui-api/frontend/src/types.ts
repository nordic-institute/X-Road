/**
 * access right given for a specific subject (ServiceClient) for specific service (service_code) owned by some client. This object does not represent endpoint-level access rights
 */
export interface AccessRight {
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
  readonly service_title?: string; // text
  /**
   * access right given at
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly rights_given_at?: string; // date-time
}
/**
 * object containing and array of AccessRights
 */
export interface AccessRights {
  /**
   * array of AccessRights
   */
  items?: AccessRight[];
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
 * approved certificate authority information. Only for top CAs.
 */
export interface CertificateAuthority {
  /**
   * name of the CA, as defined in global conf. Used also as an identifier
   * example:
   * X-Road Test CA CN
   */
  name: string; // text
  /**
   * subject distinguished name
   * example:
   * C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=X-Road Test CA CN
   */
  subject_distinguished_name: string; // text
  /**
   * issuer distinguished name
   * example:
   * C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=X-Road Test CA CN
   */
  issuer_distinguished_name: string; // text
  ocsp_response: CertificateAuthorityOcspResponse; // enum
  /**
   * certificate authority expires at
   * example:
   * 2099-12-15T00:00:00.001Z
   */
  not_after: string; // date-time
  /**
   * if the certificate authority is top CA (instead of intermediate)
   * example:
   * true
   */
  top_ca: boolean;
  /**
   * encoded path string from this CA to top CA
   * example:
   * C=FI, O=X-Road Test Intermediate, OU=X-Road Test CA OU, CN=X-Road Test CA CN Intermediate:C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=X-Road Test CA CN
   */
  path: string; // text
  /**
   * if certificate authority is limited for authentication use only
   */
  authentication_only: boolean;
}
/**
 * certificate authority OCSP status
 * example:
 * IN_USE
 */
export type CertificateAuthorityOcspResponse =
  | 'NOT_AVAILABLE'
  | 'OCSP_RESPONSE_UNKNOWN'
  | 'OCSP_RESPONSE_GOOD'
  | 'OCSP_RESPONSE_SUSPENDED'
  | 'OCSP_RESPONSE_REVOKED'; // enum
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
  key_usages: KeyUsage /* enum */[];
}
/**
 * certificate status
 * example:
 * IN_USE
 */
export type CertificateOcspStatus =
  | 'DISABLED'
  | 'EXPIRED'
  | 'OCSP_RESPONSE_UNKNOWN'
  | 'OCSP_RESPONSE_GOOD'
  | 'OCSP_RESPONSE_SUSPENDED'
  | 'OCSP_RESPONSE_REVOKED'; // enum
/**
 * certificate status
 * example:
 * IN_USE
 */
export type CertificateStatus =
  | 'SAVED'
  | 'REGISTRATION_IN_PROGRESS'
  | 'REGISTERED'
  | 'DELETION_IN_PROGRESS'
  | 'GLOBAL_ERROR'; // enum
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
   * xroad instance id
   * example:
   * FI
   */
  readonly instance_id?: string; // text
  /**
   * member name
   * example:
   * FI
   */
  readonly member_name?: string; // text
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
  subsystem_code?: string; // text
  /**
   * if this client is the owner member of this security server
   * example:
   * false
   */
  readonly owner?: boolean;
  connection_type?: ConnectionType; // enum
  readonly status?: ClientStatus; // enum
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
export type ClientStatus =
  | 'REGISTERED'
  | 'SAVED'
  | 'GLOBAL_ERROR'
  | 'REGISTRATION_IN_PROGRESS'
  | 'DELETION_IN_PROGRESS'; // enum
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
  metadata?: string /* text */[];
}
/**
 * configuration status
 * example:
 * SUCCESS
 */
export type ConfigurationStatus =
  | 'SUCCESS'
  | 'ERROR_CODE_INTERNAL'
  | 'ERROR_CODE_INVALID_SIGNATURE_VALUE'
  | 'ERROR_CODE_EXPIRED_CONF'
  | 'ERROR_CODE_CANNOT_DOWNLOAD_CONF'
  | 'ERROR_CODE_MISSING_PRIVATE_PARAMS'
  | 'ERROR_CODE_UNINITIALIZED'
  | 'UNKNOWN'; // enum
/**
 * connection type
 * example:
 * HTTP
 */
export type ConnectionType = 'HTTP' | 'HTTPS' | 'HTTPS_NO_AUTH'; // enum
/**
 * csr format
 * example:
 * PEM
 */
export type CsrFormat = 'PEM' | 'DER'; // enum
/**
 * request to generate a CSR
 */
export interface CsrGenerate {
  /**
   * which usage type this CSR is for (signing or authentication)
   */
  key_usage_type: KeyUsageType; // enum
  /**
   * common name of the CA
   * example:
   * X-Road Test CA CN
   */
  ca_name: string; // text
  /**
   * format of the certificate signing request (PEM or DER)
   */
  csr_format: CsrFormat; // enum
  /**
   * member client id for signing CSRs. <instance_id>:<member_class>:<member_code>
   * example:
   * FI:GOV:123
   */
  member_id?: string; // text
  /**
   * user-provided values for subject DN parameters
   */
  subject_field_values: {
    [name: string]: string;
  };
}
/**
 * object describing input fields for CSR subject DN info
 */
export interface CsrSubjectFieldDescription {
  /**
   * the identifier of the field (such as 'O', 'OU' etc)
   * example:
   * O
   */
  readonly id: string; // text
  /**
   * label of the field, used to display the field in the user interface
   * example:
   * ORGANIZATION_NAME
   */
  readonly label?: string; // text
  /**
   * localization key for label of the field, used to display the field in the user interface
   * example:
   * Organization name (O)
   */
  readonly label_key?: string; // text
  /**
   * the default value of the field. Can be empty.
   * example:
   * 1234
   */
  readonly default_value?: string; // text
  /**
   * if this field is read-only
   * example:
   * true
   */
  readonly read_only: boolean;
  /**
   * if this field is required to be filled
   * example:
   * true
   */
  readonly required: boolean;
  /**
   * if true, label key is in property "label_key". If false, actual label is in property "label"
   * example:
   * true
   */
  readonly localized: boolean;
}
/**
 * diagnostics status class
 * example:
 * OK
 */
export type DiagnosticStatusClass = 'OK' | 'WAITING' | 'FAIL'; // enum
/**
 * Endpoint for a service
 */
export interface Endpoint {
  /**
   * unique identifier
   * example:
   * 15
   */
  id?: string;
  /**
   * example:
   * example_service_code
   */
  service_code: string;
  /**
   * http method mapped to this endpoint
   * example:
   * GET
   */
  method:
    | '*'
    | 'GET'
    | 'POST'
    | 'PUT'
    | 'DELETE'
    | 'PATCH'
    | 'HEAD'
    | 'OPTIONS'
    | 'TRACE';
  /**
   * relative path where this endpoint is mapped to
   * example:
   * /foo
   */
  path: string;
  /**
   * has endpoint been generated from openapi3 description
   * example:
   * true
   */
  readonly generated?: boolean;
}
/**
 * Object for updating endpoints method and/or path
 */
export interface EndpointUpdate {
  /**
   * http method mapped to this endpoint
   * example:
   * GET
   */
  method?:
    | '*'
    | 'GET'
    | 'POST'
    | 'PUT'
    | 'DELETE'
    | 'PATCH'
    | 'HEAD'
    | 'OPTIONS'
    | 'TRACE';
  /**
   * relative path where this endpoint is mapped to
   * example:
   * /foo
   */
  path?: string;
}
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
 * global configuration diagnostics
 */
export interface GlobalConfDiagnostics {
  /**
   * diagnostics status class
   * example:
   * OK
   */
  readonly status_class: 'OK' | 'WAITING' | 'FAIL'; // enum
  /**
   * configuration status
   * example:
   * SUCCESS
   */
  readonly status_code:
    | 'SUCCESS'
    | 'ERROR_CODE_INTERNAL'
    | 'ERROR_CODE_INVALID_SIGNATURE_VALUE'
    | 'ERROR_CODE_EXPIRED_CONF'
    | 'ERROR_CODE_CANNOT_DOWNLOAD_CONF'
    | 'ERROR_CODE_MISSING_PRIVATE_PARAMS'
    | 'ERROR_CODE_UNINITIALIZED'
    | 'UNKNOWN'; // enum
  /**
   * last time updated
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly prev_update_at: string; // date-time
  /**
   * last time updated
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly next_update_at: string; // date-time
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
  /**
   * possible actions that can be done to this object, e.g DELETE. Has value only for operationIds getToken and getTokens. Does not consider user authorization.
   */
  possible_actions?: PossibleActions;
}
/**
 * certificate key usage
 * example:
 * NON_REPUDIATION
 */
export type KeyUsage =
  | 'DIGITAL_SIGNATURE'
  | 'NON_REPUDIATION'
  | 'KEY_ENCIPHERMENT'
  | 'DATA_ENCIPHERMENT'
  | 'KEY_AGREEMENT'
  | 'KEY_CERT_SIGN'
  | 'CRL_SIGN'
  | 'ENCIPHER_ONLY'
  | 'DECIPHER_ONLY'; // enum
/**
 * intended usage for the key
 * example:
 * AUTHENTICATION
 */
export type KeyUsageType = 'AUTHENTICATION' | 'SIGNING'; // enum
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
 * Key and TokenCertificateSigningRequest id
 */
export interface KeyWithCertificateSigningRequestId {
  key: Key;
  /**
   * CSR id
   * example:
   * 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
   */
  csr_id: string; // text
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
 * model for adding a new LocalGroup
 */
export interface LocalGroupAdd {
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
}
/**
 * Request object containing an array of member ids. The id must be an X-Road member id or subsystem id
 */
export interface Members {
  /**
   * array of members to be added
   * example:
   * [
   *   "FI:GOV:123",
   *   "FI:GOV:123:SS1",
   *   "FI:GOV:123:SS2"
   * ]
   */
  items?: string /* text */[];
}
/**
 * OCSP responder diagnostics
 */
export interface OcspResponder {
  /**
   * url of the OCSP responder
   * example:
   * http://dev.xroad.rocks:123
   */
  readonly url: string; // url
  /**
   * diagnostics status class
   * example:
   * OK
   */
  readonly status_class: 'OK' | 'WAITING' | 'FAIL'; // enum
  /**
   * OCSP responder status
   * example:
   * SUCCESS
   */
  readonly status_code:
    | 'SUCCESS'
    | 'ERROR_CODE_OCSP_CONNECTION_ERROR'
    | 'ERROR_CODE_OCSP_FAILED'
    | 'ERROR_CODE_OCSP_RESPONSE_INVALID'
    | 'ERROR_CODE_OCSP_UNINITIALIZED'
    | 'UNKNOWN'; // enum
  /**
   * last time updated
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly prev_update_at?: string; // date-time
  /**
   * next time updated
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly next_update_at: string; // date-time
}
/**
 * Ocsp responder diagnostics
 */
export interface OcspResponderDiagnostics {
  /**
   * CA distinguished name
   * example:
   * C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=X-Road Test CA CN
   */
  readonly distinguished_name: string; // text
  readonly ocsp_responders: OcspResponder[];
}
/**
 * OCSP responder status
 * example:
 * SUCCESS
 */
export type OcspStatus =
  | 'SUCCESS'
  | 'ERROR_CODE_OCSP_CONNECTION_ERROR'
  | 'ERROR_CODE_OCSP_FAILED'
  | 'ERROR_CODE_OCSP_RESPONSE_INVALID'
  | 'ERROR_CODE_OCSP_UNINITIALIZED'
  | 'UNKNOWN'; // enum
/**
 * an action to change state or edit token, key, cert or csr
 * example:
 * DELETE
 */
export type PossibleAction =
  | 'DELETE'
  | 'ACTIVATE'
  | 'DISABLE'
  | 'LOGIN'
  | 'LOGOUT'
  | 'REGISTER'
  | 'UNREGISTER'
  | 'IMPORT_FROM_TOKEN'
  | 'GENERATE_KEY'
  | 'EDIT_FRIENDLY_NAME'
  | 'GENERATE_AUTH_CSR'
  | 'GENERATE_SIGN_CSR'; // enum
/**
 * array containing the possible actions that can be done for this item
 */
export type PossibleActions = PossibleAction /* enum */[];
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
  /**
   * security server address (ip or name)
   * example:
   * 192.168.1.100
   */
  server_address?: string; // text
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
  /**
   * list of endpoints linked to this service
   */
  endpoints?: Endpoint[];
}
/**
 * service client. May be a subsystem, local group, or a global group
 */
export interface ServiceClient {
  /**
   * subject id - can be a subsystem id <instance_id>:<member_class>:<member_code>:<subsystem> | globalgroup id <instance_id>:<group_code> | localgroup resource id in number format <id>
   * example:
   * DEV:ORG:1234:Subsystem | DEV:security-server-owners | 123
   */
  id: string; // text
  /**
   * name of the ServiceClient - can be the name of a member or the description of a group
   * example:
   * Security server owners
   */
  readonly name?: string; // text
  /**
   * group code in case the object is a local group
   * example:
   * My own Local group code
   */
  readonly local_group_code?: string; // text
  service_client_type?: ServiceClientType; // text
  /**
   * access right given at
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly rights_given_at?: string; // date-time
}
/**
 * subject type
 * example:
 * GLOBALGROUP
 */
export type ServiceClientType = 'GLOBALGROUP' | 'LOCALGROUP' | 'SUBSYSTEM'; // text
/**
 * object containing and array of ServiceClients
 */
export interface ServiceClients {
  /**
   * array of ServiceClients
   */
  items?: ServiceClient[];
}
/**
 * WSDL/OPENAPI3/REST service
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
  url: string; // text
  /**
   * service code for REST service
   * example:
   * exampleServiceCode
   */
  rest_service_code?: string; // text
  /**
   * new service code for REST service
   * example:
   * newExampleServiceCode
   */
  new_rest_service_code?: string; // test
  /**
   * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
   */
  ignore_warnings?: boolean;
  type: ServiceType; // text
}
/**
 * service type
 * example:
 * WSDL
 */
export type ServiceType = 'WSDL' | 'REST' | 'OPENAPI3'; // text
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
}
/**
 * timestamping service diagnostics
 */
export interface TimestampingServiceDiagnostics {
  /**
   * url of the time stamping service
   * example:
   * http://dev.xroad.rocks:123
   */
  readonly url: string; // url
  /**
   * diagnostics status class
   * example:
   * OK
   */
  readonly status_class: 'OK' | 'WAITING' | 'FAIL'; // enum
  /**
   * timestamping status
   * example:
   * SUCCESS
   */
  readonly status_code:
    | 'SUCCESS'
    | 'ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT'
    | 'ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL'
    | 'ERROR_CODE_TIMESTAMP_UNINITIALIZED'
    | 'ERROR_CODE_INTERNAL'
    | 'UNKNOWN'; // enum
  /**
   * last time updated
   * example:
   * 2018-12-15T00:00:00.001Z
   */
  readonly prev_update_at: string; // date-time
}
/**
 * timestamping status
 * example:
 * SUCCESS
 */
export type TimestampingStatus =
  | 'SUCCESS'
  | 'ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT'
  | 'ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL'
  | 'ERROR_CODE_TIMESTAMP_UNINITIALIZED'
  | 'ERROR_CODE_INTERNAL'
  | 'UNKNOWN'; // enum
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
  /**
   * possible actions that can be done to this object, e.g DELETE. Has value only for operationIds getToken and getTokens. Does not consider user authorization.
   */
  possible_actions?: PossibleActions;
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
  /**
   * possible actions that can be done to this object, e.g DELETE. Has value only for operationIds getToken and getTokens. Does not consider user authorization.
   */
  possible_actions?: PossibleActions;
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
  /**
   * possible actions that can be done to this object, e.g DELETE. Has value only for operationIds getToken and getTokens. Does not consider user authorization.
   */
  possible_actions: PossibleActions;
}
/**
 * token type
 * example:
 * OK
 */
export type TokenStatus =
  | 'OK'
  | 'USER_PIN_LOCKED'
  | 'USER_PIN_INCORRECT'
  | 'USER_PIN_INVALID'
  | 'USER_PIN_EXPIRED'
  | 'USER_PIN_COUNT_LOW'
  | 'USER_PIN_FINAL_TRY'
  | 'NOT_INITIALIZED'; // text
/**
 * token type
 * example:
 * SOFTWARE
 */
export type TokenType = 'SOFTWARE' | 'HARDWARE'; // text
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
  roles: string /* text */[];
  /**
   * user permissions
   */
  permissions: string /* text */[];
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
