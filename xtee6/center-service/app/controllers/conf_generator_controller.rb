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

require 'tempfile'
require 'fileutils'

require "global_conf_generator"

class ConfGeneratorController < ApplicationController

  ALLOWED_IPS = ['127.0.0.1', 'localhost'].freeze

  before_filter :restrict_access

  def index
    begin
      xml = generate
      save_to_db(xml)
      save_to_disk(xml)
      GlobalConfGenerationStatus.write_success()
      render :text => ""
    rescue Exception => e
      log "#{e.message}"
      GlobalConfGenerationStatus.write_failure()
      render :text => "#{e.message}"
    end
  end

  private

  def restrict_access
    unless(ALLOWED_IPS.include? request.env['REMOTE_ADDR'])
      render :text => ""
    end
  end

  def log(msg)
    puts msg
    %x[logger #{msg}]
  end

  def generate
    log "Generating GlobalConf from database state..."
    begin
      GlobalConfGenerator.new.generate
    rescue Exception => e
      raise "Failed to generate GlobalConf: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end

  def save_to_db(data)
    full_name = Java::ee.cyber.sdsb.common.SystemProperties.getGlobalConfFile()
    file_name = Java::ee.cyber.sdsb.common.util.ResourceUtils.getFileNameFromFullPath(full_name)
    DistributedFiles.add_file(file_name, data)
  end

  def save_to_disk(xml)
    target_file = Java::ee.cyber.sdsb.common.SystemProperties.getGlobalConfFile()
    tmp = Tempfile.new("globalconf_temp")
    begin
      tmp.puts(xml)
    ensure
      tmp.close
      begin
        FileUtils.mv(tmp.path, target_file)
      rescue Exception => e
        log "Failed to save GlobalConf to disk: #{e.message}"
      end
    end
  end
end
