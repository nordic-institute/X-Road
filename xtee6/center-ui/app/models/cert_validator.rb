java_import Java::ee.ria.xroad.common.util.CryptoUtils

class CertValidator
  CERT_MAX_BYTES = 1000000 # 1 MB

  def validate(cert_file, original_filename)
    cert_size = File.size(cert_file)

    if cert_size > CERT_MAX_BYTES
      CommonUi::CertUtils.raise_invalid_cert
    end

    raw_cert = IO.read(cert_file)
    java_cert = CryptoUtils::readCertificate(raw_cert.to_java_bytes())

    validate_specific(java_cert)
  rescue Java::java.lang.Exception
    CommonUi::CertUtils.raise_invalid_cert
  end

  private

  def validate_specific(java_cert)
    # Do nothing for general cert.
  end
end
