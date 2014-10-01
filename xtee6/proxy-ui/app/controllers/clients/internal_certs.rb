java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.CertificateType

module Clients::InternalCerts

  include ValidationHelper

  def client_internal_certs
    authorize!(:view_client_internal_certs)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    render_json(read_internal_certs(client))
  end

  def internal_cert_details
    authorize!(:view_client_internal_cert_details)

    validate_params({
      :client_id => [RequiredValidator.new],
      :hash => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    dump = nil
    hash = nil

    client.isCert.each do |cert|
      if cert_hash(cert.data) == params[:hash]
        dump = cert_dump(cert.data)
        hash = cert_hash(cert.data)
        break
      end
    end

    render_json({
      :dump => dump,
      :hash => hash
    })
  end

  def internal_cert_add
    authorize!(:add_client_internal_cert)

    validate_params({
      :client_id => [RequiredValidator.new],
      :cert => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])
    uploaded_cert = pem_to_der(params[:cert].read)

    client.isCert.each do |cert|
      next unless cert_hash(cert.data) == cert_hash(uploaded_cert)

      error(t('clients.cert_exists'))
      upload_error(nil, "internalCertAddCallback")
      return
    end

    cert = CertificateType.new
    cert.data = uploaded_cert.to_java_bytes
    client.isCert.add(cert)

    serverconf_save

    upload_success(read_internal_certs(client), "internalCertAddCallback")
  end

  def internal_cert_delete
    authorize!(:delete_client_internal_cert)

    validate_params({
      :client_id => [RequiredValidator.new],
      :hash => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    deleted_cert = nil

    client.isCert.each do |cert|
      next unless cert_hash(cert.data) == params[:hash]
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
      :hash => cert_hash(read_internal_ssl_cert)
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
      :client_id => [RequiredValidator.new]
    })

    connection_type = get_client(params[:client_id]).isAuthentication

    render_json({
      :connection_type => connection_type
    })
  end

  def internal_connection_type_edit
    authorize!(:edit_client_internal_connection_type)
    
    validate_params({
      :client_id => [RequiredValidator.new],
      :connection_type => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    client.isAuthentication = params[:connection_type]

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
        :hash => cert_hash(cert.data)
      }
    end

    certs
  end
end
