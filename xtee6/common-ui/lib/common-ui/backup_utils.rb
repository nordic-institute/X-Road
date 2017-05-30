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

require "fileutils"
require "common-ui/io_utils"
require "common-ui/script_utils"
require "common-ui/tar_file"
require "common-ui/uploaded_file"

java_import Java::java.io.RandomAccessFile
java_import Java::ee.ria.xroad.common.SystemProperties

# TODO: whitelist known backup files and do not allow random filenames
# from client!
module CommonUi
  module BackupUtils

    RESTORE_LOCKFILE_NAME = "restore_lock"
    RESTORE_FLAGFILE_NAME = "restore_in_progress"

    module_function

    def backup_files
      ensure_backup_directory_exists

      result = {}

      files = Dir.entries(SystemProperties.getConfBackupPath)

      files.each do |file|
        file_path = "#{SystemProperties.getConfBackupPath}/#{file}"

        next if File.directory?(file_path) || file.start_with?(".") ||
          !IOUtils.is_filename_valid?(file)

        result[file] = {
          :name => file,
          :size => File.size(file_path) / 1000 # We need kB
        }
      end

      result
    end

    def delete_file(filename)
      if backup_files[filename].nil?
        raise "Backup file does not exist"
      end

      if filename == nil || filename.empty?
        raise "File name must not be empty by this point!"
      end

      deleted_file = backup_file(filename)
      File.delete(deleted_file)

      deleted_file
    end

    def upload_new_file(uploaded_file_param)
      ensure_backup_directory_exists

      validate_backup_file(uploaded_file_param)

      filename = uploaded_file_param.original_filename
      IOUtils.validate_filename(filename)

      uploaded_backup_file = backup_file(filename)
      IOUtils.write_binary(uploaded_backup_file, uploaded_file_param.read())

      uploaded_backup_file
    end

    def backup
      ensure_backup_directory_exists

      tarfile =
        backup_file("conf_backup_#{Time.now.strftime('%Y%m%d-%H%M%S')}.tar")

      commandline = [ScriptUtils.get_script_file("backup_xroad.sh"), tarfile]

      Rails.logger.info("Running configuration backup with command "\
                  "'#{commandline}'")

      console_output_lines = ScriptUtils.run_script(commandline)

      Rails.logger.info("Configuration backup finished with exit status" \
                  " '#{$?.exitstatus}'")
      Rails.logger.info(" --- Backup script console output - START --- ")
      Rails.logger.info("\n#{console_output_lines.join('\n')}")
      Rails.logger.info(" --- Backup script console output - END --- ")

      return $?.exitstatus, console_output_lines, tarfile
    end

    def restore(conf_file, &success_handler)
      if backup_files[conf_file].nil?
        raise "Backup file does not exist"
      end

      commandline = [
        ScriptUtils.get_script_file("restore_xroad.sh"), backup_file(conf_file) ]

      lockfile = try_restore_lock

      unless lockfile
        Rails.logger.info("Aborting restore, another restore already in progress")
        raise "Restore already in progress"
      end

      FileUtils.touch(IOUtils.temp_file(RESTORE_FLAGFILE_NAME))

      Rails.logger.info("Running configuration restore with command "\
                  "'#{commandline.join(" ")}'")

      console_output_lines = ScriptUtils.run_script(commandline)

      Rails.logger.info("Restoring configuration finished with exit status" \
                  " '#{$?.exitstatus}'")
      Rails.logger.info(" --- Restore script console output - START --- ")
      Rails.logger.info("\n#{console_output_lines.join('\n')}")
      Rails.logger.info(" --- Restore script console output - END --- ")

      return $?.exitstatus, console_output_lines, backup_file(conf_file)
    ensure
      begin
        yield if success_handler
      ensure
        if lockfile
          FileUtils.rm_f(IOUtils.temp_file(RESTORE_FLAGFILE_NAME)) # shouldn't throw?
          IOUtils.release_lock(lockfile)
        end
      end
    end

    def try_restore_lock
      return IOUtils.try_lock(RESTORE_LOCKFILE_NAME)
    end

    def restore_in_progress?
      File.exists?(IOUtils.temp_file(RESTORE_FLAGFILE_NAME))
    end

    def backup_file(filename)
      "#{SystemProperties.getConfBackupPath}/#{filename}"
    end

    def validate_backup_file(uploaded_file_param)
      UploadedFile::Validator.new(
        uploaded_file_param,
        TarFile::Validator.new,
        TarFile.restrictions).validate
    end

    def ensure_backup_directory_exists
      backup_dir = SystemProperties.getConfBackupPath
      return if File.exists?(backup_dir)

      FileUtils.mkdir_p(backup_dir)
    end
  end
end
