require 'transaction_isolation'

# Enables usage of transaction isolation levels.
TransactionIsolation.apply_activerecord_patch
