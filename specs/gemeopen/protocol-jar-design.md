# GemeOpen 官方协议 JAR 开发方案

> **版本：** 2026-06-02  
> **状态：** 设计稿（待实施）  
> **首款参考实现：** [GSPM1B](../gspm1b/)（透传 PoC 已验证）  
> **相关：** [报文共性](./message-envelope.md) · [Profile 结构](./product-profile-schema.md)

---

## 1. 背景与目标

### 1.1 现状

GSPM1B 已通过 **JetLinks 官方 MQTT 协议 + 透传脚本 + 社区补丁** 接入，能力完整，但存在：

| 问题 | 说明 |
|------|------|
| 全局副作用 | `DirectPayloadDeviceMessageCodec`、`MqttClientDeviceGateway` 等补丁影响所有官方 MQTT 产品 |
| 多型号扩展成本高 | 每款产品复制 `transparent-codec.js`、易 drift |
| Topic / async 靠补丁 | 上下行分离、messageId 闭环分散在 Connector / Service 层 |
| 运维复杂 | 脚本在 DB、Java 补丁在仓库，版本要对齐 |

### 1.2 目标

开发 **`jetlinks-gemeopen-protocol`** 官方协议 JAR：

1. **一款协议包** 服务 GemeOpen **全系列** JSON 类似产品。  
2. **产品差异** 用 Profile（JSON）配置，而非 fork 协议代码。  
3. **零依赖** 透传脚本与全局 Direct 包装（GemeOpen 产品迁移后）。  
4. 与 JetLinks 2.10 社区版 **协议管理 / MQTT Client 网关 / 物模型** 标准集成。

### 1.3 非目标（第一版不做）

- 不支持非 MQTT 传输（HTTP/CoAP 等）。  
- 不在协议内实现 EMQX 规则（仍见 [emqx-bridge.md](../gspm1b/emqx-bridge.md)）。  
- 不替代 JetLinks 物模型编辑器（仍 per-product `metadata.json`）。

---

## 2. 总体架构

```text
┌─────────────────────────────────────────────────────────────────┐
│  JetLinks 产品 (gspm1b / gsxxx / …)                              │
│  messageProtocol = gemeopen-mqtt                                 │
│  metadata.json = 物模型（每产品独立）                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│  jetlinks-gemeopen-protocol.jar                                  │
│  GemeOpenProtocolSupportProvider                                 │
│    ├─ MqttRoute: /{productId}/{deviceId}/up   (upstream)         │
│    ├─ MqttRoute: /{productId}/{deviceId}/down (downstream)       │
│    └─ GemeOpenMqttMessageCodec                                     │
│         ├─ ProfileRegistry ← profiles/*.json                     │
│         ├─ UpstreamDecoder  (JSON → DeviceMessage)               │
│         └─ DownstreamEncoder (DeviceMessage → JSON bytes)        │
└────────────────────────────┬────────────────────────────────────┘
                             │ MQTT 纯 JSON
┌────────────────────────────▼────────────────────────────────────┐
│  GemeOpen 设备 (mac = deviceId)                                  │
│  publish /up  ·  subscribe /down                                 │
└─────────────────────────────────────────────────────────────────┘
```

**职责边界：**

| 层级 | 负责 |
|------|------|
| 协议 JAR | Topic、JSON 编解码、messageId、功能回复判定、整型 JSON |
| Profile | 字段映射、功能/事件/写属性模板、commandResponse 规则 |
| 物模型 | 属性/功能/事件 ID、类型、enum、async（建议 false） |
| 平台 | 存储、规则、UI、网关接入 |

---

## 3. Maven 模块规划

```text
jetlinks-community/
└── gemeopen-protocol/
    ├── pom.xml
    ├── gemeopen-protocol-core/
    │   └── cn/gemeopen/protocol/
    │       ├── model/GemeOpenPayload.java
    │       ├── profile/ProductProfile.java
    │       ├── profile/ProfileLoader.java
    │       ├── codec/UpstreamDecoder.java
    │       ├── codec/DownstreamEncoder.java
    │       └── util/JsonWriters.java
    ├── gemeopen-protocol-jetlinks/
    │   └── cn/gemeopen/protocol/jetlinks/
    │       ├── GemeOpenProtocolSupportProvider.java
    │       ├── GemeOpenProtocolSupport.java
    │       ├── GemeOpenMqttMessageCodec.java
    │       └── route/GemeOpenMqttRoutes.java
    └── gemeopen-protocol-profiles/
        └── resources/gemeopen/profiles/
            ├── gspm1b.json
            └── _template.json
```

**依赖：** `jetlinks-core`（provided）、Jackson、JUnit 5。

**打包产物：** `jetlinks-gemeopen-protocol-1.0.0.jar`（profiles 打入同 jar）。

---

## 4. JetLinks SPI 集成

### 4.1 协议标识

| 项 | 值 |
|----|-----|
| 协议 ID | `gemeopen-mqtt` |
| 名称 | GemeOpen MQTT JSON |
| 传输 | `MQTT` |
| Provider | `cn.gemeopen.protocol.jetlinks.GemeOpenProtocolSupportProvider` |

`META-INF/services/org.jetlinks.core.spi.ProtocolSupportProvider` 注册。

### 4.2 MQTT 路由

```java
MqttRoute.builder().topic("/{productId}/{deviceId}/up").upstream(true).qos(1).build();
MqttRoute.builder().topic("/{productId}/{deviceId}/down").upstream(false).qos(1).build();
```

网关按协议包自动订阅 `/+/+/up`，**无需** `MqttClientDeviceGateway` 补丁。

### 4.3 DeviceMessageCodec

**decode：** Topic 解析 productId → Profile → JSON → `UpstreamDecoder` → `DeviceMessage`（与 [transparent-codec.js](../gspm1b/transparent-codec.js) 语义一致）。

**encode：** `FunctionInvokeMessage` / `WritePropertyMessage` → Profile 模板 → 注入平台 messageId → `SimpleMqttMessage` 原始 JSON 到 `/down`。

**不经过** `DirectDeviceMessage` 与官方 DIRECT 外壳。

### 4.4 功能调用

- 物模型功能 **`async: false`**（有 `source=command` 回包）。  
- 不依赖 `LocalDeviceInstanceService` 特殊过滤。

---

## 5. 核心类（core 模块）

| 类 | 职责 |
|----|------|
| `ProductProfile` | 对应 [product-profile-schema.md](./product-profile-schema.md) |
| `ProfileRegistry` | 加载 `classpath:/gemeopen/profiles/{productId}.json` |
| `UpstreamDecoder` | commandResponse / event / report |
| `DownstreamEncoder` | functions / writeProperties + 整型 JSON |
| `JsonWriters` | 避免 `1.0` |

单测使用 GSPM1B MQTT 抓包样例（见 specs/gspm1b）。

---

## 6. 多产品扩展

```text
gemeopen-protocol (共用 Codec)
    ├── profiles/gspm1b.json  → JetLinks 产品 gspm1b + metadata.json
    ├── profiles/gsxxx.json   → JetLinks 产品 gsxxx + metadata.json
    └── …
```

| 场景 | 动作 |
|------|------|
| JSON 90% 相同 | 复制 Profile，改 mapping/functions |
| 新属性 | Profile + metadata 增字段 |
| 新 commandName | Profile `events` / `commandResponse` |
| 全新结构 | 新 Profile 或 codec 分支 |

---

## 7. GSPM1B 迁移

| 步骤 | 操作 |
|------|------|
| M1 | core 单测 = transparent-codec.js 行为 |
| M2 | SPI 本地 jar 联调 |
| M3 | staging 产品切 `gemeopen-mqtt` |
| M4 | 删透传脚本；生产切换 |
| M5 | 撤销全局补丁（§8） |

| 项 | 透传 | 协议 JAR |
|----|------|----------|
| 消息协议 | JetLinks 官方 | **gemeopen-mqtt** |
| 透传脚本 | 需要 | 不需要 |
| 物模型 | metadata.json | 不变 |

---

## 8. 补丁撤销（迁移后）

| 补丁 | 迁移后 |
|------|--------|
| `RenameProtocolSupport` 全局 Direct 包装 | GemeOpen 不依赖；视情况保留给其他透传 |
| `MqttClientDeviceGateway` `/up` 补丁 | **删除** |
| `TransparentDeviceMessageConnector` GSPM 专用 | 不配置透传 codec |
| `LocalDeviceInstanceService` invoke 特殊逻辑 | 可恢复通用行为 |

**保留：** `DeviceProductDeployHandler`、透传 createId 修复、启动顺序。

---

## 9. 部署

1. 上传 `jetlinks-gemeopen-protocol-{version}.jar` 到协议管理。  
2. 产品选 **GemeOpen MQTT JSON**。  
3. 设备 Topic：`/up` publish，`/down` subscribe。

---

## 10. 里程碑

| 阶段 | 交付 | 预估 |
|------|------|------|
| P0 设计 | 本文档 + schema | ✅ |
| P1 core + 单测 | gspm1b golden cases | 3～5 人日 |
| P2 JetLinks SPI | MqttRoute + 联调 | 2～3 人日 |
| P3 GSPM1B 迁移 | 对比测试 | 1～2 人日 |
| P4 第二款型号 | 新 Profile | 1～2 人日 |
| P5 清理 | 撤销补丁 | 1 人日 |

---

## 11. 风险

| 风险 | 对策 |
|------|------|
| JSON 与文档不一致 | Profile 随 jar 发版；文档化样例 |
| JetLinks 版本 | jetlinks-core provided，CI 对齐 2.10 |
| 原厂 Topic | EMQX → `/up` `/down` |

---

## 12. 下一步

1. 评审协议 ID 与模块路径。  
2. 自 `transparent-codec.js` 生成 `profiles/gspm1b.json`。  
3. 创建 Maven 模块，实现 P1 单测。  
4. staging 联调后生产迁移。
