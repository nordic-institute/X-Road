require 'json'

class ApplicationController < BaseController
  include MembersHelper
  include KeysHelper

  around_filter :wrap_in_transaction

  def member_classes
    render_json(create_member_class_select(get_all_member_classes))
  end

  # Number of total entries in the list
  def get_records_count
    raise "This must be overridden by explicit controller!"
  end

  private

  def wrap_in_transaction
    ActiveRecord::Base.transaction do
      yield
    end
  end

  def get_sort_column_no
    params[:iSortCol_0].to_i
  end

  def get_list_query_params(sort_column)
    ListQueryParams.new(
      sort_column,
      params[:sSortDir_0],
      params[:iDisplayStart],
      params[:iDisplayLength],
      params[:sSearch])
  end

  def get_advanced_search_params(request_param)
    return nil unless request_param

    raw_params = JSON.parse(request_param)

    AdvancedSearchParams.new(
      {
        :name => raw_params["name"],
        :sdsb_instance => raw_params["sdsbInstance"],
        :member_class => raw_params["memberClass"],
        :member_code => raw_params["memberCode"],
        :subsystem_code => raw_params["subsystem"],
        :object_type => raw_params["objectType"],
        :service_code => raw_params["serviceCode"],
        :central_service_code => raw_params["centralServiceCode"]
      }
    )
  end

  def render_cert_details_by_id(privilege)
    authorize!(privilege)

    raw_cert = get_temp_cert_from_session(params[:certId])
    render_cert_dump_and_hash(raw_cert)
  end

  def render_details_visibility(privilege)
    render_json({:can => can?(privilege)})
  end

  def render_cert_dump_and_hash(raw_cert)
    cert = cert_from_bytes(raw_cert)

    render_json({
      :cert_dump => cert_dump(cert),
      :cert_hash => cert_hash(cert)
    })
  end
end
