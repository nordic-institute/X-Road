class BackupAndRestoreController < ApplicationController
  include BackupHelper
  include RestoreHelper

  before_filter :verify_get, :only => [
    :index,
    :check_backup_file_existence,
    :refresh_files,
    :download]

  before_filter :verify_post, :only => [
    :backup,
    :restore,
    :delete_file,
    :upload_new]

  skip_around_filter :wrap_in_transaction, :only => [:restore]

  # -- Common GET methods - start ---

  def index
    authorize!(:backup_configuration)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def check_backup_file_existence
    authorize!(:backup_configuration)

    check_backup_file_existence_and_render(params[:fileName])
  end

  def refresh_files
    authorize!(:backup_configuration)

    refresh_and_render()
  end

  def download
    authorize!(:backup_configuration)

    download_and_render(params[:tarfile])
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def backup
    authorize!(:backup_configuration)

    backup_and_render()
  end

  def restore
    authorize!(:restore_configuration)

    ActiveRecord::Base.remove_connection()

    restore_and_render(params[:fileName]) do
      ActiveRecord::Base.establish_connection()
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

  # -- Specific POST methods - end ---
end
