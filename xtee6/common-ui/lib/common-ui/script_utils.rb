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

java_import Java::ee.ria.xroad.common.util.CryptoUtils

# Methods related to backup and restore scripts
module CommonUi
  module ScriptUtils

    CONFIGURATION_CLIENT_EXIT_STATUS_MISSING_PRIVATE_PARAMS = 121
    CONFIGURATION_CLIENT_EXIT_STATUS_UNREACHABLE = 122
    CONFIGURATION_CLIENT_EXIT_STATUS_OUTDATED = 123
    CONFIGURATION_CLIENT_EXIT_STATUS_INVALID_SIGNATURE = 124
    CONFIGURATION_CLIENT_EXIT_STATUS_OTHER = 125

    INTERNAL_CONF_VERIFICATION_SCRIPT_FILE = "verify_internal_configuration.sh"
    EXTERNAL_CONF_VERIFICATION_SCRIPT_FILE = "verify_external_configuration.sh"

    class RubyExecutableException < Exception
      attr_accessor :console_output_lines

      def initialize(cause, previous_console_lines)
        @console_output_lines = previous_console_lines
        console_output_lines << cause.message
        console_output_lines << cause.backtrace
      end
    end

    module_function

    # Takes array of script arguments.
    def run_script(commandline)
      console_output_lines = []

      # Redirecting stderr to stdout when command given as array did not work.
      commandline << "2>&1"

      IO.popen(commandline.join(" ")) do |io|
        while (line = io.gets) do
          console_output_lines << line
        end
      end

      return console_output_lines
    rescue => e
      logger.error(e)
      raise RubyExecutableException.new(e, console_output_lines)
    end

    def get_script_file(filename)
      "/usr/share/xroad/scripts/#{filename}"
    end

    def verify_internal_configuration(anchor_path)
      verify_configuration(INTERNAL_CONF_VERIFICATION_SCRIPT_FILE, anchor_path)
    end

    def verify_external_configuration(anchor_path)
      verify_configuration(EXTERNAL_CONF_VERIFICATION_SCRIPT_FILE, anchor_path)
    end

    def verify_configuration(script, anchor_path)
      commandline = [get_script_file(script), anchor_path]

      Rails.logger.debug("Verifing configuration with command:\n" \
          "#{commandline.join(" ")}")

      console_output_lines = run_script(commandline)
      exit_status = $?.exitstatus

      Rails.logger.info(" --- Console output - START --- ")
      console_output_lines.each { |l| Rails.logger.info(l) }
      Rails.logger.info(" --- Console output - END --- ")

      Rails.logger.debug(
        "Verification finished with exit status '#{exit_status}'")

      case exit_status
      when 0
        Rails.logger.info("Configuration verified successfully")

      when CONFIGURATION_CLIENT_EXIT_STATUS_MISSING_PRIVATE_PARAMS
        raise I18n.t("conf_verification.missing_private_params")

      when CONFIGURATION_CLIENT_EXIT_STATUS_UNREACHABLE
        raise I18n.t("conf_verification.unreachable")

      when CONFIGURATION_CLIENT_EXIT_STATUS_OUTDATED
        raise I18n.t("conf_verification.outdated")

      when CONFIGURATION_CLIENT_EXIT_STATUS_INVALID_SIGNATURE
        raise I18n.t("conf_verification.signature_invalid")

      when CONFIGURATION_CLIENT_EXIT_STATUS_OTHER
        raise I18n.t("conf_verification.other")

      else
        # Should not reach here
        raise "Unknown exit status from configuration client: '#{exit_status}'"
      end
    end
  end
end
