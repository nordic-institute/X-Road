# Guarantees that there will always be one global conf generation
# at a time.
class GlobalConfGenerationSynchronizer
  GLOBAL_CONF_GENERATION_LOCKFILE = "global_conf_generation_lockfile"

  # Takes generation operation as a block
  def self.generate
    lock = try_globalconf_lock
    report_generation_in_progress unless lock

    yield
  ensure
    release_globalconf_lock(lock) if lock
  end

  private

  def self.try_globalconf_lock
    return CommonUi::IOUtils.try_lock(GLOBAL_CONF_GENERATION_LOCKFILE)
  end

  def self.release_globalconf_lock(lockfile)
    CommonUi::IOUtils.release_lock(lockfile)
  end

  def self.report_generation_in_progress
    raise "Global configuration is currently being generated, "\
        "parallel generations are not allowed."
  end
end
