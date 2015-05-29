class TspsController < ApplicationController
  include CertTransformationHelper

  before_filter :verify_get, :only => [
      :tsps_refresh,
      :view_tsp_cert
  ]

  before_filter :verify_post, :only => [
      :add_tsp, 
      :edit_tsp,
      :delete_tsp, 
  ]

  # -- Common GET methods - start ---

  def index
    authorize!(:view_approved_tsas)
  end

  def get_records_count
    render_json_without_messages(:count => ApprovedTsa.count)
  end

  def view_tsp_cert
    authorize!(:edit_approved_tsa)

    validate_params({
      :tsp_id => [],
      :temp_cert_id => []
    })

    cert = get_temp_cert_from_session(params[:temp_cert_id])
    cert ||= ApprovedTsa.find(params[:tsp_id]).cert

    render_cert_dump_and_hash(cert)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def tsps_refresh
    authorize!(:view_approved_tsas)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_tsp_list_column(get_sort_column_no))

    tsps = ApprovedTsa.get_approved_tsas(query_params)
    count = ApprovedTsa.get_approved_tsa_count(searchable)

    result = []

    tsps.each do |each|
      result << {
        :id => each.id,
        :url => each.url,
        :name => each.name,
        :valid_from => format_time(each.valid_from.localtime),
        :valid_to => format_time(each.valid_to.localtime)
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def add_tsp
    authorize!(:add_approved_tsa)

    tsp = ApprovedTsa.new
    tsp.url = params[:url]
    tsp.cert = get_temp_cert_from_session(params[:temp_cert_id])

    logger.info("About to save following TSP: '#{tsp}'")
    tsp.save!

    clear_all_temp_certs_from_session
    render_json
  end

  def edit_tsp
    authorize!(:edit_approved_tsa)

    tsp = ApprovedTsa.find(params[:tsp_id])
    tsp.url = params[:url]

    tsp.save!
    logger.info("TSP edited, result: '#{tsp}'")

    clear_all_temp_certs_from_session
    render_json
  end

  def delete_tsp
    authorize!(:delete_approved_tsa)

    ApprovedTsa.find(params[:tsp_id]).destroy

    render_json
  end

  def upload_tsp_cert
    authorize!(:add_approved_tsa)

    cert_data = upload_cert(params[:tsp_cert], true)
    cert_data["prefix"] = "tsp"

    notice(t("common.cert_imported"))

    upload_success(cert_data, "XROAD_URL_AND_CERT_DIALOG.certUploadCallback")
  rescue RuntimeError => e
    error(e.message)
    upload_error({
      :prefix => "tsp"
    }, "XROAD_URL_AND_CERT_DIALOG.certUploadCallback")
  end

  # -- Specific POST methods - end ---

  private

  def get_tsp_list_column(index)
    case(index)
    when 0
      return "approved_tsas.name"
    when 1
      return "approved_tsas.valid_from"
    when 2
      return "approved_tsas.valid_to"
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

end
