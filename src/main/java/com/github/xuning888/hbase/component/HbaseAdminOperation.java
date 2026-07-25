package com.github.xuning888.hbase.component;


import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xuning
 * @date 2026/7/25 16:00
 */
public class HbaseAdminOperation {

    private static final Logger logger = LoggerFactory.getLogger(HbaseAdminOperation.class);

    private final HbaseComponent hbaseComponent;

    public HbaseAdminOperation(HbaseComponent hbaseComponent) {
        this.hbaseComponent = hbaseComponent;
    }

    /**
     * 创建表
     *
     * @param tableName     表名
     * @param columnFamilys 列族名称，支持多个列族
     * @return
     */
    public boolean createTable(String tableName, String... columnFamilys) {
        return createTable(tableName, null, columnFamilys);
    }

    /**
     * 创建表。预分区,手动分区
     *
     * @param tableName
     * @param splitKeys
     * @param columnFamilys
     * @return
     */
    public boolean createSpiltTable(String tableName, byte[][] splitKeys, String... columnFamilys) {
        return createTable(tableName, splitKeys, columnFamilys);
    }


    private boolean createTable(String tableName, byte[][] splitKeys, String... columnFamilys) {
        try (Admin admin = hbaseComponent.getHbaseConn().getAdmin()) {

            TableName tableNameObject = TableName.valueOf(tableName);
            if (admin.tableExists(tableNameObject)) {
                logger.warn("table exists, tableName = {}", tableName);
            } else {
                HTableDescriptor tableDescriptor = new HTableDescriptor(tableNameObject);
                //多个列族
                for (String columnFamily : columnFamilys) {

                    HColumnDescriptor hColumnDescriptor = new HColumnDescriptor(columnFamily);
                    tableDescriptor.addFamily(hColumnDescriptor);
                }

                if (splitKeys == null) {
                    admin.createTable(tableDescriptor);
                } else {
                    //预分区
                    admin.createTable(tableDescriptor, splitKeys);
                }
                logger.info("createTable success, tableName = {}", tableName);
                return true;
            }
        } catch (Exception e) {
            logger.error("createTable failed, tableName = {}", tableName, e);
        }
        return false;
    }


    /**
     * 删除表
     *
     * @param tableName
     * @return
     */
    public boolean deleteTable(String tableName) {
        try (Admin admin = hbaseComponent.getHbaseConn().getAdmin()) {
            TableName tableNameObject = TableName.valueOf(tableName);
            if (admin.tableExists(tableNameObject)) {
                admin.disableTable(tableNameObject);
                admin.deleteTable(tableNameObject);
                return true;
            }
        } catch (Exception e) {
            logger.error("deleteTable failed, tableName = {}", tableName, e);
        }
        return false;
    }

}
