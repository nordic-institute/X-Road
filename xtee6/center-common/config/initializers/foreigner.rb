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
