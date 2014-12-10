require 'securerandom'

java_import Java::ee.cyber.sdsb.common.SystemProperties

class ImportController < ApplicationController

  IMPORTED_FILE_NAME = "xtee55_clients_importer_last"

  before_filter :verify_get, :only => [:get_imported, :last_result]
  before_filter :verify_post, :only => [:import_v5_data]

  def index
    authorize!(:execute_v5_import)
  end

  # -- Specific GET methods - start ---

  def get_imported
    file = V5Import.read()
    is_anything_imported = file != nil
    file_name = is_anything_imported ? file.file_name : nil

    translated_file_info = is_anything_imported ?  
      t("import.file",
        :file => file_name,
        :time => format_time(file.created_at.localtime())) :
      t("import.no_file")

    result = {
      :file_type => t("import.label"),
      :file_info => translated_file_info,
      :file_name => file_name
    }

    render_json(result)
  end

  def last_result
    authorize!(:execute_v5_import)

    file = V5Import.read()
    console_output = file == nil ? [] : file.console_output.split("\n")

    render_json(:console => console_output)
  end

  # -- Specific GET methods - end ---

  def import_v5_data
    authorize!(:execute_v5_import)

    file_param = params[:v5_mapping_file]

    if !file_param || !file_param.original_filename
      raise t("common.filename_empty")
    end

    GzipFileValidator.new(file_param).validate()

    data_file = write_imported_file(file_param)

    exit_status = execute_clients_importer(
        data_file, file_param.original_filename)

    V5DataImportStatus.write(data_file, exit_status)

    status = {
      :status => read_status_from_file()
    }

    import_log_path = get_last_attempt_log_path()

    case exit_status
    when 0
      notice(t("import.execute.success"))
      upload_success(status, "SDSB_IMPORT.uploadCallback")
    when 1
      notice(t("import.execute.warnings",
          :log_path => import_log_path))
      upload_success(status, "SDSB_IMPORT.uploadCallback")
    when 2
      error(t("import.execute.failure",
          :log_path => import_log_path))
      upload_error(status, "SDSB_IMPORT.uploadCallback")
    else
      error(t("import.execute.other_error"))
      upload_error(status, "SDSB_IMPORT.uploadCallback")
    end
  rescue => e
    error(e.message)
    upload_error(nil, "SDSB_IMPORT.uploadCallback")
  end

  private

  def read_status_from_file
    import_status = V5DataImportStatus.get()

    if import_status[:no_status_file] == true
      render_json(import_status)
      return
    end

    import_status[:time] = format_time(import_status[:time])

    return import_status
  end

  def write_imported_file(file)
    file_name = get_v5_import_file()
    CommonUi::IOUtils.write_binary(file_name, file.read)
    logger.debug("Importable data file saved to '#{file_name}'")

    return file_name
  end

  def get_v5_import_file()
    return "#{SystemProperties.getV5ImportPath()}/#{IMPORTED_FILE_NAME}"
  end

  def get_last_attempt_log_path()
    return "#{SystemProperties::getLogPath()}/xtee55_clients_importer-LAST.log"
  end

  def execute_clients_importer(data_file, original_filename)
    db_config = Rails.configuration.database_configuration[Rails.env]

    database = db_config["database"]
    user = db_config["username"]
    pass = db_config["password"]

    commandline = ["/usr/share/sdsb/bin/xtee55_clients_importer",
        "-d", data_file, "-b", database, "-u", user, "-p", pass]
    logger.debug("Executing V5 clients import")

    console_output_lines = CommonUi::ScriptUtils.run_script(commandline)
    V5Import.write(original_filename, console_output_lines)

    exit_status = $?.exitstatus
    logger.debug("Import exit status: #{exit_status}")

    return exit_status
  rescue RubyExecutableException => e
    V5Import.write(original_filename, e.console_output_lines)
    return 2 # Error
  end
  
  def write_last_attempt(console_output_lines)
    CommonUi::IOUtils.write(
        get_last_attempt_log_path(), console_output_lines.join("\n"))
  end
end
