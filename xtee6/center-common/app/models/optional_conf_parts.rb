require 'open3'

module OptionalConfParts
  class Validator
    def initialize(validation_program , file_bytes, content_identifier)
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

      Open3.popen3(@validation_program, "r+") \
          do |stdin, stdout, stderr, wait_thr|
        stdin.write(@file_bytes)
        stdin.close()

        stderr.each_line do |each|
          stderr_lines << each.strip()
        end

        stderr.close()
        stdout.close()

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

  class ValidationException < Exception
    attr_reader :stderr

    def initialize(message, stderr = [])
      super(message)

      @stderr = stderr
    end
  end
end
