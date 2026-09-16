-- ============================================================
-- 理财产品销售平台 · 建表脚本（依据 docs/design/technical-design.md 第 3 章）
-- 兼容 H2 MODE=MySQL（local Profile）与 MySQL 8（compose Profile）
-- 约定：不设外键约束，关联完整性由应用层保证（Phase 1 简化决策）
-- ============================================================

DROP TABLE IF EXISTS customer;
DROP TABLE IF EXISTS operator;
DROP TABLE IF EXISTS channel;
DROP TABLE IF EXISTS wealth_account;
DROP TABLE IF EXISTS trading_permission;
DROP TABLE IF EXISTS risk_assessment;
DROP TABLE IF EXISTS sign_document;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS product_nav;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS order_event;
DROP TABLE IF EXISTS capital_account;
DROP TABLE IF EXISTS capital_flow;
DROP TABLE IF EXISTS position;
DROP TABLE IF EXISTS invest_plan;
DROP TABLE IF EXISTS dividend_setting;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS ticket;
DROP TABLE IF EXISTS dual_record;
DROP TABLE IF EXISTS suitability_log;
DROP TABLE IF EXISTS suitability_rule;
DROP TABLE IF EXISTS faq;

-- 客户（C 端身份体系）
CREATE TABLE customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    real_name_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    channel_code VARCHAR(32) NOT NULL DEFAULT 'PC_WEB',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_no UNIQUE (customer_no),
    CONSTRAINT uk_customer_mobile UNIQUE (mobile)
);

-- 管理端用户（独立身份体系）
CREATE TABLE operator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operator_username UNIQUE (username)
);

-- 渠道（数据驱动差异化管理）
CREATE TABLE channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(32) NOT NULL,
    channel_name VARCHAR(50) NOT NULL,
    channel_type VARCHAR(20) NOT NULL,
    core_flag BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_channel_code UNIQUE (channel_code)
);

-- 理财账户（签约状态机）
CREATE TABLE wealth_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_no VARCHAR(32) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNSIGNED',
    ta_account_no VARCHAR(32),
    signed_at TIMESTAMP,
    terminated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wa_account_no UNIQUE (account_no)
);

-- 交易权限（自营/代销独立开关）
CREATE TABLE trading_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    opened_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tp_account_type UNIQUE (account_id, permission_type)
);

-- 风险测评（append-only，应用层禁 UPDATE/DELETE）
CREATE TABLE risk_assessment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    answers VARCHAR(500) NOT NULL,
    score INT NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 电子签署文件（适当性档案留痕）
CREATE TABLE sign_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    product_code VARCHAR(32) NOT NULL DEFAULT '-',
    signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sd_cust_type_product UNIQUE (customer_no, doc_type, product_code)
);

-- 产品（自营/代销统一货架）
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(32) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    issuer_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    term_days INT NOT NULL,
    min_purchase_amount DECIMAL(18, 2) NOT NULL,
    purchase_fee_rate DECIMAL(6, 4) NOT NULL DEFAULT 0,
    redemption_fee_rate DECIMAL(6, 4) NOT NULL DEFAULT 0,
    -- 巨额赎回阈值（单日赎回份额超产品总份额该比例时延期确认，设计文档 5.2）
    redeem_threshold_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.1000,
    expected_return VARCHAR(50),
    total_quota DECIMAL(18, 2) NOT NULL,
    used_quota DECIMAL(18, 2) NOT NULL DEFAULT 0,
    trade_start_time TIME NOT NULL,
    trade_end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_code UNIQUE (product_code)
);

-- 产品净值（自营 INTERNAL 发布 / 代销 EXTERNAL 同步）
CREATE TABLE product_nav (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(32) NOT NULL,
    nav_date DATE NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    synced_at TIMESTAMP,
    CONSTRAINT uk_nav_product_date UNIQUE (product_code, nav_date)
);

-- 订单（单表 + product_type 隔离；client_request_id 幂等）
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    amount DECIMAL(18, 2),
    shares DECIMAL(18, 2),
    fee DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    confirm_risk BOOLEAN NOT NULL DEFAULT FALSE,
    ta_serial_no VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_no UNIQUE (order_no),
    CONSTRAINT uk_client_request_id UNIQUE (client_request_id)
);

-- 订单事件轨迹（append-only，可追溯）
CREATE TABLE order_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    ta_tag VARCHAR(10),
    ta_serial_no VARCHAR(40),
    detail VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 资金账户（简化账务）
CREATE TABLE capital_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    available_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ca_customer UNIQUE (customer_no)
);

-- 资金流水（幂等键 = 订单号 + 动作类型）
CREATE TABLE capital_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    balance_after DECIMAL(18, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cf_order_action UNIQUE (order_no, action_type)
);

-- 份额持仓（乐观锁）
CREATE TABLE position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    shares DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen_shares DECIMAL(18, 2) NOT NULL DEFAULT 0,
    cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pos_customer_product UNIQUE (customer_no, product_code)
);

-- 投资计划（预约申购/定投）
CREATE TABLE invest_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_no VARCHAR(32) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    trigger_date DATE,
    period_type VARCHAR(10),
    next_trigger_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_no UNIQUE (plan_no)
);

-- 分红方式设置
CREATE TABLE dividend_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    dividend_type VARCHAR(20) NOT NULL DEFAULT 'CASH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ds_customer_product UNIQUE (customer_no, product_code)
);

-- 站内消息
CREATE TABLE message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    msg_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 客服工单
CREATE TABLE ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(32) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    ticket_type VARCHAR(20) NOT NULL,
    product_code VARCHAR(32),
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    external_sync_status VARCHAR(20),
    handler VARCHAR(50),
    reply VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_no UNIQUE (ticket_no)
);

-- 双录（代销 R4/R5 前置，Mock 标志文件）
CREATE TABLE dual_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    file_flag VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dr_order_no UNIQUE (order_no)
);

-- 适当性 C×R 动作映射（数据驱动，设计文档第 7 章）
CREATE TABLE suitability_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_level VARCHAR(10) NOT NULL,
    product_level VARCHAR(10) NOT NULL,
    action VARCHAR(20) NOT NULL,
    CONSTRAINT uk_suit_rule UNIQUE (customer_level, product_level)
);

-- 适当性校验留痕
CREATE TABLE suitability_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    customer_risk_level VARCHAR(10) NOT NULL,
    product_risk_level VARCHAR(10) NOT NULL,
    result VARCHAR(20) NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    order_no VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 投教 FAQ（AI 问答降级知识库）
CREATE TABLE faq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(200) NOT NULL,
    answer VARCHAR(1000) NOT NULL,
    keywords VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
