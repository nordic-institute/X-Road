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

java_import Java::ee.ria.xroad.asyncdb.AsyncDB

class AsyncController < ApplicationController

  TIME_FORMAT = "%F %T"

  def index
    authorize!(:view_async_reqs)
  end

  def refresh
    authorize!(:view_async_reqs)

    providers = []

    clear_cached_xroad_ids

    queues = AsyncDB::getMessageQueues
    queues.each do |queue|
      provider = queue.getQueueInfo

      providers << {
        :provider_id => provider.getName.toString,
        :name => provider.getName.toShortString,
        :requests => provider.getRequestCount,
        :send_attempts => provider.getFirstRequestSendCount,
        :last_attempt => format_time(provider.getLastSentTime),
        :last_attempt_result => can?(:view_async_req_last_send_result) ?
          provider.getLastSendResult : nil,
        :next_attempt => format_time(provider.getNextAttempt),
        :last_success => format_time(provider.getLastSuccessTime),
        :last_success_id => provider.getLastSuccessId
      }
      cache_xroad_id(provider.getName)
    end if queues

    render_json(providers)
  end

  def reset
    authorize!(:reset_async_req_send_count)

    provider_id = get_cached_xroad_id(params[:provider_id])
    AsyncDB::getMessageQueue(provider_id).resetCount

    render_json
  end

  def requests
    authorize!(:view_async_reqs)

    provider_id = get_cached_xroad_id(params[:provider_id])
    queue = AsyncDB::getMessageQueue(provider_id)

    requests = []
    queue.getRequests.each do |request|
      requests << {
        :queue_no => request.getOrderNo,
        :id => request.getId,
        :received => format_time(request.getReceivedTime),
        :removed => format_time(request.getRemovedTime),
        :sender => request.getSender.toShortString,
        :user => request.getUser,
        :service => request.getService.toShortString
      }
    end

    render_json(requests)
  end

  def remove
    authorize!(:remove_restore_async_req)

    provider_id = get_cached_xroad_id(params[:provider_id])
    queue = AsyncDB::getMessageQueue(provider_id)
    queue.markAsRemoved(params[:request_id])

    render_json
  end

  def restore
    authorize!(:remove_restore_async_req)

    provider_id = get_cached_xroad_id(params[:provider_id])
    queue = AsyncDB::getMessageQueue(provider_id)
    queue.restore(params[:request_id])

    render_json
  end

  private

  def format_time(time)
    Time.at(time.getTime / 1000).strftime(TIME_FORMAT) if time
  end

  def cache_xroad_id(xroad_id)
    key = xroad_id.toString
    session[:xroad_ids][key] = xroad_id
  end

  def get_cached_xroad_id(key)
    session[:xroad_ids][key]
  end

  def clear_cached_xroad_ids
    session[:xroad_ids] = {}
  end
end
