module CertTransformationHelper

  include BaseHelper

  private

  def upload_cert(file_param)
    if !file_param || !file_param.original_filename ||
          file_param.original_filename.empty?
      raise t("common.filename_empty")
    end

    cert_obj = cert_object(file_param.read)

    cert_id = add_temp_cert_to_session(cert_obj.to_der)

    get_cert_data(cert_obj, cert_id)
  rescue OpenSSL::OpenSSLError
    raise t("validation.invalid_cert")
  end

  def read_temp_cert(id)
    temp_cert = get_temp_cert_from_session(id)

    if temp_cert == nil
      raise("Temp cert with id '#{id}' not found")
    end

    get_cert_data_from_bytes(temp_cert, id)
  end

  def get_cert_data_from_bytes(raw_cert, cert_id = nil)
    get_cert_data(cert_object(raw_cert), cert_id)
  end

  def get_cert_data(cert_obj, cert_id = nil)
    {
      :csp => cert_csp(cert_obj),
      :serial_number => cert_obj.serial.to_s,
      :subject => cert_obj.subject.to_s,
      :issuer => cert_obj.issuer.to_s,
      :valid_from => format_time(cert_obj.not_before),
      :expires => format_time(cert_obj.not_after),
      :temp_cert_id => cert_id
    }
  end

  def add_temp_cert_to_session(cert_bytes)
    session[:temp_certs] ||= []
    session[:temp_certs] << cert_bytes

    session[:temp_certs].size - 1
  end

  def clear_all_temp_certs_from_session
    session[:temp_certs] = nil
  end

  def get_temp_cert_from_session(cert_id)
    if cert_id == nil || cert_id.to_s.empty?
      return nil
    end

    id = cert_id.is_a?(Integer) ? cert_id :cert_id.to_i

    session[:temp_certs][id]
  end
end
