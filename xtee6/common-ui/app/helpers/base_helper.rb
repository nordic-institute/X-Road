#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

java_import Java::ee.ria.xroad.common.SystemProperties

module BaseHelper

  class MenuItem
    def initialize(text, controller = nil, privilege = nil)
      @text = text
      @controller = controller
      @privilege = privilege
    end

    attr_reader :text, :controller, :privilege
  end

  class SubMenu < MenuItem
    def initialize(text, subitems)
      super(text)
      @subitems = subitems
    end

    attr_reader :subitems
  end

  def menu_item_url(item)
    if item.controller
      url_for(:controller => item.controller)
    else
      "#"
    end
  end

  def menu_item_data(item)
    if item.controller
      s = url_for(:controller => item.controller)
      s.sub! '/', ''
    else
      "#"
    end
  end

  def menu_item_class(item)
    :submenu if item.is_a?(SubMenu)
  end

  def heading(text = nil)
    render :partial => "layouts/partials/heading", :locals => {:text => text}
  end

  def default_content_for(name, &block)
    name = name.kind_of?(Symbol) ? ":#{name}" : name
    out = eval("yield #{name}", block.binding)
    concat(out && !out.empty? ? out : capture(&block))
  end

  def flash_message(type)
    flash.discard(type).join("<br />") if flash[type]
  end

  def render_optional(partial)
    if lookup_context.find_all(partial, controller.controller_name, true).any?
      render :partial => partial
    end
  end

  def render_partial_with_block(partial, options = {}, &block)
    options.merge!(:body => block ? capture(&block) : nil)
    render(:partial => partial, :locals => options)
  end

  def render_advanced_search(partial, prefix)
    render({
      :partial => partial,
      :layout => :advanced_search,
      :locals => { :prefix => prefix }
    })
  end

  def dialog(id, title = nil, &block)
    render_partial_with_block("dialog", {:id => id, :title => title}, &block)
  end

  def upload_button(input_name, clazz = nil, button_text = "common.upload")
    raw("<label id='#{input_name}_button' class='upload #{clazz}'>" +
        "#{file_field_tag(input_name)}#{t(button_text)}</label>")
  end

  def browse_button(input_name, clazz = nil)
    upload_button(input_name, clazz, "common.browse")
  end

  def server_status_class
  end

  def available_locales
    result = []

    I18n.available_locales.each do |locale|
      text = t("common.locale_#{locale}", :locale => :en, :short => locale)
      result << [text, locale]
    end

    result
  end

  def skin_installed?
    File.exists?(SystemProperties.getConfPath + BaseController::UI_SKIN_FILE)
  end
end
