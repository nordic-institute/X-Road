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

# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  def menu_items
    configuration_submenu =
      SubMenu.new(t('menu.configuration.title'), [
        MenuItem.new(t('menu.configuration.clients'),
          :clients, :view_clients),
        MenuItem.new(t('menu.configuration.sysparams'),
          :sysparams, :view_sys_params)
      ])

    management_submenu =
      SubMenu.new(t('menu.management.title'), [
        MenuItem.new(t('menu.management.keys'),
          :keys, :view_keys),
        MenuItem.new(t('menu.management.backup_and_restore'),
          :backup, :backup_configuration),
        MenuItem.new(t('menu.management.diagnostics'),
          :diagnostics)
      ])

    help_submenu =
      SubMenu.new(t('menu.help.title'), [
        MenuItem.new(t('menu.help.version'), :about)
      ])

    result = []

    if can_see_submenu?(configuration_submenu)
      result << configuration_submenu
    end

    if can_see_submenu?(management_submenu)
      result << management_submenu
    end

    if can_see_submenu?(help_submenu)
      result << help_submenu
    end

    result
  end

  def can_see_submenu?(submenu)
    submenu.subitems.each do |item|
      return true if !item.privilege || can?(item.privilege)
    end

    return false
  end

end
