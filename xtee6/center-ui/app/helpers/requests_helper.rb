module RequestsHelper
  include BaseHelper

  private

  def add_requests_to_result(requests, result)
    requests.each do |each|
      result << get_request_as_json(each)
    end
  end

  def get_request_as_json(request)
    server = request.security_server
    member_class = server.member_class
    member_code = server.member_code

    {
      :id => request.id,
      :received => format_time(request.created_at.localtime),
      :type => request.type,
      :source => request.origin,
      :complementary_id => request.get_complementary_id(),
      :revoking_id => request.get_revoking_request_id(),
      :comments => request.comments,

      :server_owner_name => request.server_owner_name,
      :server_owner_class => member_class,
      :server_owner_code => member_code,
      :server_code => server.server_code,
      :status => request.get_status(),
      :server_address => request.address
    }
  end
end
