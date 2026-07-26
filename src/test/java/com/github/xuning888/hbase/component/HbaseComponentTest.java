package com.github.xuning888.hbase.component;


import com.github.xuning888.hbase.util.RowKeyUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * HBase 完整使用示例集
 * <p>
 * 表结构: test:stock_flow
 *   列族: flow
 *   列: msgId, content, opType, stockCode, amount, createTime
 *
 * @author xuning
 * @date 2026/7/26 23:12
 */
public class HbaseComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(HbaseComponentTest.class);
    private static final String tableName = "test:stock_flow";
    private static final String flow_family = "flow";
    static int[] optType = new int[]{0, 1, 2, 3, 4};
    private HbaseComponent hbaseComponent;


    @BeforeEach
    public void setUp() throws IOException {
        // HBaseConfiguration.create() 创建一个基于默认 hbase-site.xml 的配置对象
        Configuration config = HBaseConfiguration.create();
        // ZooKeeper 地址 — HBase 客户端通过 ZK 发现集群的 RegionServer
        config.set("hbase.zookeeper.quorum", "localhost");
        // ZK 客户端端口，docker-compose 映射 2181:2181
        config.set("hbase.zookeeper.property.clientPort", "2181");
        // ConnectionFactory 根据 Configuration 创建 Connection，Connection 是线程安全的重量级对象
        Connection connection = ConnectionFactory.createConnection(config);
        hbaseComponent = new HbaseComponent(connection);
    }

    // ==================== 表管理 ====================

    /**
     * createTable — 创建表（自动创建 namespace）
     * <p>
     * HbaseAdminOperation.createTable 内部逻辑：
     * 1. 通过 Connection.getAdmin() 获取 Admin 对象（管理元数据的入口）
     * 2. 解析表名中的 namespace，若不存在则自动创建（NamespaceDescriptor.create().build()）
     * 3. 构建 HTableDescriptor（表描述符）+ HColumnDescriptor（列族描述符）
     * 4. 调用 Admin.createTable() 提交到 HMaster
     */
    @Test
    public void createTable() throws IOException {
        boolean table = hbaseComponent.getHbaseAdminOperation().createTable(tableName, flow_family);
        if (table) {
            logger.info("建表成功");
        }
    }

    /**
     * createSpiltTable — 创建带预分区的表
     * <p>
     * RowKeyUtils.createSplitKeys(numRegions, prefixLength, step) 生成分区键：
     * - numRegions: 分区数量（必须是 2 的幂次）
     * - prefixLength: 分区键前缀长度（不足补0）
     * - step: 步长
     * <p>
     * 例如 createSplitKeys(4, 3, 3) 生成 ["003", "006", "009"]，即 4 个 Region：
     *   [    - 003), [003 - 006), [006 - 009), [009 -    )
     * <p>
     * HTableDescriptor 附带 splitKeys 后，HMaster 建表时直接按这些键预分配 Region
     */
    @Test
    public void createTable2() {
        byte[][] splitKeys = RowKeyUtils.createSplitKeys(4, 3, 3);
        boolean spiltTable = hbaseComponent.getHbaseAdminOperation().createSpiltTable(tableName, splitKeys, flow_family);
        if (spiltTable) {
            logger.info("建表成功");
        }
    }

    /**
     * deleteTable — 删除表
     * <p>
     * 流程：disableTable（下线） → deleteTable（删除）
     * HBase 不允许直接删除 online 的表，必须先 disable
     */
    @Test
    public void deleteTable() {
        boolean deleteTable = hbaseComponent.getHbaseAdminOperation().deleteTable(tableName);
        if (deleteTable) {
            logger.info("删表成功");
        }
    }

    // ==================== 写入 ====================

    /**
     * put — 写入单行数据
     * <p>
     * 核心 API：Table.put(Put)
     * - Put 对象封装一行数据，以 rowKey 为标识
     * - put.addColumn(family, qualifier, value) 添加一个列
     * - HBase 中"列"是动态的，同一个列族下不同行可以有不同的列
     */
    @Test
    public void put() {
        Random random = new Random();
        Supplier<Integer> supplier = () -> {
            int idx = random.nextInt(optType.length);
            return optType[idx];
        };
        for (int i = 1; i <= 10; i++) {
            StockFlow stockFlow = new StockFlow(
                    (long) i, "content4" + i, supplier.get(), "AAPL" + i,
                    BigDecimal.valueOf(100 + i * 10), new Date());
            Map<String, String> familyMap = StockFlowConvert.convertToMap(stockFlow);
             boolean put = hbaseComponent.getHbaseSingleFamilyOperation().put(tableName, String.format("%019d", i), flow_family, familyMap);
            System.out.println(put);
        }
    }

    /**
     * putList — 批量写入多行
     * <p>
     * 核心 API：Table.put(List<Put>)
     * - 将多个 Put 对象收集到 List 中，一次性提交
     * - 比逐条 put 减少 RPC 次数，显著提高写入吞吐量
     * - RowKeyAndMap 是封装 rowKey + familyMap 的 POJO
     */
    @Test
    public void putList() {
        List<RowKeyAndMap> list = new ArrayList<>();
        for (int i = 100; i < 110; i++) {
            StockFlow stockFlow = new StockFlow((long) i, "batch_content_" + i, (i % 5), "TSLA" + i, BigDecimal.valueOf(i % 5), new Date());
            Map<String, String> familyMap = StockFlowConvert.convertToMap(stockFlow);
            list.add(new RowKeyAndMap(String.format("%019d", i), familyMap));
        }
        boolean result = hbaseComponent.getHbaseSingleFamilyOperation().putList(tableName, flow_family, list);
        System.out.println("putList result: " + result);
    }

    // ==================== 查询 ====================

    /**
     * get — 按 rowKey 精确查询单行
     * <p>
     * 核心 API：Table.get(Get)
     * - Get 指定一个 rowKey
     * - get.addFamily(family) 限定返回的列族
     * - 返回 Result 对象，通过 getFamilyMap() 解析为 Map
     * <p>
     * 类比 MySQL: SELECT * FROM table WHERE rowKey = '3'
     */
    @Test
    public void get() throws IOException {
        Map<String, String> result = hbaseComponent.getHbaseSingleFamilyOperation().get(tableName, String.format("%019d", 3), flow_family);
        System.out.println(StockFlowConvert.convertToDto(result));
    }

    /**
     * getList — 批量按 rowKey 查询多行
     * <p>
     * 核心 API：Table.get(List<Get>)
     * - 每个 Get 对应一个 rowKey
     * - 批量发送，一次 RPC 往返获取所有结果
     * - 返回 Result[] 数组，按请求顺序返回
     * <p>
     * 类比 MySQL: SELECT * FROM table WHERE rowKey IN ('1', '2', '3')
     */
    @Test
    public void getList() throws IOException {
        List<String> rowKeys = Arrays.asList(String.format("%019d", 1), String.format("%019d", 2), String.format("%019d",3));
        List<Map<String, String>> results = hbaseComponent.getHbaseSingleFamilyOperation().getList(tableName, rowKeys, flow_family);
        List<StockFlow> result = results.stream().map(StockFlowConvert::convertToDto).collect(Collectors.toList());
        result.forEach(System.out::println);
    }

    // ==================== Scan ====================

    /**
     * scan — 范围扫描
     * <p>
     * 核心 API：Table.getScanner(Scan)
     * - Scan 是 HBase 最核心的查询方式
     * - setStartRow / setStopRow 指定扫描范围 [startRow, stopRow)
     * - addFamily 限定返回的列族
     * - setCaching(n) 每次 RPC 返回 n 条，减少网络往返
     * <p>
     * 类比 MySQL: SELECT * FROM table WHERE rowKey BETWEEN '0' AND '999999' LIMIT 100
     */
    @Test
    public void scan() throws IOException {
        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family, 100);
        for (Map<String, String> stringStringMap : scan) {
            System.out.println(stringStringMap);
        }
    }

    /**
     * scanReverse — 反向扫描（从大到小）
     * <p>
     * 核心 API：scan.setReversed(true)
     * - startRow 变为扫描的起始（大值端），stopRow 为结束（小值端）
     * - 适用于"查最新 N 条"等场景
     * <p>
     * 类比 MySQL: SELECT * FROM table ORDER BY rowKey DESC LIMIT 5
     */
    @Test
    public void scanReverse() throws IOException {
        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scanReverse(
                tableName, "9", "0", flow_family, 5);
        scan.forEach(System.out::println);
    }

    /**
     * 分页扫描 — 通过 startRowInclusive=false 排除上一页最后一行
     * <p>
     * 核心思想：HBase 没有 OFFSET，分页依赖 scan 的 startRow/stopRow
     * - 第一页：scan("0", "999", ..., 3, true)  → 取 3 条
     * - 第二页：scan(lastRowKey, "999", ..., 3, false) → 从上页末尾继续，不包含上页最后一行
     * <p>
     * 类比 MySQL: SELECT * FROM table WHERE rowKey > 'last_key' LIMIT 3
     */
    @Test
    public void scanPagination() throws IOException {
        // 第一页
        int limit = 3;
        List<Map<String, String>> page1 = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family, limit, true);
        System.out.println("=== 第1页 ===");
        page1.forEach(System.out::println);

        // 假设第一页最后一行的 rowKey 是 "3"
        String lastRowKey = String.format("%019d", 3);
        List<Map<String, String>> page2 = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, lastRowKey, String.valueOf(Long.MAX_VALUE), flow_family, limit, false);
        System.out.println("=== 第2页 ===");
        page2.forEach(System.out::println);
    }

    // ==================== Filter 过滤 ====================

    /**
     * IN 查询 — 模拟 MySQL WHERE opType IN (0, 1, 2)
     * <p>
     * 核心 API：FilterList(Operator.MUST_PASS_ONE) → OR 逻辑
     * - 每个 SingleColumnValueFilter 匹配一个值
     * - BinaryComparator 精确匹配字节数组（不会把 "10" 匹配成 "0"）
     * - setFilterIfMissing(true)：列不存在时过滤掉该行
     */
    @Test
    public void scan_in() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        for (String val : new String[]{"0", "1", "2"}) {
            SingleColumnValueFilter filter = new SingleColumnValueFilter(
                    Bytes.toBytes(flow_family), Bytes.toBytes("op_type"),
                    CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes(val)));
            filter.setFilterIfMissing(true);
            filterList.addFilter(filter);
        }
        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== opType IN (0,1,2) ===");
        scan.forEach(System.out::println);
    }

    /**
     * AND 查询 — 模拟 MySQL WHERE opType = '0' AND stockCode LIKE 'AAPL%'
     * <p>
     * 核心 API：FilterList(Operator.MUST_PASS_ALL) → AND 逻辑
     * - SingleColumnValueFilter + BinaryComparator → 精确匹配
     * - SingleColumnValueFilter + RegexStringComparator → 正则匹配（类似 LIKE）
     * - RegexStringComparator("AAPL.*") 表示以 AAPL 开头
     */
    @Test
    public void scan_and() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        // opType = '0'
        SingleColumnValueFilter filter1 = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("op_type"),
                CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes("0")));
        filter1.setFilterIfMissing(true);
        filterList.addFilter(filter1);

        // stockCode LIKE 'AAPL%'
        SingleColumnValueFilter filter2 = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("stock_code"),
                CompareFilter.CompareOp.EQUAL, new RegexStringComparator("AAPL.*"));
        filter2.setFilterIfMissing(true);
        filterList.addFilter(filter2);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== opType='0' AND stockCode LIKE 'AAPL%' ===");
        scan.forEach(System.out::println);
    }

    /**
     * OR 查询 — 模拟 MySQL WHERE opType = '0' OR opType = '4'
     * <p>
     * 核心 API：FilterList(Operator.MUST_PASS_ONE) → OR 逻辑
     * 多个条件满足任意一个即可
     */
    @Test
    public void scan_or() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);

        SingleColumnValueFilter filter1 = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("op_type"),
                CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes("0")));
        filter1.setFilterIfMissing(true);
        filterList.addFilter(filter1);

        SingleColumnValueFilter filter2 = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("op_type"),
                CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes("4")));
        filter2.setFilterIfMissing(true);
        filterList.addFilter(filter2);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== opType='0' OR opType='4' ===");
        scan.forEach(System.out::println);
    }

    /**
     * LIKE 模糊查询 — 模拟 MySQL WHERE content LIKE '%content4%'
     * <p>
     * 核心 API：SubstringComparator → 子串包含匹配
     * - 判断列值中是否包含指定子串
     * - 类似 MySQL 的 LIKE '%xxx%'
     */
    @Test
    public void scan_like() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        SingleColumnValueFilter filter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("content"),
                CompareFilter.CompareOp.EQUAL, new SubstringComparator("content4"));
        filter.setFilterIfMissing(true);
        filterList.addFilter(filter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== content LIKE '%content4%' ===");
        scan.forEach(System.out::println);
    }

    /**
     * 大于/小于查询 — 模拟 MySQL WHERE amount > 150
     * <p>
     * 核心 API：CompareFilter.CompareOp.GREATER / LESS 等
     * - GREATER: 列值 > 指定值
     * - GREATER_OR_EQUAL: 列值 >= 指定值
     * - LESS: 列值 < 指定值
     * - LESS_OR_EQUAL: 列值 <= 指定值
     * - NOT_EQUAL: 列值 != 指定值
     * <p>
     * 注意：HBase 是字节比较，"100" > "99" 按字典序是 false，数值比较需保证格式一致
     */
    @Test
    public void scan_greaterThan() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        SingleColumnValueFilter filter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("amount"),
                CompareFilter.CompareOp.GREATER, new BinaryComparator(Bytes.toBytes("150")));
        filter.setFilterIfMissing(true);
        filterList.addFilter(filter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== amount > 150 ===");
        scan.forEach(System.out::println);
    }

    /**
     * 复杂组合查询 — 模拟 MySQL WHERE (opType = '0' OR opType = '1') AND stockCode LIKE 'AAPL%'
     * <p>
     * 核心思想：嵌套 FilterList
     * - 外层 MUST_PASS_ALL 保证所有组都必须满足
     * - 内层 MUST_PASS_ONE 实现组内 OR
     */
    @Test
    public void scan_complex() throws IOException {
        // 内层 OR：opType IN ('0', '1')
        FilterList orGroup = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        for (String val : new String[]{"0", "1"}) {
            SingleColumnValueFilter f = new SingleColumnValueFilter(
                    Bytes.toBytes(flow_family), Bytes.toBytes("op_type"),
                    CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes(val)));
            f.setFilterIfMissing(true);
            orGroup.addFilter(f);
        }

        // AND 条件：stockCode LIKE 'AAPL%'
        SingleColumnValueFilter stockCodeFilter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("stock_code"),
                CompareFilter.CompareOp.EQUAL, new RegexStringComparator("AAPL.*"));
        stockCodeFilter.setFilterIfMissing(true);

        // 外层 AND：orGroup AND stockCodeFilter
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        filterList.addFilter(orGroup);
        filterList.addFilter(stockCodeFilter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== (opType IN ('0','1')) AND stockCode LIKE 'AAPL%' ===");
        scan.forEach(System.out::println);
    }

    /**
     * RowKey 前缀过滤 — 模拟 MySQL WHERE rowKey LIKE '10%'
     * <p>
     * 核心 API：PrefixFilter → RowKey 前缀匹配
     * - 比 SingleColumnValueFilter 高效，直接作用于 rowKey
     * - 常用于按用户 ID、时间戳等前缀查询
     */
    @Test
    public void scan_rowKeyPrefix() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        PrefixFilter prefixFilter = new PrefixFilter(Bytes.toBytes("10"));
        filterList.addFilter(prefixFilter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== rowKey LIKE '10%' ===");
        scan.forEach(System.out::println);
    }

    // ==================== ByteArrayComparable 全部 6 种实现 ====================
    // HBase 1.2 中 ByteArrayComparable 的实现类：
    //   1. BinaryComparator       — 精确字节匹配
    //   2. BinaryPrefixComparator — 字节前缀匹配
    //   3. RegexStringComparator  — 正则表达式匹配
    //   4. SubstringComparator    — 子串包含匹配
    //   5. NullComparator         — 判断列值是否为空
    //   6. LongComparator         — 按 long 数值比较（解决字符串字典序问题）
    //   + BitComparator          — 按位与/或/异或比较（HBase 1.x 也有，但极少用）
    //
    // 上面的示例已经覆盖了 BinaryComparator、RegexStringComparator、SubstringComparator
    // 下面补充剩余 3 种

    /**
     * BinaryPrefixComparator — 字节前缀匹配
     * <p>
     * 类比 MySQL: WHERE stockCode LIKE 'AAPL%'
     * <p>
     * 与 BinaryComparator 的区别：BinaryComparator 是全等匹配，
     * BinaryPrefixComparator 只要求列值以指定字节开头
     * <p>
     * 与 RegexStringComparator("AAPL.*") 的区别：纯字节比较，性能更好，不走正则引擎
     */
    @Test
    public void scan_binaryPrefixComparator() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        SingleColumnValueFilter filter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("stockCode"),
                CompareFilter.CompareOp.EQUAL, new BinaryPrefixComparator(Bytes.toBytes("AAPL")));
        filter.setFilterIfMissing(true);
        filterList.addFilter(filter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== BinaryPrefixComparator: stockCode starts with 'AAPL' ===");
        scan.forEach(System.out::println);
    }

    /**
     * NullComparator — 判断列值是否为 null（空字节数组）
     * <p>
     * 类比 MySQL: WHERE content IS NULL / WHERE content IS NOT NULL
     * <p>
     * 用法：配合 CompareOp.EQUAL 表示"值为 null 的行"，配合 NOT_EQUAL 表示"值不为 null 的行"
     * 注意：必须设置 setFilterIfMissing(true)，否则列缺失的行也会通过
     */
    @Test
    public void scan_nullComparator() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        // content IS NOT NULL（值存在且不为空）
        SingleColumnValueFilter notNullFilter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("content"),
                CompareFilter.CompareOp.NOT_EQUAL, new NullComparator());
        notNullFilter.setFilterIfMissing(true);
        filterList.addFilter(notNullFilter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== NullComparator: content IS NOT NULL ===");
        scan.forEach(System.out::println);
    }

    /**
     * LongComparator — 按 long 数值比较
     * <p>
     * 类比 MySQL: WHERE amount > 150（数值比较而非字符串比较）
     * <p>
     * 解决 HBase 字节序问题：BinaryComparator 按字典序比较，"100" < "99"（字典序），
     * LongComparator 将字节转为 long 再比较，适用于数值列
     * <p>
     * 注意：存入 HBase 的值必须能用 Bytes.toLong() 解析（8 字节）
     */
    @Test
    public void scan_longComparator() throws IOException {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        // amount > 150，数值比较
        SingleColumnValueFilter filter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("amount"),
                CompareFilter.CompareOp.GREATER, new LongComparator(150L));
        filter.setFilterIfMissing(true);
        filterList.addFilter(filter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== LongComparator: amount > 150 ===");
        scan.forEach(System.out::println);
    }

    /**
     * BitComparator — 按位运算比较（HBase 1.x 也有，但极少使用）
     * <p>
     * 类比：无直接 MySQL 等价，适用于位图/标志位场景
     * <p>
     * BitwiseOp.AND / OR / XOR：对字节数组做位运算后与 0 比较
     * 例如：value & mask != 0 表示某标志位被设置
     */
    @Test
    public void scan_bitComparator() throws IOException {
        // 构造一个单字节 0x01 的 mask
        byte[] mask = new byte[]{0x01};
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        // 判断 opType 列值按位 AND 0x01 后不为 0（即最低位为 1）
        SingleColumnValueFilter filter = new SingleColumnValueFilter(
                Bytes.toBytes(flow_family), Bytes.toBytes("opType"),
                CompareFilter.CompareOp.NOT_EQUAL, new BitComparator(mask, BitComparator.BitwiseOp.AND));
        filter.setFilterIfMissing(true);
        filterList.addFilter(filter);

        List<Map<String, String>> scan = hbaseComponent.getHbaseSingleFamilyOperation().scan(
                tableName, "0", String.valueOf(Long.MAX_VALUE), flow_family,
                100, true, filterList);
        System.out.println("=== BitComparator: opType & 0x01 != 0 ===");
        scan.forEach(System.out::println);
    }

    // ==================== 删除 ====================

    /**
     * delete — 按 rowKey 删除一行
     * <p>
     * 核心 API：Table.delete(Delete)
     * - Delete 指定 rowKey
     * - delete.addFamily(family) 删除整个列族
     * - 也可以 delete.addColumn(family, qualifier) 只删某个列
     * <p>
     * 类比 MySQL: DELETE FROM table WHERE rowKey = '5'
     */
    @Test
    public void delete() {
        boolean result = hbaseComponent.getHbaseSingleFamilyOperation().delete(tableName, "5", flow_family);
        System.out.println("delete result: " + result);
    }

    @AfterEach
    public void des() {
        // 关闭 Connection，释放 ZK 会话和线程池
        hbaseComponent.close();
    }
}
