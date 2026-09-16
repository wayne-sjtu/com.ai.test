package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 渠道（数据驱动渠道差异化管理，仅 PC Web 真实实现） */
@Entity
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_code", nullable = false, unique = true, length = 32)
    private String channelCode;

    @Column(name = "channel_name", nullable = false, length = 50)
    private String channelName;

    /** PC_WEB / APP / H5 / MINI_PROGRAM */
    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    /** 核心渠道标识（非核心渠道允许 Mock） */
    @Column(name = "core_flag", nullable = false)
    private Boolean coreFlag;

    /** ACTIVE / SUSPENDED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public Boolean getCoreFlag() { return coreFlag; }
    public void setCoreFlag(Boolean coreFlag) { this.coreFlag = coreFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
