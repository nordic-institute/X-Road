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

# Validators compatible with ActiveRecord, used by several classes.
module Validators
  STRING_MAX_LENGTH = 255

  class MaxlengthValidator < ActiveModel::Validator 
    def validate(record)
      record.class.columns.each do |each|
        name = each.name.to_sym()
        value = record[name]
        max_length = each.limit

        next if MaxlengthValidator.string_length_valid?(value, max_length)

        record.errors.add(name, I18n.t(
            "activerecord.errors.input_too_long",
            {:max_length => max_length}))
      end
    end

    def self.string_length_valid?(str, max_length = STRING_MAX_LENGTH)
      return max_length == nil ||
          !str.is_a?(String) ||
          str.blank? ||
          str.length <= max_length
    end
  end

  # Slightly customized code from https://gist.github.com/bluemont/2986523
  class UrlValidator < ActiveModel::EachValidator
    def validate_each(record, attribute, value)
      valid = begin
        URI.parse(value).kind_of?(URI::HTTP)
      rescue URI::InvalidURIError
        false
      end
      unless valid
        record.errors[attribute] <<
          I18n.t("activerecord.errors.invalid_url", :url => record.url)
      end
    end
  end
end
