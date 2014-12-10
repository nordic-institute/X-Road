# Gets data and creates content of the signed directory.
class DistributedDirectoryBuilder

  # directory_builder must be of type SignedDirectoryBuilder.
  def initialize(directory_builder)
    @directory_builder = directory_builder
  end

  def distribute
    last_signed_file_record = DistributedSignedFiles.order(:created_at).last()
    Rails.logger.debug("Last signed file record to distribute:\n"\
        "#{last_signed_file_record}")

    if !last_signed_file_record
      raise "No distributed files in the database"
    end

    @directory_builder.build(last_signed_file_record)
  end
end
