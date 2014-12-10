# Controller for generating, signing and distributing the GlobalConf.
#
# GlobalConf is generated from the current state of the database.
# GlobalConfSigner will create multipart structure which includes the signing
# date and use the key id and algorithm id specified in the database to sign
# the data using Signer.
#
# Access to this controller is restricted to localhost
# This controller should be periodically pinged via crontab or other scheduling
# means.

require 'tmpdir'
require 'fileutils'

require 'common-ui/io_utils'
require 'common-ui/cert_utils'

java_import Java::ee.cyber.sdsb.common.conf.globalconf.PrivateParameters
java_import Java::ee.cyber.sdsb.common.conf.globalconf.SharedParameters
java_import Java::ee.cyber.sdsb.common.conf.globalconf.PrivateParametersSchemaValidator
java_import Java::ee.cyber.sdsb.common.conf.globalconf.SharedParametersSchemaValidator
java_import Java::ee.cyber.sdsb.common.util.HashCalculator

class ConfGeneratorController < ApplicationController

  ALLOWED_IPS = ['127.0.0.1', 'localhost'].freeze

  OLD_CONF_PRESERVING_SECONDS = 600

  # before_filter :restrict_access

  def index
    validate_generator_state()

    GlobalConfGeneratorState.set_generating()

    # TODO (task #5619):
    # Find more appropriate isolation level or remove it altogether!
#   ActiveRecord::Base.isolation_level(:serializable) do
    ActiveRecord::Base.transaction do
      create_distributable_configuration()
      distribute_configuration()
    end
#   end

    render :text => ""
  rescue Exception => e
    remove_new_conf_dir()

    logger.error(e.message)
    logger.error(e.backtrace.join("\n"))

    render :text => "#{e.message}\n"
  ensure
    GlobalConfGeneratorState.clear_generating()
  end

  private

  def validate_generator_state
    return unless GlobalConfGeneratorState.generating?()

    raise "Global configuration is currently being generated, "\
        "parallel generations are not allowed."
  end

  def remove_new_conf_dir
    return unless @new_conf_dir

    FileUtils.remove_entry_secure(@new_conf_dir, :force => true)
  rescue Exception
    logger.error(
        "Failed to remove new conf directory, message:\n#{$!.message}")
  end

  def restrict_access
    unless(ALLOWED_IPS.include? request.env['REMOTE_ADDR'])
      render :text => ""
    end
  end

  def log(msg)
    puts msg
    %x[logger #{msg}]
  end

  # -- Conf generation logic - start ---

  def create_distributable_configuration
    private_parameters_xml = generate(PrivateParametersGenerator.new())
    shared_parameters_xml = generate(SharedParametersGenerator.new())

    log "Validating private parameters"
    PrivateParametersSchemaValidator.validate(private_parameters_xml)
    log "Validating shared parameters"
    SharedParametersSchemaValidator.validate(shared_parameters_xml)

    log "Saving private parameters"
    DistributedFiles.save_configuration_part(
      PrivateParameters::FILE_NAME_PRIVATE_PARAMETERS,
      private_parameters_xml)

    log "Saving shared parameters"
    DistributedFiles.save_configuration_part(
      SharedParameters::FILE_NAME_SHARED_PARAMETERS,
      shared_parameters_xml)

    log "Saving success message"

    success_msg = "Global configuration generated successfully.\n"
    GlobalConfGenerationStatus.write_success(success_msg)

    log "Configuration generation: success"
  rescue Exception => e
    log "#{e.message}"
    GlobalConfGenerationStatus.write_failure(
        DistributedSignedFiles.get_exception_ctx(e))
    raise e
  end

  def generate(generator)
    log "Generating GlobalConf part with generator #{generator.class} "\
        "from database state..."
    begin
      return generator.generate()
    rescue Exception => e
      raise "Failed to generate GlobalConf: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end

  # -- Conf generation logic - end ---

  # -- Conf distribution logic - start ---

  def distribute_configuration
    init_generated_conf_location()

    save_distributed_files_to_disk()

    process_internal_configuration()
    process_external_configuration()

    serve_configuration()

    clean_up_old_configuration()

    DistributedSignedFiles.write_signed_files_log(
        "Distributed files signed successfully.\n")
  rescue Exception => e
    DistributedSignedFiles.write_signed_files_log(e)
    log "#{e.message}"
    raise e
  end

  def init_generated_conf_location
    @generation_timestamp = Time.now().utc().strftime("%Y%m%d%H%M%S%N")
    @new_conf_dir = "#{get_generated_conf_dir()}/#@generation_timestamp"

    FileUtils.mkdir_p(@new_conf_dir, :mode => 0755)
  end

  def process_internal_configuration
    logger.debug("process_internal_configuration() - start")

    signing_key = ConfigurationSource.get_internal_signing_key

    unless signing_key
      raise "Internal source must have an active key, but there is none."
    end

    target_file = get_temp_internal_directory()

    log "Generating internal conf to: #{target_file}"

    sign(
        signing_key.key_identifier,
        DistributedFiles.get_internal_source_content_identifiers())

    distribute(target_file, signing_key.certificate)

    logger.debug("process_internal_configuration() - finished")
  end

  def process_external_configuration
    logger.debug("process_external_configuration() - start")

    signing_key = ConfigurationSource.get_external_signing_key

    # TODO: To be clarified if it stays so.
    return if signing_key == nil

    allowed_content_identifiers =
      [SharedParameters::CONTENT_ID_SHARED_PARAMETERS]

    target_file = get_temp_external_directory()
    log "Generating external conf to: #{target_file}"

    sign(signing_key.key_identifier, allowed_content_identifiers)
    distribute(target_file, signing_key.certificate)

    logger.debug("process_external_configuration() - finished")
  end

  def sign(signing_key_id, allowed_content_identifiers = nil)
    log "Generating signed distributed files"
    if signing_key_id.blank?
      raise "Cannot sign without signing key!"
    end

    begin
      get_signer(signing_key_id, allowed_content_identifiers).sign()
    rescue Exception => e
      raise "Failed to sign files: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end

  def distribute(target_file, verification_cert)
    log "Distributing files to #{target_file}"
    if target_file.blank?
      raise "Distribution target file must not be blank!"
    end

    if verification_cert.blank?
      raise "Cannot distribute configuration without verification cert!"
    end

    begin
      get_distributor(target_file, verification_cert).distribute()
    rescue Exception => e
      raise "Failed to distribute files: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end

  def serve_configuration
    generated_conf_dir = get_generated_conf_dir()

    FileUtils.mv(
        "#{generated_conf_dir}/#{get_temp_internal_directory()}",
        "#{generated_conf_dir}/#{get_internal_directory()}")

    FileUtils.mv(
        "#{generated_conf_dir}/#{get_temp_external_directory()}",
        "#{generated_conf_dir}/#{get_external_directory()}")
  end

  def clean_up_old_configuration
    old_entries = Dir.glob("#{get_generated_conf_dir()}/*").select do |f|
      File.mtime(f) < (Time.now() - (OLD_CONF_PRESERVING_SECONDS))
    end

    old_entries.each do |each|
      # TODO: Can we assume that we may remove all directories that are too old?
      next unless File.directory?(each)

      FileUtils.remove_entry_secure(each, :force => true)
    end
  rescue Exception
    logger.error(
        "Failed to clean up old configuration, message:\n#{$!.message}")
  end

  def get_signer(sign_key_id, allowed_content_identifiers)
    conf_expire_time = Time.now + SystemParameter.conf_expire_interval_seconds
    hash_calculator = HashCalculator.new(SystemParameter.conf_hash_algo_uri)

    sign_algo_id = SystemParameter::conf_sign_algo_id

    content_builder = DirectoryContentBuilder.new(
        conf_expire_time,
        hash_calculator,
        @generation_timestamp,
        allowed_content_identifiers)

    return DirectorySigner.new(sign_key_id, sign_algo_id, content_builder)
  end

  def get_distributor(target_file, verification_cert)
    hash_calculator =
        HashCalculator.new(SystemParameter.conf_sign_cert_hash_algo_uri)

    signed_directory_builder = SignedDirectoryBuilder.new(
        get_generated_conf_dir(),
        target_file,
        hash_calculator,
        verification_cert)
    return DistributedDirectoryBuilder.new(signed_directory_builder)
  end

  def get_generated_conf_dir
    return \
        Java::ee.cyber.sdsb.common.SystemProperties.getCenterGeneratedConfDir()
  end

  def get_temp_internal_directory
    return "#{get_internal_directory()}.tmp"
  end

  def get_temp_external_directory
    return "#{get_external_directory()}.tmp"
  end

  def get_internal_directory
    return \
        Java::ee.cyber.sdsb.common.SystemProperties.getCenterInternalDirectory()
  end

  def get_external_directory
    return \
        Java::ee.cyber.sdsb.common.SystemProperties.getCenterExternalDirectory()
  end

  def save_distributed_files_to_disk
    DistributedFiles.find_each do |each|
      write_file_to_disk(each)
    end
  end

  def write_file_to_disk(file)
    file_name = file.file_name

    target_file = "#@new_conf_dir/#{file_name}"
    encoded_output = file.file_data.force_encoding(Rails.configuration.encoding)

    writing_process = Proc.new {|f| f.write(encoded_output)}
    CommonUi::IOUtils.write_public(target_file, writing_process)
  rescue Exception => e
    log "Failed to save distributed file '#{file_name}' "\
        "to disk: #{e.message}"
    raise e
  end

  # -- Conf distribution logic - end ---
end
