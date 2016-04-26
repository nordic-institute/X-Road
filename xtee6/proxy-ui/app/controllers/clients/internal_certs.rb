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

java_import Java::ee.ria.xroad.common.conf.serverconf.model.CertificateType

module Clients::InternalCerts

  def self.included(base)
    base.upload_callbacks({
      :internal_cert_add => "INTERNAL_CERTS.uploadCallback"
    })
  end

  def client_internal_certs
    authorize!(:view_client_internal_certs)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_internal_certs(client))
  end

  def internal_cert_details
    authorize!(:view_client_internal_cert_details)

    validate_params({
      :client_id => [:required],
      :hash => [:required]
    })

    client = get_client(params[:client_id])

    dump = nil
    hash = nil

    client.isCert.each do |cert|
      if CommonUi::CertUtils.cert_hash(cert.data) == params[:hash]
        dump = CommonUi::CertUtils.cert_dump(cert.data)
        hash = CommonUi::CertUtils.cert_hash(cert.data)
        break
      end
    end

    render_json({
      :dump => dump,
      :hash => hash
    })
  end

  def internal_cert_add
    audit_log("Add internal TLS certificate", audit_log_data = {})

    authorize!(:add_client_internal_cert)

    validate_params({
      :client_id => [:required],
      :file_upload => [:required]
    })

    client = get_client(params[:client_id])
    uploaded_cert = CommonUi::CertUtils.pem_to_der(params[:file_upload].read)
    uploaded_cert_hash = CommonUi::CertUtils.cert_hash(uploaded_cert)

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:certHash] = uploaded_cert_hash
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm
    audit_log_data[:uploadFileName] = params[:file_upload].original_filename

    client.isCert.each do |cert|
      if CommonUi::CertUtils.cert_hash(cert.data) == uploaded_cert_hash
        raise t('clients.cert_exists')
      end
    end

    cert = CertificateType.new
    cert.data = uploaded_cert.to_java_bytes
    client.isCert.add(cert)

    serverconf_save

    notice(t("common.cert_imported"))

    render_json(read_internal_certs(client))
  end

  def internal_cert_delete
    audit_log("Delete internal TLS certificate", audit_log_data = {})

    authorize!(:delete_client_internal_cert)

    validate_params({
      :client_id => [:required],
      :hash => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:certHash] = params[:hash]
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    deleted_cert = nil
    client.isCert.each do |cert|
      next unless CommonUi::CertUtils.cert_hash(cert.data) == params[:hash]
      deleted_cert = cert
      break
    end

    client.isCert.remove(deleted_cert)

    serverconf_save

    render_json(read_internal_certs(client))
  end

  def proxy_internal_cert
    authorize!(:view_proxy_internal_cert)

    render_json({
      :hash => CommonUi::CertUtils.cert_hash(read_internal_ssl_cert)
    })
  end

  def proxy_internal_cert_export
    authorize!(:export_proxy_internal_cert)

    data = export_cert(read_internal_ssl_cert)

    send_data(data, :filename => "certs.tar.gz")
  end

  def internal_connection_type
    authorize!(:view_client_internal_connection_type)

    validate_params({
      :client_id => [:required]
    })

    connection_type = get_client(params[:client_id]).isAuthentication

    render_json({
      :connection_type => connection_type
    })
  end

  def internal_connection_type_edit
    audit_log("Set connection type for servers in service consumer role",
      audit_log_data = {})

    authorize!(:edit_client_internal_connection_type)

    validate_params({
      :client_id => [:required],
      :connection_type => [:required]
    })

    client = get_client(params[:client_id])

    client.isAuthentication = params[:connection_type]

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:isAuthentication] = isAuthenticationToUIStr(client.isAuthentication)

    serverconf_save

    notice(t("common.changes_saved"))

    render_json({
      :connection_type => nil
    })
  end

  private

  def read_internal_certs(client)
    certs = []

    client.isCert.each do |cert|
      certs << {
        :hash => CommonUi::CertUtils.cert_hash(cert.data)
      }
    end

    certs
  end
end
