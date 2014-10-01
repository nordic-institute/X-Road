module RestoreHelper
  include ScriptsHelper

  private

  # Invokes configuration restore script and renders result.
  # TODO (RM #3687): We must implement some manual transaction handling!
  #
  def restore_and_render(conf_file, &success_handler)
    validate_filename(conf_file)
    commandline = [get_restore_script_file(), backup_file(conf_file)]

    logger.info("Running configuration restore with command "\
        "'#{commandline.join(" ")}'")

    console_output_lines = run_script(commandline)

    logger.info("Restoring configuration finished with exit status" \
        " '#{$?.exitstatus}'")
    logger.info(" --- Restore script console output - START --- ")
    logger.info("\n#{console_output_lines.join('\n')}")
    logger.info(" --- Restore script console output - END --- ")

    if $?.exitstatus == 0
      notice(t("restore.success", {:conf_file => conf_file}))
    else
      error(t("restore.error.script_failed", {:conf_file => conf_file}))
    end

    render_json({
      :console_output => console_output_lines
    })
  ensure
    yield if success_handler
  end
end
