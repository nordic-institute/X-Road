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
