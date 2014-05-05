class AboutController < ApplicationController
  def index
    @version = %x[dpkg-query -f '${Version}' -W sdsb-center 2>&1].strip
    @version = t("about.index.unknown") unless $?.exitstatus == 0
  end
end
