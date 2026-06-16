# GemeOpen（智鸟）协议接入规范

本目录描述 **GemeOpen 官方 MQTT JSON 协议族** 在 JetLinks 上的长期方案：独立协议 JAR，支持多款结构类似的产品（首个落地：**GSPM1B**，见 [../gspm1b/](../gspm1b/)）。

| 文档 | 说明 |
|------|------|
| [protocol-jar-design.md](./protocol-jar-design.md) | **协议 JAR 开发方案**（架构、模块、扩展点、迁移、里程碑） |
| [message-envelope.md](./message-envelope.md) | 协议族共性：JSON 信封、上下行 `type`、回复判定 |
| [product-profile-schema.md](./product-profile-schema.md) | 多产品扩展：Profile 配置结构与 GSPM1B 示例 |

## 现状与目标

| 阶段 | 方案 | 状态 |
|------|------|------|
| PoC / 首款 | JetLinks 官方协议 + 透传脚本 + 社区补丁 | ✅ GSPM1B 已跑通，见 [gspm1b/接入全过程.md](../gspm1b/接入全过程.md) |
| 量产 / 多型号 | **`jetlinks-gemeopen-protocol` 独立协议 JAR** | 📋 本文档规划，待开发 |

## 原则

1. **协议管传输与报文**，物模型（`metadata.json`）管业务语义。  
2. **一款产品一个 JetLinks 产品 ID**，共用同一协议包 ID（如 `gemeopen-mqtt`）。  
3. **上下行 Topic 分离**：`/{productId}/{deviceId}/up` / `down`（与 GSPM1B 方案 A 一致）。  
4. **差异放在 Product Profile**，不把每款设备的字段映射写死在 Java 里（除非性能或安全需要）。

## 相关仓库路径（规划）

```text
gemeopen-protocol/                    # 新建 Maven 模块（或独立仓库）
├── gemeopen-protocol-core/             # 编解码内核、Profile 加载
├── gemeopen-protocol-jetlinks/         # ProtocolSupportProvider、MqttRoute
└── gemeopen-protocol-profiles/         # gspm1b.json、后续型号…
```

部署：打包为 `jetlinks-gemeopen-protocol-{version}.jar`，上传至 JetLinks **协议管理**，产品在「消息协议」中选择 **GemeOpen MQTT JSON**。
