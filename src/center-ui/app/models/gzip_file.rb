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
