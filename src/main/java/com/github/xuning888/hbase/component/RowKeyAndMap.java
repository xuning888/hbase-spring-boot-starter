package com.github.xuning888.hbase.component;


import java.io.Serializable;
import java.util.Map;

/**
 * @author xuning
 * @date 2026/7/25 16:33
 */
public class RowKeyAndMap implements Serializable {

    private String rowKey;
    private Map<String, String> familyMap;

    public RowKeyAndMap() {
    }

    public RowKeyAndMap(String rowKey, Map<String, String> familyMap) {
        this.rowKey = rowKey;
        this.familyMap = familyMap;
    }

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }

    public Map<String, String> getFamilyMap() {
        return familyMap;
    }

    public void setFamilyMap(Map<String, String> familyMap) {
        this.familyMap = familyMap;
    }
}
