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

class CaInfo < ActiveRecord::Base
  has_many :ocsp_infos,
    :dependent => :destroy,
    :autosave => true

  before_validation :validate_cert_presence

  before_save do |ca|
    cert_obj = CommonUi::CertUtils.cert_object(ca.cert)
    ca.valid_from = cert_obj.not_before
    ca.valid_to = cert_obj.not_after

    logger.info("Saving CA: '#{ca}'")
  end

  before_destroy do |ca|
    logger.info("Deleting CA: '#{ca}'")
  end

  def validate_cert_presence
    if !self.cert || self.cert.empty?
      raise XroadArgumentError.new(:no_ca_cert)
    end
  end

  def to_s
    "CaInfo(validFrom: '#{self.valid_from}', validTo: '#{self.valid_to}', "\
    "id: '#{self.id}', ocspInfos: [#{self.ocsp_infos.join(', ')}]"
  end
end
