# GemeOpen MQTT JSON 报文共性

> 基于 GSPM1B 接入与 [transparent-codec.js](../gspm1b/transparent-codec.js) 归纳。其它 GemeOpen 型号若文档结构类似，应归入同一协议族，差异用 [Product Profile](./product-profile-schema.md) 描述。

## 传输

| 项 | 约定 |
|----|------|
| 载体 | MQTT，Payload 为 **UTF-8 JSON 对象**（无 JetLinks DIRECT 外壳、无 base64） |
| 设备 ID | 通常等于 JSON 字段 `mac`，与 JetLinks `deviceId` 一致 |
| Topic（推荐） | 上行 `/{productId}/{deviceId}/up`，下行 `/{productId}/{deviceId}/down` |
| 原厂 Topic（可选） | `{prefix}/ERP-GSPM1B-{mac}/user/update` ↑、`user/get` ↓，经 EMQX 映射，见 [gspm1b/emqx-bridge.md](../gspm1b/emqx-bridge.md) |

## 通用字段

| 字段 | 方向 | 说明 |
|------|------|------|
| `messageId` | 双向 | 平台下发须用 **JetLinks 雪花 ID**；设备回复须 **原样带回** |
| `mac` | 上行 | 设备 MAC，用于校验 / 解析 deviceId |
| `type` | 双向 | 报文类别（`event` / `setting` / `info` / `statistic`），见下表 |
| `commandName` | 上行 | 如 `controller-event`，表示具体业务子类型 |
| `source` | 上行 | `command`＝平台指令响应；`auto`＝定时/主动上报 |

部分上行完整包内另有设备型号字符串（如 `"type":"GSPM1B"` 在厂商文档中与类别字段混用时的命名习惯）；Profile 中应写清 **型号识别规则**（如 JSON 内独立字段、或 `commandName` 组合）。

## 下行 `type` 类别（平台 → 设备）

| type | 含义 | GSPM1B 示例 |
|------|------|-------------|
| `event` | 控制类（开关等） | `{"type":"event","key":0\|1,"messageId":"…"}` |
| `setting` | 参数设置 | `timerEnable`、`keyLock`、`system:restart` |
| `info` | 查询设备信息 | `{"type":"info","messageId":"…"}` |
| `statistic` | 查询统计量 | `{"type":"statistic","messageId":"…"}` |

整型字段在 JSON 中必须为 **整数**（`1` 而非 `1.0`）。

## 上行语义分类

```text
上行 JSON
    │
    ├─ isCommandResponse? ──► INVOKE_FUNCTION_REPLY（messageId 对齐）
    │       source=command
    │       或 commandName=info-all|info-statistic 且带 messageId
    │
    ├─ controller-event? ──► EVENT + 属性 REPORT（可选组合消息）
    │
    └─ 其它有效字段 ──► REPORT_PROPERTY
```

### 功能回复判定（与 JetLinks 闭环）

满足其一即视为 **功能执行完成回包**：

1. `source === "command"` 且带 `messageId`  
2. `commandName` 为 `info-all` / `info-statistic`（且非 `auto`）且带 `messageId`  

须转换为 `FunctionInvokeMessageReply`，`messageId` 与下发一致，`output` 为属性 Map 或 `true`。

### 定时 / 本地触发

`source === "auto"` 或非 command 响应的上报：**仅属性/事件**，不当作功能回复。

## 与 JetLinks 消息类型映射

| GemeOpen 语义 | JetLinks MessageType |
|---------------|----------------------|
| 属性上报 | `REPORT_PROPERTY` |
| 控制器事件 | `EVENT`（如 `controllerEvent`） |
| 指令响应 | `INVOKE_FUNCTION_REPLY` |
| 平台功能调用 | `INVOKE_FUNCTION` → 编码为下行 JSON |
| 写属性（如 switchState） | `WRITE_PROPERTY` → 通常映射为 `type:event` + `key` |

## 多型号扩展点

| 差异类型 | 处理方式 |
|----------|----------|
| 字段名相同、仅物模型 ID 不同 | 仅换 `metadata.json` + Profile 中 `productId` |
| 多出的传感器字段 | Profile `propertyMapping` 增项 |
| 不同 `commandName` / 事件 | Profile `events` / `commandResponse` |
| 不同下行 type 组合 | Profile `functions` 映射 |
| 完全不同报文结构 | 新 Profile；必要时 `codecVersion` 分支或子 Codec |
