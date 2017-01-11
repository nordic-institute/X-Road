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

class OcspInfo < ActiveRecord::Base
  include Validators

  attr_accessible :ca_info_id, :cert, :url
  validates :url, :url => true

  validates_with Validators::MaxlengthValidator

  before_destroy do |ocsp|
    logger.info("Deleting OCSP: '#{ocsp}'")
  end

  before_destroy do |ocsp|
    logger.info("Deleting OCSP: '#{ocsp}'")
  end

  def to_s
    cert_size = cert != nil ? cert.size : 0
    "OcspInfo(id: '#{id}', url: '#{url}', cert length '#{cert_size}')"
  end

  def self.validate_urls(urls)
    urls.each do |each|
      ocsp_info = OcspInfo.new(:url => each)

      next if ocsp_info.valid?

      error_msg = ocsp_info.errors.full_messages.join(", ")
      raise (error_msg)
    end
  end
end
