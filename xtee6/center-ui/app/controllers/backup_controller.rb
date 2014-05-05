class BackupController < ApplicationController
  include BackupHelper

  def index
    authorize!(:backup_configuration)
  end

  def backup
    authorize!(:backup_configuration)

    backup_and_render()
  end

  def download
    authorize!(:backup_configuration)

    download_and_render(params[:tarfile])
  end
end
