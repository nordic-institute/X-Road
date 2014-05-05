# Controller for distributing files into signed mime. Lots of distribution.

require 'tempfile'
require 'fileutils'

class DistributeFilesController < ApplicationController

  ALLOWED_IPS = ['127.0.0.1', 'localhost'].freeze

  # TODO: Perhaps it is better to read this from SystemProperties table as well?
  DIST_FILE = Java::ee.cyber.sdsb.common.SystemProperties.getCenterDistributedFile()

  before_filter :restrict_access

  def index
    begin
      sign
      distribute
      render :text => ""
    rescue Exception => e
      log "#{e.message}"
      render :text => "#{e.message}"
    end
  end

  private

  def log(msg)
    puts msg
    %x[logger #{msg}]
  end

  def restrict_access
    unless(ALLOWED_IPS.include? request.env['REMOTE_ADDR'])
      render :text => ""
    end
  end

  def sign
    log "Generating signed distributed files"
    begin
      DistributedFilesSigner.sign
    rescue Exception => e
      raise "Failed to sign files: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end

  def distribute
    log "Distributing files to #{DIST_FILE}"
    begin
      DistributedFilesDistributor.distribute(DIST_FILE)
    rescue Exception => e
      raise "Failed to distribute files: #{e.message}\n\t#{e.backtrace.join("\n\t")}"
    end
  end
end
