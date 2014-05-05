class AboutController < ApplicationController
  def index
    @version = %x[dpkg-query -f '${Version}' -W sdsb-proxy 2>&1].strip
    @version = t("about.index.unknown") unless $?.exitstatus == 0
  end
end
