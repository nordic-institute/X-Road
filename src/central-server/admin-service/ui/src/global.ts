/*
 * The MIT License
 *
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
  BaseRoute = 'base',
  Members = 'members',
  MemberDetails = 'member-details',
  MemberSubsystems = 'member-subsystems',
  SecurityServers = 'security-servers',
  ManagementRequests = 'management-requests',
  ManagementRequestDetails = 'management-request-details',
  TrustServices = 'trust-services',
  CertificationServiceDetails = 'certification-service-details',
  CertificationServiceSettings = 'certification-service-settings',
  CertificationServiceOcspResponders = 'certification-service-ocsp-responders',
  CertificationServiceCertificateDetails = 'certification-service-certificate-details',
  TimestampingServiceCertificateDetails = 'timestamping-service-certificate-details',
  CertificationServiceIntermediateCas = 'certification-service-intermediate-cas',
  IntermediateCACertificateDetails = 'intermediate-ca-certificate-details',
  OcspResponderCertificateDetails = 'ocsp-responder-certificate-details',
  ManagementServiceCertificateDetails = 'management-service-certificate-details',
  IntermediateCaDetails = 'intermediate-ca-details',
  IntermediateCaOcspResponders = 'intermediate-ca-ocsp-responders',
  Settings = 'settings',
  GlobalResources = 'global-resources',
  GlobalGroup = 'global-group',
  SystemSettings = 'system-settings',
  BackupAndRestore = 'backup-and-restore',
  ApiKeys = 'api-keys',
  TlsCertificates = 'tls-certificates',
  CreateApiKey = 'create-api-key',
  InternalConfiguration = 'internal-configuration',
  ExternalConfiguration = 'external-configuration',
  TrustedAnchors = 'trusted-anchors',
  Login = 'login',
  Initialisation = 'init',
  SecurityServerDetails = 'security-server-details',
  SecurityServerAuthenticationCertificates = 'security-server-authentication-certificates',
  SecurityServerAuthenticationCertificate = 'security-server-authentication-certificate',
  SecurityServerClients = 'security-server-clients',
  Forbidden = 'forbidden',
}

// A "single source of truth" for permission strings
export enum Permissions {
  INIT_CONFIG = 'INIT_CONFIG',
  SEARCH_MEMBERS = 'SEARCH_MEMBERS',
  VIEW_MEMBERS = 'VIEW_MEMBERS',
  ADD_NEW_MEMBER = 'ADD_NEW_MEMBER',
  VIEW_MEMBER_DETAILS = 'VIEW_MEMBER_DETAILS',
  EDIT_MEMBER_NAME = 'EDIT_MEMBER_NAME',
  ADD_MEMBER_SUBSYSTEM = 'ADD_MEMBER_SUBSYSTEM',
  REMOVE_MEMBER_SUBSYSTEM = 'REMOVE_MEMBER_SUBSYSTEM',
  UNREGISTER_SUBSYSTEM = 'UNREGISTER_SUBSYSTEM',
  DELETE_MEMBER = 'DELETE_MEMBER',
  VIEW_SECURITY_SERVERS = 'VIEW_SECURITY_SERVERS',
  VIEW_SECURITY_SERVER_DETAILS = 'VIEW_SECURITY_SERVER_DETAILS',
  EDIT_SECURITY_SERVER_ADDRESS = 'EDIT_SECURITY_SERVER_ADDRESS',
  DELETE_SECURITY_SERVER_AUTH_CERT = 'DELETE_SECURITY_SERVER_AUTH_CERT',
  DELETE_SECURITY_SERVER = 'DELETE_SECURITY_SERVER',
  VIEW_GLOBAL_GROUPS = 'VIEW_GLOBAL_GROUPS',
  ADD_GLOBAL_GROUP = 'ADD_GLOBAL_GROUP',
  VIEW_GROUP_DETAILS = 'VIEW_GROUP_DETAILS',
  EDIT_GROUP_DESCRIPTION = 'EDIT_GROUP_DESCRIPTION',
  ADD_AND_REMOVE_GROUP_MEMBERS = 'ADD_AND_REMOVE_GROUP_MEMBERS',
  DELETE_GROUP = 'DELETE_GROUP',
  VIEW_APPROVED_CAS = 'VIEW_APPROVED_CAS',
  VIEW_APPROVED_CA_DETAILS = 'VIEW_APPROVED_CA_DETAILS',
  ADD_APPROVED_CA = 'ADD_APPROVED_CA',
  EDIT_APPROVED_CA = 'EDIT_APPROVED_CA',
  DELETE_APPROVED_CA = 'DELETE_APPROVED_CA',
  VIEW_APPROVED_TSAS = 'VIEW_APPROVED_TSAS',
  VIEW_APPROVED_TSA_DETAILS = 'VIEW_APPROVED_TSA_DETAILS',
  ADD_APPROVED_TSA = 'ADD_APPROVED_TSA',
  EDIT_APPROVED_TSA = 'EDIT_APPROVED_TSA',
  DELETE_APPROVED_TSA = 'DELETE_APPROVED_TSA',
  VIEW_MANAGEMENT_REQUESTS = 'VIEW_MANAGEMENT_REQUESTS',
  VIEW_MANAGEMENT_REQUEST_DETAILS = 'VIEW_MANAGEMENT_REQUEST_DETAILS',
  VIEW_MEMBER_CLASSES = 'VIEW_MEMBER_CLASSES',
  ADD_MEMBER_CLASS = 'ADD_MEMBER_CLASS',
  EDIT_MEMBER_CLASS = 'EDIT_MEMBER_CLASS',
  DELETE_MEMBER_CLASS = 'DELETE_MEMBER_CLASS',
  VIEW_CONFIGURATION = 'VIEW_CONFIGURATION',
  VIEW_CONFIGURATION_MANAGEMENT = 'VIEW_CONFIGURATION_MANAGEMENT',
  VIEW_TRUSTED_ANCHORS = 'VIEW_TRUSTED_ANCHORS',
  UPLOAD_TRUSTED_ANCHOR = 'UPLOAD_TRUSTED_ANCHOR',
  DELETE_TRUSTED_ANCHOR = 'DELETE_TRUSTED_ANCHOR',
  DOWNLOAD_TRUSTED_ANCHOR = 'DOWNLOAD_TRUSTED_ANCHOR',
  VIEW_INTERNAL_CONFIGURATION_SOURCE = 'VIEW_INTERNAL_CONFIGURATION_SOURCE',
  VIEW_EXTERNAL_CONFIGURATION_SOURCE = 'VIEW_EXTERNAL_CONFIGURATION_SOURCE',
  GENERATE_SOURCE_ANCHOR = 'GENERATE_SOURCE_ANCHOR',
  DOWNLOAD_SOURCE_ANCHOR = 'DOWNLOAD_SOURCE_ANCHOR',
  UPLOAD_CONFIGURATION_PART = 'UPLOAD_CONFIGURATION_PART',
  DOWNLOAD_CONFIGURATION_PART = 'DOWNLOAD_CONFIGURATION_PART',
  ACTIVATE_TOKEN = 'ACTIVATE_TOKEN',
  DEACTIVATE_TOKEN = 'DEACTIVATE_TOKEN',
  GENERATE_SIGNING_KEY = 'GENERATE_SIGNING_KEY',
  ACTIVATE_SIGNING_KEY = 'ACTIVATE_SIGNING_KEY',
  DELETE_SIGNING_KEY = 'DELETE_SIGNING_KEY',
  VIEW_SYSTEM_SETTINGS = 'VIEW_SYSTEM_SETTINGS',
  REGISTER_SERVICE_PROVIDER = 'REGISTER_SERVICE_PROVIDER',
  BACKUP_CONFIGURATION = 'BACKUP_CONFIGURATION',
  RESTORE_CONFIGURATION = 'RESTORE_CONFIGURATION',
  VIEW_TLS_CERTIFICATES = 'VIEW_TLS_CERTIFICATES',
  VIEW_MANAGEMENT_SERVICE_TLS_CERT = 'VIEW_MANAGEMENT_SERVICE_TLS_CERT',
  DOWNLOAD_MANAGEMENT_SERVICE_TLS_CERT = 'DOWNLOAD_MANAGEMENT_SERVICE_TLS_CERT',
  GENERATE_MANAGEMENT_SERVICE_TLS_KEY_CERT = 'GENERATE_MANAGEMENT_SERVICE_TLS_KEY_CERT',
  GENERATE_MANAGEMENT_SERVICE_TLS_CSR = 'GENERATE_MANAGEMENT_SERVICE_TLS_CSR',
  UPLOAD_MANAGEMENT_SERVICE_TLS_CERT = 'UPLOAD_MANAGEMENT_SERVICE_TLS_CERT',
  VIEW_VERSION = 'VIEW_VERSION',
  CREATE_API_KEY = 'CREATE_API_KEY', // api key
  UPDATE_API_KEY = 'UPDATE_API_KEY', // api key
  REVOKE_API_KEY = 'REVOKE_API_KEY', // api key
  VIEW_API_KEYS = 'VIEW_API_KEYS', // api key
}

// A single source of truth for roles
export const Roles = [
  'XROAD_REGISTRATION_OFFICER',
  'XROAD_SECURITY_OFFICER',
  'XROAD_SYSTEM_ADMINISTRATOR',
  'XROAD_MANAGEMENT_SERVICE',
];

export const mainTabs: Tab[] = [
  {
    to: { name: RouteName.Members },
    key: 'members',
    name: 'tab.main.members',
    permissions: [Permissions.VIEW_MEMBERS, Permissions.VIEW_MEMBER_DETAILS],
  },
  {
    to: { name: RouteName.SecurityServers },
    key: 'keys',
    name: 'tab.main.securityServers',
    permissions: [
      Permissions.VIEW_SECURITY_SERVERS,
      Permissions.VIEW_SECURITY_SERVER_DETAILS,
    ],
  },
  {
    to: { name: RouteName.ManagementRequests },
    key: 'managementRequests',
    name: 'tab.main.managementRequests',
    permissions: [
      Permissions.VIEW_MANAGEMENT_REQUESTS,
      Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS,
    ],
  },
  {
    to: { name: RouteName.TrustServices },
    key: 'trustServices',
    name: 'tab.main.trustServices',
    permissions: [
      Permissions.VIEW_APPROVED_CAS,
      Permissions.VIEW_APPROVED_TSAS,
      Permissions.VIEW_APPROVED_CA_DETAILS,
      Permissions.VIEW_APPROVED_TSA_DETAILS,
    ],
  },
  {
    // Global configuration tab
    to: { name: RouteName.InternalConfiguration }, // name of the first child tab
    key: 'globalConfiguration',
    name: 'tab.main.globalConfiguration',
    permissions: [
      Permissions.VIEW_CONFIGURATION_MANAGEMENT,
      Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE,
      Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE,
      Permissions.VIEW_TRUSTED_ANCHORS,
    ],
  },
  {
    // Settings tab
    to: { name: RouteName.Settings },
    key: 'settings',
    name: 'tab.main.settings',
    permissions: [
      Permissions.VIEW_SYSTEM_SETTINGS,
      Permissions.VIEW_GLOBAL_GROUPS,
      Permissions.VIEW_SECURITY_SERVERS,
      Permissions.BACKUP_CONFIGURATION,
      Permissions.VIEW_API_KEYS,
    ],
  },
];

// Version 7.0 colors as enum.
export enum Colors {
  Purple10 = '#efebfb',
  Purple20 = '#e0d8f8',
  Purple30 = '#d1c4f4',
  Purple70 = '#9376e6',
  Purple100 = '#663cdc',
  Black10 = '#e8e8e8',
  Black30 = '#bcbbbb',
  Black50 = '#908e8e',
  Black70 = '#636161',
  Black100 = '#211e1e',
  White100 = '#ffffff',
  Yellow = '#f2994A',
  WarmGrey10 = '#f4f3f6',
  WarmGrey20 = '#eae8ee',
  WarmGrey30 = '#dedce4',
  WarmGrey50 = '#c9c6d3',
  WarmGrey70 = '#b4afc2',
  WarmGrey100 = '#575169',
  Error = '#ec4040',
  Success100 = '#0cc177',
  Success10 = '#e6f8f1',
  Background = '#e5e5e5',
}

export const Timeouts = {
  POLL_SESSION_TIMEOUT: 30000,
} as const;
