# Checks status of central components

java_import Java::java.util.ArrayList

java_import Java::ee.cyber.sdsb.commonui.SignerProxy

java_import Java::ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo
java_import Java::ee.cyber.sdsb.signer.protocol.dto.TokenInfo
java_import Java::ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo

class SystemStatusController < ApplicationController
  include BaseHelper

  before_filter :verify_get, :only => [:check_status]

  before_filter :verify_post, :only => [:enter_signing_token_pin]

  def index
  end

  def check_status
    error_messages = []

    check_global_conf_generation_status(error_messages)
    token_pin_required = check_signing_token_status(error_messages)

    render_json_without_messages({
      :error_messages => error_messages,
      :token_pin_required => token_pin_required})
  end

  def enter_signing_token_pin
    authorize!(:enter_signing_token_pin)

    pin = Array.new
    params[:pin].bytes do |b|
      pin << b
    end

    # "0" is for software token.
    SignerProxy::activateToken("0", pin.to_java(:char))
    notice(t("status.signing_token.token_activated"))
    render_json();
  end

  private

  # error_messages is an array collecting error messages
  def check_global_conf_generation_status(error_messages)
    generation_status = GlobalConfGenerationStatus.get()

    logger.info("Global configuration generation status from file: "\
        "'#{generation_status}'")

    last_attempt_time = generation_status[:time]

    if generation_status[:success] == true
      if conf_generated_more_than_minute_ago?(last_attempt_time)
        error_messages << {:text => t("status.global_conf_gen.out_of_date",
            {:time => format_time(last_attempt_time)})}
      end

      return
    end

    if generation_status[:no_status_file] == true
      error_messages << {:text => t("status.global_conf_gen.no_status_file")}
    else
      error_messages << {:text => t("status.global_conf_gen.failure",
          {:time => format_time(last_attempt_time)})}
    end
  end

  def conf_generated_more_than_minute_ago?(generation_time)
    generation_time < Time.now() - 60
  end

  # Returns whether token PIN is required or not.
  def check_signing_token_status(error_messages)
    key_id = SystemParameter.conf_sign_key_id()
    token = get_token_for_active_signing_key(key_id)

    if !token
      error_messages << {:text => "Key with id #{key_id} not found."}
      return
    end

    return handle_token_status(token, error_messages)
  rescue java.lang.Exception => e
    error_messages << {:text => t("status.signing_token.invocation_failed",
        :message => e.message)}
  end

  def get_token_for_active_signing_key(key_id)
    tokens = SignerProxy::getTokens()

    tokens.each do |each_token|
      each_token.key_info.each do |each_key|
        if is_active_signing_key?(each_key, key_id)
          return each_token
        end
      end
    end

    return nil
  end

  def is_active_signing_key?(key, key_id)
    return key.id == key_id && key.usage == KeyUsageInfo::SIGNING
  end

  def handle_token_status(token, error_messages)
    logger.info("Handling status of token with id '#{token.id}'")

    if !token.active
      error_messages << {:text => t("status.signing_token.not_active"),
        :signing_token_pin_required => can_enter_signing_token_pin?()}
      return
    end

    case token.status
    when TokenStatusInfo::OK
      return
    when TokenStatusInfo::USER_PIN_LOCKED
      error_messages << {:text => t("status.signing_token.user_pin_locked")}
    when TokenStatusInfo::USER_PIN_INCORRECT
      error_messages << {:text => t("status.signing_token.user_pin_incorrect"),
        :signing_token_pin_required => can_enter_signing_token_pin?()}
    when TokenStatusInfo::USER_PIN_INVALID
      error_messages << {:text => t("status.signing_token.user_pin_invalid"),
        :signing_token_pin_required => can_enter_signing_token_pin?()}
    when TokenStatusInfo::USER_PIN_EXPIRED
      error_messages << {:text => t("status.signing_token.user_pin_expired"),
        :signing_token_pin_required => can_enter_signing_token_pin?()}
    when TokenStatusInfo::NOT_INITIALIZED
      error_messages << {:text => t("status.signing_token.not_initialized")}
    end
  end

  def can_enter_signing_token_pin?
    return can?(:enter_signing_token_pin)
  end
end
