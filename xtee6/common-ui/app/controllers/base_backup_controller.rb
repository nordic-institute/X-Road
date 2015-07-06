require "common-ui/backup_utils"

class BaseBackupController < ApplicationController

  before_filter :verify_get, :only => [
    :index,
    :refresh_files,
    :download
  ]

  before_filter :verify_post, :only => [
    :backup,
    :restore,
    :delete_file,
    :upload_new
  ]

  skip_around_filter :wrap_in_transaction, :only => [:restore]

  def index
    authorize!(:backup_configuration)
  end

  def refresh_files
    authorize!(:backup_configuration)

    render_json(CommonUi::BackupUtils.backup_files.values)
  end

  def download
    authorize!(:backup_configuration)

    unless CommonUi::BackupUtils.backup_files[params[:tarfile]]
      raise "Backup file does not exist"
    end

    send_file(CommonUi::BackupUtils.backup_file(params[:tarfile]), {
      :filename => params[:tarfile]
    })
  end

  def backup
    audit_log("Back up configuration", audit_log_data = {})

    authorize!(:backup_configuration)

    exitcode, output, filename = CommonUi::BackupUtils.backup

    audit_log_data[:backupFileName] = filename

    if exitcode == 0
      notice(t("backup.index.done"))
    else
      error(t("backup.index.error", {:code => exitcode}))
    end

    render_json({
      :console_output => output
    })
  end

  def restore
    audit_log("Restore configuration", audit_log_data = {})

    authorize!(:restore_configuration)

    before_restore

    audit_log_data[:backupFileName] = params[:fileName]

    exitcode, output, filename = CommonUi::BackupUtils.restore(params[:fileName]) do
      after_restore
    end

    audit_log_data[:backupFileName] = filename if filename

    if exitcode == 0
      notice(t("restore.success", {:conf_file => params[:fileName]}))
      after_restore_success
    else
      error(t("restore.error.script_failed", {:conf_file => params[:fileName]}))
    end

    render_json({
      :console_output => output
    }.merge!(@extra_data || {}))
  end

  def delete_file
    audit_log("Delete backup file", audit_log_data = {})

    authorize!(:backup_configuration)

    filename = CommonUi::BackupUtils.delete_file(params[:fileName])

    audit_log_data[:backupFileName] = filename

    notice(t("backup.success.delete"))
  rescue Exception => e
    logger.error(e)
    error(t("backup.error.delete", {:reason => e.message}))
  ensure
    render_json
  end

  def upload_new
    audit_log("Upload backup file", audit_log_data = {})

    authorize!(:backup_configuration)

    filename = CommonUi::BackupUtils.upload_new_file(params[:file_upload])

    audit_log_data[:backupFileName] = filename

    notice(t("backup.success.upload"))

    render_json
  end
end
