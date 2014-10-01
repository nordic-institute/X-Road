class AboutController < ApplicationController
  def index
    @version = %x[dpkg-query -f '${Version}' -W xroad-proxy 2>&1].strip
    @version = t('about.unknown') unless $?.exitstatus == 0
  end
end
