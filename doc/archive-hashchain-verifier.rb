#!/usr/bin/env ruby

require 'rubygems'
require 'zip' # gem 'rubyzip' must be installed!
require 'digest'

class DigestCalculator
  SUPPORTED_DIGEST_ALGO_IDS = ["SHA-256", "SHA-384", "SHA-512"]

  def initialize(digest_algo_id)
    digest_algo_id ||= ""

    case digest_algo_id.upcase
    when "SHA-256"
      @digest_class = Digest::SHA256
    when "SHA-384"
      @digest_class = Digest::SHA384
    when "SHA-512"
      @digest_class = Digest::SHA512
    else
      raise "Digest algorithm id '#{digest_algo_id}' is not supported, "\
          "supported ones are:\n#{SUPPORTED_DIGEST_ALGO_IDS.join(", ")}"
    end
  end

  def chain_digest(file_content, prev_hexdigest = "")
    return hexdigest(prev_hexdigest + hexdigest(file_content))
  end

  def hexdigest(input)
    digest = @digest_class.new
    digest.update(input)
    return digest.hexdigest
  end
end

# Raised when log archive is not in correct format.
class InvalidLogArchiveException < Exception
  def initialize(message)
    super(message)
  end
end

class LinkingInfo
  attr_reader :prev_digest, :prev_arch_file, :digest_algo_id

  def initialize(file_lines)
    @content_lines = {}

    parse(file_lines)
  end

  def digest_calculator
    DigestCalculator.new(@digest_algo_id)
  end

  def digest_for_file(file_name)
    @content_lines[file_name]
  end

  def file_names
    @content_lines.keys
  end

  private

  def parse(file_lines)
    prev_digest, prev_digest_file, @digest_algo_id =
        file_lines.shift.split(/\s+/)

    @prev_digest = prev_digest == "-" ? "" : prev_digest
    @prev_arch_file = prev_arch_file == "-" ? "" : prev_arch_file

    file_lines.each do |each_content_line|
      digest, file_name = each_content_line.split(/\s+/)

      @content_lines[file_name] = digest
    end
  end
end

class ArchiveExtractor
  def initialize(archive_file)
    @archive_file = archive_file
    @asic_containers = []
    @linking_info = nil

    @previous_digest = nil
  end

  def extract
    Zip::File.open(@archive_file) do |zip_file|
      extract_linking_info(zip_file)

      zip_file.each do |entry|
        process_asic(entry) if is_asic?(entry)
      end
    end

    return {
      :asic_containers => @asic_containers,
      :linking_info => @linking_info,
      :last_digest => @previous_digest
    }
  rescue Zip::Error, Errno::EACCES
    handle_input_error("File '#@archive_file' cannot be extracted - "\
        "it may not be valid zip file.")
  end

  private

  def process_asic(entry)
    file_name = entry.name
    file_content = entry.get_input_stream.read

    digest = @digest_calculator.chain_digest(file_content, @previous_digest)

    @asic_containers << {:name => file_name, :digest => digest}
    @previous_digest = digest
  end

  def is_asic?(entry)
    /\A*\.asice\z/ =~ entry.name
  end

  def extract_linking_info(zip_file)
    linking_info_entry = zip_file.get_entry("linkinginfo")

    linking_info_content = linking_info_entry.get_input_stream.read
    linking_info_lines = linking_info_content.split(/\n+/)

    @linking_info = LinkingInfo.new(linking_info_lines)
    @digest_calculator = @linking_info.digest_calculator
    @previous_digest = @linking_info.prev_digest
  rescue Errno::ENOENT
    raise InvalidLogArchiveException.new(
        "Linking info not found in archive file '#{zip_file.name}'")
  end
end

def print_usage
  STDERR.puts(%{
Program must be invoked like this:

  ./archive-hashchain-verifier.rb <pathToZippedAsicContainersArchive> \
<(previousArchiveHexDigest) or (-f) or (--first)>
  })
end

def handle_input_error(message)
  STDERR.puts("INPUT ERROR: #{message}")
  print_usage
  exit 1
end

def validate_archive_file
  unless ARGV.length == 2
    raise "First argument must provide two arguments, "\
        "but there is less."
  end

  zip_file = ARGV.first

  unless File.exists?(zip_file)
    raise "Archive file '#{zip_file}' does not exist."
  end
rescue Exception
  handle_input_error($!.message)
end

def parse_arguments
  validate_archive_file

  archive_file = ARGV.first
  prev_digest = ""

  unless first_in_hash_chain?
    prev_digest = ARGV[1]
  end

  return archive_file, prev_digest
end

def first_in_hash_chain?
  second_arg = ARGV[1] ? ARGV[1].downcase : ""

  return second_arg == "-f" || second_arg == "--first"
end

def extract(archive_file)
  ArchiveExtractor.new(archive_file).extract
end

def verify(archive_file, prev_digest, extracted_archive)
  asic_containers = extracted_archive[:asic_containers]
  linking_info = extracted_archive[:linking_info]

  unless asic_containers.any?
    raise InvalidLogArchiveException.new(
        "There are no ASiC containers in archive file "\
        "'#{archive_file}', at least one is expected.")
  end

  unless prev_digest == linking_info.prev_digest
    raise InvalidLogArchiveException.new(
        "Last hash steps given by user and in linking info differ\n"\
        "\tBy user: '#{prev_digest}'\n"\
        "\tIn linking info: '#{linking_info.prev_digest}'\n")
  end

  linking_info_file_names = linking_info.file_names
  in_archive_file_names = asic_containers.map { |c| c[:name] }

  unless linking_info_file_names == in_archive_file_names
    raise InvalidLogArchiveException.new(
        "File names in linking info and in archive file differ\n"\
        "\tIn archive file: '#{in_archive_file_names.join(", ")}'\n"\
        "\tIn linking info: '#{linking_info_file_names.join(", ")}'\n")
  end

  asic_containers.each do |each_container|
    file_name = each_container[:name]
    file_digest = each_container[:digest]

    linking_info_digest = linking_info.digest_for_file(file_name)

    if file_digest != linking_info_digest
      raise InvalidLogArchiveException.new(
          "Digests of file '#{file_name} do not match:\n"\
          "\tDigest in linkinginfo: #{linking_info_digest}\n"\
          "\tActual file digest: #{file_digest}\n")
    end
  end
end

def exit_with_error(archive_file)
  STDERR.puts("ERROR: Archive file '#{archive_file}' "\
      "is invalid, reason:\n#{$!.message}")
  exit 1
end

def print_last_digest(extracted_archive)
  puts extracted_archive[:last_digest]
end

def run
  archive_file, prev_digest = parse_arguments
  extracted_archive = extract(archive_file)

  verify(archive_file, prev_digest, extracted_archive)
  print_last_digest(extracted_archive)
rescue InvalidLogArchiveException
  exit_with_error(archive_file)
end

run
