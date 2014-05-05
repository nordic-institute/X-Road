class RestoreController < ApplicationController
  include RestoreHelper

  def index
    authorize!(:restore_configuration)
  end
  
  def restore
    authorize!(:restore_configuration)

    restore_and_render(params[:restore_configuration_file])
  end
end