require 'ruby_cert_helper'

class RequestsController < ApplicationController
  include RequestsHelper
  include RubyCertHelper

  def index
    authorize!(:view_management_requests)
  end

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

  def can_see_details
    render_details_visibility(:view_management_request_details)
  end

  def cancel_client_reg_request
    request_id = params[:requestId]
    ClientRegRequest.cancel(request_id)

    notice(t("management_requests.client_reg_request_canceled",
        {:id => request_id}))

    render_json()
  end

  def cancel_auth_cert_reg_request
    request_id = params[:requestId]
    AuthCertRegRequest.cancel(request_id)

    notice(t("management_requests.auth_cert_reg_request_canceled",
        {:id => request_id}))

    render_json()
  end

  def get_records_count
    render_json(:count => Request.count)
  end

  private

  def get_auth_cert_data(request)
    cert = cert_from_bytes(request.auth_cert)

    {
      :csp => cert_csp(cert),
      :serial_number => cert.serial.to_s,
      :subject => cert.subject.to_s,
      :expires => format_time(cert.not_after)
    }
  end

  def get_client_data(request)
    client_id = request.sec_serv_user
    member_class = client_id.member_class
    member_code = client_id.member_code

    saved_member = SdsbMember.find_by_code(member_class, member_code)
    member_name = saved_member ? saved_member.name : ""

    {
      :member_name => member_name,
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
      return 'security_server_clients.name'
    when 5
      return 'identifiers.member_class'
    when 6
      return 'identifiers.member_code'
    when 7
      return 'identifiers.server_code'
    when 8
      return 'request_processings.status'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
