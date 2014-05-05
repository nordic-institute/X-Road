require "validation_helper"

# Validators compatible with ActiveRecord, used by several classes. Wraps some 
# validators in ValidationHelper.
module Validators
  # XXX For some reason I could not override PresenceValidator of ActiveModel 
  # properly.
  class PresentValidator < ActiveModel::EachValidator
    include ValidationHelper

    def validate_each(record, attribute, value)
      # XXX to_s used so that integers also could be validated
      RequiredValidator.new.validate(value.to_s, attribute)
    end
  end

  # Uses functionality of built-in UniquenessValidator, but raises error if
  # validation fails.
  class UniqueValidator < ActiveRecord::Validations::UniquenessValidator

    def validate_each(record, attribute, value)
      super

      if !record.errors.messages.empty?
        raise I18n.t("validation.already_exists",
            :attribute => attribute, :value => value)
      end
    end
  end

  class EmailValidator < ActiveModel::EachValidator
    include ValidationHelper

    def validate_each(record, attribute, value)
      EmailAddressValidator.new.validate(value, attribute)
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
        # TODO: format error message better
        record.errors[attribute] <<
          I18n.t("errors.invalid_url", :url => record.url)
      end
    end
  end

end