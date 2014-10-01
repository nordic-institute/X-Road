class Identifier < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :object_type, :presence => true
  validates :sdsb_instance, :presence => true

  # Creates copy of the object with the same data.
  # The copy is not in any way connected to the database.
#  def clean_copy
#    Identifier.new(
#        :object_type => object_type,
#        :sdsb_instance => sdsb_instance,
#        :member_class => member_class,
#        :member_code => member_code,
#        :subsystem_code => subsystem_code,
#        :service_code => service_code,
#        :server_code => server_code,
#        :type => type)
#  end
end
