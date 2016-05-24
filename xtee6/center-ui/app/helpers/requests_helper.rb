module RequestsHelper

  private

  def add_requests_to_result(requests, result)
    requests.each do |each|
      result << get_request_as_json(each)
    end
  end

  def get_request_as_json(request)
    return {
      :id => request.id,
      :received => format_time(request.created_at.localtime),
      :type => request.type,
      :source => request.origin,

      :server_owner_name => request.server_owner_name,
      :server_owner_class => request.server_owner_class,
      :server_owner_code => request.server_owner_code,
      :server_code => request.server_code,
      :status => request.processing_status,
    }
  end
end
