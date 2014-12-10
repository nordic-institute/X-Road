require 'zlib'

class GzipFileValidator < UploadedFileValidator
  ALLOWED_EXTENSIONS = ["gz", "tgz"]
  ALLOWED_CONTENT_TYPES = [
      "application/octet-stream",
      "application/x-compressed-tar",
      "application/x-compressed",
      "application/gnutar",
      "application/x-gzip",
      "application/gzip"
  ]

  def validate
    validate_extension()
    validate_content_type()
    validate_content()
  end

  def validate_extension
    filename = @file.original_filename

    ALLOWED_EXTENSIONS.each do |each|
      return if filename.end_with?(".#{each}")
    end

    raise I18n.t("errors.import.invalid_extension", {
        :file => filename,
        :extensions => ALLOWED_EXTENSIONS.join(", ")
    })
  end

  def validate_content_type
    content_type = @file.content_type

    ALLOWED_CONTENT_TYPES.each do |each|
      return if each.eql?(content_type)
    end

    raise I18n.t("errors.import.invalid_content_type", {
        :content_type => content_type,
        :allowed_content_types => ALLOWED_CONTENT_TYPES.join(", ")
    })
  end

  def validate_content
    Zlib::GzipReader.open(@file.tempfile)
  rescue Zlib::GzipFile::Error
    raise I18n.t("errors.import.invalid_content", {
      :file => @file.original_filename
    })
  end
end
