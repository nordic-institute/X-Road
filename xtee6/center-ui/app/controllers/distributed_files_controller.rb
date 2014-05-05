class DistributedFilesController < ApplicationController
  def index
    authorize!(:view_distributed_files)
  end

  def add_identifier_mapping
    authorize!(:edit_distributed_files)
    file_param = params[:identifier_mapping_file]

    if !file_param || !file_param.original_filename
      raise t("common.filename_empty")
    end

    DistributedFiles.add_file(
        "identifiermapping.xml",
        file_param.read,
        XmlValidator.new)

    notice(t("distributed_files.identifier_mapping_uploaded"))

    upload_success(nil, "uploadCallbackIdentifierMapping")
  rescue => e
    error(e.message)
    upload_error(nil, "uploadCallbackIdentifierMapping")
  end
end
