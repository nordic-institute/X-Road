#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

java_import Java::org.bouncycastle.asn1.x509.CRLReason
java_import Java::org.bouncycastle.cert.ocsp.OCSPResp
java_import Java::org.bouncycastle.cert.ocsp.RevokedStatus

module KeysHelper

  def columns(tokens)
    result = []

    tokens.each do |token|
      result << token_columns(token)

      token.keyInfo.each do |key|
        result << key_columns(token, key)

        key.certs.each do |cert|
          result << cert_columns(token, key, cert)
        end if key.certs

        key.certRequests.each do |cert_request|
          result << cert_request_columns(token, key, cert_request)
        end if key.certRequests

      end if token.keyInfo
    end

    result
  end

  def token_columns(token)
    {
      :token_id => token.id,
      :token_friendly_name => token.friendlyName,
      :token_available => token.available,
      :token_active => token.active,
      :token_activatable => can?(:activate_token),
      :token_locked => token.status == TokenStatusInfo::USER_PIN_LOCKED,
      :token_saved_to_conf => token_saved_to_configuration?(token),
      :key_id => nil,
      :key_friendly_name => nil,
      :key_available => nil,
      :key_usage => nil,
      :key_deletable => nil,
      :key_saved_to_conf => nil,
      :cert_id => nil,
      :cert_friendly_name => nil,
      :cert_member_id => nil,
      :cert_member_code => nil,
      :cert_ocsp_response => nil,
      :cert_expires => nil,
      :cert_expires_in => nil,
      :cert_status => nil,
      :cert_saved_to_conf => nil,
      :cert_importable => can?(:import_auth_cert) && can?(:import_sign_cert),
      :cert_request => nil,
      :cert_active => nil,
      :cert_activatable => nil,
      :cert_deletable => nil,
      :buttons => nil,
      :register_enabled => nil,
      :unregister_enabled => nil
    }
  end

  def key_columns(token, key)
    saved_to_conf = key_saved_to_configuration?(key)

    token_columns(token).merge!({
      :key_id => key.id,
      :key_friendly_name => key.friendlyName,
      :key_available => key.available && token.active,
      :key_usage => key_usage_to_sym(key.usage),
      :key_deletable => can_delete_key?(token, key, saved_to_conf),
      :key_saved_to_conf => saved_to_conf
    })
  end

  def cert_columns(token, key, cert)
    cert_obj = CommonUi::CertUtils.cert_object(cert.certificateBytes)

    key_columns(token, key).merge!({
      :cert_id => cert.id,
      :cert_friendly_name => cert_friendly_name(cert_obj),
      :cert_member_id => (cert.member_id.toString if cert.member_id),
      :cert_member_code => member_code(cert.member_id),
      :cert_ocsp_response => cert_ocsp_response(cert, cert_obj),
      :cert_expires => cert_expires(cert_obj),
      :cert_expires_in => cert_expires_in(cert_obj),
      :cert_status => cert_status(cert),
      :cert_saved_to_conf => cert.savedToConfiguration,
      :cert_request => false,
      :cert_active => cert.active,
      :cert_activatable => can_activate_cert?(key),
      :cert_deletable => can_delete_cert?(token, key, cert.savedToConfiguration),
      :register_enabled => key.usage == KeyUsageInfo::AUTHENTICATION &&
        [CertificateInfo::STATUS_SAVED].include?(cert.status),
      :unregister_enabled => key.usage == KeyUsageInfo::AUTHENTICATION &&
        [CertificateInfo::STATUS_REGINPROG,
         CertificateInfo::STATUS_REGISTERED].include?(cert.status)
    })
  end

  def cert_request_columns(token, key, cert_request)
    key_columns(token, key).merge!({
      :cert_id => cert_request.id,
      :cert_friendly_name => t('keys.csr_friendly_name'),
      :cert_member_id => (cert_request.member_id.toString if cert_request.member_id),
      :cert_member_code => member_code(cert_request.member_id),
      :cert_saved_to_conf => true,
      :cert_request => true,
      :cert_deletable => can_delete_cert?(token, key, true)
    })
  end

  def key_usage_to_sym(key_usage)
    return :auth if key_usage == KeyUsageInfo::AUTHENTICATION
    return :sign if key_usage == KeyUsageInfo::SIGNING
    nil
  end

  def member_code(member_id)
    "#{member_id.member_class} : #{member_id.member_code}" if member_id
  end

  def cert_status(cert_info)
    if cert_info.status
      # TODO: no need to split anymore
      cert_info.status.split(CertificateInfo::OCSP_RESPONSE_DELIMITER)[0]
    end 
  end

  def cert_ocsp_response(cert_info, cert)
    return "disabled" if !cert_info.active
    return "expired" if (cert.not_after - Time.now).to_i < 0

    unless CertificateInfo::STATUS_REGISTERED == cert_info.status
      return nil
    end

    unless cert_info.ocspBytes
      return CertificateInfo::OCSP_RESPONSE_UNKNOWN
    end

    resp = OCSPResp.new(cert_info.ocspBytes)
    basic_resp = resp.responseObject
    single_resp = basic_resp.responses[0]

    status = single_resp.certStatus

    if !status
      return CertificateInfo::OCSP_RESPONSE_GOOD
    elsif status.java_kind_of?(RevokedStatus)
      if status.hasRevocationReason &&
          status.revocationReason == CRLReason::certificateHold
        return CertificateInfo::OCSP_RESPONSE_SUSPENDED
      end

      return CertificateInfo::OCSP_RESPONSE_REVOKED
    else
      return CertificateInfo::OCSP_RESPONSE_UNKNOWN
    end
  end

  def cert_expires(cert)
    cert.not_after.strftime("%F")
  end

  def cert_expires_in(cert)
    (cert.not_after - Time.now).to_i / (60 * 60 * 24)
  end

  def cert_friendly_name(cert)
    issuer_cn = ""
    cert.issuer.to_a.each do |part|
      if part[0] == "CN"
        issuer_cn = part[1]
        break
      end
    end

    "#{issuer_cn} #{cert.serial}"
  end

  def cert_subject(cert)
    result = ""
    cert.subject.to_a.each do |part|
      result += ", " if !result.empty?
      result += "#{part[0]}=#{part[1]}"
    end

    result
  end

  def client_options(client_ids)
    options = []
    client_ids.each do |key,value|
      text = "#{value.xRoadInstance}:#{value.memberClass}:#{value.memberCode}:*"
      options << [text, key]
    end
    options
  end

  def token_saved_to_configuration?(token)
    token.keyInfo.each do |key|
      return true if key_saved_to_configuration?(key)
    end

    false
  end

  def key_saved_to_configuration?(key)
    return true unless key.certRequests.isEmpty

    key.certs.each do |cert|
      return true if cert.savedToConfiguration
    end

    false
  end

  def can_activate_cert?(key)
    (key.usage == KeyUsageInfo::AUTHENTICATION &&
      can?(:activate_disable_auth_cert)) ||
    (key.usage == KeyUsageInfo::SIGNING &&
      can?(:activate_disable_sign_cert))
  end

  def can_delete_cert?(token, key, saved_to_conf)
    if token.readOnly && !saved_to_conf
      return false
    end

    return true unless key.usage

    (key.usage == KeyUsageInfo::AUTHENTICATION &&
      can?(:delete_auth_cert)) ||
    (key.usage == KeyUsageInfo::SIGNING &&
      can?(:delete_sign_cert))
  end

  def can_delete_key?(token, key, saved_to_conf)
    if token.readOnly && !saved_to_conf
      return false
    end

    if key.usage == KeyUsageInfo::AUTHENTICATION
      can?(:delete_auth_key)
    elsif key.usage == KeyUsageInfo::SIGNING
      can?(:delete_sign_key)
    else
      can?(:delete_key)
    end
  end
end
