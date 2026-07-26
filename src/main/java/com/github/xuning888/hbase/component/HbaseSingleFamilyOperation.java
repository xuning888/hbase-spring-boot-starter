package com.github.xuning888.hbase.component;


import com.github.xuning888.hbase.util.HbaseUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author xuning
 * @date 2026/7/25 16:06
 */
public class HbaseSingleFamilyOperation {

    private static final Logger logger = LoggerFactory.getLogger(HbaseSingleFamilyOperation.class);

    private final HbaseComponent hbaseComponent;

    public HbaseSingleFamilyOperation(HbaseComponent hbaseComponent) {
        this.hbaseComponent = hbaseComponent;
    }

    /**
     * 保存一行数据
     */
    public boolean put(String tableName, String rowKey, String family, Map<String, String> familyMap) {
        try (Table table= hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {
            Put put = new Put(Bytes.toBytes(rowKey));
            HbaseUtils.appendPutFamily(put, family, familyMap);
            table.put(put);
            return true;
        } catch (IOException ex) {
            logger.error("put, 网络异常, tableName:{}, rowKey:{}, family:{}",
                    tableName, rowKey, family, ex);
            return false;
        } catch (Exception ex) {
            logger.error("put, 未知异常, tableName:{}, rowKey:{}, family:{}",
                    tableName, rowKey, family, ex);
            return false;
        }
    }

    public boolean putList(String tableName, String family, List<RowKeyAndMap> rowKeyAndMapList) {
        try (Table table= hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {
            List<Put> puts = new ArrayList<>(rowKeyAndMapList.size());
            for (RowKeyAndMap rowKeyAndMap : rowKeyAndMapList) {
                Put put = new Put(Bytes.toBytes(rowKeyAndMap.getRowKey()));
                HbaseUtils.appendPutFamily(put, family, rowKeyAndMap.getFamilyMap());
                puts.add(put);
            }
            table.put(puts);
            return true;
        } catch (IOException ex) {
            logger.error("putList 网络异常, tableName:{}, family:{}, batchSize:{}",
                    tableName, family, rowKeyAndMapList.size(), ex);
            return false;
        } catch (Exception ex) {
            logger.error("putList 未知异常, tableName:{}, family:{}, batchSize:{}",
                    tableName, family, rowKeyAndMapList.size(), ex);
            return false;
        }
    }

    public Map<String, String> get(String tableName, String rowKey, String family) throws IOException {
        try (Table table = hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {

            Get get = new Get(Bytes.toBytes(rowKey));
            get.addFamily(Bytes.toBytes(family));

            Result result = table.get(get);
            return getSingleFamilyResult(result, family);
        } catch (Exception e) {
            logger.error("", e);
            throw e;
        }
    }



    /**
     * 获取多行数据
     *
     * @param tableName
     * @param rowKeyList
     * @param family
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> getList(String tableName, List<String> rowKeyList, String family) throws IOException {
        try (Table table = hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {

            List<Get> getList = new ArrayList<>();
            for (String rowKey : rowKeyList) {
                Get get = new Get(Bytes.toBytes(rowKey));
                get.addFamily(Bytes.toBytes(family));
                getList.add(get);
            }

            Result[] results = table.get(getList);

            List<Map<String, String>> list = new ArrayList<>();
            for (Result result : results) {
                Map<String, String> singleFamilyResult = getSingleFamilyResult(result, family);
                if (singleFamilyResult != null) {
                    list.add(singleFamilyResult);
                }
            }
            return list;
        } catch (Exception e) {
            logger.error("", e);
            throw e;
        }
    }

    /**
     * scan
     *
     * @param tableName
     * @param startRow
     * @param stopRow
     * @param family
     * @param limit
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scan(String tableName, String startRow, String stopRow, String family, int limit) throws IOException {
        return scan(tableName, startRow, stopRow, family, limit, true);
    }


    /**
     * scan
     *
     * @param tableName
     * @param startRow
     * @param stopRow
     * @param family
     * @param limit
     * @param startRowInclusive
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scan(String tableName, String startRow, String stopRow, String family, int limit, boolean startRowInclusive) throws IOException {
        return scan(tableName, startRow, stopRow, family, limit, startRowInclusive, null);
    }

    /**
     * scan
     *
     * @param tableName         表名
     * @param startRow          开始行
     * @param stopRow           结束行
     * @param family            列族
     * @param limit             限制行数
     * @param startRowInclusive 是否包含startRow，默认是包含的，设置为false可不包含
     * @param filterList        过滤器
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scan(String tableName, String startRow, String stopRow, String family,
                                          int limit, boolean startRowInclusive, FilterList filterList) throws IOException {
        try (Table table = hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {

            final Scan scan = buildScan(startRow, stopRow, family, limit, startRowInclusive, filterList);

            return HbaseUtils.executeSingleFamilyScan(table, scan, family);
        } catch (Exception e) {
            logger.error("", e);
            throw e;
        }
    }


    /**
     * 反向scan
     *
     * @param tableName
     * @param startRow
     * @param stopRow
     * @param family
     * @param limit
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scanReverse(String tableName, String startRow, String stopRow, String family, int limit) throws IOException {
        return scanReverse(tableName, startRow, stopRow, family, limit, true);

    }

    /**
     * 反向scan
     *
     * @param tableName
     * @param startRow
     * @param stopRow
     * @param family
     * @param limit
     * @param startRowInclusive
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scanReverse(String tableName, String startRow, String stopRow, String family, int limit, boolean startRowInclusive) throws IOException {
        return scanReverse(tableName, startRow, stopRow, family, limit, startRowInclusive, null);

    }

    /**
     * 反向scan
     *
     * @param tableName         表名
     * @param startRow          开始行
     * @param stopRow           结束行
     * @param family            列族
     * @param limit             限制行数
     * @param startRowInclusive 是否包含startRow，默认是包含的，设置为false可不包含
     * @param filterList        过滤器
     * @return
     * @throws IOException
     */
    public List<Map<String, String>> scanReverse(String tableName, String startRow, String stopRow, String family, int limit, boolean startRowInclusive, FilterList filterList) throws IOException {
        try (Table table = hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {

            final Scan scan = buildScan(startRow, stopRow, family, limit, startRowInclusive, filterList);

            //反向
            scan.setReversed(true);
            return executeSingleFamilyScan(table, scan, family);
        } catch (Exception e) {
            logger.error("", e);
            throw e;
        }
    }

    /**
     * 删除一行
     *
     * @param tableName
     * @param rowKey
     * @param family    删除的列族
     * @return
     */
    public boolean delete(String tableName, String rowKey, String family) {
        try (Table table = hbaseComponent.getHbaseConn().getTable(TableName.valueOf(tableName))) {

            Delete delete = new Delete(Bytes.toBytes(rowKey));
            delete.addFamily(Bytes.toBytes(family));
            table.delete(delete);
            return true;
        } catch (Exception e) {
            logger.error("", e);
        }
        return false;
    }

    /**
     * 解析单个列族的数据
     *
     * @param result
     * @param family
     * @return
     */
    private Map<String, String> getSingleFamilyResult(Result result, String family) {
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
     * @param table
     * @param scan
     * @param family
     * @return
     * @throws IOException
     */
    private List<Map<String, String>> executeSingleFamilyScan(Table table, Scan scan, String family) throws IOException {
        ResultScanner resultScanner = table.getScanner(scan);

        List<Map<String, String>> list = new ArrayList<>();
        for (Result result : resultScanner) {
            Map<String, String> singleFamilyResult = HbaseUtils.getSingleFamilyResult(result, family);
            if (singleFamilyResult != null) {
                list.add(singleFamilyResult);
            }
        }
        resultScanner.close();
        return list;
    }


    /**
     * 创建scan对象
     *
     * @param startRow
     * @param stopRow
     * @param family
     * @param limit
     * @param startRowInclusive 排除startRow
     * @param filterList
     * @return
     */
    private Scan buildScan(String startRow, String stopRow, String family, int limit, boolean startRowInclusive, FilterList filterList) {
        Scan scan = new Scan();
        if (startRow != null) {
            scan.setStartRow(Bytes.toBytes(startRow));
        }
        if (stopRow != null) {
            scan.setStopRow(Bytes.toBytes(stopRow));
        }

        FilterList topLevel = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        if (filterList != null) {
            topLevel.addFilter(filterList);
        }

        //过滤掉startRow
        if (startRow != null && !startRowInclusive) {
            RowFilter rowFilter = new RowFilter(CompareFilter.CompareOp.NOT_EQUAL, new BinaryComparator(Bytes.toBytes(startRow)));
            topLevel.addFilter(rowFilter);
        }
        //默认加上分页。必须放在最后
        PageFilter pageFilter = new PageFilter(limit);
        topLevel.addFilter(pageFilter);
        scan.setFilter(topLevel);

        if (family != null) {
            scan.addFamily(Bytes.toBytes(family));
        }

        //优化性能
        scan.setCaching(limit);
        return scan;
    }
}
