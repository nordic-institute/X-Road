class AboutController < ApplicationController
  before_filter :verify_get

  def index
    @version = %x[dpkg-query -f '${Version}' -W xroad-center 2>&1].strip
    @version = t('about.unknown') unless $?.exitstatus == 0
  end
end
