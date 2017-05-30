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

require 'addressable/uri'
require 'addressable/idna'

module CommonUi
  module ValidationUtils

    class ValidationError < RuntimeError
      attr_reader :param, :validator

      def initialize(param, validator = nil)
        @param = param
        @validator = validator
      end
    end

    private

    DEFAULT_VALIDATORS = {
      :action => [],
      :controller => [],
      :authenticity_token => [],
      :utf8 => [],
      :ignore => [],
      :allowTimeout => []
    }

    def strip_params
      params.each do |key, val|
        if val.respond_to?(:strip)
          params[key] = val.strip
        elsif val.is_a?(Array)
          params[key] = val.collect { |i| i.strip }
        end
      end
    end

    def validate_params(param_validators = {})
      param_validators.merge!(DEFAULT_VALIDATORS)

      check_existence(param_validators, params)
      run_validators(param_validators, params)
    end

    def check_existence(param_validators, params)
      param_validators.each do |param, validators|
        if validators.is_a?(Hash)
          params = (params && params[param].is_a?(Hash)) ? params[param] : nil
          check_existence(validators, params)
          return
        end

        validators.each do |validator|
          if validator == :required && (!params || !params[param] ||
               (params[param].is_a?(String) && params[param].length == 0))
            raise ValidationError.new(param, :required),
              I18n.t('validation.missing_param', :param => param)
          end
        end
      end
    end

    def run_validators(params_validators, params)
      params.each do |param, value|
        unless params_validators.is_a?(Hash) &&
            validators = params_validators[param.to_sym]
          raise ValidationError.new(param),
            I18n.t('validation.unexpected_param', :param => param)
        end

        if value.is_a?(Hash)
          run_validators(validators, value)
        else
          values = value.is_a?(Array) ? value : [value]
          values.each do |value|
            validators.each do |validator|
              AVAILABLE_VALIDATORS[validator].validate(value, param)
            end
          end
        end
      end
    end

    class Validator
      def validate(val, param)
      end
    end

    class RequiredValidator < Validator
      def validate(val, param)
        if !param || !val || (val.is_a?(String) && val.empty?)
          raise ValidationError.new(param, :required),
            I18n.t('validation.missing_param', :param => param)
        end
      end
    end

    class IntValidator < Validator
      def validate(val, param)
        m = val.match(/\A\d+\z/)
        unless m
          raise ValidationError.new(param, :int),
            I18n.t('validation.invalid_int', :param => param, :val => val)
        end
      end
    end

    class TimeoutValidator < Validator
      def validate(val, param)
        m = val.match(/\A\d+\z/)
        unless m
          raise ValidationError.new(param, :timeout),
            I18n.t('validation.invalid_timeout')
        end
      end
    end

    class EmailAddressValidator < Validator
      def validate(val, param)
        return if !val || val.empty?

        emailValid = val =~ /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/
        unless emailValid
          raise ValidationError.new(param, :email),
            I18n.t("validation.invalid_email", :addr => val)
        end
      end
    end

    class FilenameValidator < Validator
      def validate(val, param)
        m = val.match('\A[a-z0-9]*\z')
        unless m
          raise ValidationError.new(param, :filename),
            I18n.t("validation.invalid_filename", :val => val)
        end
      end
    end

    class URLValidator < Validator
      def validate(val, param)
        begin
          url = Addressable::URI.parse(val)
          invalid = !["http", "https"].include?(url.scheme)
        rescue Addressable::URI::InvalidURIError
          invalid = true
        end

        if invalid
          raise ValidationError.new(param, :url),
            I18n.t("validation.invalid_url", :param => param, :val => val)
        end
      end
    end

    class HostValidator < Validator
      def validate(val, param)
        begin
          ascii_val = Addressable::IDNA.to_ascii(val)
          host_regexp = %r{
            ^
            (([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*
            ([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])
            $
          }x

          invalid = !(ascii_val =~ host_regexp)
        rescue
          invalid = true
        end

        if invalid
          raise ValidationError.new(param, :host),
            I18n.t('validation.invalid_host_address')
        end
      end
    end

    AVAILABLE_VALIDATORS = {
      :required => RequiredValidator.new,
      :int => IntValidator.new,
      :timeout => TimeoutValidator.new,
      :email => EmailAddressValidator.new,
      :filename => FilenameValidator.new,
      :url => URLValidator.new,
      :host => HostValidator.new
    }
  end
end
