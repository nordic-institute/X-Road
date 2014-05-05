# Methods related to backup and restore scripts
module ScriptsHelper
  private

  def get_restore_script_file
    return get_script_file("restore_sdsb.sh")
  end

  def get_backup_script_file
    return get_script_file("backup_sdsb.sh")
  end

  def get_script_file(filename)
    return "/usr/share/sdsb/scripts/#{filename}"
  end
end
