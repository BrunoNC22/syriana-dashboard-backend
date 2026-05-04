package com.firebird_data_extractor.www.repositories;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.models.ColumnInfo;
import com.firebird_data_extractor.www.models.RelationInfo;
import com.firebird_data_extractor.www.models.TableInfo;

@Repository
public class MetadataRepository {
    private final JdbcTemplate jdbc;

    public MetadataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findTables() {
        return jdbc.query(
            "SELECT TRIM(RDB$RELATION_NAME) FROM RDB$RELATIONS WHERE RDB$SYSTEM_FLAG = 0",
            (rs, rowNum) -> rs.getString(1)
        );
    }

    public List<ColumnInfo> findColumns(String tableName) {
        return jdbc.query(
            """
            SELECT 
                TRIM(rf.RDB$FIELD_NAME) as COLUMN_NAME,
                TRIM(f.RDB$FIELD_TYPE) as FIELD_TYPE
            FROM RDB$RELATION_FIELDS rf
            JOIN RDB$FIELDS f ON rf.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME
            WHERE rf.RDB$RELATION_NAME = ?
            """,
            new Object[]{tableName.toUpperCase()},
            (rs, rowNum) -> {
                ColumnInfo col = new ColumnInfo();
                col.setName(rs.getString("COLUMN_NAME"));
                col.setType(rs.getString("FIELD_TYPE"));
                return col;
            }
        );
    }

    public List<RelationInfo> findRelations(String tableName) {
        return jdbc.query(
            """
            SELECT
                TRIM(rc.RDB$RELATION_NAME) AS TABLE_NAME,
                TRIM(s.RDB$FIELD_NAME) AS COLUMN_NAME,
                TRIM(refc.RDB$CONST_NAME_UQ) AS REF_CONSTRAINT,
                TRIM(i2.RDB$RELATION_NAME) AS REF_TABLE,
                TRIM(s2.RDB$FIELD_NAME) AS REF_COLUMN
            FROM RDB$RELATION_CONSTRAINTS rc
            JOIN RDB$REF_CONSTRAINTS refc ON rc.RDB$CONSTRAINT_NAME = refc.RDB$CONSTRAINT_NAME
            JOIN RDB$INDICES i1 ON rc.RDB$INDEX_NAME = i1.RDB$INDEX_NAME
            JOIN RDB$INDEX_SEGMENTS s ON i1.RDB$INDEX_NAME = s.RDB$INDEX_NAME
            JOIN RDB$RELATION_CONSTRAINTS rc2 ON refc.RDB$CONST_NAME_UQ = rc2.RDB$CONSTRAINT_NAME
            JOIN RDB$INDICES i2 ON rc2.RDB$INDEX_NAME = i2.RDB$INDEX_NAME
            JOIN RDB$INDEX_SEGMENTS s2 ON i2.RDB$INDEX_NAME = s2.RDB$INDEX_NAME
            WHERE rc.RDB$RELATION_NAME = ?
            """,
            new Object[]{tableName.toUpperCase()},
            (rs, rowNum) -> {
                RelationInfo rel = new RelationInfo();
                rel.setColumn(rs.getString("COLUMN_NAME"));
                rel.setReferencedTable(rs.getString("REF_TABLE"));
                rel.setReferencedColumn(rs.getString("REF_COLUMN"));
                return rel;
            }
        );
    }

    public List<TableInfo> getDatabaseMetadata() {
        List<String> tables = findTables();

        return tables.stream().map(table -> {
            TableInfo t = new TableInfo();
            t.setName(table);
            t.setColumns(findColumns(table));
            t.setRelations(findRelations(table));
            return t;
        }).toList();
    }
}
