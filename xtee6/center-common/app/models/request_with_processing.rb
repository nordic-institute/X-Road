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
