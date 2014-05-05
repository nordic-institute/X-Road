require 'ruby_cert_helper'
require 'keys_helper'
require 'set'

class SecurityserversController < ApplicationController
  include RubyCertHelper
  include KeysHelper
  include RequestsHelper
  include SecurityserversHelper
  include AuthCertHelper

  def index
    authorize!(:view_security_servers)
  end

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

  def address_edit
    authorize!(:edit_security_server_address)

    server_to_update = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    server_to_update.update_attributes!(:address => params[:address])

    render_json({})
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

  def edit_security_categories
    authorize!(:edit_security_server_security_category)

    server_data = params[:serverData]
    server  = find_server(server_data[:serverCode],
        server_data[:ownerCode], server_data[:ownerClass])

    category_codes = params[:categories]

    new_categories = SecurityCategory.where(:code => category_codes)

    server.update_attributes!(:security_categories => new_categories)

    render_json(get_security_categories(server))
  end

  def clients
    authorize!(:view_security_server_details)

    server  = find_server(params[:serverCode],
        params[:ownerCode], params[:ownerClass])

    # TODO: Is it normal that without Set duplicate subsystems may occur?
    clients = Set.new

    server.security_server_clients.each do |client|
      sdsb_member = nil
      subsystem_code = ""

      if client.is_a?(Subsystem)
        subsystem_code = client.subsystem_code
        sdsb_member = client.sdsb_member
      else
        sdsb_member = client
      end

      logger.debug("SDSB member for client: '#{sdsb_member.inspect}'")

      clients << {
        :id => sdsb_member.id,
        :name => sdsb_member.name,
        :member_class => sdsb_member.member_class.code,
        :member_code => sdsb_member.member_code,
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
      cert_obj = cert_from_bytes(cert.certificate)

      auth_certs << {
        :id => cert.id,
        :csp => cert_csp(cert_obj),
        :serial_number => cert_obj.serial.to_s,
        :subject => cert_obj.subject.to_s,
        :expires => format_time(cert_obj.not_after)
      }
    end

    render_json(auth_certs)
  end

  def management_requests
    authorize!(:view_security_server_details)

    result = []

    requests = Request.joins(:security_server).where(
      :identifiers => {
        :member_class => params[:ownerClass],
        :member_code => params[:ownerCode],
        :server_code => params[:serverCode]
      }
    )

    add_requests_to_result(requests, result)

    render_json(result)
  end

  def delete
    authorize!(:delete_security_server)

    server  = find_server(params[:serverCode],
            params[:ownerCode], params[:ownerClass])

    SecurityServer.destroy(server)

    render_json({})
  end

  def import_auth_cert
    authorize!(:add_security_server_auth_cert_reg_request)

    auth_cert_data = upload_cert(params[:server_auth_cert_file])

    notice(t("common.cert_imported"))

    upload_success(auth_cert_data, "uploadCallbackAuthCert")

  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackAuthCert")
  end

  def auth_cert_adding_request
    authorize!(:add_security_server_auth_cert_reg_request)

    server_data = {
        :owner_class => params[:ownerClass],
        :owner_code => params[:ownerCode],
        :server_code => params[:serverCode]
    }

    request = add_auth_cert_reg_request(server_data, params[:tempCertId])

    notice(t("securityservers.add_auth_cert_adding_request",
        {:security_server => request.security_server}))
    render :partial => "application/messages"
  end

  def cancel_new_auth_cert_request
    authorize!(:add_security_server_auth_cert_reg_request)

    render :partial => "application/messages"
  end

  def auth_cert_deletion_request
    authorize!(:delete_security_server_auth_cert)

    auth_cert = AuthCert.find(params[:certId])

    security_server_id = SecurityServerId.from_parts(
      SystemParameter.sdsb_instance,
      params[:ownerClass],
      params[:ownerCode],
      params[:serverCode])

    comment = "Auth cert deletion from security server "\
        "'#{security_server_id.to_s}'"

    auth_cert_deletion_request = AuthCertDeletionRequest.new(
        :security_server => security_server_id,
        :auth_cert => auth_cert.certificate,
        :comments => comment,
        :origin => Request::CENTER)

    auth_cert_deletion_request.register()

    logger.debug("Auth cert deletion request "\
        "'#{auth_cert_deletion_request.inspect}' registered successfully")

    notice(t("securityservers.add_auth_cert_deletion_request",
        {:security_server_id => security_server_id}))
    render :partial => "application/messages"
  end

  def get_server_by_id
    authorize!(:view_security_server_details)

    server = SecurityServer.find(params[:serverId])
    render_json(get_full_server_data_as_json(server))
  end

  def get_cert_details_by_id
    authorize!(:view_security_server_details)

    raw_cert = AuthCert.find(params[:certId])
    cert = cert_from_bytes(raw_cert.certificate)

    render_json({
      :cert_dump => cert_dump(cert),
      :cert_hash => cert_hash(cert)
    })
  end

  def can_see_details
    render_details_visibility(:view_security_server_details)
  end

  def get_records_count
    render_json(:count => SecurityServer.count)
  end

  private

  def get_security_categories(server)
    security_categories = []

    server.security_categories.each do |category|
      security_categories << {
        :code => category.code,
        :description => category.description
      }
    end

    security_categories
  end

  def find_server(server_code, member_code, member_class_code)
    server =
      SecurityServer.find_server(server_code, member_code, member_class_code)

    # TODO: Maybe just render error?
    throw "Server with server code '#{server_code}', member code '#{member_code}'
        and member class code '#{member_class_code}' not found." unless server

    server
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
end
