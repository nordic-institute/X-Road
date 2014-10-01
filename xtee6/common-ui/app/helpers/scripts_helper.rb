java_import Java::ee.cyber.sdsb.common.util.CryptoUtils

# Methods related to backup and restore scripts
module ScriptsHelper
  include BaseHelper

  private

  class RubyExecutableException < Exception
    attr_accessor :console_output_lines

    def initialize(cause, previous_console_lines)
      @console_output_lines = previous_console_lines
      console_output_lines << cause.message
      console_output_lines << cause.backtrace
    end
  end

  # Takes array of script arguments.
  def run_script(commandline)
    console_output_lines = []

    # Redirecting stderr to stdout when command given as array did not work.
    commandline << "2>&1"

    IO.popen(commandline.join(" ")) do |io|
      while (line=io.gets) do 
        console_output_lines << line
      end
    end

    return console_output_lines
  rescue => e
    logger.error(e)
    raise RubyExecutableException.new(e, console_output_lines)
  end

  def get_restore_script_file
    return get_script_file("restore_sdsb.sh")
  end

  def get_backup_script_file
    return get_script_file("backup_sdsb.sh")
  end

  def get_current_timestamp_for_filename()
    return Time.now.strftime('%Y%m%d-%H%M%S')
  end

  def backup_file(filename)
    return "#{SystemProperties.getConfBackupPath()}/#{filename}"
  end

  def get_script_file(filename)
    return "/usr/share/sdsb/scripts/#{filename}"
  end
end
