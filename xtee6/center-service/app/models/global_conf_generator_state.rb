# Responsible for controlling and providing state of global conf generation.
class GlobalConfGeneratorState
  @@mutex = Mutex.new()

  @@generating = false

  def self.generating?
    @@mutex.synchronize do
      return @@generating
    end
  end

  def self.set_generating
    @@mutex.synchronize do
      @@generating = true
    end
  end

  def self.clear_generating
    @@mutex.synchronize do
      @@generating = false
    end
  end
end
