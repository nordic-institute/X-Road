require 'tempfile'
require 'fileutils'

class DistributedFilesDistributor
  
  def self.distribute(target_file)
    dist_sig = DistributedSignedFiles.order(:created_at).last
    if dist_sig
      generate_multipart(dist_sig, target_file)
    else
      raise "No distributed files in database!"
    end
  end

  private

  def self.generate_multipart(dist_sig, target_file)
    main_boundary = %x[openssl rand -base64 20].chomp

    tmp = Tempfile.new("dist_files_multipart_temp")
    begin
      tmp.puts "Content-Type: multipart/related; charset=UTF-8; boundary=#{main_boundary}"
      tmp.puts ""
      tmp.puts "--#{main_boundary}"

      tmp.puts "Content-Type: multipart/mixed; charset=UTF-8; boundary=#{dist_sig.data_boundary}"
      tmp.puts ""

      tmp.write dist_sig.data

      tmp.puts ""
      tmp.puts "--#{main_boundary}"

      tmp.puts "Content-Type: text/plain"
      tmp.puts "Content-Transfer-Encoding: base64"
      tmp.puts "Signature-Algorithm-Id: #{dist_sig.sig_algo_id}"
      tmp.puts ""

      tmp.puts dist_sig.signature

      tmp.puts "--#{main_boundary}--"
    ensure
      tmp.close
      FileUtils.chmod(0644, tmp.path)
      FileUtils.mv(tmp.path, target_file)
    end
  end

end