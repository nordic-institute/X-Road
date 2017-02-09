# Migration for updating sequence values after backup restoration.


class UpdateSequences < ActiveRecord::Migration

  def up
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
    execute <<-SQL
CREATE OR REPLACE FUNCTION _fix_sequence_all() RETURNS void AS $body$

DECLARE
  s INTEGER;
  t INTEGER;
  x RECORD; 
BEGIN

  FOR x IN SELECT PGT.schemaname,S.relname sname ,C.attname, T.relname tname
   FROM pg_class AS S, pg_depend AS D, pg_class AS T, pg_attribute AS C, pg_tables AS PGT
   WHERE S.relkind = 'S' AND S.oid = D.objid AND D.refobjid = T.oid AND D.refobjid = C.attrelid AND D.refobjsubid = C.attnum AND T.relname = PGT.tablename AND PGT.schemaname = 'public' 
   LOOP
     -- get max used value from table
     EXECUTE format('select COALESCE(max(%I),0) from public.%I', x.attname, x.tname) into t;
       LOOP
         -- roll sequence till it is bigger than used value
         EXECUTE format('select nextval(%L)', 'public.'||x.sname) into s;
         IF s>t THEN
           exit;
         END IF;
       END LOOP;
   END LOOP;

END;
$body$ LANGUAGE plpgsql;
    SQL
    execute <<-SQL
CREATE OR REPLACE FUNCTION fix_sequence() RETURNS void AS $body$
BEGIN

  IF exists(SELECT 1 FROM pg_extension WHERE extname='bdr') THEN
    RAISE NOTICE 'BDR';
    PERFORM bdr.bdr_replicate_ddl_command('select public._fix_sequence_all();');
  ELSE
    RAISE NOTICE 'nonBDR';
    PERFORM public._fix_sequence_all();
  END IF;

END;
$body$ LANGUAGE plpgsql;
    SQL
    end
  end


  def down
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
DROP FUNCTION IF EXISTS fix_sequence();
DROP FUNCTION IF EXISTS _fix_sequence_all();
      SQL
    end
  end
end
