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

java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParameters
java_import Java::ee.ria.xroad.common.conf.globalconf.SharedParameters
java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParametersSchemaValidator
java_import Java::ee.ria.xroad.common.conf.globalconf.SharedParametersSchemaValidator
java_import Java::ee.ria.xroad.common.util.HashCalculator

class ConfGeneratorController < ApplicationController

  ALLOWED_IPS = ['127.0.0.1', 'localhost'].freeze

  OLD_CONF_PRESERVING_SECONDS = 600

  before_filter :restrict_access

  def index
    GlobalConfGenerationSynchronizer.generate() do
      logger.debug("Starting global conf generation transaction "\
          "for thread #{get_current_thread_name()}")

      # TODO: Move global conf generation implementation away from this
      # controller!
      ActiveRecord::Base.isolation_level(:repeatable_read) do
        ActiveRecord::Base.transaction do
          create_distributable_configuration()
          distribute_configuration()
        end
      end

      logger.info("Finished global conf generation transaction "\
          "for thread #{get_current_thread_name()}")
    end

    render :text => ""
  rescue
    remove_new_conf_dir()

    logger.error($!.message)
    logger.error($!.backtrace.join("\n"))

    render :text => "#{$!.message}\n"
# ensure
#   GlobalConfGeneratorState.clear_generating()
  end

  private

  def get_current_thread_name
    return Java::java.lang.Thread.currentThread().getName()
  end

  def remove_new_conf_dir
    return unless @new_conf_dir

    FileUtils.remove_entry_secure(@new_conf_dir, :force => true)
  rescue
    logger.error(
        "Failed to remove new conf directory, message:\n#{$!.message}")
  end

  def restrict_access
    unless(Rails.env.start_with?("devel") ||
        ALLOWED_IPS.include?(request.env['REMOTE_ADDR']))
      render :text => ""
    end
  end

  # -- Conf generation logic - start ---

  def create_distributable_configuration
    private_parameters_xml = generate(PrivateParametersGenerator.new())
    shared_parameters_xml = generate(SharedParametersGenerator.new())

    logger.info("Validating private parameters")
    PrivateParametersSchemaValidator.validate(private_parameters_xml)
    logger.info("Validating shared parameters")
    SharedParametersSchemaValidator.validate(shared_parameters_xml)

    logger.info("Saving private parameters")
    DistributedFiles.save_configuration_part(
      PrivateParameters::FILE_NAME_PRIVATE_PARAMETERS,
      private_parameters_xml)

    logger.info("Saving shared parameters")
    DistributedFiles.save_configuration_part(
      SharedParameters::FILE_NAME_SHARED_PARAMETERS,
      shared_parameters_xml)

    logger.info("Saving success message")

    success_msg = "Global configuration generated successfully.\n"
    GlobalConfGenerationStatus.write_success(success_msg)

    logger.info("Configuration generation: success")
  rescue
    logger.info("#{$!.message}")
    GlobalConfGenerationStatus.write_failure(
        GlobalConfSigningLog.get_exception_ctx($!))
    raise "Failed to generate valid global configuration: #{$!.message}"
  end

  def generate(generator)
    logger.info("Generating global configuration part with generator "\
        "#{generator.class} from database state...")

    generator.generate
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
  rescue
    logger.error("#{$!.message}")
    raise $!
  end

  def init_generated_conf_location
    @generation_timestamp = Time.now().utc().strftime("%Y%m%d%H%M%S%N")
    @new_conf_dir = "#{get_generated_conf_dir()}/#@generation_timestamp"

    FileUtils.mkdir_p(@new_conf_dir, :mode => 0755)
  rescue
    raise "Failed to initialize generated configuration location: "\
        "'#{$!.message}'"
  end

  def process_internal_configuration
    logger.debug("process_internal_configuration() - start")

    signing_key = ConfigurationSource.get_internal_signing_key

    unless signing_key
      raise "Internal source must have an active key, but there is none."
    end

    target_file = get_temp_internal_directory()

    logger.info("Generating internal conf to: #{target_file}")

    signed_file = sign(
        signing_key.key_identifier,
        DistributedFiles.get_internal_source_content_identifiers())

    distribute(signed_file, target_file, signing_key.cert)

    GlobalConfSigningLog.write(
        "Internal configuration distributed successfully.\n",
        get_internal_directory)

    logger.debug("process_internal_configuration() - finished")
  rescue
    GlobalConfSigningLog.write(
        GlobalConfSigningLog.get_exception_ctx($!),
        get_internal_directory)

    raise "Processing internal configuration failed: #{$!.message}"
  end

  def process_external_configuration
    logger.debug("process_external_configuration() - start")

    signing_key = ConfigurationSource.get_external_signing_key

    return if signing_key == nil

    allowed_content_identifiers =
      [SharedParameters::CONTENT_ID_SHARED_PARAMETERS]

    target_file = get_temp_external_directory()
    logger.info("Generating external conf to: #{target_file}")

    signed_file = sign(signing_key.key_identifier, allowed_content_identifiers)
    distribute(signed_file, target_file, signing_key.cert)

    GlobalConfSigningLog.write(
        "External configuration distributed successfully.\n",
        get_external_directory)

    logger.debug("process_external_configuration() - finished")
  rescue
    GlobalConfSigningLog.write(
        GlobalConfSigningLog.get_exception_ctx($!),
        get_external_directory)

    raise "Processing external configuration failed: #{$!.message}"
  end

  def sign(signing_key_id, allowed_content_identifiers = nil)
    logger.info("Generating signed distributed files")
    if signing_key_id.blank?
      raise "Cannot sign without signing key!"
    end

    begin
      get_signer(signing_key_id, allowed_content_identifiers).sign()
    rescue
      raise "Failed to sign files: #{$!.message}"
    end
  end

  def distribute(signed_file, target_file, signing_cert)
    logger.info("Distributing files to #{target_file}")
    if target_file.blank?
      raise "Distribution target file must not be blank!"
    end

    if signing_cert.blank?
      raise "Cannot distribute configuration without verification cert!"
    end

    begin
      get_distributor(target_file, signing_cert).distribute(signed_file)
    rescue
      raise "Failed to distribute files: #{$!.message}"
    end
  end

  def serve_configuration
    generated_conf_dir = get_generated_conf_dir

    internal_directory_path = "#{generated_conf_dir}/#{get_internal_directory}"
    logger.debug("Serving internal conf on path '#{internal_directory_path}'")

    begin
      FileUtils.mv(
          "#{generated_conf_dir}/#{get_temp_internal_directory}",
          internal_directory_path)
    rescue
      raise "Failed to serve internal configuration: #{$!.message}"
    end

    return unless can_serve_external_directory?

    external_directory_path = "#{generated_conf_dir}/#{get_external_directory}"
    logger.debug("Serving external conf on path '#{external_directory_path}'")

    begin
      FileUtils.mv(
          "#{generated_conf_dir}/#{get_temp_external_directory}",
          external_directory_path)
    rescue
      raise "Failed to serve external configuration: #{$!.message}"
    end
  end

  def can_serve_external_directory?
    File.exists?("#{get_generated_conf_dir}/#{get_temp_external_directory}")
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
  rescue
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

  def get_distributor(target_file, signing_cert)
    hash_calculator =
        HashCalculator.new(SystemParameter.conf_sign_cert_hash_algo_uri)

    SignedDirectoryDistributor.new(
        get_generated_conf_dir(),
        target_file,
        hash_calculator,
        signing_cert)
  end

  def get_generated_conf_dir
    return \
        Java::ee.ria.xroad.common.SystemProperties.getCenterGeneratedConfDir()
  end

  def get_temp_internal_directory
    return "#{get_internal_directory()}.tmp"
  end

  def get_temp_external_directory
    return "#{get_external_directory()}.tmp"
  end

  def get_internal_directory
    return \
        Java::ee.ria.xroad.common.SystemProperties.getCenterInternalDirectory()
  end

  def get_external_directory
    return \
        Java::ee.ria.xroad.common.SystemProperties.getCenterExternalDirectory()
  end

  def get_local_conf_directory
    return \
        Java::ee.ria.xroad.common.SystemProperties.getConfigurationPath()
  end

  def save_distributed_files_to_disk
    DistributedFiles.get_all.each do |file|
      write_public_copy(file)
      write_local_copy(file)
    end
    write_local_instance
  rescue
    raise "Failed to save configuration to disk: #{$!.message}"
  end

  def write_public_copy(file)
    target_file = "#@new_conf_dir/#{file.file_name}"
    write_to_disk(target_file, file.file_data)
  end

  # Creates a local copy of the global conf file to /etc/xroad/globalconf.
  # This is necessary so that central server Java components could easily read
  # the global configuration through the same API as the security server.
  def write_local_copy(file)
    instance_identifier = SystemParameter.instance_identifier
    target_directory = "#{get_local_conf_directory()}/#{instance_identifier}"

    # Create the target directory, if it does not exist
    # TODO might need to delete old files first
    FileUtils.mkdir_p(target_directory, :mode => 0755)

    target_file = "#{target_directory}/#{file.file_name}"
    write_to_disk(target_file, file.file_data)

    # Create a dummy metadata so that ConfigurationDirectory could read the conf
    conf_expire_time = Time.now + SystemParameter.conf_expire_interval_seconds
    dummy_metadata = "{\"contentIdentifier\":\"DUMMY\","\
      "\"instanceIdentifier\":\"#{instance_identifier}\",\"contentFileName\":null,"\
      "\"contentLocation\":\"\""\
      ",\"expirationDate\":\"#{conf_expire_time.utc().strftime "%Y-%m-%dT%H:%M:%SZ"}\"}"
    write_to_disk("#{target_file}.metadata", dummy_metadata)
  end

  def write_local_instance
    instance_identifier = SystemParameter.instance_identifier
    target_file = "#{get_local_conf_directory()}/instance-identifier"
    write_to_disk(target_file, instance_identifier)
  end

  def write_to_disk(target_file, file_data)
    logger.info("Writing data to '#{target_file}'")

    encoded_output = file_data.force_encoding(Rails.configuration.encoding)

    writing_process = Proc.new {|f| f.write(encoded_output)}
    CommonUi::IOUtils.write_public(target_file, writing_process)
  rescue
    logger.error("Failed to save distributed file #{target_file} "\
        "to disk: #{$!.message}")
    raise $!
  end

  # -- Conf distribution logic - end ---
end
