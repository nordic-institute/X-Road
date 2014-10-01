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
    disabled = @disabled_controllers &&
      @disabled_controllers.include?(item.controller)

    if item.controller && !disabled
      url_for(:controller => item.controller)
    else
      "#"
    end
  end

  def menu_item_class(item)
    disabled = @disabled_controllers &&
      @disabled_controllers.include?(item.controller)

    if item.is_a?(SubMenu)
      :submenu
    else
      :disabled if disabled
    end
  end

  def heading(text = nil)
    render :partial => "layouts/heading", :locals => {:text => text}
  end

  def display_name(name, fullname)
    h "#{fullname} (#{name})"
  end

  def format_time(time)
    time.to_i == 0 ? "&mdash;" : time.strftime(t('common.time_format'))
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

  def dialog(id, title = nil, &block)
    render_partial_with_block("dialog", {:id => id, :title => title}, &block)
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

  def validate_filename(filename)
    if !is_filename_valid?(filename)
      raise t("common.filename_error", :file => filename)
    end
  end

  def is_filename_valid?(filename)
    return filename =~ /\A[\w\.\-]+\z/
  end
end
