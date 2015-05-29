java_import Java::ee.ria.xroad.common.util.CertUtils

class AuthCertValidator

  def validate(cert_file, original_filename)
    raw_cert = IO.read(cert_file)
    java_cert = CommonUi::CertUtils.pem_to_java_cert(raw_cert)

    if !CertUtils::isAuthCert(java_cert)
      raise I18n.t("errors.request.cert_not_auth")
    end
  end
end
