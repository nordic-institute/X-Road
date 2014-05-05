require 'ruby_cert_helper'

module CertTransformationHelper
  include BaseHelper
  include RubyCertHelper

  private

  # TODO: FIXME: Why does it give OutOfMemoryError when arbitrary stuff loaded?
  def upload_cert(file_param)
    if !file_param || !file_param.original_filename ||
          file_param.original_filename.empty?
      raise t("common.filename_empty")
    end

    cert_obj = cert_from_bytes(file_param.read)

    cert_id = add_temp_cert_to_session(cert_obj.to_der)

    get_cert_data(cert_obj, cert_id)
  rescue OpenSSL::OpenSSLError
    file_param = file_param
    filename = file_param ? file_param.original_filename : ""
    raise t("common.cert_unreadable", {:filename => filename})
  end

  def read_temp_cert(id)
    temp_cert = get_temp_cert_from_session(id)

    if temp_cert == nil
      raise("Temp cert with id '#{id}' not found")
    end

    get_cert_data_from_bytes(temp_cert, id)
  end

  def get_cert_data_from_bytes(raw_cert, cert_id = nil)
    get_cert_data(cert_from_bytes(raw_cert), cert_id)
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

  def create_cert(domain, address)
    key = OpenSSL::PKey::RSA.new 2048
    ca = OpenSSL::X509::Certificate.new
    ca.version = 2
    ca.serial = 1
    ca.subject = OpenSSL::X509::Name.parse("/CN=#{domain}/O=X-tee turvaserver/C=EE")
    ca.issuer = ca.subject
    ca.public_key = key.public_key
    ca.not_before = Time.now
    ca.not_after = ca.not_before + 20 * 365 * 24 * 60 * 60
    extensions = OpenSSL::X509::ExtensionFactory.new
    extensions.subject_certificate = ca
    extensions.issuer_certificate = ca
    ca.add_extension(extensions.create_extension("subjectKeyIdentifier", "hash", true))
    ca.add_extension(extensions.create_extension("subjectAltName", "IP:#{address}", true))
    ca.add_extension(extensions.create_extension("authorityKeyIdentifier", "keyid", false))
    ca.add_extension(extensions.create_extension("issuerAltName", "issuer:copy", true))
    ca.sign(key, OpenSSL::Digest::SHA1.new)
    ca
  end

  def get_fingerprint(bytes)
    Digest::MD5.hexdigest(bytes)
  end

  def export_cert(cert)
    gz = 'certs.tar.gz'
    Dir.mktmpdir { |dir|
      open("#{dir}/cert.cer", "wb") { |f|
        f.print cert.to_der
      }
      open("#{dir}/cert.pem", "wb") { |f|
        f.print cert.to_pem
      }
      system("tar -zcvf /tmp/#{gz} --directory=/tmp -C #{dir} .")
    }
    file = File.open("/tmp/#{gz}", 'rb')
    file.read
  end

end