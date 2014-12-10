class Utils
  def self.create_mime_boundary
    return %x[openssl rand -base64 20].chomp()
  end
end
