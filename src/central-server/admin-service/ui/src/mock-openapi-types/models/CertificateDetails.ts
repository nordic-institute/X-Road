/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { KeyUsage } from './KeyUsage';

/**
 * certificate details for any kind of certificate (TLS, auth, sign)
 */
export type CertificateDetails = {
  /**
   * certificate issuer distinguished name
   */
  issuer_distinguished_name: string;
  /**
   * certificate issuer common name
   */
  issuer_common_name: string;
  /**
   * certificate subject distinguished name
   */
  subject_distinguished_name: string;
  /**
   * certificate subject common name
   */
  subject_common_name: string;
  /**
   * certificate validity not before
   */
  not_before: string;
  /**
   * certificate validity not after
   */
  not_after: string;
  /**
   * serial number
   */
  serial: string;
  /**
   * version
   */
  version: number;
  /**
   * certificate signature algorithm
   */
  signature_algorithm: string;
  /**
   * hex encoded certificate signature
   */
  signature: string;
  /**
   * certificate public key algorithm
   */
  public_key_algorithm: string;
  /**
   * hex encoded RSA public key modulus (if RSA key)
   */
  rsa_public_key_modulus: string;
  /**
   * RSA public key exponent (if RSA key) as an integer
   */
  rsa_public_key_exponent: number;
  /**
   * certificate SHA-1 hash
   */
  hash: string;
  /**
   * certificate key usage array
   */
  key_usages: Array<KeyUsage>;
  /**
   * certificate subject alternative names
   */
  subject_alternative_names: string;
};
