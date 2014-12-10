class TurnFileAndSignatureRelatedClobsIntoBlobs < ActiveRecord::Migration
  def up
    ActiveRecord::Base.connection.execute( %q{
      ALTER TABLE distributed_files
      ALTER COLUMN file_data
      TYPE bytea using cast(file_data AS bytea);
      ALTER TABLE distributed_signed_files
      ALTER COLUMN data
      TYPE bytea using cast(data AS bytea),
      ALTER COLUMN signature
      TYPE bytea using cast(signature AS bytea)
    })
  end

  def down
    ActiveRecord::Base.connection.execute( %q{
      ALTER TABLE distributed_files
      ALTER COLUMN file_data
      TYPE text using cast(file_data AS text);
      ALTER TABLE distributed_signed_files
      ALTER COLUMN data
      TYPE text using cast(data AS text),
      ALTER COLUMN signature
      TYPE text using cast(signature AS text)
    })
  end
end
