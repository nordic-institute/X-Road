require 'json'

class V5DataImportStatus

  def self.write(file_name, exit_status)
    status_as_hash = {
      :time => Time.now().to_f(),
      :file_name => file_name,
      :exit_status => exit_status
    }

    SdsbFileUtils.write(get_status_file(), status_as_hash.to_json())
  end

  def self.get
    status_file = get_status_file()

    if !File.exists?(status_file)
      return {:no_status_file => true}
    end

    raw_status = SdsbFileUtils.read(status_file)

    status_as_json = JSON.parse(raw_status)

    return {
      :time => Time.at(status_as_json["time"]),
      :file_name => status_as_json["file_name"],
      :exit_status => status_as_json["exit_status"]
    }
  end

  private

  def self.get_status_file
    ENV["HOME"] + "/.v5_data_import_status"
  end
end
