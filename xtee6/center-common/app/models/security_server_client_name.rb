# Maps security server client identifier with name to facilitate sorting and
# searching.
class SecurityServerClientName < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
end