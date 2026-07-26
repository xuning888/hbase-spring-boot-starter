package com.github.xuning888.hbase.util;


import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;

/**
 * @author xuning
 * @date 2026/7/27 00:05
 */
public class RowKeyUtils {


    private static final Logger logger = LoggerFactory.getLogger(RowKeyUtils.class);


    /**
     * @param numRegions   分区数
     * @param prefixLength 前缀长度。若长度不够，前面补0
     * @param step         步长
     * @return
     */
    public static byte[][] createSplitKeys(int numRegions, int prefixLength, int step) {

        if ((numRegions & numRegions - 1) != 0) {
            logger.error("numRegions is invalid");
            throw new InvalidParameterException();
        }

        if (String.valueOf(numRegions * step).length() > prefixLength) {
            logger.error("numRegions * step is invalid");
            throw new InvalidParameterException();
        }

        byte[][] splitKeys = new byte[numRegions - 1][];
        for (int i = 1; i < numRegions; i++) {
            splitKeys[i - 1] = Bytes.toBytes(align(i * step, prefixLength));
        }
        return splitKeys;
    }

    /**
     * 对齐位数，前面补0
     *
     * @param i
     * @param prefixLength
     */
    private static String align(int i, int prefixLength) {
        StringBuilder sb = new StringBuilder(String.valueOf(i));
        while (sb.length() < prefixLength) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }
}
