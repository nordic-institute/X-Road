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
    :upload_tsp_cert
  ]

  upload_callbacks({
    :upload_tsp_cert => "XROAD_URL_AND_CERT_DIALOG.certUploadCallback",
    :upload_tsp_cert_data => { :prefix => "tsp" }
  })

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
    audit_log("Add timestamping service", audit_log_data = {})

    authorize!(:add_approved_tsa)

    tsp = ApprovedTsa.new
    tsp.url = params[:url]
    tsp.cert = get_temp_cert_from_session(params[:temp_cert_id])

    logger.info("About to save following TSP: '#{tsp}'")
    tsp.save!

    audit_log_data[:tsaId] = tsp.id
    audit_log_data[:tsaName] = tsp.name
    audit_log_data[:tsaUrl] = tsp.url
    audit_log_data[:tsaCertHash] =
      CommonUi::CertUtils.cert_hash(tsp.cert)
    audit_log_data[:tsaCertHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm

    clear_all_temp_certs_from_session

    render_json
  end

  def edit_tsp
    audit_log("Edit timestamping service", audit_log_data = {})

    authorize!(:edit_approved_tsa)

    tsp = ApprovedTsa.find(params[:tsp_id])
    tsp.url = params[:url]

    audit_log_data[:tsaId] = tsp.id
    audit_log_data[:tsaName] = tsp.name
    audit_log_data[:tsaUrl] = tsp.url

    tsp.save!
    logger.info("TSP edited, result: '#{tsp}'")

    clear_all_temp_certs_from_session

    render_json
  end

  def delete_tsp
    audit_log("Delete timestamping service", audit_log_data = {})

    authorize!(:delete_approved_tsa)

    tsp = ApprovedTsa.find(params[:tsp_id])

    audit_log_data[:tsaId] = tsp.id
    audit_log_data[:tsaName] = tsp.name
    audit_log_data[:tsaUrl] = tsp.url

    tsp.destroy

    render_json
  end

  def upload_tsp_cert
    authorize!(:add_approved_tsa)

    cert_data = upload_cert(params[:tsp_cert], true)

    notice(t("common.cert_imported"))

    render_json(cert_data)
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
