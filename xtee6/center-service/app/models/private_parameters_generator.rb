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

class PrivateParametersGenerator

  def initialize
    @marshaller = ConfMarshaller.new(
        get_object_factory(), get_root_type_creator())

    Rails.logger.debug(
      "Initialized private parameters generator: #{self.inspect()}")
  end

  def generate
    add_instance_identifier()
    add_configuration_sources()
    add_management_service()
    add_time_stamping_interval()

    return @marshaller.write_to_string()
  end

  private

  def get_object_factory
    return \
      Java::ee.ria.xroad.common.conf.globalconf.privateparameters.ObjectFactory
  end

  def get_root_type_creator
    return Proc.new() do |factory|
      factory.createConf(factory.createPrivateParametersType())
    end
  end

  def add_instance_identifier
    @marshaller.root.instanceIdentifier = SystemParameter.instance_identifier
  end

  def add_configuration_sources
    TrustedAnchor.find_each do |each_anchor|
      anchor_type = @marshaller.factory.createConfigurationAnchorType()
      anchor_type.instanceIdentifier = each_anchor.instance_identifier

      add_source_locations(each_anchor, anchor_type)

      @marshaller.root.getConfigurationAnchor.add(anchor_type)
    end
  end

  def add_source_locations(raw_anchor, anchor_type)
    raw_anchor.anchor_urls.find_each do |each_location|
      source_type = @marshaller.factory.createConfigurationSourceType()
      source_type.downloadURL = each_location.url

      each_location.anchor_url_certs.find_each do |each_cert|
        source_type.getVerificationCert().add(
            each_cert.cert.to_java_bytes())
      end

      anchor_type.getSource().add(source_type)
    end
  end

  def add_management_service
    management_service_type = @marshaller.factory.createManagementServiceType()

    auth_cert_reg_url = SystemParameter.auth_cert_reg_url

    if auth_cert_reg_url.blank?
      raise "No authentication service registration URL present. "\
          "Central server may have not been initialized."
    end

    management_service_type.authCertRegServiceAddress = auth_cert_reg_url

    add_central_server_ssl_cert(management_service_type)

    management_service_provider_class =
        SystemParameter.management_service_provider_class

    if management_service_provider_class.blank?
      raise "Management services provider is not configured"
    end

    provider_id_type = Java::ee.ria.xroad.common.identifier.ClientId.create(
        @marshaller.root.instanceIdentifier,
        management_service_provider_class,
        SystemParameter.management_service_provider_code,
        SystemParameter.management_service_provider_subsystem)

    management_service_type.managementRequestServiceProviderId =
      provider_id_type

    @marshaller.root.managementService = management_service_type
  end

  def add_time_stamping_interval
    @marshaller.root.timeStampingIntervalSeconds =
        SystemParameter.time_stamping_interval_seconds
  end

  def add_central_server_ssl_cert(management_service_type)
    cert_file = get_central_server_ssl_cert_file()
    cert = extract_cert(cert_file)

    management_service_type.authCertRegServiceCert = cert
  end

  def get_central_server_ssl_cert_file
    # XXX Can we assume that it remains like this?
    return "/etc/xroad/ssl/internal.crt"
  end

  def extract_cert(cert_file)
    raw_cert = CommonUi::IOUtils.read(cert_file)
    cert_obj = CommonUi::CertUtils.cert_object(raw_cert)

    return cert_obj.to_der().to_java_bytes()
  end
end
