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

java_import Java::ee.ria.xroad.common.signer.SignerHelper

# Responsible of creating signed directory to the point when its content is
# stored into database.
class DirectorySigner
  # content_builder must be of type DirectoryContentBuilder.
  def initialize(sign_key_id, sign_algo_id, content_builder)
    if sign_key_id.blank?
      raise "Cannot sign without signing key"
    end

    if sign_algo_id.blank?
      raise "Signature algorithm id system parameter not set in database"
    end

    @sign_key_id = sign_key_id
    @sign_algo_id = sign_algo_id
    @content_builder = content_builder
  end

  def sign
    Rails.logger.debug("Signing directory with signing key '#{@sign_key_id}' "\
        "and signing algorithm '#{@sign_algo_id}'")

    data_boundary = Utils.create_mime_boundary()
    data = @content_builder.build(data_boundary)

    signature = SignerHelper.sign(@sign_key_id, @sign_algo_id, data)

    {
      :data => data,
      :data_boundary => data_boundary,
      :signature => signature,
      :sig_algo_id => @sign_algo_id
    }
  end
end
