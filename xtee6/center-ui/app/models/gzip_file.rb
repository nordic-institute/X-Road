require 'zlib'

module GzipFile
  ALLOWED_EXTENSIONS = ["gz", "tgz"]
  ALLOWED_CONTENT_TYPES = [
      "application/octet-stream",
      "application/x-compressed-tar",
      "application/x-compressed",
      "application/gnutar",
      "application/x-gzip",
      "application/gzip"
  ]

  # Returns object of type 'UploadedFile::Restrictions'.
  def self.restrictions
    CommonUi::UploadedFile::Restrictions.new(
        ALLOWED_EXTENSIONS, ALLOWED_CONTENT_TYPES)
  end

  class Validator
    def validate(gzip_file, original_filename)
      Zlib::GzipReader.open(gzip_file)
    rescue Zlib::GzipFile::Error
      raise I18n.t("errors.import.invalid_content", {
        :file => original_filename
      })
    end
  end
end
