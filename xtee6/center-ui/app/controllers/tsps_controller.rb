class TspsController < ApplicationController
  include BaseHelper
  include CertTransformationHelper

  def index
    authorize!(:view_approved_tsps)
  end

  def tsps_refresh
    authorize!(:view_approved_tsps)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_tsp_list_column(get_sort_column_no))

    tsps = ApprovedTsp.get_approved_tsps(query_params)
    count = ApprovedTsp.get_approved_tsp_count(searchable)

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

  def save_new_tsp
    authorize!(:add_approved_tsp)
    tsp = ApprovedTsp.new()
    tsp.url = params[:url]
    tsp.cert = get_temp_cert_from_session(params[:tempCertId])

    logger.info("About to save following TSP: '#{tsp}'")
    tsp.save!

    clear_all_temp_certs_from_session()
    render_json()
  rescue => e
    handle_tsp_saving_errors(e)
  end

  def edit_existing_tsp
    authorize!(:edit_approved_tsp)

    tsp = ApprovedTsp.find(params[:id])
    tsp.url = params[:url]

    tsp.save!
    logger.info("TSP edited, result: '#{tsp}'")

    clear_all_temp_certs_from_session()
    render_json()
  rescue => e
    handle_tsp_saving_errors(e)
  end

  def delete_tsp
    authorize!(:delete_approved_tsp)

    ApprovedTsp.find(params[:id]).destroy

    render_json()
  end

  def upload_tsp_cert
    authorize!(:add_approved_tsp)

    cert_data = upload_cert(params[:upload_tsp_cert_file])
    notice(t("common.cert_imported"))

    upload_success(cert_data, "uploadCallbackTspCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackTspCert")
  end

  def get_cert_details_by_id
    render_cert_details_by_id(:view_approved_tsps)
  end

  def get_existing_tsp_cert_details
    authorize!(:add_approved_tsp)

    tsp = ApprovedTsp.find(params[:tspId])
    cert_details = get_cert_data_from_bytes(tsp.cert)
    render_json(cert_details)
  end

  def get_existing_tsp_cert_dump_and_hash
    authorize!(:edit_approved_tsp)

    tsp = ApprovedTsp.find(params[:tspId])
    render_cert_dump_and_hash(tsp.cert)
  end

  def get_records_count
    render_json(:count => ApprovedTsp.count)
  end

  private

  def handle_tsp_saving_errors(e)
    if !e.is_a?(ActiveRecord::RecordInvalid)
      raise e
    end

    error_messages = e.record.errors.messages

    raise_first_error(error_messages[:url])
    raise_first_error(error_messages[:cert])

    raise e
  end

  def raise_first_error(errors)
    if errors != nil && !errors.empty?
      raise errors[0]
    end
  end

  def get_tsp_list_column(index)
    case(index)
    when 0
      return "approved_tsps.name"
    when 1
      return "approved_tsps.valid_from"
    when 2
      return "approved_tsps.valid_to"
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

end
