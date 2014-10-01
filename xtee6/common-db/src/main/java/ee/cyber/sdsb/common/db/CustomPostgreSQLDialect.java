package ee.cyber.sdsb.common.db;

import org.hibernate.dialect.PostgreSQL82Dialect;
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

// Handle incompatibility between postgres blob => oid|bytea
public class CustomPostgreSQLDialect extends PostgreSQL82Dialect {

    @Override
    public SqlTypeDescriptor remapSqlTypeDescriptor(
            SqlTypeDescriptor sqlTypeDescriptor) {
        if (sqlTypeDescriptor.getSqlType() == java.sql.Types.BLOB) {
            return BinaryTypeDescriptor.INSTANCE;
        }

        return super.remapSqlTypeDescriptor(sqlTypeDescriptor);
    }
}
