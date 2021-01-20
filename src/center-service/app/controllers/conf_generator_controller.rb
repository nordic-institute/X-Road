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

class ConfGeneratorController < ApplicationController

  ALLOWED_IPS = ['127.0.0.1', 'localhost'].freeze

  before_filter :restrict_access

  def index
    @configurations_generator = ConfigurationsGenerator.new

    GlobalConfGenerationSynchronizer.generate() do
      logger.debug("Starting global conf generation transaction "\
          "for thread #{get_current_thread_name()}")

      ActiveRecord::Base.isolation_level(:repeatable_read) do
        ActiveRecord::Base.transaction do
          @configurations_generator.create_distributable_configuration
          @configurations_generator.distribute_configuration
        end
      end

      logger.info("hello world")
      logger.info("hello world")
      logger.info("hello " + '\u0085' + '\u008D' + " world \r\n")
      logger.info("hello world")
      logger.info("hello world")

      raise "hello " + '\u0085' + '\u008D' + " world \r\n"

      logger.info("Finished global conf generation transaction "\
          "for thread #{get_current_thread_name()}")
    end

    success_msg = "Global configuration generated successfully.\n"
    GlobalConfGenerationStatus.write_success(success_msg)

    render :text => ""
  rescue
    @configurations_generator.remove_conf_locations if @configurations_generator

    logger.error($!.message)
    logger.error($!.backtrace.join("\n"))

    GlobalConfGenerationStatus.write_failure(
        GlobalConfSigningLog.get_exception_ctx($!))

    render :text => "#{$!.message}\n"
  end

  private

  def get_current_thread_name
    return Java::java.lang.Thread.currentThread().getName()
  end

  def restrict_access
    unless(Rails.env.start_with?("devel") ||
        ALLOWED_IPS.include?(request.env['REMOTE_ADDR']))
      render :text => ""
    end
  end
end
