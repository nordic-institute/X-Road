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

module CommonUi
  module UploadedFile
    class Restrictions
      attr_reader :allowed_extensions, :allowed_content_types

      def initialize(allowed_extensions, allowed_content_types)
        @allowed_extensions = allowed_extensions
        @allowed_content_types = allowed_content_types
      end
    end

    class Validator
      # Uploaded file must be of type ActionDispatch::Http::UploadedFile
      def initialize(uploaded_file, content_validator, restrictions = nil)
        unless uploaded_file.is_a?(ActionDispatch::Http::UploadedFile)
          raise "Uploaded file must be of type "\
              "'ActionDispatch::Http::UploadedFile', "\
              "but is '#{uploaded_file.class}'"
        end

        @file = uploaded_file
        @allowed_extensions = restrictions.allowed_extensions if restrictions
        @allowed_content_types = restrictions.allowed_content_types if restrictions
        @content_validator = content_validator
      end

      def validate
        validate_extension
        validate_content_type
        validate_content
      end

      private

      def validate_extension
        return unless @allowed_extensions

        filename = @file.original_filename

        @allowed_extensions.each do |each|
          return if filename.end_with?(".#{each}")
        end

        raise I18n.t("backup.error.invalid_extension", {
            :file => filename,
            :extensions => @allowed_extensions.join(", ")
        })
      end

      def validate_content_type
        return unless @allowed_content_types

        content_type = @file.content_type

        @allowed_content_types.each do |each|
          return if each.eql?(content_type)
        end

        raise I18n.t("backup.error.invalid_content_type", {
            :content_type => content_type,
            :allowed_content_types => @allowed_content_types.join(", ")
        })
      end

      def validate_content
        @content_validator.validate(@file.tempfile, @file.original_filename)
      end
    end
  end
end
