
// A "single source of thuth" for route names
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
  SignAndAuthKeys = 'sign-and-auth-keys',
  ApiKey = 'api-key',
  SSTlsCertificate = 'ss-tls-certificate',
  Token = 'token',
  Key = 'key',
  SystemParameters = 'system-parameters',
  BackupAndRestore = 'backup-and-restore',
}

// A "single source of thuth" for permission strings
export enum Permissions {
  ACTIVATE_DISABLE_AUTH_CERT = 'ACTIVATE_DISABLE_AUTH_CERT',
  ACTIVATE_DISABLE_SIGN_CERT = 'ACTIVATE_DISABLE_SIGN_CERT',
  ACTIVATE_TOKEN = 'ACTIVATE_TOKEN',
  ADD_CLIENT = 'ADD_CLIENT', // clients > add client
  ADD_CLIENT_INTERNAL_CERT = 'ADD_CLIENT_INTERNAL_CERT',  // add TLS certificate in client "internal servers"
  ADD_LOCAL_GROUP = 'ADD_LOCAL_GROUP', // client > local groups
  ADD_TSP = 'ADD_TSP',
  ADD_WSDL = 'ADD_WSDL', // client > services > add WSDL / REST
  BACKUP_CONFIGURATION = 'BACKUP_CONFIGURATION',
  DEACTIVATE_TOKEN = 'DEACTIVATE_TOKEN',
  DELETE_AUTH_CERT = 'DELETE_AUTH_CERT',
  DELETE_AUTH_KEY = 'DELETE_AUTH_KEY',
  DELETE_CLIENT = 'DELETE_CLIENT',
  DELETE_CLIENT_INTERNAL_CERT = 'DELETE_CLIENT_INTERNAL_CERT',  // detete certificate in client - cetificate view
  DELETE_KEY = 'DELETE_KEY',
  DELETE_LOCAL_GROUP = 'DELETE_LOCAL_GROUP', // client > local groups
  DELETE_SIGN_CERT = 'DELETE_SIGN_CERT',
  DELETE_SIGN_KEY = 'DELETE_SIGN_KEY',
  DELETE_TSP = 'DELETE_TSP',
  DELETE_WSDL = 'DELETE_WSDL',  // can delete WSDL or REST
  DIAGNOSTICS = 'DIAGNOSTICS', // diagnostics tab
  DOWNLOAD_ANCHOR = 'DOWNLOAD_ANCHOR',
  EDIT_ACL_SUBJECT_OPEN_SERVICES = 'EDIT_ACL_SUBJECT_OPEN_SERVICES',
  EDIT_CLIENT_INTERNAL_CONNECTION_TYPE = 'EDIT_CLIENT_INTERNAL_CONNECTION_TYPE', // internal servers > connection type
  EDIT_KEYTABLE_FRIENDLY_NAMES = 'EDIT_KEYTABLE_FRIENDLY_NAMES',
  EDIT_LOCAL_GROUP_DESC = 'EDIT_LOCAL_GROUP_DESC', // client > local groups
  EDIT_LOCAL_GROUP_MEMBERS = 'EDIT_LOCAL_GROUP_MEMBERS', // client > local groups
  EDIT_SERVICE_ACL = 'EDIT_SERVICE_ACL',
  EDIT_SERVICE_PARAMS = 'EDIT_SERVICE_PARAMS',
  EDIT_WSDL = 'EDIT_WSDL', // client > services > edit service description
  ENABLE_DISABLE_WSDL = 'ENABLE_DISABLE_WSDL',  // client > services > enable / disable WSDL switch
  EXPORT_CLIENT_SERVICES_ACL = 'EXPORT_CLIENT_SERVICES_ACL',
  EXPORT_INTERNAL_SSL_CERT = 'EXPORT_INTERNAL_SSL_CERT', // export certificate in client "internal servers" view
  EXPORT_PROXY_INTERNAL_CERT = 'EXPORT_PROXY_INTERNAL_CERT',
  GENERATE_AUTH_CERT_REQ = 'GENERATE_AUTH_CERT_REQ',
  GENERATE_INTERNAL_CERT_REQ = 'GENERATE_INTERNAL_CERT_REQ',
  GENERATE_INTERNAL_SSL = 'GENERATE_INTERNAL_SSL',
  GENERATE_INTERNAL_SSL_CSR = 'GENERATE_INTERNAL_SSL_CSR',
  GENERATE_KEY = 'GENERATE_KEY',
  GENERATE_SIGN_CERT_REQ = 'GENERATE_SIGN_CERT_REQ',
  IMPORT_AUTH_CERT = 'IMPORT_AUTH_CERT',
  IMPORT_EXPORT_SERVICE_ACL = 'IMPORT_EXPORT_SERVICE_ACL',
  IMPORT_INTERNAL_SSL_CERT = 'IMPORT_INTERNAL_SSL_CERT',
  IMPORT_SIGN_CERT = 'IMPORT_SIGN_CERT',
  INIT_CONFIG = 'INIT_CONFIG',
  REFRESH_WSDL = 'REFRESH_WSDL', // client > services > refresh WSDL
  RESTORE_CONFIGURATION = 'RESTORE_CONFIGURATION',
  SEND_AUTH_CERT_DEL_REQ = 'SEND_AUTH_CERT_DEL_REQ',
  SEND_AUTH_CERT_REG_REQ = 'SEND_AUTH_CERT_REG_REQ',
  SEND_CLIENT_DEL_REQ = 'SEND_CLIENT_DEL_REQ',
  SEND_CLIENT_REG_REQ = 'SEND_CLIENT_REG_REQ',
  UPLOAD_ANCHOR = 'UPLOAD_ANCHOR',
  VIEW_ACL_SUBJECT_OPEN_SERVICES = 'VIEW_ACL_SUBJECT_OPEN_SERVICES',
  VIEW_ANCHOR = 'VIEW_ANCHOR',
  VIEW_CLIENTS = 'VIEW_CLIENTS', // clients tab (clients table)
  VIEW_CLIENT_ACL_SUBJECTS = 'VIEW_CLIENT_ACL_SUBJECTS', // subsystem "service clients" tab
  VIEW_CLIENT_DETAILS = 'VIEW_CLIENT_DETAILS', // * member / subsystem view
  VIEW_CLIENT_INTERNAL_CERTS = 'VIEW_CLIENT_INTERNAL_CERTS',  // * member / subsystem  "internal servers" tab
  VIEW_CLIENT_INTERNAL_CERT_DETAILS = 'VIEW_CLIENT_INTERNAL_CERT_DETAILS', // member / subsystem - System TLS certificate details view
  VIEW_CLIENT_INTERNAL_CONNECTION_TYPE = 'VIEW_CLIENT_INTERNAL_CONNECTION_TYPE', // internal servers > connection type
  VIEW_CLIENT_LOCAL_GROUPS = 'VIEW_CLIENT_LOCAL_GROUPS', // subsystem "local groups" tab
  VIEW_CLIENT_SERVICES = 'VIEW_CLIENT_SERVICES', // subsystem "services" tab
  VIEW_INTERNAL_SSL_CERT = 'VIEW_INTERNAL_SSL_CERT', // view certificate in client "internal servers"
  VIEW_KEYS = 'VIEW_KEYS', // keys and certificates tab
  VIEW_PROXY_INTERNAL_CERT = 'VIEW_PROXY_INTERNAL_CERT',
  VIEW_SERVICE_ACL = 'VIEW_SERVICE_ACL',
  VIEW_SYS_PARAMS = 'VIEW_SYS_PARAMS',
  VIEW_TSPS = 'VIEW_TSPS',
}

export const mainTabs = [
  {
    to: { name: RouteName.Clients },
    key: 'clients',
    name: 'tab.main.clients',
    permission: Permissions.VIEW_CLIENTS,
  },
  {
    to: { name: RouteName.SignAndAuthKeys },
    key: 'keys',
    name: 'tab.main.keys',
    permission: Permissions.VIEW_KEYS,
  },
  {
    to: { name: RouteName.Diagnostics },
    key: 'diagnostics',
    name: 'tab.main.diagnostics',
    permission: Permissions.DIAGNOSTICS,
  },
  {
    to: { name: RouteName.SystemParameters },
    key: 'settings',
    name: 'tab.main.settings',
  },
];
