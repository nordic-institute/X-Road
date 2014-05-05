# Base class for requests that are associated with processing.
class RequestWithProcessing < Request
  belongs_to :request_processing, :autosave => true, :inverse_of => :requests

  # TODO: change table name and everything that goes with it in Schema!
  # validates :request_processing_id, :present => true

  def register()
    verify_origin()
    verify_request()

    processing = find_processing
    if processing == nil then
      processing = new_processing
    end

    processing.add_request(self)
    puts "Processing: #{processing.status}"
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

  def can_cancel?
    request_processing.status.eql?(RequestProcessing::WAITING) &&
        origin.eql?(Request::CENTER)
  end

  def get_canceling_request_id
    throw "This method must be reimplemented in a subclass"
  end
end
