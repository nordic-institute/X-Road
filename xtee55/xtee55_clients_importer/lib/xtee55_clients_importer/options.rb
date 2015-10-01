require 'optparse'

module Xtee55ClientsImporter

  class Options
    attr_reader :data_file
    attr_reader :host
    attr_reader :adapter
    attr_reader :database
    attr_reader :username
    attr_reader :password

    def initialize(argv)
      @data_file = nil
      @host = "localhost"
      @adapter = "postgresql"
      @database = "centerui_production"
      @username = nil
      @password = nil

      parse(argv)
    end

  private
    def parse(argv)
      OptionParser.new do |opts|
        opts.banner = "Usage: xtee55_clients_importer [ options ]"

        opts.on("-h", "--help", "Show this message") do
          puts opts
          exit
        end

        opts.on("-d", "--data_file PATH", String, "v5 CA exported file") do |d|
          @data_file = d
        end

        opts.on("-a", "--address HOST", String,
            "database server host (defaults to localhost)") do |a|
          @host = a
        end

        opts.on("-t", "--adapter ADAPTER", String,
            "database adapter (defaults to postgresql)") do |t|
          @adapter = t
        end

        opts.on("-b", "--database DBNAME", String,
            "database name (defaults to centerui_production)") do |b|
          @database = b
        end

        opts.on("-u", "--username USERNAME", String,
            "database user name") do |u|
          @username = u
        end

        opts.on("-p", "--password PASSWORD", String,
            "datatabase user password") do |p|
          @password = p
        end

        begin
          argv = ["-h"] if argv.empty?
          opts.parse!(argv)

          if @data_file == nil ||
              @username == nil ||
              @password == nil ||
              argv.size != 0
            STDERR.puts opts
            exit 2
          end
        rescue OptionParser::ParseError => e
          STDERR.puts e.message, "\n", opts
          exit 2
        end
      end
    end
  end

end
