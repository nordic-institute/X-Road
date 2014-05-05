require 'json'

class GlobalConfGenerationStatus

  def self.write_success
    write(true)
  end
   
  def self.write_failure
    write(false)
  end

  def self.get
    status_file = get_status_file()

    if !File.exists?(status_file)
      return {:no_status_file => true}
    end

    raw_status = ""
    File.open(status_file, "r:UTF-8") do |f|
      raw_status << f.read()
    end
    status_as_json = JSON.parse(raw_status)

    {
      :time => Time.at(status_as_json["time"]),
      :success => status_as_json["success"]
    }
  end

  private

  def self.write(success)
    status_as_hash = {
      :time => Time.now().to_f(),
      :success => success
    }

    File.open(get_status_file, 'w:UTF-8') do |f| 
      f.write(status_as_hash.to_json())
    end
  end

  def self.get_status_file
    ENV["HOME"] + "/.global_conf_gen_status"
  end
end
