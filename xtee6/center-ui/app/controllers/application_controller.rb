require 'json'

class ApplicationController < BaseController
  include MembersHelper

  around_filter :wrap_in_transaction

  rescue_from ActiveRecord::StatementInvalid, 
      ActiveRecord::ConnectionNotEstablished do
    ActiveRecord::Base.establish_connection
    error(t("common.db_error"))
    render_error_response()
  end

  before_filter :read_locale
  before_filter :read_server_id, :except => [:menu]
  before_filter :verify_get, :only => [
      :index,
      :get_cert_details_by_id,
      :get_records_count,
      :can_see_details]

  def member_classes
    render_json(create_member_class_select(get_all_member_classes))
  end

  # Number of total entries in the list
  def get_records_count
    raise "This must be overridden by explicit controller!"
  end

  def set_locale
    unless I18n.available_locales.include?(params[:locale].to_sym)
      raise "invalid locale"
    end

    ui_user = UiUser.find_by_username(current_user.name)

    unless ui_user
      ui_user = UiUser.new
      ui_user.username = current_user.name
    end

    ui_user.locale = params[:locale]
    ui_user.save!

    render :nothing => true
  end

  private

  def wrap_in_transaction
    ActiveRecord::Base.transaction do
      yield
    end
  end

  def verify_get
    return if request.get?

    raise "Expected HTTP method 'GET', but was: '#{request.method}'"
  end

  def verify_post
    return if request.post?

    raise "Expected HTTP method 'POST', but was: '#{request.method}'"
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

    return AdvancedSearchParams.new(
      {
        :name => raw_params["name"],
        :sdsb_instance => raw_params["sdsbInstance"],
        :member_class => raw_params["memberClass"],
        :member_code => raw_params["memberCode"],
        :subsystem_code => raw_params["subsystem"],
        :object_type => raw_params["objectType"],
        :service_code => raw_params["serviceCode"],
        :central_service_code => raw_params["centralServiceCode"],
        :server_code => raw_params["serverCode"]
      }
    )
  end

  def render_temp_cert_details_by_id(privilege)
    authorize!(privilege)

    raw_cert = get_temp_cert_from_session(params[:certId])
    render_cert_dump_and_hash(raw_cert)
  end

  def render_details_visibility(privilege)
    render_json_without_messages({:can => can?(privilege)})
  end

  def render_cert_dump_and_hash(raw_cert)
    render_json({
      :cert_dump => cert_dump(raw_cert),
      :cert_hash => cert_hash(raw_cert)
    })
  end

  def read_locale
    return unless current_user

    ui_user = UiUser.find_by_username(current_user.name)
    I18n.locale = ui_user.locale if ui_user
  end

  def read_server_id
    return @server_id if @server_id

    @server_id = CentralServerId.new(SystemParameter.sdsb_instance())
  end

  class CentralServerId
    def initialize(sdsb_instance)
      @sdsb_instance = sdsb_instance
    end

    # To make it compatible with common-ui
    def toShortString
      return @sdsb_instance
    end
  end
end
