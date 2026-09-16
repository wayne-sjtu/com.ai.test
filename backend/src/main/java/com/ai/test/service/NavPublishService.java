package com.ai.test.service;

import com.ai.test.model.NavPublishRequest;
import com.ai.test.repository.MessageRepository;
import com.ai.test.repository.PositionRepository;
import com.ai.test.repository.ProductNavRepository;
import com.ai.test.repository.ProductRepository;
import com.ai.test.repository.entity.Message;
import com.ai.test.repository.entity.Position;
import com.ai.test.repository.entity.Product;
import com.ai.test.repository.entity.ProductNav;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自营净值发布用例（spec 用户故事 33，管理端运营）：
 * 仅自营产品可本端发布（代销净值以外部 TA 同步为准）；
 * 每产品每估值日唯一（append-only，不覆盖历史）；
 * 发布后向全体持有人推送净值站内消息（spec 用户故事 28）。
 */
@Service
public class NavPublishService {

        private final ProductRepository productRepository;
        private final ProductNavRepository productNavRepository;
        private final PositionRepository positionRepository;
        private final MessageRepository messageRepository;

        public NavPublishService(ProductRepository productRepository,
                        ProductNavRepository productNavRepository,
                        PositionRepository positionRepository,
                        MessageRepository messageRepository) {
                this.productRepository = productRepository;
                this.productNavRepository = productNavRepository;
                this.positionRepository = positionRepository;
                this.messageRepository = messageRepository;
        }

        @Transactional
        public ProductNav publish(NavPublishRequest request) {
                Product product = productRepository.findByProductCode(request.productCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                                                "产品不存在：" + request.productCode()));
                if (!"PROPRIETARY".equals(product.getProductType())) {
                        throw new TradeException("NAV_CONSIGNMENT_FORBIDDEN",
                                        "代销产品净值以发行机构（外部 TA）同步为准，不可在本端发布");
                }
                if (productNavRepository.findByProductCodeAndNavDate(
                                request.productCode(), request.navDate()).isPresent()) {
                        throw new TradeException("NAV_ALREADY_EXISTS",
                                        "该产品 " + request.navDate() + " 净值已发布，不允许重复发布或修改");
                }

                ProductNav nav = new ProductNav();
                nav.setProductCode(request.productCode());
                nav.setNavDate(request.navDate());
                nav.setNav(request.nav());
                nav.setSource("INTERNAL");
                nav.setSyncedAt(LocalDateTime.now());
                productNavRepository.save(nav);

                // 净值消息提醒：推送全体持有人（站内信；按产品查询避免全表扫描）
                List<Position> holders = positionRepository.findByProductCode(request.productCode()).stream()
                                .filter(p -> p.getShares().signum() > 0)
                                .toList();
                LocalDateTime now = LocalDateTime.now();
                for (Position holder : holders) {
                        Message message = new Message();
                        message.setCustomerNo(holder.getCustomerNo());
                        message.setMsgType("NAV");
                        message.setTitle(product.getProductName() + " 最新净值公布");
                        message.setContent(product.getProductName() + "（" + request.navDate()
                                        + "）单位净值 " + request.nav().toPlainString()
                                        + "，请前往持仓查看最新估值。");
                        message.setReadFlag(false);
                        message.setCreatedAt(now);
                        messageRepository.save(message);
                }
                return nav;
        }
}
