require 'ruby_cert_helper'

# Wraps cert generation method of RubyCertHelper for use in model classes
class CertObjectGenerator
  include RubyCertHelper

  def generate(bytes)
    cert_object(bytes)
  end
end
