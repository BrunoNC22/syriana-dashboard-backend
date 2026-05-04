package com.firebird_data_extractor.www.models;

import java.util.List;

public class TableInfo {
    private String name;
    private List<ColumnInfo> columns;
    private List<RelationInfo> relations;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public List<ColumnInfo> getColumns() {
        return columns;
    }
    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }
    public List<RelationInfo> getRelations() {
        return relations;
    }
    public void setRelations(List<RelationInfo> relations) {
        this.relations = relations;
    }    
}