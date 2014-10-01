# This table will hold files to be distributed by the Central. It contains
# file name and file data (as blob) pairs.
class DistributedFiles < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :file_name, :uniqueness => true

  FILE_ID_GLOBALCONF = "globalconf"
  FILE_ID_IDENTIFIERMAPPING = "identifiermapping"
  FILE_ID_SIGNED = "signed"

  ALLOWED_FILE_IDS = [
      FILE_ID_GLOBALCONF,
      FILE_ID_IDENTIFIERMAPPING,
      FILE_ID_SIGNED
  ].freeze()

  TABLE_NAME_DISTRIBUTED = "distributed_files"
  TABLE_NAME_SIGNED = "distributed_signed_files"

  ALLOWED_TABLE_NAMES = [
      TABLE_NAME_DISTRIBUTED,
      TABLE_NAME_SIGNED
  ].freeze()

  def self.get_files(can_import_v5_data = false)
    result = []
    result << get_distributed_file(FILE_ID_GLOBALCONF)

    if can_import_v5_data
      result << get_distributed_file(FILE_ID_IDENTIFIERMAPPING)
    end

    result << get_first_signed_file()
    return result
  end

  def self.add_file(file_name, file_data, original_filename = nil)
    DistributedFiles.where(:file_name => file_name).destroy_all
    DistributedFiles.create!(
        :file_name => file_name,
        :file_data => file_data,
        :original_filename_last_successful => original_filename)
  end

  def self.mark_failed_last_identifier_mapping_upload(original_filename)
    file_name = get_file_name(FILE_ID_IDENTIFIERMAPPING)

    DistributedFiles.where(:file_name => file_name).
        limit(1).update_all(
            :last_successful => false,
            :updated_at => Time.now,
            :original_filename_last_failed => original_filename)
  end

  # Returns log file content as array of file lines.
  def self.get_log_file_content(table_name, file_id)
    logger.debug(
        "DistributedFiles.get_log_file_content(#{table_name}, #{file_id})")
    verify_file_args(table_name, file_id)

    log_file = get_log_file(table_name, file_id)

    return [] if !File.exist?(log_file)

    return SdsbFileUtils.read_to_array(log_file)
  end

  def self.log_file_exists?(table_name, file_id)
    return File.exist?(get_log_file(table_name, file_id))
  end

  # Gets name and content of the file.
  def self.get_file(table_name, file_id)
    logger.debug(
        "DistributedFiles.get_file_content(#{table_name}, #{file_id})")
    verify_file_args(table_name, file_id)

    result = {}

    if TABLE_NAME_DISTRIBUTED == table_name
      file_entry = DistributedFiles.
          where(:file_name => get_file_name(file_id)).first()
      file_name = file_entry.original_filename_last_successful.blank? ?
          file_entry.file_name : file_entry.original_filename_last_successful

      result[:name] = file_name
      result[:content] = file_entry.file_data
    else
      file_entry = DistributedSignedFiles.first()
      result[:name] = "signed"
      result[:content] = file_entry.data
    end

    return result
  end

  def self.get_exception_ctx(ex)
    result = "EXCEPTION MESSAGE:\n"
    result << "#{ex.message}\n\n"

    result << "EXCEPTION BACKTRACE:\n"
    result << ex.backtrace.join("\n\t")
    result << "\n"

    return result
  end

  def self.write_identifier_mapping_log(writable, first_line = nil)
    write_to_log(
        writable, TABLE_NAME_DISTRIBUTED, FILE_ID_IDENTIFIERMAPPING, first_line)
  end

  def self.write_signed_files_log(writable)
    write_to_log(writable, TABLE_NAME_SIGNED, FILE_ID_SIGNED)
  end

  private

  def self.verify_file_args(table_name, file_id)
    if !ALLOWED_FILE_IDS.include?(file_id)
      raise "Invalid file id '#{file_id}', allowed ones are: "\
          "'#{ALLOWED_FILE_IDS.join(", ")}'"
    end

    if !ALLOWED_TABLE_NAMES.include?(table_name)
      raise "Invalid table name '#{table_name}', allowed ones are: "\
          "'#{ALLOWED_TABLE_NAMES.join(", ")}'"
    end
  end

  def self.get_distributed_file(file_id)
    file_name = get_file_name(file_id)
    raw_file = DistributedFiles.where(:file_name => file_name).first()

    if raw_file == nil
      return {
        :table => "distributed_files",
        :file_name => "#{file_id}.xml",
        :file_id => file_id,
        :empty => true,
        :log_exists => log_file_exists?(TABLE_NAME_DISTRIBUTED, file_id)
      }
    end

    return {
        :table => "distributed_files",
        :file_name => raw_file.file_name,
        :file_id => file_id,
        :time => raw_file.created_at.localtime,
        :last_attempt_successful => raw_file.last_successful,
        :last_successful_name => raw_file.original_filename_last_successful,
        :last_failed_name => raw_file.original_filename_last_failed,
        :log_exists => true
    }
  end

  def self.get_first_signed_file()
    raw_file = DistributedSignedFiles.first()

    if raw_file == nil
      return {
        :table => "distributed_signed_files",
        :file_name => "signed",
        :file_id => FILE_ID_SIGNED,
        :empty => true,
        :log_exists => log_file_exists?(TABLE_NAME_DISTRIBUTED, FILE_ID_SIGNED)
      }
    end

    return {
        :table => "distributed_signed_files",
        :file_name => "signed",
        :file_id => FILE_ID_SIGNED,
        :time => raw_file.created_at.localtime,
        :log_exists => true
    }
  end

  def self.get_log_file(table_name, file_id)
    return "#{SdsbFileUtils.get_log_dir()}/"\
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

    SdsbFileUtils.write(identifier_mapping_log_file, log_file_content)
  end

  def self.get_file_name(file_id)
    case file_id
    when FILE_ID_GLOBALCONF
      return "globalconf.xml"
    when FILE_ID_IDENTIFIERMAPPING
      return "identifiermapping.xml"
    end

    return nil
  end
end
