require 'json'

class GlobalConfGenerationStatus

  def self.write_success(log_file_content)
    write(true, log_file_content)
  end

  def self.write_failure(log_file_content)
    write(false, log_file_content)
  end

  def self.get
    status_file = get_status_file()

    if !File.exists?(status_file)
      return {:no_status_file => true}
    end

    raw_status = CommonUi::IOUtils.read(status_file)

    if raw_status.blank?
      return {
        :time => File.mtime(status_file),
        :success => false
      }
    end

    status_as_json = JSON.parse(raw_status)

    return {
      :time => Time.at(status_as_json["time"]),
      :success => status_as_json["success"]
    }
  end

  private

  def self.write(success, log_file_content)
    status_as_hash = {
      :time => Time.now().to_f(),
      :success => success
    }

    if can_write_status_file?(success)
      CommonUi::IOUtils.write(get_status_file(), status_as_hash.to_json())
    end

    CommonUi::IOUtils.write(get_log_file(), log_file_content)
  end

  def self.can_write_status_file?(success)
    previous_successful = get()[:success]
    previous_successful = true if previous_successful == nil

    return success || previous_successful
  end

  def self.get_status_file
    log_dir = CommonUi::IOUtils.get_log_dir()
    return "#{log_dir}/.global_conf_gen_status"
  end

  def self.get_log_file
    log_dir = CommonUi::IOUtils.get_log_dir()
    return "#{log_dir}/sdsb_distributed_files-distributed_files-globalconf"
  end
end
