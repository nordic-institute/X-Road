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

class ApprovedCasController < ApplicationController
  include CertTransformationHelper

  before_filter :verify_get, :only => [
    :top_cas,
    :intermediate_cas,
    :ocsp_responders,
    :ca_cert,
    :ocsp_responder_cert
  ]

  before_filter :verify_post, :only => [
    :upload_top_ca_cert,
    :add_top_ca,
    :delete_top_ca,

    :upload_intermediate_ca_cert,
    :delete_intermediate_ca,

    :edit_ca_settings,

    :upload_ocsp_responder_cert,
    :add_ocsp_responder,
    :edit_ocsp_responder,
    :delete_ocsp_responder
  ]

  upload_callbacks({
    :upload_top_ca_cert => "XROAD_APPROVED_CA_DIALOG.certUploadCallback",
    :upload_intermediate_ca_cert => "XROAD_INTERMEDIATE_CA_DIALOG.certUploadCallback",
    :upload_ocsp_responder_cert => "XROAD_URL_AND_CERT_DIALOG.certUploadCallback",
    :upload_ocsp_responder_cert_data => { :prefix => "ocsp_responder" }
  })

  def index
    authorize!(:view_approved_cas)

    @member_classes = MemberClass.get_all_codes
  end

  def top_cas
    authorize!(:view_approved_cas)

    render_top_cas
  end

  def intermediate_cas
    authorize!(:view_approved_ca_details)

    validate_params({
      :ca_id => [:required]
    })

    ca = ApprovedCa.find(params[:ca_id])

    render_intermediate_cas(ca)
  end

  def ocsp_responders
    authorize!(:view_approved_ca_details)

    validate_params({
      :ca_id => [:required],
      :intermediate_ca_id => []
    })

    ca = get_top_or_intermediate_ca(params[:ca_id], params[:intermediate_ca_id])

    render_ocsp_responders(ca)
  end

  def ca_cert
    authorize!(:view_approved_ca_details)

    validate_params({
      :ca_id => [:required],
      :intermediate_ca_id => []
    })

    ca = get_top_or_intermediate_ca(params[:ca_id], params[:intermediate_ca_id])

    render_cert_dump_and_hash(ca.cert)
  end

  def ocsp_responder_cert
    authorize!(:view_approved_ca_details)

    validate_params({
      :ocsp_responder_id => [],
      :temp_cert_id => []
    })

    cert = get_temp_cert_from_session(params[:temp_cert_id])
    cert ||= OcspInfo.find(params[:ocsp_responder_id]).cert

    render_cert_dump_and_hash(cert)
  end

  def upload_top_ca_cert
    authorize!(:add_approved_ca)

    validate_params({
      :ca_cert => [:required]
    })

    cert_data = upload_cert(params[:ca_cert])

    notice(t("common.cert_imported"))

    render_json({
      :temp_cert_id => cert_data[:temp_cert_id]
    })
  end

  def add_top_ca
    audit_log("Add certification service", audit_log_data = {})

    authorize!(:add_approved_ca)

    validate_params({
      :temp_cert_id => [:required],
      :name_extractor_disabled => [],
      :name_extractor_member_class => [],
      :name_extractor_method => []
    })

    ca = ApprovedCa.new
    ca.top_ca = CaInfo.new
    ca.top_ca.cert = get_temp_cert_from_session(params[:temp_cert_id])
    ca.authentication_only = params[:name_extractor_disabled]
    ca.identifier_decoder_member_class = params[:name_extractor_member_class]
    ca.identifier_decoder_method_name = params[:name_extractor_method]

    ca.save!

    audit_log_data[:caId] = ca.id
    audit_log_data[:caCertHash] =
      CommonUi::CertUtils.cert_hash(
        get_temp_cert_from_session(params[:temp_cert_id]))
    audit_log_data[:caCertHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm
    audit_log_data[:authenticationOnly] = ca.authentication_only != nil
    audit_log_data[:nameExtractorMemberClass] = ca.identifier_decoder_member_class
    audit_log_data[:nameExtractorMethod] = ca.identifier_decoder_method_name

    notice(t("approved_cas.approved_ca_added"))

    render_json(top_ca_to_json(ca))
  end

  def delete_top_ca
    audit_log("Delete certification service", audit_log_data = {})

    authorize!(:delete_approved_ca)

    validate_params({
      :ca_id => [:required]
    })

    audit_log_data[:caId] = params[:ca_id]

    ApprovedCa.find(params[:ca_id]).destroy

    render_top_cas
  end

  def upload_intermediate_ca_cert
    audit_log("Add intermediate CA", audit_log_data = {})

    authorize!(:add_approved_ca)

    validate_params({
      :ca_id => [:required],
      :ca_cert => [:required]
    })

    uploaded_bytes = params[:ca_cert].read

    ca = ApprovedCa.find(params[:ca_id])

    intermediate_ca = CaInfo.new
    intermediate_ca.cert = CommonUi::CertUtils.pem_to_der(uploaded_bytes)

    ca.intermediate_cas << intermediate_ca

    audit_log_data[:caId] = params[:ca_id]
    audit_log_data[:intermediateCaId] = intermediate_ca.id
    audit_log_data[:intermediateCaCertHash] =
      CommonUi::CertUtils.cert_hash(intermediate_ca.cert)
    audit_log_data[:intermediateCaCertHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm

    notice(t("approved_cas.intermediate_ca_added"))

    render_json(intermediate_ca_to_json(intermediate_ca))
  end

  def delete_intermediate_ca
    audit_log("Delete intermediate CA", audit_log_data = {})

    authorize!(:add_approved_ca)

    validate_params({
      :intermediate_ca_id => [:required]
    })

    audit_log_data[:intermediateCaId] = params[:intermediate_ca_id]

    CaInfo.destroy(params[:intermediate_ca_id])

    render_json
  end

  def edit_ca_settings
    audit_log("Edit certification service settings", audit_log_data = {})

    authorize!(:edit_approved_ca)

    validate_params({
      :ca_id => [:required],
      :name_extractor_disabled => [],
      :name_extractor_member_class => [],
      :name_extractor_method => []
    })

    ca = ApprovedCa.find(params[:ca_id])

    ca.authentication_only = params[:name_extractor_disabled]
    ca.identifier_decoder_member_class = params[:name_extractor_member_class]
    ca.identifier_decoder_method_name = params[:name_extractor_method]

    audit_log_data[:caId] = params[:ca_id]
    audit_log_data[:authenticationOnly] = ca.authentication_only != nil
    audit_log_data[:nameExtractorMemberClass] = ca.identifier_decoder_member_class
    audit_log_data[:nameExtractorMethod] = ca.identifier_decoder_method_name

    ca.save!

    logger.info("Name extractor edited, result: '#{ca}'")
    notice(t("approved_cas.ca_settings_saved"))

    render_json
  end

  def upload_ocsp_responder_cert
    authorize!(:add_approved_ca)

    validate_params({
      :ocsp_responder_cert => [:required]
    })

    cert_data = upload_cert(params[:ocsp_responder_cert], true)

    notice(t("common.cert_imported"))

    render_json(cert_data)
  end

  def add_ocsp_responder
    if params[:ca_id]
      audit_log("Add OCSP responder of certification service", audit_log_data = {})
    else
      audit_log("Add OCSP responder of intermediate CA", audit_log_data = {})
    end

    authorize!(:add_approved_ca)

    validate_params({
      :ca_id => [],
      :intermediate_ca_id => [],
      :url => [:required, :url],
      :temp_cert_id => []
    })

    ca = get_top_or_intermediate_ca(params[:ca_id], params[:intermediate_ca_id])

    ocsp_responder = OcspInfo.new
    ocsp_responder.url = params[:url]
    ocsp_responder.cert = get_temp_cert_from_session(params[:temp_cert_id])

    ca.ocsp_infos << ocsp_responder

    if params[:ca_id]
      audit_log_data[:caId] = params[:ca_id]
    else
      audit_log_data[:intermediateCaId] = params[:intermediate_ca_id]
    end
    audit_log_data[:ocspId] = ocsp_responder.id
    audit_log_data[:ocspUrl] = ocsp_responder.url
    audit_log_data[:ocspCertHash] =
      CommonUi::CertUtils.cert_hash(ocsp_responder.cert)
    audit_log_data[:ocspCertHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm

    render_json
  end

  def edit_ocsp_responder
    audit_log("Edit OCSP responder", audit_log_data = {})

    authorize!(:edit_approved_ca)

    validate_params({
      :ocsp_responder_id => [:required],
      :url => [:required, :url],
      :temp_cert_id => []
    })

    ocsp_responder = OcspInfo.find(params[:ocsp_responder_id])
    ocsp_responder.url = params[:url]

    ocsp_responder.cert = get_temp_cert_from_session(params[:temp_cert_id])

    audit_log_data[:ocspId] = params[:ocsp_responder_id]
    audit_log_data[:ocspUrl] = params[:url]
    audit_log_data[:ocspCertHash] =
      CommonUi::CertUtils.cert_hash(ocsp_responder.cert)
    audit_log_data[:ocspCertHashAlgorithm] =
      CommonUi::CertUtils.cert_hash_algorithm

    ocsp_responder.save!

    render_json
  end

  def delete_ocsp_responder
    audit_log("Delete OCSP responder", audit_log_data = {})

    authorize!(:edit_approved_ca)

    validate_params({
      :ocsp_responder_id => [:required]
    })

    audit_log_data[:ocspId] = params[:ocsp_responder_id]

    OcspInfo.destroy(params[:ocsp_responder_id])

    render_json
  end

  private

  def render_top_cas
    top_cas = []

    ApprovedCa.find_each do |top_ca|
      top_cas << top_ca_to_json(top_ca)
    end

    render_json(top_cas)
  end

  def top_ca_to_json(top_ca)
    cert_obj = CommonUi::CertUtils.cert_object(top_ca.top_ca.cert)

    {
      :id => top_ca.id,
      :name => top_ca.name,
      :subject => cert_obj.subject.to_s,
      :issuer => cert_obj.issuer.to_s,
      :valid_from => format_time(top_ca.top_ca.valid_from.localtime),
      :valid_to => format_time(top_ca.top_ca.valid_to.localtime),
      :name_extractor_disabled => top_ca.authentication_only,
      :name_extractor_member_class => top_ca.identifier_decoder_member_class,
      :name_extractor_method => top_ca.identifier_decoder_method_name
    }
  end

  def render_ocsp_responders(ca)
    ocsp_responders = []

    ca.ocsp_infos.each do |ocsp_responder|
      ocsp_responders << {
        :id => ocsp_responder.id,
        :url => ocsp_responder.url,
        :has_cert => !ocsp_responder.cert.nil?
      }
    end

    render_json(ocsp_responders)
  end

  def render_intermediate_cas(ca)
    intermediate_cas = []

    ca.intermediate_cas.each do |intermediate_ca|
      intermediate_cas << intermediate_ca_to_json(intermediate_ca)
    end

    render_json(intermediate_cas)
  end

  def intermediate_ca_to_json(intermediate_ca)
    cert_obj = CommonUi::CertUtils.cert_object(intermediate_ca.cert)

    {
      :id => intermediate_ca.id,
      :name => cert_obj.subject.to_s,
      :subject => cert_obj.subject.to_s,
      :issuer => cert_obj.issuer.to_s,
      :valid_from => format_time(cert_obj.not_before.localtime),
      :valid_to => format_time(cert_obj.not_after.localtime),
    }
  end

  def get_top_or_intermediate_ca(approved_ca_id, intermediate_ca_id)
    if intermediate_ca_id
      CaInfo.find(intermediate_ca_id)
    else
      ApprovedCa.find(approved_ca_id).top_ca
    end
  end
end
