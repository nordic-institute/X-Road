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

require 'open3'

module OptionalConfParts
  def self.get_optional_parts_dir
    return "#{Java::ee.ria.xroad.common.SystemProperties::getConfPath()}/"\
        "configuration-parts"
  end

  class Validator
    STDOUT_CHUNK_SIZE = 1000

    def initialize(validation_program, file_bytes, content_identifier)
      @validation_program = validation_program
      @file_bytes = file_bytes
      @content_identifier = content_identifier
    end

    # Validates configuration source file content, returns standard error of it
    def validate
      return [] if @validation_program.blank?()

      Rails.logger.debug(
          "Validating with program '#@validation_program' "\
          "following file content:\n#@file_bytes")

      unless File.exist?(@validation_program)
        message = I18n.t(
            "configuration_management.sources.conf_part_upload.no_program",
            :program => @validation_program)
        raise ValidationException.new(message)
      end

      stderr_lines = []
      is_successful = false

      Open3.popen3(@validation_program) \
          do |stdin, stdout, stderr, wait_thr|
        stdin_thr = Thread.new do
          # TODO (task #4995): We should somehow handle raw file bytes,
          # not strings
          stdin.write(@file_bytes)
          stdin.close()
        end

        Thread.new do
          outbuf = ""
          stdout.read(STDOUT_CHUNK_SIZE, outbuf) until stdout.eof?
          stdout.close()
        end

        stderr_thr = Thread.new do
          stderr.each_line do |each|
            stderr_lines << each.strip()
          end

          stderr.close()
        end

        stdin_thr.join()
        stderr_thr.join()
        wait_thr.join()

        is_successful = wait_thr.value.success?()
      end

      validate_exit_status(is_successful, stderr_lines)

      return stderr_lines
    rescue Errno::EPIPE
      message = I18n.t(
          "configuration_management.sources.conf_part_upload.program_ended",
          :program => @validation_program)
      raise ValidationException.new(message)
    rescue IOError => e
      message = I18n.t(
          "configuration_management.sources.conf_part_upload.io_error",
          :program => @validation_program, :message => e.message)
      raise IOError.new(message)
    end

    private

    def validate_exit_status(is_successful, stderr)
      return if is_successful

      error_message =
          I18n.t("configuration_management.sources.conf_part_upload.error",
              :content_identifier => @content_identifier)

      raise ValidationException.new(error_message, stderr)
    end
  end

  class ValidationException < StandardError
    attr_reader :stderr

    def initialize(message, stderr = [])
      super(message)

      @stderr = stderr
    end
  end
end
