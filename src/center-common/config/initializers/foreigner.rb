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

require 'foreigner'

ActiveSupport.on_load :active_record do
  Foreigner.load
end
# As foreign key creation seems to be failing for 'sqlite' adapter, let us do
# it if and only if we are using 'postgresql' adapter.

module Foreigner
  module ConnectionAdapters
    module Sql2003
      SUPPORTED_ADAPTERS = ["postgresql"].freeze()

      alias_method :old_add_foreign_key, :add_foreign_key
      alias_method :old_remove_foreign_key, :remove_foreign_key

      def add_foreign_key(from_table, to_table, options = {})
        return unless adapter_supported?()

        old_add_foreign_key(from_table, to_table, options)
      end

      def remove_foreign_key(table, options)
        return unless adapter_supported?()

        old_remove_foreign_key(table, options)
      end

      private

      def adapter_supported?
        adapter =
            ActiveRecord::Base.connection.instance_values["config"][:adapter]

        return SUPPORTED_ADAPTERS.include?(adapter)
      end
    end
  end
end
