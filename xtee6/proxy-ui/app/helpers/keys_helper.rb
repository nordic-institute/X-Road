module KeysHelper

  def client_options(client_ids)
    options = []

    client_ids.each do |key,value|
      text = "#{value.xRoadInstance}:#{value.memberClass}:#{value.memberCode}:*"
      options << [text, key]
    end

    options
  end
end
