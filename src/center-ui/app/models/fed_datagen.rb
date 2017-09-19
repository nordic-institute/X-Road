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

# Can be used for generating configuration anchor data into database.
#
# HOW TO USE:
# * Move this class to some place visible to Rails OR load later via console;
# * Connect to the database You want to modify using Rails console
#     (You might want to use port forwarding to accomplish this)
# * Create data You desire calling one of public functions, for example:
#     FedAnchors.generate_anchors_for_fed0()
class FedDatagen
  FED0_INTERNAL_KEY_ID = "3EC4C6276A75A4C108361D246481E9E506E37C97"
  FED0_EXTERNAL_KEY_ID = "621EFB351E234A5352FD10BF7D06947EF432FA58"
  FED2_INTERNAL_KEY_ID = "D277C5BDE506198CBC0B98CA90BD60D9E550DE77"
  FED2_EXTERNAL_KEY_ID = "352C7E08C9461CBFF6F96DD7A200E71AC19B0C6F"

  def self.generate_fed_data_for_fed0
    generate_sources_for_fed0()
    generate_anchors_for_fed0()
  end

  def self.generate_fed_data_for_fed2
    generate_sources_for_fed2()
    generate_anchors_for_fed2()
  end

  def self.generate_sources_for_fed0
    ConfigurationSource.destroy_all()

    internal_signing_key = ConfigurationSigningKey.new(
        :key_identifier => FED0_INTERNAL_KEY_ID,
        :cert => read_fed0_internal_source_cert()
    )

    internal_source = ConfigurationSource.new(
      :source_type => ConfigurationSource::SOURCE_TYPE_INTERNAL,
      :configuration_signing_keys => [internal_signing_key],
      :active_key => internal_signing_key
    )

    internal_source.save!

    # ---
    external_signing_key = ConfigurationSigningKey.new(
        :key_identifier => FED0_EXTERNAL_KEY_ID,
        :cert => read_fed0_external_source_cert()
    )

    external_source = ConfigurationSource.new(
      :source_type => ConfigurationSource::SOURCE_TYPE_EXTERNAL,
      :configuration_signing_keys => [external_signing_key],
      :active_key => external_signing_key
    )

    external_source.save!
  end

  def self.generate_sources_for_fed2
    ConfigurationSource.destroy_all()

    internal_signing_key = ConfigurationSigningKey.new(
        :key_identifier => FED2_INTERNAL_KEY_ID,
        :cert => read_fed2_internal_source_cert()
    )

    internal_source = ConfigurationSource.new(
      :source_type => ConfigurationSource::SOURCE_TYPE_INTERNAL,
      :configuration_signing_keys => [internal_signing_key],
      :active_key => internal_signing_key
    )

    internal_source.save!

    # ---
    external_signing_key = ConfigurationSigningKey.new(
        :key_identifier => FED2_EXTERNAL_KEY_ID,
        :cert => read_fed2_external_source_cert()
    )

    external_source = ConfigurationSource.new(
      :source_type => ConfigurationSource::SOURCE_TYPE_EXTERNAL,
      :configuration_signing_keys => [external_signing_key],
      :active_key => external_signing_key
    )

    external_source.save!
  end

  # Generates anchor into iks2-fed0 pointing to iks2-fed2
  def self.generate_anchors_for_fed0
    TrustedAnchor.destroy_all()

    url_cert = AnchorUrlCert.new(
        :cert => read_fed2_external_source_cert())

    anchor_url = AnchorUrl.new(
        :url => "http://iks2-fed2.cyber.ee/externalconf",
        :anchor_url_certs => [url_cert])

    anchor = TrustedAnchor.new(
        :friendly_name => "Friendly name for the BBB anchor",
        :instance_identifier => "BBB",
        :anchor_urls => [anchor_url])

    anchor.save!
  end

  # Generates anchor into iks2-fed2 pointing to iks2-fed0
  def self.generate_anchors_for_fed2
    TrustedAnchor.destroy_all()

    url_cert = AnchorUrlCert.new(
        :cert => read_fed0_external_source_cert())

    anchor_url = AnchorUrl.new(
        :url => "http://iks2-fed0.cyber.ee/externalconf",
        :anchor_url_certs => [url_cert])

    anchor = TrustedAnchor.new(
        :friendly_name => "Friendly name for the AAA anchor",
        :instance_identifier => "AAA",
        :anchor_urls => [anchor_url])

    anchor.save!
  end

  private

  def self.read_fed0_internal_source_cert
    return read_file("cert_sign_internal_AAA.pem")
  end

  def self.read_fed0_external_source_cert
    return read_file("cert_sign_external_AAA.pem")
  end

  def self.read_fed2_internal_source_cert
    return read_file("cert_sign_internal_BBB.pem")
  end

  def self.read_fed2_external_source_cert
    return read_file("cert_sign_external_BBB.pem")
  end

  def self.read_file(filename)
    filepath = "#{ENV["XROAD_HOME"]}/center-ui/test/resources/#{filename}"

    file_content = File.open(filepath, "rb") { |file| file.read() }

    cert_obj = CertObjectGenerator.new().generate(file_content)

    return cert_obj.to_der()
  end
end
