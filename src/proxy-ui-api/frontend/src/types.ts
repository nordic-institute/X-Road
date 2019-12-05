
export interface Service {
  id: string;
  service_code: string;
  code: string;
  timeout: number;
  ssl_auth: boolean;
  security_category: string;
  url: string;
}

export interface ServiceDescription {
  id: number;
  url: string;
  type: string;
  disabled: boolean;
  disabled_notice: string;
  refreshed_date: string;
  services: Service[];
  client_id: string;
}

export interface AccessRightSubject {
  rights_given_at: string;
  subject: {
    id: string;
    member_name_group_description: string;
    subject_type: string;
  };
}

export interface Token {
  id: string;
  name: string;
  type: string;
  keys: Key[];
  status: string;
  logged_in: boolean;
  available: boolean;
  saved_to_configuration: boolean;
  read_only: boolean;
  token_infos: TokenInfo[];
}

export interface TokenInfo {
  key: string;
  value: string;
}

export interface Key {
  id: string;
  name: string;
  label: string;
  certificates: Certificate[];
  certificate_signing_requests: TokenCertificateSigningRequest[];
  usage: string;
  available: boolean;
  saved_to_configuration: boolean;
}

export interface TokenCertificateSigningRequest {
  id: string;
  owner_id: string;
}

export interface Certificate {
  ocsp_status: string;
  owner_id: string;
  active: boolean;
  saved_to_configuration: boolean;
  certificate_details: CertificateDetails;
  status: string;
}

export interface CertificateDetails {
  issuer_distinguished_name: string;
  issuer_common_name: string;
  subject_distinguished_name: string;
  subject_common_name: string;
  not_before: string;
  not_after: string;
  serial: string;
  version: number;
  signature_algorithm: string;
  signature: string;
  public_key_algorithm: string;
  rsa_public_key_modulus: string;
  rsa_public_key_exponent: number;
  hash: string;
  key_usages: string[];
}

