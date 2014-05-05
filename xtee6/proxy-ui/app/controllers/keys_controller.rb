require "ruby_cert_helper"

java_import Java::java.util.ArrayList

java_import Java::org.bouncycastle.pkcs.PKCS10CertificationRequest
java_import Java::org.bouncycastle.asn1.x500.style.BCStyle

java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType
java_import Java::ee.cyber.sdsb.proxyui.SignerProxy
java_import Java::ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo
java_import Java::ee.cyber.sdsb.signer.protocol.dto.TokenInfo
java_import Java::ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo

class KeysController < ApplicationController

  include RubyCertHelper

  STATE_SAVED = "saved"
  STATE_REGINPROG = "registration in progress"
  STATE_REGISTERED = "registered"
  STATE_DELINPROG = "deletion in progress"
  STATE_GLOBALERR = "global error"

  def index
    authorize!(:view_keys)

    @client_ids = cache_client_ids
  end

  def refresh
    authorize!(:view_keys)

    cache_client_ids
    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def activate_token
    authorize!(:login_logout_tokens)

    validate_params({
      :pin => [RequiredValidator.new],
      :token_id => [RequiredValidator.new]
    })

    pin = Array.new
    params[:pin].bytes do |b|
      pin << b
    end

    SignerProxy::activateToken(params[:token_id], pin.to_java(:char))

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def deactivate_token
    authorize!(:login_logout_tokens)

    SignerProxy::deactivateToken(params[:token_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def generate_key
    authorize!(:generate_key)

    key = SignerProxy::generateKey(params[:token_id])

    token = get_token(params[:token_id])

    # lets make a clone with just the generated key inside
    clone = TokenInfo.new(
      token.type,
      token.friendlyName,
      token.id,
      token.readOnly,
      token.available,
      token.active,
      token.serialNumber,
      token.label,
      token.slotIndex,
      token.status,
      ArrayList.new,
      token.tokenInfo)

    clone.keyInfo.add(key)

    @tokens = [clone]
    render :partial => "refresh"
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def generate_csr
    if params[:key_usage] == "auth"
      authorize!(:generate_auth_cert_req)
    else
      authorize!(:generate_sign_cert_req)
    end

    client_id = get_cached_client_id(params[:member_id])

    key_usage = params[:key_usage] == "auth" ?
      KeyUsageInfo::AUTHENTICATION : KeyUsageInfo::SIGNING

    csr = SignerProxy::generateCertRequest(
      params[:key_id], client_id, key_usage, params[:subject_name])

    csr_file = SecureRandom.hex(4)

    File.open(serverconf.temp_file(csr_file), 'wb') do |f|
      f.write(csr)
    end

    render_json({
      :redirect => csr_file
    })
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def download_csr
    validate_params({
      :csr => [RequiredValidator.new, FilenameValidator.new]
    })

    file = serverconf.temp_file(params[:csr])

    # file name parts
    subject = get_csr_subject(file)
    date = Time.now.strftime("%Y%m%d")

    send_file(file, :filename => "cert_request_#{date}#{subject}.p10")
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def import_cert
    authorize!(:import_auth_cert)
    authorize!(:import_sign_cert)

    cert_bytes = params[:file].read
    cert_obj = cert_from_bytes(cert_bytes)

    SignerProxy::importCert(cert_obj.to_der.to_java_bytes, STATE_SAVED)

    notice("Certificate loaded")
    upload_success
  rescue Exception
    render_error($!)
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def import
    authorize!(:import_auth_cert)
    authorize!(:import_sign_cert)

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])
    SignerProxy::importCert(cert.certificateBytes, STATE_SAVED)

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def activate_cert
    authorize!(:activate_disable_auth_cert)
    authorize!(:activate_disable_sign_cert)

    SignerProxy::activateCert(params[:cert_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def deactivate_cert
    authorize!(:activate_disable_auth_cert)
    authorize!(:activate_disable_sign_cert)

    SignerProxy::deactivateCert(params[:cert_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def register
    authorize!(:send_auth_cert_reg_req)

    validate_params({
      :token_id => [RequiredValidator.new],
      :key_id => [RequiredValidator.new],
      :cert_id => [RequiredValidator.new],
      :address => [RequiredValidator.new]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])
    register_cert(params[:address], cert.certificateBytes)
    notice("Request sent")

    SignerProxy::setCertStatus(cert.id, STATE_REGINPROG)

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def unregister
    authorize!(:send_auth_cert_del_req)

    validate_params({
      :token_id => [RequiredValidator.new],
      :key_id => [RequiredValidator.new],
      :cert_id => [RequiredValidator.new]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])
    begin
      unregister_cert(params[:address], cert.certificateBytes)
      notice("Request sent")
    rescue
      warn("delreq_failed", "Failed to send certificate deletion request: " \
           "#{$!.message}. Continue with certificate deletion anyway?")
    end

    SignerProxy::setCertStatus(cert.id, STATE_DELINPROG)

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def delete_key
    validate_params({
      :key_id => [RequiredValidator.new]
    })

    SignerProxy::deleteKey(params[:key_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def delete_cert_request
    authorize!(:delete_auth_cert)
    authorize!(:delete_sign_cert)

    validate_params({
      :cert_id => [RequiredValidator.new]
    })

    SignerProxy::deleteCertRequest(params[:cert_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def delete_cert
    authorize!(:delete_auth_cert)
    authorize!(:delete_sign_cert)

    validate_params({
      :cert_id => [RequiredValidator.new]
    })

    SignerProxy::deleteCert(params[:cert_id])

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def friendly_name
    if params[:friendly_name]
      if params[:token_id]
        SignerProxy::setTokenFriendlyName(
          params[:token_id], params[:friendly_name])
      elsif params[:key_id]
        SignerProxy::setKeyFriendlyName(
          params[:key_id], params[:friendly_name])
      end
    end

    render_tokens
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def token_details
    @token = get_token(params[:token_id])

    render :partial => "token_details"
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def key_details
    @key = get_key(params[:token_id], params[:key_id])

    render :partial => "key_details"
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def cert_details
    @cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    render :partial => "cert_details"
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  private

  def render_tokens
    @tokens = SignerProxy::getTokens

    # check if any auth certs have been registered
    # TODO: should be done upon receiving globalconf
    registered_certs = []
    local_server_id = read_server_id

    globalconf.root.securityServer.each do |server|
      if extract_server_id(server) == local_server_id
        server.authCertHash.each do |cert_hash|
          registered_certs << String.from_java_bytes(cert_hash)
        end

        break
      end
    end

    @cert_statuses = {}

    @tokens.each do |token|
      token.keyInfo.each do |key|
        key.certs.each do |cert|

          if key.usage == KeyUsageInfo::AUTHENTICATION
            cert_hash = String.from_java_bytes(
              CryptoUtils::certHash(cert.certificateBytes))

            registered = registered_certs.include?(cert_hash)

            if cert.status == STATE_REGINPROG && registered
              SignerProxy::setCertStatus(cert.id, STATE_REGISTERED)
              @cert_statuses[cert.id] = STATE_REGISTERED
            end

            if cert.status == STATE_REGISTERED && !registered
              @cert_statuses[cert.id] = STATE_GLOBALERR
            end

          # SIGN certs will get state REGISTERED unconditionally
          elsif cert.status == STATE_SAVED
            SignerProxy::setCertStatus(cert.id, STATE_REGISTERED)
            @cert_statuses[cert.id] = STATE_REGISTERED
          end
        end
      end
    end

    render :partial => "refresh"
  end

  def get_token(token_id)
    SignerProxy::getTokens.each do |token|
      return token if token.id == token_id
    end

    raise "token not found"
  end

  def get_key(token_id, key_id)
    get_token(token_id).keyInfo.each do |key|
      return key if key.id == key_id
    end

    raise "key not found"
  end

  def get_cert(token_id, key_id, cert_id)
    get_key(token_id, key_id).certs.each do |cert|
      return cert if cert.id == cert_id
    end

    raise "cert not found"
  end

  def cache_client_ids
    session[:client_ids] = {}

    serverconf.root.client.each do |client|
      # no certs for subsystems
      client_id = to_member_id(client.identifier)
      session[:client_ids][client_id.toString] = client_id
    end

    session[:client_ids]
  end

  def get_cached_client_id(key)
    session[:client_ids][key]
  end

  # returns subject in format "_C_xx_O_xx_CN_xx"
  def get_csr_subject(file)
    bytes = IO.read(file).to_java_bytes
    csr = PKCS10CertificationRequest.new(bytes)

    result = ""
    csr.subject.getRDNs.each do |rdn|
      if BCStyle::C == rdn.first.type
        result += "_C_#{rdn.first.value}"
      elsif BCStyle::O == rdn.first.type
        result += "_O_#{rdn.first.value}"
      elsif BCStyle::CN == rdn.first.type
        result += "_CN_#{rdn.first.value}"
      end
    end

    result
  end
end
