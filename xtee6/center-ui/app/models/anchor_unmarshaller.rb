java_import Java::ee.cyber.sdsb.common.conf.globalconf.ConfigurationAnchor

class AnchorUnmarshaller
  def initialize(anchor_file)
    @anchor_file = anchor_file
    @anchor = ConfigurationAnchor.new(anchor_file)
  end

  def get_instance_identifier
    return @anchor.getInstanceIdentifier()
  end

  def get_generated_at
    anchor_generated_at = @anchor.getGeneratedAt()
    return nil if anchor_generated_at == nil

    return Time.at(anchor_generated_at.getTime() / 1000)
  end

  def get_xml
    # To guarantee same hash, we use the file that was uploaded.
    return CommonUi::IOUtils.read(@anchor_file)
  end

  # Returns ActiveRecord AnchorUrl objects.
  def get_anchor_urls
    result = []

    @anchor.getLocations().each do |each|
      result << AnchorUrl.new(
          :url => each.getDownloadURL(),
          :anchor_url_certs => get_certs(each))
    end

    return result
  end

  private

  def get_certs(location)
    result = []

    location.getVerificationCerts().each do |each|
      result << AnchorUrlCert.new(
          :certificate => String.from_java_bytes(each))
    end

    return result
  end
end
