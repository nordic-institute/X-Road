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

  class ExceptionWithOutput < StandardError
    attr_reader :stderr

    def initialize(message, stderr = [])
      super(message)

      @stderr = stderr
    end
  end

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
      raise ExceptionWithOutput.new(t("backup.index.error", {:code => exitcode}), output)
    end

    render_json({
      :stderr => output
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
      raise ExceptionWithOutput.new(
        t("restore.error.script_failed", {:conf_file => params[:fileName]}), output)
    end

    render_json({
      :stderr => output
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
