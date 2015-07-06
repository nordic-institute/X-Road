JavaClientId = Java::ee.ria.xroad.common.identifier.ClientId

class JavaClientId
  def as_json(options = {})
    {
      :xRoadInstance => xRoadInstance,
      :memberClass => memberClass,
      :memberCode => memberCode,
      :subsystemCode => subsystemCode
    }.reject { |k,v| v.nil? }
  end
end
