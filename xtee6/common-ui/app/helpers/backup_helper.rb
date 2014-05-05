module BackupHelper
  include ScriptsHelper

  # Invokes configuration backup script and renders result.
  def backup_and_render
    tarfile = 
      backup_file("conf_backup_#{Time.now.strftime('%Y%m%d-%H%M%S')}.tar")

    commandline = "#{get_backup_script_file} #{tarfile}"

    logger.debug("About to backup conf with command '#{commandline}'")

    system(commandline)

    render :json => {
      :success => $?.exitstatus == 0 ? 1 : 0,
      :exit_status => $?.exitstatus,
      :tarfile => File.basename(tarfile)
    }
  end

  def download_and_render(tarfile_name)
    send_data(File.read(backup_file(tarfile_name)), :filename => tarfile_name)

    # TODO: Ensure that all temp artifacts will be removed
  end

  def backup_file(filename)
    return "/var/lib/sdsb/backup/#{filename}"
  end
end