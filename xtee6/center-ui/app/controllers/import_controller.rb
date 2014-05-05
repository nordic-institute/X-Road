require 'securerandom'

java_import Java::ee.cyber.sdsb.common.SystemProperties

class ImportController < ApplicationController
  def index
  end

  def import_v5_data
    file_param = params[:v5_mapping_file]

    if !file_param || !file_param.original_filename
      raise t("common.filename_empty")
    end

    data_file = write_temp_file(file_param.read)

    exit_status = execute_clients_importer(data_file)

    case exit_status
    when 0
      notice(t("import.execute.success"))
      upload_success(nil, "uploadCallbackImportV5Data")
    when 1
      warn(exit_status, t("import.execute.warnings"))
      upload_success(nil, "uploadCallbackImportV5Data")
    when 2
      error(t("import.execute.failure"))
      upload_error(nil, "uploadCallbackImportV5Data")
    else
      error(t("import.execute.other_error"))
      upload_error(nil, "uploadCallbackImportV5Data")
    end
  rescue => e
    error(e.message)
    upload_error(nil, "uploadCallbackImportV5Data")
  end

  private

  def write_temp_file(file_content)
    temp_files_dir = SystemProperties.getTempFilesPath()
    data_file = temp_files_dir + SecureRandom.hex
    File.open(data_file, 'wb') {|f| f.write(file_content) }
    logger.debug("Importable data file saved to '#{data_file}'")

    data_file
  end

  def execute_clients_importer(data_file)
    db_config = Rails.configuration.database_configuration[Rails.env]

    database = db_config["database"]
    user = db_config["username"]
    pass = db_config["password"]

    commandline = "/usr/share/sdsb/bin/xtee55_clients_importer " +
        "-d #{data_file} -b #{database} -u #{user} -p #{pass}"
    logger.debug("Executing: #{commandline}")

    system(commandline)

    exit_status = $?.exitstatus
    logger.debug("Import exit status: #{exit_status}")

    exit_status
  end
end
