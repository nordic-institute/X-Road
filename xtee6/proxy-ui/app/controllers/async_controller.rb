java_import Java::ee.cyber.sdsb.asyncdb.AsyncDB

class AsyncController < ApplicationController

  TIME_FORMAT = "%F %T"

  def index
    authorize!(:view_async_reqs)
  end

  def refresh
    authorize!(:view_async_reqs)

    providers = []

    clear_cached_sdsb_ids

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
      cache_sdsb_id(provider.getName)
    end if queues

    render_json(providers)
  end

  def reset
    authorize!(:reset_async_req_send_count)

    provider_id = get_cached_sdsb_id(params[:provider_id])
    AsyncDB::getMessageQueue(provider_id).resetCount

    render_json
  end

  def requests
    authorize!(:view_async_reqs)

    provider_id = get_cached_sdsb_id(params[:provider_id])
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

    provider_id = get_cached_sdsb_id(params[:provider_id])
    queue = AsyncDB::getMessageQueue(provider_id)
    queue.markAsRemoved(params[:request_id])

    render_json
  end

  def restore
    authorize!(:remove_restore_async_req)

    provider_id = get_cached_sdsb_id(params[:provider_id])
    queue = AsyncDB::getMessageQueue(provider_id)
    queue.restore(params[:request_id])

    render_json
  end

  private

  def format_time(time)
    Time.at(time.getTime / 1000).strftime(TIME_FORMAT) if time
  end

  def cache_sdsb_id(sdsb_id)
    key = sdsb_id.toString
    session[:sdsb_ids][key] = sdsb_id
  end

  def get_cached_sdsb_id(key)
    session[:sdsb_ids][key]
  end

  def clear_cached_sdsb_ids
    session[:sdsb_ids] = {}
  end
end
