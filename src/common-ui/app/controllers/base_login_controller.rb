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
