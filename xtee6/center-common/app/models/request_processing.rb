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

class RequestProcessing < ActiveRecord::Base

  after_save do |rec|
    requests_of_processing = Request.where(:request_processing_id => rec.id)

    requests_of_processing.each do |each|
      each.update_attributes!(:processing_status => rec.status)
    end
  end

  # Processing has been created but not associated with any requests.
  NEW = "NEW"
  # Processing has one request and is waiting for second.
  WAITING = "WAITING"
  # Processing has two requests and is executing the task.
  EXECUTING = "EXECUTING"
  # Processing has to request and is waiting for approval.
  SUBMITTED_FOR_APPROVAL = "SUBMITTED FOR APPROVAL"
  # Processing is approved.
  APPROVED = "APPROVED"
  # Processing is revoked.
  REVOKED = "REVOKED"
  # Processing is declined by the registration officer.
  DECLINED = "DECLINED"

  validates_with Validators::MaxlengthValidator

  has_many :requests,
      :class_name => "RequestWithProcessing",
      :inverse_of => :request_processing,
      :limit => 2

  def initialize()
    super(:status => NEW)
  end

  # Takes as argument a list of requests that matches search criteria for
  # given processing.
  # If there are no requests, returns null.
  # If there is exactly one request, returns processing associated with
  # this request.
  # If there are more requests, throws exception (there should be only one
  # open processing matching the search criteria)
  def self.processing_from_requests(requests)
    if requests.empty?
      # No pending processings.
      nil
    elsif requests.length == 1
      # We have exactly one pending request. Let's use it.
      requests.first.request_processing
    else
      # We have several processings open at the same time. This is error.
      first_request = requests.first
      raise I18n.t("requests.multiple_open_requests",
          :server_id => first_request.security_server.to_s,
          :request_id => first_request.id)
    end
  end

  # Adds given request to this processing.
  def add_request(request)
    Rails.logger.info("add_request(#{request}), status = #{status}")

    if status == NEW # Newly created processing
      # Attach request to processing
      connect_to(request)
      self.status = WAITING
    elsif status == WAITING # We were waiting for second request.
      compare_request_data(self.single_request, request)

      # Attach new request to processing.
      connect_to(request)

      self.status = SUBMITTED_FOR_APPROVAL
    else # not waiting for requests
      raise I18n.t("requests.invalid_processing_state",
          :status => status)
    end

    save!
  end

  def approve
    if self.status != SUBMITTED_FOR_APPROVAL
      raise "Cannot approve request with status '#{self.status}'!"
    end

    logger.info(
        "Approving processing for following requests: '#{requests.to_yaml}'")

    # Determine the request that came from the security server.
    from_server = requests.find {|req| req.origin == Request::SECURITY_SERVER}

    self.status = EXECUTING

    execute(from_server)

    self.status = APPROVED

    save!
  end

  def decline
    if self.status != SUBMITTED_FOR_APPROVAL
      raise "Cannot decline request with status '#{self.status}'!"
    end

    logger.info(
        "Declining processing for following requests: '#{requests.to_yaml}'")

    self.status = DECLINED

    save!
  end

  def compare_request_data(first, second)
    # If we have two requests from the same origin, this is an error.
    if first.origin == second.origin
      raise I18n.t("requests.duplicate_requests",
          :user => first.sec_serv_user,
          :security_server => first.security_server,
          :received => CenterUtils::format_time(first.created_at),
          :id => first.id)
    end
  end

  # Performs the task associated with this request.
  # The parameter is the request that was received from security server.
  def execute(request_from_server)
    throw "This method must be reimplemented in a subclass"
  end

  # Returns the only request associated with this processing
  def single_request
    if requests.empty?
      nil
    elsif requests.size == 1
      requests[0]
    else
      raise I18n.t("requests.more_than_one_requests")
    end
  end

  def connect_to(request)
    request.request_processing = self
    self.requests << request
  end

  # Takes as input a request and returns the other request associated
  # with this processing. Returns null, if this processing only has one
  # request.
  # Note: assumes that the argument request is saved in database.
  def get_other_request(request)
    requests.find {|req| req.id != request.id}
  end
end
