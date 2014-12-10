require "common-ui/backup_utils"

class BaseBackupController < ApplicationController

  before_filter :verify_get, :only => [
    :index,
    :check_backup_file_existence,
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

  def check_backup_file_existence
    authorize!(:backup_configuration)

    exists = !CommonUi::BackupUtils.backup_files[params[:fileName]].nil?

    render_json(:exists => exists)
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
    authorize!(:backup_configuration)

    exitcode, output = CommonUi::BackupUtils.backup

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
    authorize!(:restore_configuration)

    before_restore

    exitcode, output = CommonUi::BackupUtils.restore(params[:fileName]) do
      after_restore
    end

    if exitcode == 0
      notice(t("restore.success", {:conf_file => params[:fileName]}))
    else
      error(t("restore.error.script_failed", {:conf_file => params[:fileName]}))
    end

    render_json({
      :console_output => output
    })
  end

  def delete_file
    authorize!(:backup_configuration)

    CommonUi::BackupUtils.delete_file(params[:fileName])

    notice(t("backup.success.delete"))
  rescue Exception => e
    logger.error(e)
    error(t("backup.error.delete", {:reason => e.message}))
  ensure
    render_json
  end

  def upload_new
    authorize!(:backup_configuration)

    CommonUi::BackupUtils.upload_new_file(params[:new_backup_file_upload_field])

    notice(t("backup.success.upload"))
    upload_success(nil, "confBackup.uploadCallback")
  rescue Exception => e
    error(e.message)
    upload_error(nil, "confBackup.uploadCallback")
  end
end
