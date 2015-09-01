# Responsible of logging last global configuration signing attempt.
class GlobalConfSigningLog

  def self.get_exception_ctx(ex)
    result = "EXCEPTION MESSAGE:\n"
    result << "#{ex.message}\n\n"

    result << "EXCEPTION BACKTRACE:\n"
    result << ex.backtrace.join("\n\t")
    result << "\n"

    return result
  end

  def self.write(writable, file_id, first_line = nil)
    Rails.logger.debug(
        "write_to_log(#{writable}, #{file_id}, #{first_line})")

    log_file_content = ""
    log_file_content << "#{first_line}\n\n" if first_line

    log_file_content << (writable.is_a?(Exception) ?
        get_exception_ctx(writable): writable)

    identifier_mapping_log_file = get_log_file(file_id)

    CommonUi::IOUtils.write(identifier_mapping_log_file, log_file_content)
  end

  private

  def self.get_log_file(file_id)
    return "#{CommonUi::IOUtils.get_log_dir()}/"\
        "xroad_globalconf_signed-#{file_id}"
  end
end
