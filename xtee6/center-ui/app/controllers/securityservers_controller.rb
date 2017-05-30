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

class SecurityserversController < ApplicationController

  include RequestsHelper
  include SecurityserversHelper
  include AuthCertHelper

  before_filter :verify_get, :only => [
      :get_cert_details_by_id,
      :securityservers_refresh,
      :server_security_categories,
      :all_security_categories,
      :clients,
      :auth_certs,
      :management_requests,
      :get_server_by_id,
      :addable_clients]

  before_filter :verify_post, :only => [
      :address_edit,
      :edit_security_categories,
      :delete,
      :import_auth_cert,
      :auth_cert_adding_request,
      :auth_cert_deletion_request]

  upload_callbacks({
    :import_auth_cert => "XROAD_SECURITYSERVER_EDIT.uploadCallbackAuthCert",
  })

  # -- Common GET methods - start ---

  def index
    authorize!(:view_security_servers)
  end

  def get_cert_details_by_id
    authorize!(:view_security_server_details)

    raw_cert = AuthCert.find(params[:certId])
    render_cert_dump_and_hash(raw_cert.cert)
  end

  def get_records_count
    render_json_without_messages(:count => SecurityServer.count)
  end

  def can_see_details
    render_details_visibility(:view_security_server_details)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def securityservers_refresh
    authorize!(:view_security_servers)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_column(get_sort_column_no))

    servers = SecurityServer.get_servers(query_params)
    count = SecurityServer.get_server_count(searchable)

    result = []
    servers.each do |each|
      result << get_full_server_data_as_json(each)
    end

    render_data_table(result, count, params[:sEcho])
  end

  def server_security_categories
    authorize!(:view_security_server_details)

    server  = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    render_json(get_security_categories(server))
  end

  def all_security_categories
    authorize!(:view_security_server_details)

    server  = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    all_categories = SecurityCategory.find(:all)
    result = []

    all_categories.each do |category|
      result << {
        :code => category.code,
        :description => category.description,
        :belongs_to_server => server.security_categories.include?(category)
      }
    end

    render_json(result)
  end

  def clients
    authorize!(:view_security_server_details)

    server  = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    clients = []

    server.security_server_clients.each do |client|
      xroad_member = nil
      subsystem_code = ""

      if client.is_a?(Subsystem)
        subsystem_code = client.subsystem_code
        xroad_member = client.xroad_member
      else
        xroad_member = client
      end

      logger.debug("XROAD member for client: '#{xroad_member.inspect}'")

      clients << {
        :id => xroad_member.id,
        :name => xroad_member.name,
        :member_class => xroad_member.member_class.code,
        :member_code => xroad_member.member_code,
        :subsystem_code => subsystem_code
      }
    end

    render_json(clients.to_a)
  end

  def auth_certs
    authorize!(:view_security_server_details)

    server  = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    auth_certs = []

    server.auth_certs.each do |cert|
      cert_obj = CommonUi::CertUtils.cert_object(cert.cert)

      auth_certs << {
        :id => cert.id,
        :csp => CommonUi::CertUtils.cert_csp(cert_obj),
        :serial_number => cert_obj.serial.to_s,
        :subject => cert_obj.subject.to_s,
        :expires => format_time(cert_obj.not_after)
      }
    end

    render_json(auth_certs)
  end

  def management_requests
    authorize!(:view_security_server_details)

    server = {
      :member_class => params[:ownerClass],
      :member_code => params[:ownerCode],
      :server_code => params[:serverCode]
    }

    query_params = get_list_query_params(
      get_management_requests_column(get_sort_column_no))

    requests = SecurityServer.get_management_requests(server, query_params)
    count = SecurityServer.get_management_requests_count(server)

    result = []
    add_requests_to_result(requests, result)

    render_data_table(result, count, params[:sEcho])
  end

  def get_server_by_id
    authorize!(:view_security_server_details)

    server = SecurityServer.find(params[:serverId])
    render_json(get_full_server_data_as_json(server))
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def address_edit
    audit_log("Edit security server address", audit_log_data = {})

    authorize!(:edit_security_server_address)

    audit_log_data[:serverCode] = params[:serverCode]
    audit_log_data[:ownerCode] = params[:ownerCode]
    audit_log_data[:ownerClass] = params[:ownerClass]
    audit_log_data[:address] = params[:address]

    server_to_update = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    server_to_update.update_attributes!(:address => params[:address])

    render_json
  end

  def edit_security_categories
    authorize!(:edit_security_server_security_category)

    server_data = params[:serverData]
    server = find_server(server_data[:serverCode],
      server_data[:ownerCode], server_data[:ownerClass])

    category_codes = params[:categories]

    new_categories = SecurityCategory.where(:code => category_codes)

    server.update_attributes!(:security_categories => new_categories)

    render_json(get_security_categories(server))
  end

  def delete
    audit_log("Delete security server", audit_log_data = {})

    authorize!(:delete_security_server)

    audit_log_data[:serverCode] = params[:serverCode]
    audit_log_data[:ownerCode] = params[:ownerCode]
    audit_log_data[:ownerClass] = params[:ownerClass]

    server = find_server(params[:serverCode],
      params[:ownerCode], params[:ownerClass])

    SecurityServer.destroy(server)

    render_json
  end

  def import_auth_cert
    authorize!(:add_security_server_auth_cert_reg_request)

    cert_param = get_uploaded_file_param
    validate_auth_cert(cert_param)
    auth_cert_data = upload_cert(cert_param)

    notice(t("common.cert_imported"))

    render_json(auth_cert_data)
  end

  def auth_cert_adding_request
    audit_log("Add authentication certificate for security server",
      audit_log_data = {})

    authorize!(:add_security_server_auth_cert_reg_request)

    audit_log_data[:ownerClass] = params[:ownerClass]
    audit_log_data[:ownerCode] = params[:ownerCode]
    audit_log_data[:serverCode] = params[:serverCode]

    auth_cert_bytes = get_temp_cert_from_session(params[:tempCertId])

    audit_log_data[:certHash] = CommonUi::CertUtils.cert_hash(auth_cert_bytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    server_data = {
        :owner_class => params[:ownerClass],
        :owner_code => params[:ownerCode],
        :server_code => params[:serverCode]
    }

    request = add_auth_cert_reg_request(server_data, auth_cert_bytes)

    notice(t("securityservers.add_auth_cert_adding_request",
        {:security_server => request.security_server}))

    render_json()
  end

  def auth_cert_deletion_request
    audit_log("Delete authentication certificate of security server",
      audit_log_data = {})

    authorize!(:delete_security_server_auth_cert)

    audit_log_data[:ownerClass] = params[:ownerClass]
    audit_log_data[:ownerCode] = params[:ownerCode]
    audit_log_data[:serverCode] = params[:serverCode]

    auth_cert = AuthCert.find(params[:certId])

    audit_log_data[:certHash] =
      CommonUi::CertUtils.cert_hash(auth_cert.cert)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    security_server_id = SecurityServerId.from_parts(
      SystemParameter.instance_identifier,
      params[:ownerClass],
      params[:ownerCode],
      params[:serverCode])

    comment = "Authentication certificate deletion"

    auth_cert_deletion_request = AuthCertDeletionRequest.new(
        :security_server => security_server_id,
        :auth_cert => auth_cert.cert,
        :comments => comment,
        :origin => Request::CENTER)

    auth_cert_deletion_request.register()

    logger.debug("Auth cert deletion request "\
        "'#{auth_cert_deletion_request.inspect}' registered successfully")

    notice(t("securityservers.add_auth_cert_deletion_request",
        {:security_server_id => security_server_id}))

    render_json
  end

  # -- Specific POST methods - end ---

  private

  def get_security_categories(server)
    security_categories = []

    server.security_categories.each do |category|
      security_categories << {
        :code => category.code,
        :description => category.description
      }
    end

    return security_categories
  end

  def find_server(server_code, member_code, member_class_code)
    server =
      SecurityServer.find_server(server_code, member_code, member_class_code)

    throw "Server with server code '#{server_code}', member code '#{member_code}'
        and member class code '#{member_class_code}' not found." unless server

    return server
  end

  def get_column(index)
    case index
    when 0
      return 'server_code'
    when 1
      return 'security_server_clients.name'
    when 2
      return 'member_classes.code'
    when 3
      return 'security_server_clients.member_code'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_management_requests_column(index)
    case index
    when 0
      return 'id'
    when 1
      return 'type'
    when 2
      return 'created_at'
    when 3
      return 'processing_status'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
