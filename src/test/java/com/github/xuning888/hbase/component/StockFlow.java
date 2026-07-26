package com.github.xuning888.hbase.component;


import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author xuning
 * @date 2026/7/27 01:08
 */
public class StockFlow implements Serializable {

    private Long flowId;

    private String content;

    private Integer opType;

    private String stockCode;

    private BigDecimal amount;

    private Date createTime;

    public StockFlow() {}


    public StockFlow(Long flowId, String content, Integer opType, String stockCode, BigDecimal amount, Date createTime) {
        this.amount = amount;
        this.flowId = flowId;
        this.content = content;
        this.opType = opType;
        this.stockCode = stockCode;
        this.createTime = createTime;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getOpType() {
        return opType;
    }

    public void setOpType(Integer opType) {
        this.opType = opType;
    }

    public String getStockCode() {
        return stockCode;
    }

    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "StockFlow{" +
                "flowId=" + flowId +
                ", content='" + content + '\'' +
                ", opType=" + opType +
                ", stockCode='" + stockCode + '\'' +
                ", amount=" + amount +
                ", createTime=" + createTime +
                '}';
    }
}
