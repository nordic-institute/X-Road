class BaseLoginController < ApplicationController

  def index
    if request.xhr?
      # let's clear the session to keep the servlet container from
      # redirecting us back to irrelevant ajax requests
      session.clear
      render_redirect
      return
    end

    if servlet_request.getRemoteUser
      redirect_to root_path
    end

    if params[:error]
      error "Authentication failed"
    end
  end

  def logout
    reset_session
    redirect_to root_path
  end
end
