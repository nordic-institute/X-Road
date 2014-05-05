require 'fileutils'
require 'tmpdir'
require 'base64'

require 'xtee55_clients_importer/xlogger'

include Xtee55ClientsImporter

module Xtee55ClientsImporter

  class Group
    attr_reader :name
    attr_reader :description

    def initialize(name, description)
      @name = name
      @description = description
    end
  end

  class Producer
    attr_reader :name
    attr_reader :full_name

    def initialize(name, full_name)
      @name = name
      @full_name = full_name
    end
  end

  class Consumer < Producer
    attr_reader :groups

    def initialize(name, full_name, groups)
      super(name, full_name)
      @groups = groups
    end
  end

  class Parser
    @@log = xlogger(Parser)

    attr_reader :groups
    attr_reader :consumers
    attr_reader :producers

    def initialize(ca_data_file)
      @data_file = ca_data_file
      @groups = []
      @consumers = []
      @producers = []
    end

    def parse
      @groups = []
      @consumers = []
      @producers = []
      dir = Dir.mktmpdir("xtee55-")

      begin
        start = Time.now
        result = %x[tar -C #{dir} -xzf #{@data_file} 2>&1]
        if $? != 0
          raise result
        end

        Dir["#{dir}/group/*"].each do |f|
          if File.directory?(f)
            path = f + "/description"
            @groups << Group.new(File.basename(f),
                read_base64_file(path).strip) if File.file?(path)
          end
        end

        Dir["#{dir}/org/*"].each do |f|
          if File.directory?(f)
            fullname_path = f + "/fullname"

            if File.file?(fullname_path)
              name = File.basename(f)
              fullname = read_base64_file(fullname_path).strip

              if File.directory?(f + "/producer_certs")
                @producers << Producer.new(name, fullname)
              end

              if File.directory?(f + "/consumer_certs")
                groups = get_sub_dirs("#{f}/groups")
                @consumers << Consumer.new(name, fullname, groups)
              end
            end
          end
        end

        @@log.info("PERFORMANCE: Preparing data for import: " +
                  "#{Time.now - start} seconds")
      ensure
        FileUtils.rm_rf dir
      end
    end

  private
    def get_sub_dirs(dir_path)
      if (File.directory?(dir_path))
        Dir["#{dir_path}/*"].reject{|f| not File.directory?(f)}.map{
            |f| File.basename(f)}
      else
        []
      end
    end

    def read_base64_file(path)
      file = File.open(path, "rb")
      contents = file.read
      file.close

      Base64.decode64(contents)
    end

  end
end

