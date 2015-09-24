class XroadArgumentError < ArgumentError
  attr_reader :type

  def initialize(type)
    @type = type
  end
end

