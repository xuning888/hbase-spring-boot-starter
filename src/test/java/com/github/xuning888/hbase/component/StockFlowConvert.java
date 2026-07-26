package com.github.xuning888.hbase.component;


import org.apache.hadoop.hbase.shaded.org.apache.commons.lang.StringUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author xuning
 * @date 2026/7/27 01:10
 */
public class StockFlowConvert {


    public static final String FLOW_ID = "flow_id";
    public static final String CONTENT = "content";
    public static final String OP_TYPE = "op_type";
    public static final String STOCK_CODE = "stock_code";
    public static final String AMOUNT = "amount";
    public static final String CREATE_TIME = "create_time";


    public static Map<String, String> convertToMap(StockFlow stockFlow) {
        Map<String, String> map = new HashMap<>();
        Long flowId = stockFlow.getFlowId();
        if (Objects.isNull(flowId)) {
            safePut(map, FLOW_ID, StringUtils.EMPTY);
        } else {
            safePut(map, FLOW_ID, String.format("%019d", flowId));
        }
        safePut(map, CONTENT, Objects.nonNull(stockFlow.getContent()) ? stockFlow.getContent(): StringUtils.EMPTY);
        safePut(map, OP_TYPE, Objects.nonNull(stockFlow.getOpType()) ? stockFlow.getOpType().toString() : StringUtils.EMPTY);
        safePut(map, STOCK_CODE, Objects.nonNull(stockFlow.getStockCode()) ? stockFlow.getStockCode() : StringUtils.EMPTY);
        safePut(map, AMOUNT, Objects.nonNull(stockFlow.getAmount()) ? stockFlow.getAmount().toString() : StringUtils.EMPTY);
        Date createTime = stockFlow.getCreateTime();
        if (createTime == null) {
            createTime = new Date();
        }
        safePut(map, CREATE_TIME, format(createTime, "yyyy-MM-dd HH:mm:ss"));
        return map;
    }


    public static StockFlow convertToDto(Map<String, String> map) {
        StockFlow stockFlow = new StockFlow();

        String flowId = map.get(FLOW_ID);
        if (StringUtils.isNotBlank(flowId)) {
            String s = flowId.replaceFirst("^0+", "");
            stockFlow.setFlowId(Long.valueOf(s));
        }

        String content = map.get(CONTENT);
        if (StringUtils.isNotBlank(content)) {
            stockFlow.setContent(content);
        }

        String opType = map.get(OP_TYPE);
        if (StringUtils.isNotBlank(opType)) {
            stockFlow.setOpType(Integer.valueOf(opType));
        }

        String stockCode = map.get(STOCK_CODE);
        if (StringUtils.isNotBlank(stockCode)) {
            stockFlow.setStockCode(stockCode);
        }

        String amount = map.get(AMOUNT);
        if (StringUtils.isNotBlank(amount)) {
            stockFlow.setAmount(new BigDecimal(amount));
        }

        String createTime = map.get(CREATE_TIME);
        if (StringUtils.isNotBlank(createTime)) {
            stockFlow.setCreateTime(parse(createTime, "yyyy-MM-dd HH:mm:ss"));
        }

        return stockFlow;
    }


    public static String format(Date date, String pattern) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        return simpleDateFormat.format(date);
    }

    public static Date parse(String dateStr, String pattern) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            return simpleDateFormat.parse(dateStr);
        } catch (ParseException e) {
            throw new RuntimeException("日期解析失败: " + dateStr + ", pattern: " + pattern, e);
        }
    }

    /**
     * 防止写错列名导致数据被覆盖
     */
    private static void safePut(Map<String, String> map, String key, String value) {
        if (map.containsKey(key)) {
            throw new IllegalArgumentException("map中已经存在key=" + key);
        }
        map.put(key, value);
    }
}
