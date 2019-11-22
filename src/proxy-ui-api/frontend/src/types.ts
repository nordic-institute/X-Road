



export interface Service {
  'id': string;
  'service_code': string;
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
  'rights_given_at': string;
  'subject': {
    'id': string;
    'member_name_group_description': string;
    'subject_type': string;
  };
}

