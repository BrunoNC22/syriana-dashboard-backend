package com.firebird_data_extractor.www.models;

public class RelationInfo {
    private String column;
    private String referencedTable;
    private String referencedColumn;


    public String getColumn() {
        return column;
    }
    public void setColumn(String column) {
        this.column = column;
    }
    public String getReferencedTable() {
        return referencedTable;
    }
    public void setReferencedTable(String referencedTable) {
        this.referencedTable = referencedTable;
    }
    public String getReferencedColumn() {
        return referencedColumn;
    }
    public void setReferencedColumn(String referencedColumn) {
        this.referencedColumn = referencedColumn;
    }
}
