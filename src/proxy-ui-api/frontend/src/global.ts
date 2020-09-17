/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import { Tab } from '@/ui-types';

// A "single source of truth" for route names
export enum RouteName {
  Keys = 'keys',
  Diagnostics = 'diagnostics',
  AddSubsystem = 'add-subsystem',
  AddClient = 'add-client',
  Clients = 'clients',
  Subsystem = 'subsystem',
  Client = 'client',
  Login = 'login',
  Certificate = 'certificate',
  ClientTlsCertificate = 'client-tls-certificate',
  MemberDetails = 'member-details',
  MemberServers = 'member-servers',
  SubsystemDetails = 'subs-details',
  SubsystemServers = 'subs-servers',
  SubsystemLocalGroups = 'subs-local-groups',
  LocalGroup = 'local-group',
  SubsystemServiceClients = 'subs-clients',
  SubsystemServices = 'subs-services',
  ServiceDescriptionDetails = 'service-description-details',
  Service = 'service',
  ServiceParameters = 'service-parameters',
  Endpoints = 'service-endpoints',
  SignAndAuthKeys = 'sign-and-auth-keys',
  ApiKey = 'api-key',
  CreateApiKey = 'create-api-key',
  SSTlsCertificate = 'ss-tls-certificate',
  Token = 'token',
  Key = 'key',
  SystemParameters = 'system-parameters',
  BackupAndRestore = 'backup-and-restore',
  AddKey = 'add-key',
  GenerateCertificateSignRequest = 'generate-csr',
  InternalTlsCertificate = 'internal-tls-certificate',
  GenerateInternalCSR = 'generate-internal-csr',
  EndpointDetails = 'endpoint-details',
  AddMember = 'add-member',
  EndpointAccessRights = 'endpoint-access-rights',
  ServiceClientAccessRights = 'service-client-access-rights',
  InitialConfiguration = 'initial-configuration',
  AddServiceClientAccessRight = 'add-service-client-access-right',
}

// A "single source of truth" for permission strings
export enum Permissions {
  ACTIVATE_DISABLE_AUTH_CERT = 'ACTIVATE_DISABLE_AUTH_CERT', // certificate details
  ACTIVATE_DISABLE_SIGN_CERT = 'ACTIVATE_DISABLE_SIGN_CERT', // certificate details
  ACTIVATE_DEACTIVATE_TOKEN = 'ACTIVATE_DEACTIVATE_TOKEN',
  ADD_CLIENT = 'ADD_CLIENT', // clients > add client
  ADD_CLIENT_INTERNAL_CERT = 'ADD_CLIENT_INTERNAL_CERT', // add TLS certificate in client "internal servers"
  ADD_LOCAL_GROUP = 'ADD_LOCAL_GROUP', // client > local groups
  ADD_TSP = 'ADD_TSP', // settings > system parameters
  ADD_WSDL = 'ADD_WSDL', // client > services > add WSDL / REST
  BACKUP_CONFIGURATION = 'BACKUP_CONFIGURATION', // settings > backup and restore
  CREATE_API_KEY = 'CREATE_API_KEY', // api key
  DELETE_AUTH_CERT = 'DELETE_AUTH_CERT', // certificate details
  DELETE_AUTH_KEY = 'DELETE_AUTH_KEY', // key details
  DELETE_CLIENT = 'DELETE_CLIENT', // client
  DELETE_CLIENT_INTERNAL_CERT = 'DELETE_CLIENT_INTERNAL_CERT', // detete certificate in client - cetificate view
  DELETE_KEY = 'DELETE_KEY', // key details
  DELETE_LOCAL_GROUP = 'DELETE_LOCAL_GROUP', // client > local groups
  DELETE_SIGN_CERT = 'DELETE_SIGN_CERT', // sign cert details
  DELETE_SIGN_KEY = 'DELETE_SIGN_KEY', // key details
  DELETE_TSP = 'DELETE_TSP', // settings > system parameters
  DELETE_WSDL = 'DELETE_WSDL', // can delete WSDL or REST
  DELETE_ENDPOINT = 'DELETE_ENDPOINT', // can delete endpoint
  DIAGNOSTICS = 'DIAGNOSTICS', // diagnostics tab
  DOWNLOAD_ANCHOR = 'DOWNLOAD_ANCHOR', // settings > system parameters
  EDIT_ACL_SUBJECT_OPEN_SERVICES = 'EDIT_ACL_SUBJECT_OPEN_SERVICES', // client > service clients
  EDIT_CLIENT_INTERNAL_CONNECTION_TYPE = 'EDIT_CLIENT_INTERNAL_CONNECTION_TYPE', // internal servers > connection type
  EDIT_ENDPOINT_ACL = 'EDIT_ENDPOINT_ACL', // edit endpoint acess rights
  EDIT_KEY_FRIENDLY_NAME = 'EDIT_KEY_FRIENDLY_NAME', // keys and certificates > sign and auth keys > key
  EDIT_LOCAL_GROUP_DESC = 'EDIT_LOCAL_GROUP_DESC', // client > local groups
  EDIT_LOCAL_GROUP_MEMBERS = 'EDIT_LOCAL_GROUP_MEMBERS', // client > local groups
  EDIT_SERVICE_ACL = 'EDIT_SERVICE_ACL', // client > service clients > access rights
  EDIT_SERVICE_PARAMS = 'EDIT_SERVICE_PARAMS', // client > services > rest/wsdl > service params
  EDIT_TOKEN_FRIENDLY_NAME = 'EDIT_TOKEN_FRIENDLY_NAME', // token details
  EDIT_WSDL = 'EDIT_WSDL', // client > services > edit service description
  ENABLE_DISABLE_WSDL = 'ENABLE_DISABLE_WSDL', // client > services > enable / disable WSDL switch
  EXPORT_INTERNAL_TLS_CERT = 'EXPORT_INTERNAL_TLS_CERT', // export SS TLS certificate in "internal servers" view & system parameters
  GENERATE_AUTH_CERT_REQ = 'GENERATE_AUTH_CERT_REQ',
  GENERATE_INTERNAL_TLS_KEY_CERT = 'GENERATE_INTERNAL_TLS_KEY_CERT', // Generate Security server TLS key and certificate
  GENERATE_INTERNAL_TLS_CSR = 'GENERATE_INTERNAL_TLS_CSR', // Security server TLS certificate
  GENERATE_KEY = 'GENERATE_KEY',
  GENERATE_SIGN_CERT_REQ = 'GENERATE_SIGN_CERT_REQ',
  IMPORT_AUTH_CERT = 'IMPORT_AUTH_CERT',
  IMPORT_INTERNAL_TLS_CERT = 'IMPORT_INTERNAL_TLS_CERT', // Import security server TLS certificate
  IMPORT_SIGN_CERT = 'IMPORT_SIGN_CERT',
  INIT_CONFIG = 'INIT_CONFIG', // can initialise security server
  VIEW_API_KEYS = 'VIEW_API_KEYS', // api key
  REFRESH_WSDL = 'REFRESH_WSDL', // client > services > refresh wsdl
  REFRESH_REST = 'REFRESH_REST', // not used?
  REFRESH_OPENAPI3 = 'REFRESH_OPENAPI3', // client > services > refresh openapi3
  RESTORE_CONFIGURATION = 'RESTORE_CONFIGURATION',
  REVOKE_API_KEY = 'REVOKE_API_KEY', // api key
  SEND_AUTH_CERT_DEL_REQ = 'SEND_AUTH_CERT_DEL_REQ', // auth cert details > unregister
  SEND_AUTH_CERT_REG_REQ = 'SEND_AUTH_CERT_REG_REQ', // sign and keys > register
  SEND_CLIENT_DEL_REQ = 'SEND_CLIENT_DEL_REQ', // client / subsystem > delete
  SEND_CLIENT_REG_REQ = 'SEND_CLIENT_REG_REQ', // clients > register
  SEND_OWNER_CHANGE_REQ = 'SEND_OWNER_CHANGE_REQ', // client > make owner
  UPDATE_API_KEY = 'UPDATE_API_KEY', // api key
  UPLOAD_ANCHOR = 'UPLOAD_ANCHOR', // settings / initialisation > upload anchor
  VIEW_ACL_SUBJECT_OPEN_SERVICES = 'VIEW_ACL_SUBJECT_OPEN_SERVICES', // not needed because roles can't access the view
  VIEW_ANCHOR = 'VIEW_ANCHOR', // settings > system paramters > configuration anchor
  VIEW_APPROVED_CERTIFICATE_AUTHORITIES = 'VIEW_APPROVED_CERTIFICATE_AUTHORITIES', // Settings / certificate authorities
  VIEW_CLIENTS = 'VIEW_CLIENTS', // clients tab (clients table)
  VIEW_CLIENT_ACL_SUBJECTS = 'VIEW_CLIENT_ACL_SUBJECTS', // subsystem "service clients" tab
  VIEW_CLIENT_DETAILS = 'VIEW_CLIENT_DETAILS', // * member / subsystem view
  VIEW_CLIENT_INTERNAL_CERTS = 'VIEW_CLIENT_INTERNAL_CERTS', // * member / subsystem  "internal servers" tab
  VIEW_CLIENT_INTERNAL_CERT_DETAILS = 'VIEW_CLIENT_INTERNAL_CERT_DETAILS', // member / subsystem - System TLS certificate details view
  VIEW_CLIENT_INTERNAL_CONNECTION_TYPE = 'VIEW_CLIENT_INTERNAL_CONNECTION_TYPE', // internal servers > connection type
  VIEW_CLIENT_LOCAL_GROUPS = 'VIEW_CLIENT_LOCAL_GROUPS', // subsystem "local groups" tab
  VIEW_CLIENT_SERVICES = 'VIEW_CLIENT_SERVICES', // subsystem "services" tab
  VIEW_ENDPOINT_ACL = 'VIEW_ENDPOINT_ACL', // client > services > rest > endpoint > acces rights
  VIEW_INTERNAL_TLS_CERT = 'VIEW_INTERNAL_TLS_CERT', // view server TLS certificate in client "internal servers" or in system parameters
  VIEW_KEYS = 'VIEW_KEYS', // keys and certificates tab
  VIEW_SERVICE_ACL = 'VIEW_SERVICE_ACL', // not needed because roles can't access the view
  VIEW_SYS_PARAMS = 'VIEW_SYS_PARAMS', // settings > system paramters tab
  VIEW_TSPS = 'VIEW_TSPS', // settings > system paramters > timestamping services
}

export enum UsageTypes {
  SIGNING = 'SIGNING',
  AUTHENTICATION = 'AUTHENTICATION',
}

export enum CsrFormatTypes {
  PEM = 'PEM',
  DER = 'DER',
}

export enum PossibleActions {
  DELETE = 'DELETE',
  ACTIVATE = 'ACTIVATE',
  DISABLE = 'DISABLE',
  LOGIN = 'LOGIN',
  LOGOUT = 'LOGOUT',
  REGISTER = 'REGISTER',
  UNREGISTER = 'UNREGISTER',
  IMPORT_FROM_TOKEN = 'IMPORT_FROM_TOKEN',
  GENERATE_KEY = 'GENERATE_KEY',
  EDIT_FRIENDLY_NAME = 'EDIT_FRIENDLY_NAME',
  GENERATE_AUTH_CSR = 'GENERATE_AUTH_CSR',
  GENERATE_SIGN_CSR = 'GENERATE_SIGN_CSR',
}

export enum CertificateStatus {
  SAVED = 'SAVED',
  REGISTRATION_IN_PROGRESS = 'REGISTRATION_IN_PROGRESS',
  REGISTERED = 'REGISTERED',
  DELETION_IN_PROGRESS = 'DELETION_IN_PROGRESS',
  GLOBAL_ERROR = 'GLOBAL_ERROR',
}

export enum TokenInitStatusEnum {
  INITIALIZED = 'INITIALIZED',
  NOT_INITIALIZED = 'NOT_INITIALIZED',
  UNKNOWN = 'UNKNOWN',
}

export const mainTabs: Tab[] = [
  {
    to: { name: RouteName.Clients },
    key: 'clients',
    name: 'tab.main.clients',
    permissions: [Permissions.VIEW_CLIENTS],
  },
  {
    to: { name: RouteName.SignAndAuthKeys },
    key: 'keys',
    name: 'tab.main.keys',
    permissions: [Permissions.VIEW_KEYS],
  },
  {
    to: { name: RouteName.Diagnostics },
    key: 'diagnostics',
    name: 'tab.main.diagnostics',
    permissions: [Permissions.DIAGNOSTICS],
  },
  {
    to: { name: RouteName.SystemParameters },
    key: 'settings',
    name: 'tab.main.settings',
    permissions: [
      Permissions.VIEW_SYS_PARAMS,
      Permissions.BACKUP_CONFIGURATION,
    ],
  },
];

// A single source of truth for roles
export const Roles = [
  'XROAD_SECURITY_OFFICER',
  'XROAD_REGISTRATION_OFFICER',
  'XROAD_SERVICE_ADMINISTRATOR',
  'XROAD_SYSTEM_ADMINISTRATOR',
  'XROAD_SECURITYSERVER_OBSERVER',
];

// Client types used in client list
export enum ClientTypes {
  OWNER_MEMBER = 'OWNER_MEMBER',
  MEMBER = 'MEMBER',
  VIRTUAL_MEMBER = 'VIRTUAL_MEMBER',
  SUBSYSTEM = 'SUBSYSTEM',
}

// Different modes for the add member wizard
export enum AddMemberWizardModes {
  CSR_EXISTS = 'CSR_EXISTS',
  CERTIFICATE_EXISTS = 'CERTIFICATE_EXISTS',
  FULL = 'FULL',
}
