class RequestsController < ApplicationController
  include RequestsHelper

  before_filter :verify_get, :only => [
      :requests_refresh,
      :get_additional_request_data,
      :get_auth_cert_reg_request_data,
      :get_client_reg_request_data,
      :get_auth_cert_deletion_request_data,
      :get_client_deletion_request_data]

  before_filter :verify_post, :only => [
      :revoke_client_reg_request,
      :revoke_auth_cert_reg_request,
      :approve_reg_request,
      :decline_reg_request]

  # -- Common GET methods - start ---

  def index
    authorize!(:view_management_requests)
  end

  def get_records_count
    render_json_without_messages(:count => Request.count)
  end

  def can_see_details
    render_details_visibility(:view_management_request_details)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def requests_refresh
    authorize!(:view_management_requests)

    searchable = params[:sSearch]

    query_params = get_list_query_params(get_column(get_sort_column_no))

    converted_search_params = params[:sSearchConverted].split(",")

    requests = Request.get_requests(query_params, converted_search_params)
    count = Request.get_request_count(searchable, converted_search_params)

    result = []
    requests.each do |each|
      result << get_request_as_json(each)
    end

    render_data_table(result, count, params[:sEcho])
  end

  def get_additional_request_data
    authorize!(:view_management_request_details)

    request = Request.find(params[:id])

    server_owner_name = XroadMember.get_name(
        request.server_owner_class, request.server_owner_code)

    request.update_server_owner_name(server_owner_name)

    additional_data = {
      :complementary_id => request.get_complementary_id(),
      :revoking_id => request.get_revoking_request_id(),
      :comments => request.comments,
      :server_address => request.address,
      :server_owner_name => server_owner_name
    }

    render_json(additional_data)
  end

  def get_auth_cert_reg_request_data
    authorize!(:view_management_request_details)

    request = AuthCertRegRequest.find(params[:id])
    render_json(get_auth_cert_data(request))
  end

  def get_client_reg_request_data
    authorize!(:view_management_request_details)

    request = ClientRegRequest.find(params[:id])
    render_json(get_client_data(request))
  end

  def get_auth_cert_deletion_request_data
    authorize!(:view_management_request_details)

    request = AuthCertDeletionRequest.find(params[:id])
    render_json(get_auth_cert_data(request))
  end

  def get_client_deletion_request_data
    authorize!(:view_management_request_details)

    request = ClientDeletionRequest.find(params[:id])
    render_json(get_client_data(request))
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def revoke_client_reg_request
    request_id = params[:requestId]
    ClientRegRequest.revoke(request_id)

    notice(t("requests.client_reg_request_revoked",
        {:id => request_id}))

    render_json()
  end

  def revoke_auth_cert_reg_request
    request_id = params[:requestId]
    AuthCertRegRequest.revoke(request_id)

    notice(t("requests.auth_cert_reg_request_revoked",
        {:id => request_id}))

    render_json()
  end

  def approve_reg_request
    request_id = params[:requestId]
    RequestWithProcessing.approve(request_id)

    notice(t("requests.request_approved",
        {:id => request_id}))

    render_json()
  end

  def decline_reg_request
    request_id = params[:requestId]
    RequestWithProcessing.decline(request_id)

    notice(t("requests.request_declined",
        {:id => request_id}))

    render_json()
  end

  # -- Specific POST methods - end ---

  private

  def get_auth_cert_data(request)
    cert = CommonUi::CertUtils.cert_object(request.auth_cert)

    {
      :csp => CommonUi::CertUtils.cert_csp(cert),
      :serial_number => cert.serial.to_s,
      :subject => cert.subject.to_s,
      :expires => format_time(cert.not_after)
    }
  end

  def get_client_data(request)
    client_id = request.sec_serv_user
    member_class = client_id.member_class
    member_code = client_id.member_code

    {
      :member_name => request.server_user_name,
      :member_class => member_class,
      :member_code => member_code,
      :subsystem_code => client_id.subsystem_code
    }
  end

  def get_column(index)
    case index
    when 0
      return 'id'
    when 1
      return 'created_at'
    when 2
      return 'type'
    when 3
      return 'origin'
    when 4
      return 'server_owner_name'
    when 5
      return 'server_owner_class'
    when 6
      return 'server_owner_code'
    when 7
      return 'server_code'
    when 8
      return 'processing_status'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
