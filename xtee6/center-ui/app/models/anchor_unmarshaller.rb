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

java_import Java::ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor

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
          :cert => String.from_java_bytes(each))
    end

    return result
  end
end
