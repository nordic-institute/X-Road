



export interface Service {
  'service_id': string;
  'code': string;
  'timeout': number;
  'ssl_auth': boolean;
  'security_category': string;
  'url': string;
}


export interface ServiceDescription {
  'id': number;
  'url': string;
  'type': string;
  'disabled': boolean;
  'disabled_notice': string;
  'refreshed_date': string;
  'services': Service[];
  'client_id': string;
}


export interface AccessRightSubject {
  'id': string;
  'name': string;
}