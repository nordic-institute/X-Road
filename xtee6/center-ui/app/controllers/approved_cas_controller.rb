class ApprovedCasController < ApplicationController
  include CertTransformationHelper

  before_filter :verify_get, :only => [
      :refresh,
      :get_intermediate_ca_temp_cert_details,
      :get_existing_intermediate_ca_cert_details,
      :get_existing_intermediate_ca_cert_dump_and_hash,
      :get_top_ca_cert_details,
      :get_top_ca_cert_dump_and_hash,
      :get_name_extractor_data,
      :get_ocsp_infos,
      :get_existing_ocsp_cert_details,
      :get_intermediate_cas]

  before_filter :verify_post, :only => [
      :upload_top_ca_cert,
      :upload_ocsp_responder_cert,
      :upload_intermediate_ca_cert,
      :save_new_approved_ca,
      :edit_existing_approved_ca,
      :delete_approved_ca]

  # -- Common GET methods - start ---

  def index
    authorize!(:view_approved_cas)
  end

  def get_cert_details_by_id
    render_temp_cert_details_by_id(:view_approved_cas)
  end

  def get_records_count
    authorize!(:view_approved_cas)

    render_json_without_messages(:count => ApprovedCa.count)
  end

  def can_see_details
    render_details_visibility(:view_approved_ca_details)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def refresh
    authorize!(:view_approved_cas)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_approved_ca_list_column(get_sort_column_no))

    approved_cas = ApprovedCa.get_approved_cas(query_params)
    count = ApprovedCa.get_approved_cas_count(searchable)

    result = []

    approved_cas.each do |each|
      top_ca = each.top_ca
      result << {
        :id => each.id,
        :top_ca_id => each.top_ca.id,
        :trusted_certification_service => each.name,
        :valid_from => format_time(top_ca.valid_from.localtime),
        :valid_to => format_time(top_ca.valid_to.localtime)
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  def get_intermediate_ca_temp_cert_details
    authorize!(:add_approved_ca)

    render_json(read_temp_cert(params[:intermediateCaTempCertId]))
  end

  def get_existing_intermediate_ca_cert_details
    authorize!(:view_approved_ca_details)

    intermediate_ca = CaInfo.find(params[:intermediateCaId])
    cert_details = get_cert_data_from_bytes(intermediate_ca.cert)
    render_json(cert_details)
  end

  def get_existing_intermediate_ca_cert_dump_and_hash
    authorize!(:view_approved_ca_details)

    intermediate_ca = CaInfo.find(params[:intermediateCaId])
    render_cert_dump_and_hash(intermediate_ca.cert)
  end

  def get_top_ca_cert_details
    authorize!(:view_approved_ca_details)

    cert_bytes = get_top_ca_cert_bytes()
    cert_details = get_cert_data_from_bytes(cert_bytes)
    render_json(cert_details)
  end

  def get_top_ca_cert_dump_and_hash
    authorize!(:view_approved_ca_details)

    cert_bytes = get_top_ca_cert_bytes()
    render_cert_dump_and_hash(cert_bytes)
  end

  def get_name_extractor_data
    authorize!(:view_approved_ca_details)

    approved_ca = ApprovedCa.find(params[:caId])
    render_json({
      :auth_only => approved_ca.authentication_only,
      :member_class => approved_ca.identifier_decoder_member_class,
      :method_name => approved_ca.identifier_decoder_method_name
    })
  end

  def get_ocsp_infos
    authorize!(:view_approved_ca_details)

    ca = CaInfo.find(params[:caId])
    ocsp_infos = []

    ca.ocsp_infos.each do |each|
      ocsp_infos << {
        :id => each.id,
        :url => each.url,
        :has_cert => each.cert != nil
      }
    end

    render_json(ocsp_infos)
  end

  def get_existing_ocsp_cert_details
    authorize!(:view_approved_ca_details)

    ocsp_info = OcspInfo.find(params[:ocspInfoId])
    render_cert_dump_and_hash(ocsp_info.cert)
  end

  def get_intermediate_cas
    authorize!(:view_approved_ca_details)

    approved_ca = ApprovedCa.find(params[:caId])
    intermediate_cas = []

    approved_ca.intermediate_cas.each do |each|
      cert_data = get_cert_data_from_bytes(each.cert)

      intermediate_cas << {
        :id => each.id,
        :intermediate_ca => cert_data[:subject],
        :valid_from => cert_data[:valid_from],
        :valid_to => cert_data[:expires]
      }
    end

    render_json(intermediate_cas)
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def upload_top_ca_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_top_ca_cert_file])
    notice(t("common.cert_imported"))

    upload_success(cert_data, "SDSB_CA_COMMON.uploadCallbackApprovedCaAddTopCaCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "SDSB_CA_COMMON.uploadCallbackApprovedCaAddTopCaCert")
  end

  def upload_ocsp_responder_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_ocsp_responder_cert_file])

    notice(t("common.cert_imported"))

    upload_success(cert_data,
        "SDSB_CA_COMMON.uploadCallbackApprovedCaAddOcspResponderCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "SDSB_CA_COMMON.uploadCallbackApprovedCaAddOcspResponderCert")
  end

  def upload_intermediate_ca_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_intermediate_ca_cert_file])

    notice(t("common.cert_imported"))

    upload_success(cert_data,
        "SDSB_CA_COMMON.uploadCallbackApprovedCaAddIntermediateCaCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil,
        "SDSB_CA_COMMON.uploadCallbackApprovedCaAddIntermediateCaCert")
  end

  def save_new_approved_ca
    authorize!(:add_approved_ca)
    raw_name_extractor = JSON.parse(params[:nameExtractor])
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = raw_name_extractor["authOnly"]
    approved_ca.identifier_decoder_member_class = raw_name_extractor["memberClass"]
    approved_ca.identifier_decoder_method_name = raw_name_extractor["extractorMethod"]

    top_ca_ocsp_infos = get_new_ocsp_infos(params[:topCaOcspInfos])
    approved_ca.top_ca = get_ca(params[:topCaTempCertId], top_ca_ocsp_infos)

    approved_ca.intermediate_cas = get_new_intermediate_cas(params[:intermediateCas])

    logger.info("About to save following approved_ca: '#{approved_ca}'")
    approved_ca.save!

    clear_all_temp_certs_from_session()
    render_json()
  rescue SdsbArgumentError => e
    handle_sdsb_argument_error(e)
  end

  def edit_existing_approved_ca
    authorize!(:edit_approved_ca)

    approved_ca = edit_approved_ca()

    edit_ocsp_infos(approved_ca)

    edit_intermediate_cas(approved_ca)

    approved_ca.save!
    logger.info("ApprovedCa edited, result: '#{approved_ca}'")

    clear_all_temp_certs_from_session()
    render_json()
  rescue SdsbArgumentError => e
    handle_sdsb_argument_error(e)
  end

  def delete_approved_ca
    authorize!(:delete_approved_ca)

    ApprovedCa.find(params[:id]).destroy

    render_json()
  end

  # -- Specific POST methods - end ---

  private

  def get_ca(temp_cert_id, ocsp_infos = [])
    result = CaInfo.new()
    result.cert = get_temp_cert_from_session(temp_cert_id)
    result.ocsp_infos = ocsp_infos

    return result
  end

  # Parameter may be either String representing JSON array request parameter or
  # same string parsed into raw JSON array
  def get_new_ocsp_infos(raw_infos)
    raw_ocsp_infos = get_json_array(raw_infos)
    result = []

    raw_ocsp_infos.each do |each|
      temp_ocsp_cert_id = each["ocspTempCertId"]

      url = each["url"].strip()
      logger.debug("OCSP - url: '#{url}',"\
          " temp cert id: '#{temp_ocsp_cert_id}'")

      ocsp = OcspInfo.new()
      ocsp.url = url
      ocsp.cert = get_temp_cert_from_session(temp_ocsp_cert_id)
      result << ocsp
    end

    result
  end

  def get_new_intermediate_cas(intermediate_cas_param)
    raw_intermediate_cas = get_json_array(intermediate_cas_param)
    result = []
    raw_intermediate_cas.each do |each|
      temp_ca_cert_id = each["intermediateCaTempCertId"]
      ocsp_infos = get_new_ocsp_infos(each["ocspInfos"])
      result << get_ca(temp_ca_cert_id, ocsp_infos)
    end

    result
  end

  # Editing functions - start

  # Edits approved CA and returns modified one.
  def edit_approved_ca
    approved_ca_param = JSON.parse(params[:ca])
    approved_ca = ApprovedCa.find(approved_ca_param["id"])
    approved_ca.authentication_only = approved_ca_param["authOnly"]
    approved_ca.identifier_decoder_member_class =
        approved_ca_param["nameExtractorMemberClass"].strip()
    approved_ca.identifier_decoder_method_name =
        approved_ca_param["nameExtractorMethodName"].strip()

    return approved_ca
  end

  def edit_ocsp_infos(approved_ca)
    raw_ocsp_param = params[:ocspInfos]

    if !raw_ocsp_param || raw_ocsp_param.empty?
      return
    end

    ocsp_infos_param = JSON.parse(raw_ocsp_param)

    delete_ocsp_infos(ocsp_infos_param["delete"])

    update_existing_ocsp_infos(ocsp_infos_param["update"])

    add_new_ocsp_infos(ocsp_infos_param["new"])
  end

  def delete_ocsp_infos(deletable_ocsp_info_ids)
    return if deletable_ocsp_info_ids == nil || 
        !deletable_ocsp_info_ids.is_a?(Array)

    deletable_ocsp_info_ids.each do |each|
      OcspInfo.destroy(each)
    end
  end

  def update_existing_ocsp_infos(ocsp_infos_to_update)
    return if ocsp_infos_to_update == nil || !ocsp_infos_to_update.is_a?(Array)

    ocsp_infos_to_update.each do |each|
      ocsp_info_to_update = OcspInfo.find(each["id"])
      ocsp_info_to_update.url = each["url"].strip()
      update_ocsp_cert(ocsp_info_to_update, each)

      ocsp_info_to_update.save!
    end
  end

  def add_new_ocsp_infos(ocsp_infos_to_add)
    return if ocsp_infos_to_add == nil || !ocsp_infos_to_add.is_a?(Array)

    ocsp_infos_to_add.each do |each|
      new_ocsp_info = OcspInfo.new()
      new_ocsp_info.ca_info_id = each["caInfoId"]
      new_ocsp_info.url = each["url"].strip()
      update_ocsp_cert(new_ocsp_info, each)

      new_ocsp_info.save!
    end
  end

  def edit_intermediate_cas(approved_ca)
    raw_intermediate_ca_param = params[:intermediateCas]

    if !raw_intermediate_ca_param || raw_intermediate_ca_param.empty?
      return
    end

    intermediate_cas_param = JSON.parse(raw_intermediate_ca_param)

    delete_intermediate_cas(intermediate_cas_param["delete"])
    update_existing_intermediate_cas(intermediate_cas_param["update"])
    approved_ca.intermediate_cas << get_new_intermediate_cas(
      intermediate_cas_param["new"])
  end

  def delete_intermediate_cas(deletable_intermediate_ca_ids)
    return if deletable_intermediate_ca_ids == nil || 
        !deletable_intermediate_ca_ids.is_a?(Array)

    deletable_intermediate_ca_ids.each do |each|
      intermediate_ca = CaInfo.find(each)

      # So that we won't accidentally remove topCA
      if intermediate_ca.top_ca_id != nil
        raise "Cannot delete top_ca (id=#{each}) without approved CA"
      end

      intermediate_ca.destroy()
    end
  end

  def update_existing_intermediate_cas(intermediate_cas_to_update)
    return if intermediate_cas_to_update == nil ||
      !intermediate_cas_to_update.is_a?(Array)

    intermediate_cas_to_update.each do |each|
      intermediate_ca_to_update = CaInfo.find(each["id"])
      update_intermediate_ca_cert(intermediate_ca_to_update, each)

      intermediate_ca_to_update.save!
    end
  end

  def update_ocsp_cert(ocsp, ocsp_info)
    update_cert(ocsp, ocsp_info["ocspTempCertId"])
  end

  def update_intermediate_ca_cert(intermediate_ca, intermediate_ca_info)
    update_cert(intermediate_ca,
        intermediate_ca_info["intermediateCaTempCertId"])
  end

  def update_cert(object_to_update, temp_cert_id)
    return if temp_cert_id == nil || temp_cert_id.to_s.empty?

    new_cert_bytes = get_temp_cert_from_session(temp_cert_id)
    object_to_update.cert = new_cert_bytes
  end

  # Editing functions - end

  # JSON utilities - start

  def get_json_array(raw_data)
    return [] if raw_data == nil || raw_data.empty?

    if !raw_data.is_a?(Array) && !raw_data.is_a?(String)
      raise "JSON string or JSON array must be given to this method."
    end

    raw_data.is_a?(Array) ? raw_data : parse_json_array(raw_data)
  end

  def parse_json_array(array_param_string)
    logger.debug("parse_json_array(#{array_param_string})")

    array_contains_data?(array_param_string) ? 
        JSON.parse(array_param_string) : []
  end

  # JSON utilities - end

  def get_top_ca_cert_bytes
    approved_ca = ApprovedCa.find(params[:caId])
    return approved_ca.top_ca.cert
  end

  def get_approved_ca_list_column(index)
    case(index)
    when 0
      return "approved_cas.name"
    when 1
      return "ca_infos.valid_from"
    when 2
      return "ca_infos.valid_to"
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def array_contains_data?(array_param_string)
    array_param_string && 
        !array_param_string.empty? &&
        !array_param_string.eql?("null")
  end

  def handle_sdsb_argument_error(e)
    logger.error(e)

    case(e.type)
    when :no_name_extractor_method
      raise t('approved_cas.add.no_name_extractor')
    when :no_ca_cert
      raise t('approved_cas.edit.no_ca_cert')
    end

    raise e
  end
end
