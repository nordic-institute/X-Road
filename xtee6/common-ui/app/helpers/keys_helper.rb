# TODO: should separate general cert utility functions and move back
# view helpers to proxy-ui
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
      :token_activatable => can?(:login_logout_tokens),
      :token_locked => token.status == TokenStatusInfo::USER_PIN_LOCKED,
      :key_id => nil,
      :key_friendly_name => nil,
      :key_available => nil,
      :key_usage => nil,
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
      :buttons => nil,
      :register_enabled => nil,
      :unregister_enabled => nil
    }
  end

  def key_columns(token, key)
    token_columns(token).merge!({
      :key_id => key.id,
      :key_friendly_name => key.friendlyName,
      :key_available => key.available,
      :key_usage => key_usage_to_sym(key.usage)
    })
  end

  def cert_columns(token, key, cert)
    cert_obj = cert_from_java_bytes(cert.certificateBytes)
    status = cert_status(cert)

    kc = KeysController
    key_columns(token, key).merge!({
      :cert_id => cert.id,
      :cert_friendly_name => cert_friendly_name(cert_obj),
      :cert_member_id => (cert.member_id.toString if cert.member_id),
      :cert_member_code => member_code(cert.member_id),
      :cert_ocsp_response => cert_ocsp_response(cert),
      :cert_expires => cert_expires(cert_obj),
      :cert_expires_in => cert_expires_in(cert_obj),
      :cert_status => status,
      :cert_saved_to_conf => cert.savedToConfiguration,
      :cert_request => false,
      :cert_active => cert.active,
      :register_enabled => key.usage == KeyUsageInfo::AUTHENTICATION &&
        [kc::STATE_SAVED].include?(status),
      :unregister_enabled => key.usage == KeyUsageInfo::AUTHENTICATION &&
        [kc::STATE_REGINPROG, kc::STATE_REGISTERED].include?(status)
    })
  end

  def cert_request_columns(token, key, cert_request)
    key_columns(token, key).merge!({
      :cert_id => cert_request.id,
      :cert_friendly_name => "Request",
      :cert_member_id => (cert_request.member_id.toString if cert_request.member_id),
      :cert_member_code => member_code(cert_request.member_id),
      :cert_saved_to_conf => true,
      :cert_request => true
    })
  end

  def key_usage_to_sym(key_usage)
    return :auth if key_usage == KeyUsageInfo::AUTHENTICATION
    return :sign if key_usage == KeyUsageInfo::SIGNING
    nil
  end

  def member_code(member_id)
    "#{member_id.member_code} (#{member_id.member_class})" if member_id
  end
  
  def cert_from_java_bytes(bytes)
    OpenSSL::X509::Certificate.new(String.from_java_bytes(bytes))
  end

  def cert_ocsp_response(cert)
    "disabled" unless cert.active
  end

  def cert_expires(cert)
    cert.not_after.strftime("%F")
  end

  def cert_expires_in(cert)
    (cert.not_after - Time.now).to_i / (60 * 60 * 24)
  end

  def cert_status(cert)
    (@cert_statuses && @cert_statuses[cert.id]) || cert.status
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

  def cert_hash(cert)
    digest = CryptoUtils::certHash(cert.to_der.to_java_bytes)
    CryptoUtils::encodeBase64(digest)
  end

  def cert_dump(cert)
    %x[echo "#{cert.to_s}" | openssl x509 -text -noout 2>&1]
  end

  def client_options(client_ids)
    options = []
    client_ids.each do |key,value|
      options << [value.toString, key]
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
end
