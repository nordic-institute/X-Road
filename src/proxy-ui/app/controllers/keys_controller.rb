#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

java_import Java::java.util.ArrayList

java_import Java::ee.ria.xroad.common.certificateprofile.GetCertificateProfile
java_import Java::ee.ria.xroad.common.certificateprofile.impl.AuthCertificateProfileInfoParameters
java_import Java::ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters
java_import Java::ee.ria.xroad.common.util.CertUtils
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.commonui.SignerProxy
java_import Java::ee.ria.xroad.proxyui.ImportCertUtil
java_import Java::ee.ria.xroad.signer.protocol.dto.CertificateInfo
java_import Java::ee.ria.xroad.signer.protocol.dto.KeyUsageInfo
java_import Java::ee.ria.xroad.signer.protocol.dto.TokenInfo
java_import Java::ee.ria.xroad.signer.protocol.dto.TokenStatusInfo
java_import Java::ee.ria.xroad.signer.protocol.message.GenerateCertRequest
java_import Java::ee.ria.xroad.signer.protocol.message.CertificateRequestFormat

class KeysController < ApplicationController

  PARAM_KEY_USAGE_AUTH = "auth"
  PARAM_KEY_USAGE_SIGN = "sign"

  CSR_FORMATS = CertificateRequestFormat.values.map { |e| e.toString }

  include Keys::TokenRenderer

  helper_method :token_saved_to_configuration?
  helper_method :key_saved_to_configuration?

  def index
    authorize!(:view_keys)

    @client_ids = cache_client_ids
    @csr_formats = CSR_FORMATS
  end

  def refresh
    authorize!(:view_keys)

    cache_client_ids
    render_tokens
  end

  def generate_key
    audit_log("Generate key", audit_log_data = {})

    authorize!(:generate_key)

    validate_params({
      :token_id => [:required],
      :label => []
    })

    token = SignerProxy::getToken(params[:token_id])

    audit_log_data[:tokenId] = token.id
    audit_log_data[:tokenSerialNumber] = token.serialNumber
    audit_log_data[:tokenFriendlyName] = token.friendlyName

    key = SignerProxy::generateKey(params[:token_id], params[:label])

    audit_log_data[:keyId] = key.id
    audit_log_data[:keyLabel] = key.label
    audit_log_data[:keyFriendlyName] = key.friendlyName

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

    render_json({
      :tokens => tokens_to_json([clone])
    })
  end

  def approved_cas
    if params[:key_usage] == PARAM_KEY_USAGE_AUTH
      authorize!(:generate_auth_cert_req)
    else
      authorize!(:generate_sign_cert_req)
    end

    validate_params({
      :key_usage => [:required]
    })

    approved_cas = GlobalConf::getApprovedCAs(xroad_instance)

    names = []
    approved_cas.each do |ca|
      if params[:key_usage] == PARAM_KEY_USAGE_SIGN && ca.authenticationOnly
        next
      end

      names << ca.name
    end

    render_json({
      :approved_cas => names
    })
  end

  def subject_dn_fields
    if params[:key_usage] == PARAM_KEY_USAGE_AUTH
      authorize!(:generate_auth_cert_req)
    else
      authorize!(:generate_sign_cert_req)
    end

    validate_params({
      :key_usage => [:required],
      :approved_ca => [:required],
      :member_id => []
    })

    profile = get_certificate_profile(params[:approved_ca], params[:key_usage],
      get_cached_client_id(params[:member_id]))

    fields = []
    profile.subjectFields.each do |field|
      fields << {
        :id => field.id,
        :label => field.label,
        :default_value => field.defaultValue,
        :read_only => field.isReadOnly,
        :required => field.isRequired
      }
    end if profile.subjectFields

    render_json({
      :fields => fields
    })
  end

  # rubocop:disable all
  def generate_csr
    audit_log("Generate CSR", audit_log_data = {})

    if params[:key_usage] == PARAM_KEY_USAGE_AUTH
      authorize!(:generate_auth_cert_req)
    else
      authorize!(:generate_sign_cert_req)
    end

    profile = get_certificate_profile(params[:approved_ca], params[:key_usage],
      get_cached_client_id(params[:member_id]))

    field_params = {}
    profile.subjectFields.each do |field|
      field_params[field.id.to_sym] =
        field.required? && !field.read_only? ? [:required] : []
    end

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :key_usage => [:required],
      :member_id => [],
      :approved_ca => [:required],
      :csr_format => [:required]
    }.merge(field_params))

    unless [PARAM_KEY_USAGE_AUTH, PARAM_KEY_USAGE_SIGN].include?(
        params[:key_usage])
      raise "Invalid key usage"
    end

    unless CSR_FORMATS.include?(
        params[:csr_format])
      raise "Invalid CSR format: #{params[:csr_format]}, #{CSR_FORMATS}"
    end

    field_values = []
    profile.subjectFields.each do |field|
      if field.read_only? || params[field.id.to_sym].blank?
        value = field.default_value
      else
        value = params[field.id.to_sym]
      end

      if value
        field_values << "#{field.id}=#{value}"
      end
    end
    subject_name = field_values.join(", ")

    if params[:key_usage] == PARAM_KEY_USAGE_SIGN
      client_id = get_cached_client_id(params[:member_id])
    end

    key_usage = params[:key_usage] == PARAM_KEY_USAGE_AUTH ?
      KeyUsageInfo::AUTHENTICATION : KeyUsageInfo::SIGNING

    key = get_key(params[:token_id], params[:key_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = key.id
    audit_log_data[:keyFriendlyName] = key.friendlyName
    audit_log_data[:keyUsage] = (key_usage.toString if key_usage)
    audit_log_data[:clientIdentifier] = client_id if client_id
    audit_log_data[:subjectName] = subject_name
    audit_log_data[:certificationServiceName] = params[:approved_ca]
    audit_log_data[:csrFormat] = params[:csr_format].upcase

    csr = SignerProxy::generateCertRequest(
      params[:key_id], client_id, key_usage, subject_name,
      CertificateRequestFormat.valueOf(params[:csr_format])).certRequest

    if params[:key_usage] == PARAM_KEY_USAGE_AUTH
      identifier = "securityserver_" \
        "#{@server_id.xRoadInstance}_" \
        "#{@server_id.memberClass}_" \
        "#{@server_id.memberCode}_" \
        "#{@server_id.serverCode}"
    else
      identifier = "member_" \
        "#{client_id.xRoadInstance}_" \
        "#{client_id.memberClass}_" \
        "#{client_id.memberCode}"
    end

    date = Time.now.strftime("%Y%m%d")

    csr_file = SecureRandom.hex(4)
    session[csr_file] = "#{params[:key_usage]}_csr_" \
      "#{date}_#{identifier}.#{params[:csr_format].downcase}"

    File.open(CommonUi::IOUtils.temp_file(csr_file), 'wb') do |f|
      f.write(csr)
    end

    render_json({
      :tokens => tokens_to_json(SignerProxy::getTokens),
      :redirect => csr_file
    })
  end
  # rubocop:enable all

  def download_csr
    validate_params({
      :csr => [:required, :filename],
      :key_usage => [:required]
    })

    file = CommonUi::IOUtils.temp_file(params[:csr])

    send_file(file, :filename => session[params[:csr]])
  end

  def import_cert
    audit_log("Import certificate from file", audit_log_data = {})

    validate_params({
      :file_upload => [:required, :cert]
    })

    GlobalConf::verifyValidity

    uploaded_cert = CommonUi::CertUtils.pem_to_der(params[:file_upload].read)

    audit_log_data[:certFileName] = params[:file_upload].original_filename
    audit_log_data[:certHash] = CommonUi::CertUtils.cert_hash(uploaded_cert)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    java_cert_obj = CryptoUtils::readCertificate(uploaded_cert.to_java_bytes)

    client_id = nil
    cert_state = nil

    if CertUtils::isAuthCert(java_cert_obj)
      authorize!(:import_auth_cert)

      audit_log_data[:keyUsage] = KeyUsageInfo::AUTHENTICATION.toString

      cert_state = CertificateInfo::STATUS_SAVED
    else
      authorize!(:import_sign_cert)

      client_id = ImportCertUtil::getClientIdForSigningCert(
        xroad_instance, java_cert_obj)

      audit_log_data[:clientIdentifier] = client_id if client_id
      audit_log_data[:keyUsage] = KeyUsageInfo::SIGNING.toString

      ImportCertUtil::verifyClientExists(client_id)
      cert_state = CertificateInfo::STATUS_REGISTERED
    end

    SignerProxy::importCert(uploaded_cert.to_java_bytes, cert_state, client_id)

    notice(t('keys.cert_loaded'))

    render_json
  end

  def import
    audit_log("Import certificate from token", audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    GlobalConf::verifyValidity

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])
    java_cert_obj = CryptoUtils::readCertificate(cert.certificateBytes)

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:keyFriendlyName] = @key.friendlyName
    audit_log_data[:keyUsage] = (@key.usage.toString if @key.usage)
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    client_id = nil
    cert_state = nil

    if CertUtils::isAuthCert(java_cert_obj)
      authorize!(:import_auth_cert)
      cert_state = CertificateInfo::STATUS_SAVED
    else
      authorize!(:import_sign_cert)

      client_id = ImportCertUtil::getClientIdForSigningCert(
        xroad_instance, java_cert_obj)

      audit_log_data[:clientIdentifier] = (client_id.toString if client_id)

      ImportCertUtil::verifyClientExists(client_id)
      cert_state = CertificateInfo::STATUS_REGISTERED
    end

    SignerProxy::importCert(cert.certificateBytes, cert_state, client_id)

    render_tokens
  end

  def activate_cert
    audit_log("Enable certificate", audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:keyFriendlyName] = @key.friendlyName
    audit_log_data[:keyUsage] = (@key.usage.toString if @key.usage)
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    if @key.usage == KeyUsageInfo::AUTHENTICATION
      authorize!(:activate_disable_auth_cert)
    else
      authorize!(:activate_disable_sign_cert)
    end

    SignerProxy::activateCert(params[:cert_id])

    render_tokens
  end

  def deactivate_cert
    audit_log("Disable certificate", audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:keyFriendlyName] = @key.friendlyName
    audit_log_data[:keyUsage] = (@key.usage.toString if @key.usage)
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    if @key.usage == KeyUsageInfo::AUTHENTICATION
      authorize!(:activate_disable_auth_cert)
    else
      authorize!(:activate_disable_sign_cert)
    end

    SignerProxy::deactivateCert(params[:cert_id])

    render_tokens
  end

  def register
    audit_log("Register authentication certificate", audit_log_data = {})

    authorize!(:send_auth_cert_reg_req)

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required],
      :address => [:required, :host]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm
    audit_log_data[:address] = params[:address]

    request_id = register_cert(params[:address], cert.certificateBytes)
    audit_log_data[:managementRequestId] = request_id

    notice(t('keys.request_sent'))

    SignerProxy::setCertStatus(cert.id, CertificateInfo::STATUS_REGINPROG)

    audit_log_data[:certStatus] = CertificateInfo::STATUS_REGINPROG

    render_tokens
  end

  def unregister
    audit_log("Unregister authentication certificate", audit_log_data = {})

    authorize!(:send_auth_cert_del_req)

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    GlobalConf::verifyValidity

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    request_id = unregister_cert(cert.certificateBytes)
    audit_log_data[:managementRequestId] = request_id

    notice(t('keys.request_sent'))

    SignerProxy::setCertStatus(cert.id, CertificateInfo::STATUS_DELINPROG)

    audit_log_data[:certStatus] = CertificateInfo::STATUS_DELINPROG

    render_tokens
  end

  def skip_unregister
    audit_log("Skip unregistration of authentication certificate", audit_log_data = {})

    authorize!(:send_auth_cert_del_req)

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    GlobalConf::verifyValidity

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:certId] = cert.id
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    SignerProxy::setCertStatus(cert.id, CertificateInfo::STATUS_DELINPROG)
    audit_log_data[:certStatus] = CertificateInfo::STATUS_DELINPROG

    render_tokens
  end

  def delete_key
    audit_log_event = "Delete key"
    audit_log(audit_log_event, audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required]
    })

    key = get_key(params[:token_id], params[:key_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = key.id
    audit_log_data[:keyFriendlyName] = key.friendlyName
    audit_log_data[:keyUsage] = (key.usage.toString if key.usage)

    if key.usage == KeyUsageInfo::AUTHENTICATION
      authorize!(:delete_auth_key)
    elsif key.usage == KeyUsageInfo::SIGNING
      authorize!(:delete_sign_key)
    else
      authorize!(:delete_key)
    end

    key.certs.each do |cert|
      if key.usage == KeyUsageInfo::AUTHENTICATION &&
         [CertificateInfo::STATUS_REGINPROG,
          CertificateInfo::STATUS_REGISTERED].include?(cert.status)
        authorize!(:send_auth_cert_del_req)
        unregister_cert(cert.certificateBytes)

        SignerProxy::setCertStatus(cert.id, CertificateInfo::STATUS_DELINPROG)
      end
    end

    audit_log_event.replace(audit_log_event +
        " from token and configuration")

    SignerProxy::deleteKey(params[:key_id], false)
    SignerProxy::deleteKey(params[:key_id], true)

    render_tokens
  end

  def delete_cert_request
    audit_log("Delete CSR", audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    key = get_key(params[:token_id], params[:key_id])

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = key.id
    audit_log_data[:keyFriendlyName] = key.friendlyName
    audit_log_data[:keyUsage] = (key.usage.toString if key.usage)
    audit_log_data[:certId] = params[:cert_id]

    if key.usage == KeyUsageInfo::AUTHENTICATION
      authorize!(:delete_auth_cert)
    else
      authorize!(:delete_sign_cert)
    end

    SignerProxy::deleteCertRequest(params[:cert_id])

    render_tokens
  end

  def delete_cert
    audit_log_event = "Delete certificate"
    audit_log(audit_log_event, audit_log_data = {})

    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    audit_log_event.replace(audit_log_event +
        (cert.savedToConfiguration ? " from configuration" : " from token"))

    audit_log_data[:tokenId] = @token.id
    audit_log_data[:tokenSerialNumber] = @token.serialNumber
    audit_log_data[:tokenFriendlyName] = @token.friendlyName
    audit_log_data[:keyId] = @key.id
    audit_log_data[:keyFriendlyName] = @key.friendlyName
    audit_log_data[:keyUsage] = (@key.usage.toString if @key.usage)
    audit_log_data[:certId] = params[:cert_id]
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    if @key.usage == KeyUsageInfo::AUTHENTICATION
      authorize!(:delete_auth_cert)
    else
      authorize!(:delete_sign_cert)
    end

    SignerProxy::deleteCert(params[:cert_id])

    render_tokens
  end

  def friendly_name
    if params[:token_id]
      audit_log("Set friendly name to token", audit_log_data = {})
      audit_log_data[:tokenId] = params[:token_id]
    else
      audit_log("Set friendly name to key", audit_log_data = {})
      audit_log_data[:keyId] = params[:key_id]
    end

    validate_params({
      :friendly_name => [:required],
      :token_id => [],
      :key_id => []
    })

    if params[:token_id]
      token = SignerProxy::getToken(params[:token_id])
      audit_log_data[:tokenFriendlyName] = params[:friendly_name]
      audit_log_data[:tokenSerialNumber] = token.serialNumber

      SignerProxy::setTokenFriendlyName(
        params[:token_id], params[:friendly_name])
    elsif params[:key_id]
      audit_log_data[:keyFriendlyName] = params[:friendly_name]

      SignerProxy::setKeyFriendlyName(
        params[:key_id], params[:friendly_name])
    end

    render_tokens
  end

  def token_details
    validate_params({
      :token_id => [:required]
    })

    @token = SignerProxy::getToken(params[:token_id])

    render :partial => "token_details"
  end

  def key_details
    validate_params({
      :token_id => [:required],
      :key_id => [:required]
    })

    @key = get_key(params[:token_id], params[:key_id])

    render :partial => "key_details"
  end

  def cert_details
    validate_params({
      :token_id => [:required],
      :key_id => [:required],
      :cert_id => [:required]
    })

    cert = get_cert(params[:token_id], params[:key_id], params[:cert_id])

    render_json({
      :dump => CommonUi::CertUtils.cert_dump(cert.certificateBytes),
      :hash => CommonUi::CertUtils.cert_hash(cert.certificateBytes)
    })
  end

  private

  def render_tokens
    render_json({
      :tokens => tokens_to_json(SignerProxy::getTokens)
    })
  end

  def get_key(token_id, key_id)
    @token = SignerProxy::getToken(token_id)
    @token.keyInfo.each do |key|
      return key if key.id == key_id
    end

    raise "key not found"
  end

  def get_cert(token_id, key_id, cert_id)
    @key = get_key(token_id, key_id)
    @key.certs.each do |cert|
      return cert if cert.id == cert_id
    end

    raise "cert not found"
  end

  def cache_client_ids
    session[:client_ids] = {}

    serverconf.client.each do |client|
      # no certs for subsystems
      client_id = ClientId.create(
        client.identifier.xRoadInstance,
        client.identifier.memberClass,
        client.identifier.memberCode, nil)

      session[:client_ids][client_id.toString] = client_id
    end

    session[:client_ids]
  end

  def get_cached_client_id(key)
    get_identifier(session[:client_ids][key])
  end

  def get_certificate_profile(ca_name, key_usage, client_id = nil)
    profile = nil

    GlobalConf::getApprovedCAs(xroad_instance).each do |ca|
      if ca.name == ca_name
        profile = GetCertificateProfile.new(ca.certificateProfileInfo).instance
        break
      end
    end

    if key_usage == PARAM_KEY_USAGE_AUTH
      member_name = get_member_name(
          @server_id.memberClass, @server_id.memberCode)

      profile.getAuthCertProfile(
        AuthCertificateProfileInfoParameters.new(@server_id, member_name))
    else
      if client_id
        member_name = get_member_name(
          client_id.memberClass, client_id.memberCode)
      end

      profile.getSignCertProfile(
        SignCertificateProfileInfoParameters.new(@server_id, client_id, member_name))
    end
  end
end
