package com.ai.test.service;

import com.ai.test.model.DividendSettingRequest;
import com.ai.test.repository.DividendSettingRepository;
import com.ai.test.repository.ProductRepository;
import com.ai.test.repository.entity.DividendSetting;
import com.ai.test.repository.entity.Product;
import com.ai.test.taclient.DividendCommand;
import com.ai.test.taclient.ExternalTaClient;
import com.ai.test.taclient.TaClient;
import com.ai.test.taclient.TaResult;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分红方式设置用例（spec 用户故事 23，功能 12 分级：真实实现，走 TA 报文）：
 * CASH 现金分红 / REINVEST 红利再投资；按产品类型路由本行 TA / 外部 TA 登记，
 * TA 受理成功后落库（每客户每产品一份设置，可覆盖更新）。
 */
@Service
public class DividendSettingService {

    private final DividendSettingRepository dividendSettingRepository;
    private final ProductRepository productRepository;
    private final TaClient internalTaClient;
    private final ExternalTaClient externalTaClient;

    public DividendSettingService(DividendSettingRepository dividendSettingRepository,
                                  ProductRepository productRepository,
                                  @Qualifier("inProcessInternalTaClient") TaClient internalTaClient,
                                  @Qualifier("inProcessExternalTaClient") ExternalTaClient externalTaClient) {
        this.dividendSettingRepository = dividendSettingRepository;
        this.productRepository = productRepository;
        this.internalTaClient = internalTaClient;
        this.externalTaClient = externalTaClient;
    }

    /** 设置分红方式：先走 TA 报文登记，受理成功后落库 */
    @Transactional
    public Map<String, Object> set(String customerNo, DividendSettingRequest request) {
        Product product = productRepository.findByProductCode(request.productCode())
                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                        "产品不存在：" + request.productCode()));

        // TA 报文登记（自营 → 本行 TA 同步确认；代销 → 外部 TA 受理）
        DividendCommand cmd = new DividendCommand(customerNo,
                request.productCode(), request.dividendType());
        TaResult taResult = "PROPRIETARY".equals(product.getProductType())
                ? internalTaClient.setDividendType(cmd)
                : externalTaClient.setDividendType(cmd);
        if ("REJECTED".equals(taResult.status())) {
            throw new TradeException("TA_REJECTED", "TA 拒绝分红方式登记：" + taResult.message());
        }

        DividendSetting setting = dividendSettingRepository
                .findByCustomerNoAndProductCode(customerNo, request.productCode())
                .orElseGet(() -> {
                    DividendSetting s = new DividendSetting();
                    s.setCustomerNo(customerNo);
                    s.setProductCode(request.productCode());
                    return s;
                });
        setting.setDividendType(request.dividendType());
        setting.setUpdatedAt(LocalDateTime.now());
        dividendSettingRepository.save(setting);

        return Map.of(
                "productCode", request.productCode(),
                "productType", product.getProductType(),
                "dividendType", request.dividendType(),
                "taSerialNo", taResult.serialNo(),
                "taMessage", taResult.message());
    }

    /** 本人全部分红方式设置（含未设置产品默认 CASH 提示，仅返回已设置项） */
    @Transactional
    public List<Map<String, Object>> list(String customerNo) {
        return dividendSettingRepository.findByCustomerNo(customerNo).stream()
                .map(s -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("productCode", s.getProductCode());
                    item.put("dividendType", s.getDividendType());
                    item.put("updatedAt", s.getUpdatedAt());
                    return item;
                })
                .toList();
    }
}
