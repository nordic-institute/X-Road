java_import Java::ee.cyber.sdsb.common.SystemProperties

module BackupHelper
  include ScriptsHelper

  private

  # Invokes configuration backup script and renders result.
  def backup_and_render
    tarfile = 
      backup_file("conf_backup_#{get_current_timestamp_for_filename()}.tar")

    commandline = [get_backup_script_file(), tarfile]

    logger.info("Running configuration backup with command "\
        "'#{commandline}'")

    console_output_lines = run_script(commandline)

    logger.info("Configuration backup finished with exit status" \
        " '#{$?.exitstatus}'")
    logger.info(" --- Backup script console output - START --- ")
    logger.info("\n#{console_output_lines.join('\n')}")
    logger.info(" --- Backup script console output - END --- ")

    if $?.exitstatus == 0
      notice(t("backup.index.done"))
    else
      error(t("backup.index.error", {:code => $?.exitstatus}))
    end

    render_json({
      :console_output => console_output_lines
    })
  end

  def check_backup_file_existence_and_render(filename)
    render_json(:exists => File.exists?(backup_file(filename)))
  end

  def refresh_and_render
    render_json(get_backup_files())
  end

  def delete_file_and_render(filename)
    if filename == nil || filename.empty?
      raise "File name must not be empty by this point!"
    end

    File.delete(backup_file(filename))

    notice(t("backup.success.delete"))
  rescue Exception => e
    logger.error(e)
    error(t("backup.error.delete", {:reason => e.message}))
  ensure
    render_json()
  end

  def upload_new_file_and_render(uploaded_file_param)
    filename = uploaded_file_param.original_filename
    validate_filename(filename)

    uploaded_backup_file = backup_file(filename)
    SdsbFileUtils.write_binary(uploaded_backup_file, uploaded_file_param.read())

    notice(t("backup.success.upload"))
    upload_success(nil, "confBackup.uploadCallback")
  rescue Exception => e
    error(e.message)
    upload_error(nil, "confBackup.uploadCallback")
  end

  def download_and_render(tarfile_name)
    send_data(SdsbFileUtils.read_binary(backup_file(tarfile_name)),
        :filename => tarfile_name)
  end

  def get_backup_files
    result = []
    files_in_backup_dir = Dir.entries(SystemProperties.getConfBackupPath())

    files_in_backup_dir.each do |each|
      backup_path = backup_file(each)
      next if File.directory?(backup_path) || 
          each.start_with?(".") ||
          !is_filename_valid?(each)

      result << {
        :name => each,
        :size => File.size(backup_path) / 1000 # We need kB
      }
    end

    return result
  end
end
