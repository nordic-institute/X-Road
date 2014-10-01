class UploadedFileValidator
  # Uploaded file must be of type ActionDispatch::Http::UploadedFile
  def initialize(uploaded_file)
    @file = uploaded_file
  end

  def validate
    raise "This must be overridden by subclass."
  end

  private

  def read_file
    return @file.read()
  ensure
    @file.rewind()
  end
end
