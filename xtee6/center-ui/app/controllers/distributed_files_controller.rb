class DistributedFilesController < ApplicationController
  include BaseHelper
  include ApplicationHelper

  before_filter :verify_get, :only => [
      :get_files,
      :last_result,
      :download,
      :get_last_added,
      :can_upload_identifier_mapping]

  before_filter :verify_post, :only => [:upload_identifier_mapping]

  def index
    authorize!(:view_distributed_files)
  end

  # -- Specific GET methods - start ---

  def get_files
    authorize!(:view_distributed_files)

    render_json(get_files_as_json())
  end

  def last_result
    authorize!(:view_distributed_files)

   file_content = 
      DistributedFiles.get_log_file_content(params[:table], params[:file_id])

    render_json(:console => file_content)
  end

  def download
    authorize!(:view_distributed_files)

    file = DistributedFiles.get_file(params[:table], params[:file_id])

    send_data(file[:content], :filename => file[:name])
  end

  def get_last_added
    authorize!(:view_distributed_files)

    render_json(read_last_added_file())
  end

  # -- Specific GET methods - end ---

  def upload_identifier_mapping
    authorize!(:upload_identifier_mapping)
    file_param = params[:identifier_mapping_file]
    original_filename = file_param.original_filename

    if !file_param || !original_filename
      raise t("common.filename_empty")
    end

    validate_filename(original_filename)
    validate_extension(original_filename)

    IdentifierMappingValidator.new(
        file_param,
        SystemParameter.sdsb_instance(),
        MemberClass.get_all_codes()
    ).validate()

    DistributedFiles.add_file(
        "identifiermapping.xml",
        file_param.read,
        original_filename)

    DistributedFiles.write_identifier_mapping_log(
        "New identifier mapping file added successfully.\n")
    notice(t("distributed_files.identifier_mapping_uploaded"))

    upload_success({:file => read_last_added_file(),
        :table => get_files_as_json()},
        "SDSB_DISTRIBUTED_FILES.uploadCallbackIdentifierMapping")
  rescue => e
    error_description = 
        t("distributed_files.identifier_mapping.description.fail",
            {:file => original_filename, :time => format_time(Time.now)})
    DistributedFiles.write_identifier_mapping_log(e, error_description)
    DistributedFiles.mark_failed_last_identifier_mapping_upload(
        original_filename)
    error(e.message)
    upload_error({:file => read_last_added_file(),
        :table => get_files_as_json()},
        "SDSB_DISTRIBUTED_FILES.uploadCallbackIdentifierMapping")
  end

  private

  def get_files_as_json
    files = DistributedFiles.get_files(can_import_V5_data?())
    add_metainfo(files)

    return files
  end

  def read_last_added_file
    last_added = DistributedFiles.order(:created_at).last

    if last_added == nil
      return {:no_distributed_files => true}
    end

    return {
      :file_name => last_added.file_name,
      :created => format_time(last_added.created_at.localtime)
    }
  end

  def add_metainfo(files)
    files.each do |each|
      file_infos = get_metainfo(each)

      each[:file_type] = file_infos[:type]
      each[:file_info] = file_infos[:info]
      each[:can_upload] = file_infos[:can_upload]
    end
  end

  def get_metainfo(file)
    case file[:file_name]
    when 'identifiermapping.xml'
      return {
        :type => t("distributed_files.identifier_mapping.type"),
        :info => get_translated_file_info(
            "distributed_files.identifier_mapping.description.success", file),
        :can_upload => can?(:upload_identifier_mapping)
      }
    when 'globalconf.xml'
      return {
        :type => t("distributed_files.global_conf.type"),
        :info => get_translated_file_info(
            "distributed_files.global_conf.description", file)
      }
    else # Assuming signed file
      return {
        :type => t("distributed_files.signed_files.type"),
        :info => get_translated_file_info(
            "distributed_files.signed_files.description", file)
      }
    end
  end

  def get_translated_file_info(translation_key, file)
    if file[:empty]
      return t("distributed_files.no_file")
    end

    name = file[:last_successful_name].blank? ?
        file[:file_name] : file[:last_successful_name]

    desc_translation = {
        :file => name, 
        :time => format_time(file[:time])
    }

    return t(translation_key, desc_translation)
  end

  def validate_extension(filename)
    return if filename =~ /\.xml$/i

    raise t("distributed_files.invalid_extension")
  end
end
