java_import Java::ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx

class BackupAndRestoreController < ApplicationController
  include BackupHelper
  include RestoreHelper

  skip_around_filter :transaction, :only => [:restore]
  skip_before_filter :check_conf, :read_server_id, :read_owner_name, :only => [:restore]

  def index
    authorize!(:backup_configuration)
  end

  def backup
    authorize!(:backup_configuration)

    backup_and_render()
  end

  def check_backup_file_existence
    authorize!(:backup_configuration)

    check_backup_file_existence_and_render(params[:fileName])
  end

  def restore
    authorize!(:restore_configuration)

    restore_and_render(params[:fileName]) do
      ServerConfDatabaseCtx.get.closeSessionFactory
    end
  end

  def delete_file
    authorize!(:backup_configuration)

    delete_file_and_render(params[:fileName])
  end

  def upload_new
    authorize!(:backup_configuration)

    upload_new_file_and_render(params[:new_backup_file_upload_field])
  end

  def refresh_files
    authorize!(:backup_configuration)

    refresh_and_render()
  end

  def download
    authorize!(:backup_configuration)

    download_and_render(params[:tarfile])
  end
end
