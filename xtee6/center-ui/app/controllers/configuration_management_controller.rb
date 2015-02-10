require 'fileutils'

java_import Java::ee.cyber.sdsb.common.SystemProperties
java_import Java::ee.cyber.sdsb.common.util.CryptoUtils
java_import Java::ee.cyber.sdsb.common.util.HashCalculator
java_import Java::ee.cyber.sdsb.commonui.SignerProxy

class ConfigurationManagementController < ApplicationController

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

    conf_part = DistributedFiles.where(
      :content_identifier => params[:content_identifier]).first

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
    authorize!(:generate_source_anchor)

    validate_params({
      :source_type => [:required]
    })

    source = ConfigurationSource.get_source_by_type(params[:source_type])

    unless source
      raise "Configuration source not found"
    end

    source.generate_anchor

    notice(t("configuration_management.sources." \
             "#{source.source_type}_anchor_generated"))

    render_source(source)
  end

  def generate_signing_key
    authorize!(:generate_signing_key)

    validate_params({
      :source_type => [:required],
      :token_id => [:required]
    })

    source = ConfigurationSource.get_source_by_type(params[:source_type])
    source.generate_signing_key(params[:token_id])

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
    authorize!(:activate_signing_key)

    validate_params({
      :id => [:required]
    })

    key = ConfigurationSigningKey.find(params[:id])
    key.configuration_source.update_attributes!({
      :active_key => key
    })

    render_source(key.configuration_source)
  end

  def delete_signing_key
    authorize!(:delete_signing_key)

    validate_params({
      :id => [:required]
    })

    key = ConfigurationSigningKey.find(params[:id])
    key.destroy

    begin
      SignerProxy::deleteKey(key.key_identifier, true)
    rescue
      error($!.message)
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
    authorize!(:upload_configuration_part)

    validate_params({
      :source_type => [:required],
      :content_identifier => [:required],
      :conf_part_file => [:required],
      :part_file_name => [:required]
    })

    source = ConfigurationSource.get_source_by_type(params[:source_type])
    content_identifier = params[:content_identifier]

    source_type = source.source_type

    if source_type == ConfigurationSource::SOURCE_TYPE_EXTERNAL &&
        content_identifier != DistributedFiles::CONTENT_IDENTIFIER_SHARED_PARAMS
      raise "Unknown configuration part"
    end

    file_name = params[:part_file_name]

    optional_parts_conf = DistributedFiles.get_optional_parts_conf()

    validation_program =
        optional_parts_conf.getValidationProgram(file_name)

    file_bytes = params[:conf_part_file].read()

    file_validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    validator_stderr = file_validator.validate()

    DistributedFiles.save_configuration_part(file_name , file_bytes)

    notice(get_uploaded_message(validator_stderr, content_identifier))

    response = {
      :parts => DistributedFiles.get_configuration_parts_as_json(source_type),
      :stderr => validator_stderr
    }

    upload_success(response, "SDSB_CONFIGURATION_SOURCE.uploadCallback")
  rescue Exception => e
    log_stacktrace(e)

    validator_stderr = e.respond_to?(:stderr) ? e.stderr : []

    error(e.message)

    upload_error(
        {:stderr => validator_stderr},
        "SDSB_CONFIGURATION_SOURCE.uploadCallback")
  end

  def upload_trusted_anchor
    authorize!(:upload_trusted_anchor)

    @temp_anchor_path = get_temp_anchor_path()
    @anchor_xml = get_anchor_xml()
    @anchor_hash = get_anchor_hash()

    save_temp_anchor()

    # File must be saved to disk in order to use unmarshaller!
    @anchor_unmarshaller = AnchorUnmarshaller.new(@temp_anchor_path)

    anchor_info = get_anchor_info()
    upload_success(
        {:anchor_info => anchor_info}, "SDSB_TRUSTED_ANCHORS.uploadCallback")
  rescue Java::ee.cyber.sdsb.common.CodedException => e
    log_stacktrace(e)

    logger.error("Schema validation of uploaded anchor failed, message:\n'"\
        "#{e.message}'")

    error(t("configuration_management.trusted_anchors.error.anchor_malformed"))

    upload_error(nil, "SDSB_TRUSTED_ANCHORS.uploadCallback")
  rescue RuntimeError => e
    log_stacktrace(e)

    error(e.message)
    upload_error(nil, "SDSB_TRUSTED_ANCHORS.uploadCallback")
  end

  def save_uploaded_trusted_anchor
    authorize!(:upload_trusted_anchor)

    init_temp_anchor

    CommonUi::ScriptUtils.verify_external_configuration(@temp_anchor_path)

    save_anchor
    clear_temp_anchor_data

    render_json
  end

  def clear_uploaded_trusted_anchor
    authorize!(:upload_trusted_anchor)

    @upload_cancelled = true

    clear_temp_anchor_data()

    render_json_without_messages()
  end

  def delete_trusted_anchor
    authorize!(:delete_trusted_anchor)

    TrustedAnchor.destroy(params[:id])

    notice(t("configuration_management.trusted_anchors.delete_successful",
        :instance => params[:instanceIdentifier]))

    render_json()
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
        :key_id => key.key_identifier,
        :key_generated_at => format_time(key_generation_time),
        :key_active => source.active_key && key.id == source.active_key.id,
        :key_available => false
      }
    end

    SignerProxy::getTokens.each do |token|
      token.keyInfo.each do |key|
        if keys.has_key?(key.id)
          keys[key.id][:key_available] = key.available
          keys[key.id][:token_active] = token.active
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
          source.source_type),
    })
  end

  def get_uploaded_message(validator_stderr, content_identifier)
    translation_key = validator_stderr.empty? ?
        "configuration_management.sources.conf_part_upload.successful":
        "configuration_management.sources.conf_part_upload.warnings"

    return t(translation_key, :content_identifier => content_identifier)
  end

  # -- Methods related to anchor upload - start ---

  def get_anchor_xml
    file_param = params[:upload_trusted_anchor_file]
    return file_param.read().force_encoding(Rails.configuration.encoding)
  end

  def save_temp_anchor()
    raise "Temp anchor path must be present" if @temp_anchor_path.blank?
    raise "Anchor XML must be present" if @anchor_xml.blank?
    raise "Anchor hash must be present" if @anchor_hash.blank?

    temp_anchor_path = get_temp_anchor_path()
    CommonUi::IOUtils.write(@temp_anchor_path, @anchor_xml)

    session[:anchor_temp_path] = @temp_anchor_path
    session[:anchor_hash] = @anchor_hash
  end

  def get_temp_anchor_path()
    CommonUi::IOUtils.temp_file("uploaded_anchor_#{request.session_options[:id]}")
  end

  def get_anchor_hash()
    raise "Anchor XML must be present" if @anchor_xml.blank?

    return format_hash(CryptoUtils::hexDigest(
        CryptoUtils::SHA224_ID, @anchor_xml.to_java_bytes))
  end

  def get_anchor_info()
    raise "Anchor hash must be present" if @anchor_hash.blank?
    raise "Anchor unmarshaller must be present" if @anchor_unmarshaller.blank?

    instance_identifier = @anchor_unmarshaller.get_instance_identifier()

    if instance_identifier.eql?(SystemParameter.instance_identifier)
      raise t("configuration_management.trusted_anchors.error.same_instance")
    end

    instance_info =
        t("configuration_management.trusted_anchors.upload_info.instance",
            :instance => instance_identifier)

    generated_at = @anchor_unmarshaller.get_generated_at()
    formatted_generation_time = generated_at != nil ?
        format_time(generated_at.utc, true) : "N/A"
    generated_info =
        t("configuration_management.trusted_anchors.upload_info.generated",
            :generated => formatted_generation_time)

    hash_info =
        t("configuration_management.trusted_anchors.upload_info.hash",
            :hash => @anchor_hash)

    return {
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
