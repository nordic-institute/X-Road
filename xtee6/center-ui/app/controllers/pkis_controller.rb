require 'keys_helper'

class PkisController < ApplicationController
  include BaseHelper
  include CertTransformationHelper

  def index
    authorize!(:view_approved_cas)
  end

  def pkis_refresh
    authorize!(:view_approved_cas)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_pki_list_column(get_sort_column_no))

    pkis = Pki.get_pkis(query_params)
    count = Pki.get_pkis_count(searchable)

    result = []

    pkis.each do |each|
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

  def upload_top_ca_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_top_ca_cert_file])
    notice(t("common.cert_imported"))

    upload_success(cert_data, "uploadCallbackPkiAddTopCaCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackPkiAddTopCaCert")
  end

  def upload_ocsp_responder_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_ocsp_responder_cert_file])

    notice(t("common.cert_imported"))

    upload_success(cert_data, "uploadCallbackPkiAddOcspResponderCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackPkiAddOcspResponderCert")
  end

  def upload_intermediate_ca_cert
    authorize!(:add_approved_ca)

    cert_data = upload_cert(params[:upload_intermediate_ca_cert_file])

    notice(t("common.cert_imported"))

    upload_success(cert_data, "uploadCallbackPkiAddIntermediateCaCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackPkiAddIntermediateCaCert")
  end

  def get_cert_details_by_id
    render_cert_details_by_id(:view_approved_cas)
  end

  def save_new_pki
    authorize!(:add_approved_ca)
    raw_name_extractor = JSON.parse(params[:nameExtractor])
    pki = Pki.new()
    pki.authentication_only = raw_name_extractor["authOnly"]
    pki.name_extractor_member_class = raw_name_extractor["memberClass"]
    pki.name_extractor_method_name = raw_name_extractor["extractorMethod"]

    top_ca_ocsp_infos = get_new_ocsp_infos(params[:topCaOcspInfos])
    pki.top_ca = get_ca(params[:topCaTempCertId], top_ca_ocsp_infos)

    pki.intermediate_cas = get_new_intermediate_cas(params[:intermediateCas])

    logger.info("About to save following pki: '#{pki}'")
    pki.save!

    clear_all_temp_certs_from_session()
    render_json()
  rescue SdsbArgumentError => e
    handle_sdsb_argument_error(e)
  end

  def edit_existing_pki
    authorize!(:edit_approved_ca)

    pki = edit_pki()

    edit_ocsp_infos(pki)

    edit_intermediate_cas(pki)

    pki.save!
    logger.info("Pki edited, result: '#{pki}'")

    clear_all_temp_certs_from_session()
    render_json()
  rescue SdsbArgumentError => e
    handle_sdsb_argument_error(e)
  end

  def delete_pki
    authorize!(:delete_approved_ca)

    Pki.find(params[:id]).destroy

    render_json()
  end

  def get_intermediate_ca_temp_cert_details
    authorize!(:add_approved_ca)

    render_json(read_temp_cert(params[:intermediateCaTempCertId]))
  end

  def get_existing_intermediate_ca_cert_details
    authorize!(:edit_approved_ca)

    intermediate_ca = CaInfo.find(params[:intermediateCaId])
    cert_details = get_cert_data_from_bytes(intermediate_ca.cert)
    render_json(cert_details)
  end

  def get_existing_intermediate_ca_cert_dump_and_hash
    authorize!(:edit_approved_ca)

    intermediate_ca = CaInfo.find(params[:intermediateCaId])
    render_cert_dump_and_hash(intermediate_ca.cert)
  end

  def get_top_ca_cert_details
    authorize!(:edit_approved_ca)

    cert_bytes = get_top_ca_cert_bytes()
    cert_details = get_cert_data_from_bytes(cert_bytes)
    render_json(cert_details)
  end

  def get_top_ca_cert_dump_and_hash
    authorize!(:edit_approved_ca)

    cert_bytes = get_top_ca_cert_bytes()
    render_cert_dump_and_hash(cert_bytes)
  end

  def get_name_extractor_data
    authorize!(:edit_approved_ca)

    pki = Pki.find(params[:pkiId])
    render_json({
      :auth_only => pki.authentication_only,
      :member_class => pki.name_extractor_member_class,
      :method_name => pki.name_extractor_method_name
    })
  end

  def get_ocsp_infos
    authorize!(:edit_approved_ca)

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
    authorize!(:edit_approved_ca)

    ocsp_info = OcspInfo.find(params[:ocspInfoId])
    render_cert_dump_and_hash(ocsp_info.cert)
  end

  def get_intermediate_cas
    authorize!(:edit_approved_ca)

    pki = Pki.find(params[:pkiId])
    intermediate_cas = []

    pki.intermediate_cas.each do |each|
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

  def set_editing_pki
    session[:editing_pki] = true
    render_json()
  end

  def clear_editing_pki
    session[:editing_pki] = nil
    render_json()
  end

  def get_records_count
    render_json(:count => Pki.count)
  end

  private

  def get_ca(temp_cert_id, ocsp_infos = [])
    result = CaInfo.new()
    result.cert = get_temp_cert_from_session(temp_cert_id)
    result.ocsp_infos = ocsp_infos

    result
  end

  # Parameter may be either String representing JSON array request parameter or
  # same string parsed into raw JSON array
  def get_new_ocsp_infos(raw_infos)
    raw_ocsp_infos = get_json_array(raw_infos)
    result = []

    raw_ocsp_infos.each do |each|
      temp_ocsp_cert_id = each["ocspTempCertId"]

      url = each["url"]
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

  # Edits PKI and returns modified one.
  def edit_pki
    pki_param = JSON.parse(params[:pki])
    pki = Pki.find(pki_param["id"])
    pki.authentication_only = pki_param["authOnly"]
    pki.name_extractor_member_class = pki_param["nameExtractorMemberClass"]
    pki.name_extractor_method_name = pki_param["nameExtractorMethodName"]

    pki
  end

  def edit_ocsp_infos(pki)
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
      ocsp_info_to_update.url = each["url"]
      update_ocsp_cert(ocsp_info_to_update, each)

      ocsp_info_to_update.save!
    end
  end

  def add_new_ocsp_infos(ocsp_infos_to_add)
    return if ocsp_infos_to_add == nil || !ocsp_infos_to_add.is_a?(Array)

    ocsp_infos_to_add.each do |each|
      new_ocsp_info = OcspInfo.new()
      new_ocsp_info.ca_info_id = each["caInfoId"]
      new_ocsp_info.url = each["url"]
      update_ocsp_cert(new_ocsp_info, each)

      new_ocsp_info.save!
    end
  end

  def edit_intermediate_cas(pki)
    raw_intermediate_ca_param = params[:intermediateCas]

    if !raw_intermediate_ca_param || raw_intermediate_ca_param.empty?
      return
    end

    intermediate_cas_param = JSON.parse(raw_intermediate_ca_param)

    delete_intermediate_cas(intermediate_cas_param["delete"])
    update_existing_intermediate_cas(intermediate_cas_param["update"])
    pki.intermediate_cas << get_new_intermediate_cas(
      intermediate_cas_param["new"])
  end

  def delete_intermediate_cas(deletable_intermediate_ca_ids)
    return if deletable_intermediate_ca_ids == nil || 
        !deletable_intermediate_ca_ids.is_a?(Array)

    deletable_intermediate_ca_ids.each do |each|
      intermediate_ca = CaInfo.find(each)

      # So that we won't accidentally remove topCA
      if intermediate_ca.top_ca_id != nil
        raise "Cannot delete top_ca (id=#{each}) without PKI"
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
    pki = Pki.find(params[:pkiId])
    pki.top_ca.cert
  end

  def get_pki_list_column(index)
    case(index)
    when 0
      return "pkis.name"
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
      raise t('pki.add.no_name_extractor')
    when :no_ca_cert
      raise t('pki.edit.no_ca_cert')
    end

    raise e
  end
end
