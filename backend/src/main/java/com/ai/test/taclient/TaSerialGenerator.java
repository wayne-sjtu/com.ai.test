package com.ai.test.taclient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TA 报文流水号生成器（设计文档 6.4 节）。
 * 格式：{TA标识}-{yyMMdd}-{8位序列}，如 EXTA-260915-00000001。
 */
public final class TaSerialGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final String taTag;
    private final AtomicLong seq = new AtomicLong();

    public TaSerialGenerator(String taTag) {
        this.taTag = taTag;
    }

    public String next() {
        return taTag + "-" + LocalDate.now().format(DATE_FMT) + "-" + "%08d".formatted(seq.incrementAndGet());
    }
}
