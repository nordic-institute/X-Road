class BaseLoginController < ApplicationController

  skip_before_filter :check_restore

  def index
    if request.xhr?
      # let's clear the session to keep the servlet container from
      # redirecting us back to irrelevant ajax requests
      session.clear

      if params[:restore]
        render_redirect(root_path, "common.restore_in_progress")
      else
        render_redirect(root_path, "common.session_timed_out")
      end

      return
    end

    if params[:restore]
      error("Restore in progress, try again later")
    end

    if params[:error]
      error("Authentication failed")
    end

    if servlet_request.getRemoteUser
      redirect_to(root_path)
    end
  end

  def logout
    reset_session
    redirect_to(root_path)
  end
end
