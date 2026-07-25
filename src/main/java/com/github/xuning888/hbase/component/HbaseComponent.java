package com.github.xuning888.hbase.component;


import org.apache.hadoop.hbase.client.Connection;

/**
 * @author xuning
 * @date 2026/7/25 16:03
 */
public class HbaseComponent {

    private final Connection hbaseConn;

    private final HbaseAdminOperation hbaseAdminOperation;

    private final HbaseSingleFamilyOperation hbaseSingleFamilyOperation;

    public HbaseComponent(Connection hbaseConn) {
        this.hbaseConn = hbaseConn;
        this.hbaseAdminOperation = new HbaseAdminOperation(this);
        this.hbaseSingleFamilyOperation = new HbaseSingleFamilyOperation(this);
    }

    public Connection getHbaseConn() {
        return hbaseConn;
    }

    public HbaseAdminOperation getHbaseAdminOperation() {
        return this.hbaseAdminOperation;
    }

    public HbaseSingleFamilyOperation getHbaseSingleFamilyOperation() {
        return hbaseSingleFamilyOperation;
    }
}
