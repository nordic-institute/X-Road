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

# Base class for requests that are associated with processing.
class RequestWithProcessing < Request
  belongs_to :request_processing, :autosave => true, :inverse_of => :requests

  def self.approve(request_id)
    get_request_with_processing(request_id).request_processing.approve()
  end

  def self.decline(request_id)
    get_request_with_processing(request_id).request_processing.decline()
  end

  def register()
    verify_origin()
    verify_request()

    processing = find_processing
    if processing == nil then
      processing = new_processing
    end

    processing.add_request(self)
    Rails.logger.info("Processing: #{processing.status}")
    save!
  end

  # Finds the processing for this request.
  def find_processing
    throw "This method must be reimplemented in a subclass"
  end

  # Creates new instance of processing of the correct class for this request
  def new_processing
    throw "This method must be reimplemented in a subclass"
  end

  def can_revoke?
    request_processing.status.eql?(RequestProcessing::WAITING) &&
        origin.eql?(Request::CENTER)
  end

  def get_revoking_request_id
    throw "This method must be reimplemented in a subclass"
  end

  private

  def self.get_request_with_processing(request_id)
    request = Request.find(request_id)

    unless request
      raise "No request with id '#{request_id}' found"
    end

    unless request.respond_to?(:request_processing)
      raise "Request with id '#{request_id}' does not have processing"
    end

    return request
  end
end
