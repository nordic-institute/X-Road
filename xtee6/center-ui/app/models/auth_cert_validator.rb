java_import Java::ee.ria.xroad.common.util.CertUtils

class AuthCertValidator < CertValidator

  def validate_specific(java_cert)
    if !CertUtils::isAuthCert(java_cert)
      raise I18n.t("errors.request.cert_not_auth")
    end
  end
end
