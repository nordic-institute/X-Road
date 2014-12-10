class DistributedSignedFiles < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator

  FILE_ID_SIGNED = "signed"

  def self.get_exception_ctx(ex)
    result = "EXCEPTION MESSAGE:\n"
    result << "#{ex.message}\n\n"

    result << "EXCEPTION BACKTRACE:\n"
    result << ex.backtrace.join("\n\t")
    result << "\n"

    return result
  end

  def self.write_signed_files_log(writable)
    write_to_log(writable, "distributed_signed_files", FILE_ID_SIGNED)
  end

  private

  def self.get_log_file(table_name, file_id)
    return "#{CommonUi::IOUtils.get_log_dir()}/"\
        "sdsb_distributed_files-#{table_name}-#{file_id}"
  end

  def self.write_to_log(writable, table_name, file_id, first_line = nil)
    logger.debug(
        "write_to_log(#{writable}, #{table_name}, #{file_id}, #{first_line})")

    log_file_content = ""
    log_file_content << "#{first_line}\n\n" if first_line

    log_file_content << (writable.is_a?(Exception) ?
        get_exception_ctx(writable): writable)

    identifier_mapping_log_file = get_log_file(table_name, file_id)

    CommonUi::IOUtils.write(identifier_mapping_log_file, log_file_content)
  end
end
