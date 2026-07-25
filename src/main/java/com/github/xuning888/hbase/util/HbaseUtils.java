package com.github.xuning888.hbase.util;


import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.*;

/**
 * @author xuning
 * @date 2026/7/25 16:25
 */
public class HbaseUtils {

    public static void appendPutFamily(Put put, String family, Map<String, String> familyMap) {
        if (familyMap == null || familyMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : familyMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(key), Bytes.toBytes(value));
        }
    }

    public static Map<String, String> getSingleFamilyResult(Result result, String family) {
        final NavigableMap<byte[], byte[]> familyMap = result.getFamilyMap(Bytes.toBytes(family));
        if (familyMap == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<byte[], byte[]> entry : familyMap.entrySet()) {
            map.put(Bytes.toString(entry.getKey()), Bytes.toString(entry.getValue()));
        }
        return map;
    }

    /**
     * 解析单个列族 scan的结果
     *
     */
    public static List<Map<String, String>> executeSingleFamilyScan(Table table, Scan scan, String family) throws IOException {
        ResultScanner resultScanner = table.getScanner(scan);

        List<Map<String, String>> list = new ArrayList<>();
        for (Result result : resultScanner) {
            Map<String, String> singleFamilyResult = getSingleFamilyResult(result, family);
            if (singleFamilyResult != null) {
                list.add(singleFamilyResult);
            }
        }
        resultScanner.close();
        return list;
    }
}
