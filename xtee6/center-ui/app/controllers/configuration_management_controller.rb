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

require 'fileutils'

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.common.util.HashCalculator
java_import Java::ee.ria.xroad.commonui.SignerProxy

class ConfigurationManagementController < ApplicationController
  UPLOAD_FILE_HASH_ALGORITHM = CryptoUtils::SHA224_ID

  before_filter :verify_get, :only => [
    :index,
    :source,
    :available_tokens,
    :download_conf_part,
    :trusted_anchors,
    :can_view_trusted_anchors
  ]

  before_filter :verify_post, :only => [
    :generate_source_anchor,
    :generate_signing_key,
    :activate_signing_key,
    :delete_signing_key,
    :logout_token,
    :upload_conf_part,
    :upload_trusted_anchor,
    :save_uploaded_trusted_anchor,
    :clear_uploaded_trusted_anchor,
    :delete_trusted_anchor
  ]

  upload_callbacks({
    :upload_conf_part => "XROAD_CONFIGURATION_SOURCE.uploadCallback",
    :upload_trusted_anchor => "XROAD_TRUSTED_ANCHORS.uploadCallback"
  })

  # -- Common GET methods - start ---

  def index
    authorize!(:view_configuration_management)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def source
    validate_params({
      :source_type => [:required]
    })

    if params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL
      authorize!(:view_internal_configuration_source)
    elsif params[:source_type] == ConfigurationSource::SOURCE_TYPE_EXTERNAL
      authorize!(:view_external_configuration_source)
    else
      raise "Unknown source type"
    end

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    render_source(source)
  end

  def download_source_anchor
    authorize!(:download_source_anchor)

    validate_params({
      :source_type => [:required]
    })

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    raise "Anchor not found" unless source.anchor_file

    send_data(source.anchor_file, :filename =>
      get_anchor_filename(
        SystemParameter.instance_identifier,
        source.source_type,
        source.anchor_generated_at))
  end

  def available_tokens
    authorize!(:generate_signing_key)

    validate_params

    tokens = []

    SignerProxy::getTokens.each do |token|
      tokens << {
        :id => token.id,
        :label => token.friendlyName || token.id,
        :inactive => !token.active
      } if token.available
    end

    render_json(tokens)
  end

  def download_conf_part
    authorize!(:download_configuration_part)

    validate_params({
      :content_identifier => [:required]
    })

    conf_part = DistributedFiles.get_by_content_id(params[:content_identifier])
    file_name = conf_part.file_name
    ext = File.extname(file_name)
    file_name[ext] = "_" +
      format_time(conf_part.file_updated_at.localtime).gsub(" ", "_") + ext

    send_data(conf_part.file_data, :filename => file_name)
  end

  def trusted_anchors
    authorize!(:view_trusted_anchors)

    can_delete = can?(:delete_trusted_anchor)
    can_download = can?(:download_trusted_anchor)

    result = []

    TrustedAnchor.find_each do |each|
      generated_at = each.generated_at != nil ?
        format_time(each.generated_at, true): "N/A"

      result << {
          :id => each.id,
          :instance_identifier => each.instance_identifier,
          :hash => each.trusted_anchor_hash,
          :generated_at => generated_at,
          :can_delete => can_delete,
          :can_download => can_download
      }
    end

    render_json_without_messages(result)
  end

  def download_trusted_anchor
    authorize!(:download_trusted_anchor)

    anchor = TrustedAnchor.find(params[:id])
    raise "Anchor not found" unless anchor

    send_data(anchor.trusted_anchor_file, :filename =>
      get_anchor_filename(
        anchor.instance_identifier,
        ConfigurationSource::SOURCE_TYPE_EXTERNAL,
        anchor.generated_at))
  end

  def can_view_trusted_anchors
    render_json({:can => can?(:view_trusted_anchors)})
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def generate_source_anchor
    if params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL
      audit_log("Re-create internal configuration anchor", audit_log_data = {})
    else
      audit_log("Re-create external configuration anchor", audit_log_data = {})
    end

    authorize!(:generate_source_anchor)

    validate_params({
      :source_type => [:required]
    })

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    unless source
      raise "Configuration source not found"
    end

    source.generate_anchor

    audit_log_data[:anchorFileHash] = source.anchor_file_hash
    audit_log_data[:anchorFileHashAlgorithm] =
      ConfigurationSource::ANCHOR_FILE_HASH_ALGORITHM

    notice(t("configuration_management.sources." \
             "#{source.source_type}_anchor_generated"))

    render_source(source)
  end

  def generate_signing_key
    if params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL
      audit_log("Generate internal configuration signing key",
        audit_log_data = {})
    else
      audit_log("Generate external configuration signing key",
        audit_log_data = {})
    end

    authorize!(:generate_signing_key)

    validate_params({
      :source_type => [:required],
      :token_id => [:required]
    })

    token = SignerProxy::getToken(params[:token_id])

    audit_log_data[:tokenId] = token.id
    audit_log_data[:tokenSerialNumber] = token.serialNumber
    audit_log_data[:tokenFriendlyName] = token.friendlyName

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    signing_key = source.generate_signing_key(params[:token_id])

    audit_log_data[:keyId] = signing_key.key_identifier
    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(signing_key.cert)
    audit_log_data[:certHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm

    begin
      source.generate_anchor
      notice(t("configuration_management.sources." \
        "#{source.source_type}_anchor_generated"))
    rescue
      error(t("configuration_management.sources." \
        "#{source.source_type}_anchor_error", :reason => $!.message))
    end

    render_source(source)
  end

  def activate_signing_key
    if params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL
      audit_log("Activate internal configuration signing key",
        audit_log_data = {})
    else
      audit_log("Activate external configuration signing key",
        audit_log_data = {})
    end

    authorize!(:activate_signing_key)

    validate_params({
      :source_type => [:required],
      :id => [:required]
    })

    key = ConfigurationSigningKey.find(params[:id])

    # Only activate available keys
    token = SignerProxy::getToken(key.token_identifier)

    audit_log_data[:tokenId] = token.id
    audit_log_data[:tokenSerialNumber] = token.serialNumber
    audit_log_data[:tokenFriendlyName] = token.friendlyName
    audit_log_data[:keyId] = key.key_identifier

    token.keyInfo.each do |key_info|
      if key_info.id == key.key_identifier
        if !token.available || !key_info.available
          raise t("configuration_management.sources.token_or_key_not_available")
        end

        break
      end
    end

    key.configuration_source.update_attributes!({
      :active_key => key
    })

    render_source(key.configuration_source)
  end

  def delete_signing_key
    if params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL
      audit_log("Delete internal configuration signing key",
        audit_log_data = {})
    else
      audit_log("Delete external configuration signing key",
        audit_log_data = {})
    end

    authorize!(:delete_signing_key)

    validate_params({
      :source_type => [:required],
      :id => [:required]
    })

    key = ConfigurationSigningKey.find(params[:id])

    audit_log_data[:tokenId] = key.token_identifier
    audit_log_data[:keyId] = key.key_identifier

    key.destroy

    notice(t("configuration_management.sources.deleting_key_from_conf_success"))

    begin
      token = SignerProxy::getToken(key.token_identifier)
      token_name = (token && token.friendlyName) || key.token_identifier

      audit_log_data[:tokenSerialNumber] = token.serialNumber
      audit_log_data[:tokenFriendlyName] = token.friendlyName

      translate_coded_exception do
        SignerProxy::deleteKey(key.key_identifier, true)
      end

      notice!(t("configuration_management.sources.deleting_key_from_token_success", {
        :token => token_name
      }))
    rescue
      error(t("configuration_management.sources.deleting_key_from_token_failed", {
        :token => token_name,
        :reason => $!.message
      }))
    end

    source = key.configuration_source

    begin
      source.generate_anchor
      notice(t("configuration_management.sources." \
        "#{source.source_type}_anchor_generated"))
    rescue
      error(t("configuration_management.sources." \
        "#{source.source_type}_anchor_error", :reason => $!.message))
    end

    render_source(key.configuration_source)
  end

  def upload_conf_part
    audit_log("Upload configuration part", audit_log_data = {})

    authorize!(:upload_configuration_part)

    validate_params({
      :source_type => [:required],
      :content_identifier => [:required],
      :file_upload => [:required],
      :part_file_name => [:required]
    })

    content_identifier = params[:content_identifier]
    upload_file_name = params[:file_upload].original_filename
    file_name = params[:part_file_name]

    audit_log_data[:sourceType] = params[:source_type]
    audit_log_data[:contentIdentifier] = content_identifier
    audit_log_data[:partFileName] = file_name
    audit_log_data[:uploadFileName] = upload_file_name

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    source_type = source.source_type

    if source_type == ConfigurationSource::SOURCE_TYPE_EXTERNAL &&
        content_identifier != DistributedFiles::CONTENT_IDENTIFIER_SHARED_PARAMS
      raise "Unknown configuration part"
    end

    optional_parts_conf = DistributedFiles.get_optional_parts_conf()

    validation_program =
        optional_parts_conf.getValidationProgram(file_name)

    file_bytes = params[:file_upload].read
    file_hash = CryptoUtils::hexDigest(UPLOAD_FILE_HASH_ALGORITHM,
        file_bytes.to_java_bytes)

    audit_log_data[:uploadFileHash] = file_hash
    audit_log_data[:uploadFileHashAlgorithm] = UPLOAD_FILE_HASH_ALGORITHM

    file_validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    validator_stderr = file_validator.validate()

    DistributedFiles.save_configuration_part(file_name , file_bytes)

    notice(get_uploaded_message(validator_stderr, content_identifier))

    response = {
      :parts => DistributedFiles.get_configuration_parts_as_json(source_type),
      :stderr => validator_stderr
    }

    render_json(response)
  end

  def upload_trusted_anchor
    authorize!(:upload_trusted_anchor)

    @temp_anchor_path = get_temp_anchor_path
    @anchor_xml = params[:file_upload].read
    @anchor_hash = get_anchor_hash

    save_temp_anchor

    # File must be saved to disk in order to use unmarshaller!
    @anchor_unmarshaller = AnchorUnmarshaller.new(@temp_anchor_path)

    render_json(get_anchor_info)
  rescue Java::ee.ria.xroad.common.CodedException => e
    log_stacktrace(e)

    logger.error("Schema validation of uploaded anchor failed, message:\n'"\
        "#{e.message}'")

    raise t("configuration_management.trusted_anchors.error.anchor_malformed")
  end

  def save_uploaded_trusted_anchor
    audit_log("Add trusted anchor", audit_log_data = {})

    authorize!(:upload_trusted_anchor)

    init_temp_anchor

    audit_log_data[:anchorFileHash] = @temp_anchor_hash
    audit_log_data[:anchorFileHashAlgorithm] =
      ConfigurationSource::ANCHOR_FILE_HASH_ALGORITHM

    @anchor_unmarshaller = AnchorUnmarshaller.new(@temp_anchor_path)

    audit_log_data[:instanceIdentifier] =
      @anchor_unmarshaller.get_instance_identifier
    audit_log_data[:generatedAt] = @anchor_unmarshaller.get_generated_at
    audit_log_data[:anchorUrls] =
      @anchor_unmarshaller.get_anchor_urls.collect do |anchor_url|
        anchor_url.url
      end

    CommonUi::ScriptUtils.verify_external_configuration(@temp_anchor_path)

    save_anchor
    clear_temp_anchor_data

    render_json
  end

  # TODO Get rid of
  def clear_uploaded_trusted_anchor
    authorize!(:upload_trusted_anchor)

    @upload_cancelled = true

    clear_temp_anchor_data

    render_json_without_messages
  end

  def delete_trusted_anchor
    audit_log("Delete trusted anchor", audit_log_data = {})

    authorize!(:delete_trusted_anchor)

    trusted_anchor = TrustedAnchor.find(params[:id])

    audit_log_data[:instanceIdentifier] = trusted_anchor.instance_identifier
    audit_log_data[:anchorFileHash] = trusted_anchor.trusted_anchor_hash
    audit_log_data[:anchorFileHashAlgorithm] =
      ConfigurationSource::ANCHOR_FILE_HASH_ALGORITHM

    trusted_anchor.destroy

    notice(t("configuration_management.trusted_anchors.delete_successful",
        :instance => params[:instanceIdentifier]))

    render_json
  end

  # -- Specific POST methods - end ---

  private

  def render_source(source)
    source_dir =
      (params[:source_type] == ConfigurationSource::SOURCE_TYPE_INTERNAL) \
        ? SystemProperties::getCenterInternalDirectory \
        : SystemProperties::getCenterExternalDirectory

    if SystemParameter.central_server_address
      download_url = "http://#{SystemParameter.central_server_address}/#{source_dir}"
    end

    keys = {}

    source.configuration_signing_keys.find_each do |key|
      key_generation_time = key.key_generated_at != nil ?
          key.key_generated_at.localtime : nil

      keys[key.key_identifier] = {
        :id => key.id,
        :token_id => key.token_identifier,
        :token_friendly_name => key.token_identifier,
        :token_active => false,
        :token_available => false,
        :key_id => key.key_identifier,
        :key_generated_at => format_time(key_generation_time),
        :key_active => source.active_key && key.id == source.active_key.id,
        :key_available => false
      }
    end

    SignerProxy::getTokens.each do |token|
      token.keyInfo.each do |key|
        if keys.has_key?(key.id)
          keys[key.id][:token_active] = token.active
          keys[key.id][:token_available] = token.available
          keys[key.id][:key_available] =
            key.available || (token.available && !token.active)
        end
      end

      keys.each_value do |val|
        if val[:token_id] == token.id
          val[:token_friendly_name] = token.friendlyName || token.id
        end
      end
    end

    render_json({
      :anchor_file_hash => source.anchor_file_hash,
      :anchor_generated_at => format_time(source.anchor_generated_at, true),
      :download_url => download_url,
      :keys => keys.values,
      :parts => DistributedFiles.get_configuration_parts_as_json(
          source.source_type)
    })
  end

  def get_uploaded_message(validator_stderr, content_identifier)
    translation_key = validator_stderr.empty? ?
        "configuration_management.sources.conf_part_upload.successful":
        "configuration_management.sources.conf_part_upload.warnings"

    return t(translation_key, :content_identifier => content_identifier)
  end

  # -- Methods related to anchor upload - start ---

  def save_temp_anchor
    raise "Temp anchor path must be present" if @temp_anchor_path.blank?
    raise "Anchor XML must be present" if @anchor_xml.blank?
    raise "Anchor hash must be present" if @anchor_hash.blank?

    CommonUi::IOUtils.write_binary(@temp_anchor_path, @anchor_xml)

    session[:anchor_temp_path] = @temp_anchor_path
    session[:anchor_hash] = @anchor_hash
  end

  def get_temp_anchor_path
    CommonUi::IOUtils.temp_file("uploaded_anchor_#{request.session_options[:id]}")
  end

  def get_anchor_hash
    raise "Anchor XML must be present" if @anchor_xml.blank?

    format_hash(CryptoUtils::hexDigest(
      ConfigurationSource::ANCHOR_FILE_HASH_ALGORITHM,
      @anchor_xml.to_java_bytes))
  end

  def get_anchor_info
    raise "Anchor hash must be present" if @anchor_hash.blank?
    raise "Anchor unmarshaller must be present" if @anchor_unmarshaller.blank?

    instance_identifier = @anchor_unmarshaller.get_instance_identifier

    if instance_identifier.eql?(SystemParameter.instance_identifier)
      raise t("configuration_management.trusted_anchors.error.same_instance")
    end

    instance_info =
        t("configuration_management.trusted_anchors.upload_info.instance",
            :instance => instance_identifier)

    generated_at = @anchor_unmarshaller.get_generated_at
    formatted_generation_time = generated_at != nil ?
        format_time(generated_at.utc, true) : "N/A"
    generated_info =
        t("configuration_management.trusted_anchors.upload_info.generated",
            :generated => formatted_generation_time)

    hash_info =
      t("configuration_management.trusted_anchors.upload_info.hash",
        :alg => ConfigurationSource::ANCHOR_FILE_HASH_ALGORITHM,
        :hash => @anchor_hash)

    {
      :instance => instance_info,
      :generated => generated_info,
      :hash => hash_info
    }
  end

  # -- Methods related to anchor upload - end ---

  # -- Methods related to saving anchor - start ---

  def init_temp_anchor
     @temp_anchor_path = session[:anchor_temp_path]
     @temp_anchor_hash = session[:anchor_hash]
  end

  def save_anchor
    anchor_hash = session[:anchor_hash]

    logger.debug("Going to save anchor from temp file '#@temp_anchor_path' "\
        "and with hash '#{anchor_hash}'")

    TrustedAnchor.add_anchor(
        AnchorUnmarshaller.new(@temp_anchor_path), @temp_anchor_hash)

    @anchor_saved = true
  end

  # -- Methods related to saving anchor - end ---

  def clear_temp_anchor_data
    clear_session_temp_anchor_data()
    clear_anchor_temp_file() if can_clear_anchor_temp_file?()
  end

  def clear_session_temp_anchor_data
    session.delete(:anchor_temp_path)
    session.delete(:anchor_hash)
  end

  def clear_anchor_temp_file
    return if @temp_anchor_path.blank?

    logger.debug("Removing anchor temp file '#@temp_anchor_path'...")

    FileUtils.rm(@temp_anchor_path);
  end

  def get_anchor_filename(instance_identifier, source_type, generated_at)
    formatted = generated_at != nil ?
        "_#{format_time(generated_at, true).gsub(" ", "_")}" : ""

    return "configuration_anchor_#{instance_identifier}_#{source_type}#{formatted}.xml"
  end

  def can_clear_anchor_temp_file?
    return @upload_cancelled || @anchor_saved
  end

  def format_hash(hash)
    return hash.upcase.scan(/.{1,2}/).join(':')
  end
end
