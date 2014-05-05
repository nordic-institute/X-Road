module RestoreHelper
  include ScriptsHelper

  private

  # Invokes configuration restore script and renders result.
  #
  def restore_and_render(uploaded_file_param)
    uploaded_conf_file = save_uploaded_conf(uploaded_file_param)

    commandline = 
        "#{get_restore_script_file()} #{uploaded_conf_file}"

    logger.debug("About to restore conf with command '#{commandline}'")

    system(commandline)

    logger.debug("Restore command executed")

    if $?.exitstatus != 0
      error(t("restore.error.script_failed", {:exitstatus => $?.exitstatus}))
      upload_error(nil, "restoreConfiguration.uploadCallback")
      return
    end

    logger.debug("Restore command was executed successfully")

    notice(t("restore.success"))

    upload_success(nil , "restoreConfiguration.uploadCallback")
  end

  def save_uploaded_conf(uploaded_file_param)
    uploaded_conf_file =
        "/var/tmp/sdsb/#{uploaded_file_param.original_filename}"
    uploaded_conf = File.open(uploaded_conf_file, "wb")
    uploaded_conf.write(uploaded_file_param.read())

    return uploaded_conf_file
  ensure
    uploaded_conf.close if uploaded_conf
  end
end