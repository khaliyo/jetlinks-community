# GSPM1B EMQX Topic 桥接规则

当设备 **无法修改** 默认 MQTT Topic 时使用。若设备已按 JetLinks 方案 A 配置为 `/up` + `/down`，且 **直连 JetLinks MQTT**，则 **不需要** 本桥接。

## 参数说明（请按实际环境修改）

| 变量 | 示例值 | 说明 |
|------|--------|------|
| `JETLINKS_PRODUCT_ID` | `gspm1b` | JetLinks 产品 ID，与物模型 metadata.id 一致 |
| `GSPM_TOPIC_PREFIX` | `/a1ezmNNNr57` | 设备文档中的 Topic 前缀（每家配置可能不同） |
| `JETLINKS_MQTT_HOST` | `127.0.0.1` | JetLinks MQTT 服务地址 |
| `JETLINKS_MQTT_PORT` | `1883` | JetLinks MQTT 端口（网络组件里配置的端口） |
| `EMQX_DASHBOARD` | `http://127.0.0.1:18083` | 你环境中的 yudao-emqx 控制台 |

设备原厂 Topic（来自 GSPM 文档/样例）：

```
设备上报(发布): {GSPM_TOPIC_PREFIX}/ERP-GSPM1B-{mac}/user/update
平台下发(订阅): {GSPM_TOPIC_PREFIX}/ERP-GSPM1B-{mac}/user/get
```

JetLinks 透传 Topic（方案 A，推荐直连设备配置）：

```
上行: /{JETLINKS_PRODUCT_ID}/{mac}/up
下行: /{JETLINKS_PRODUCT_ID}/{mac}/down
```

---

## 方案 A：EMQX 5.x 规则引擎（推荐）

在 EMQX Dashboard → **数据集成 → 规则** 中创建两条规则。

### 规则 1：设备上行 → JetLinks

**SQL：**

```sql
SELECT
  payload,
  regex_replace(nth(4, split(topic, '/')), 'ERP-GSPM1B-', '') AS device_id
FROM
  "/a1ezmNNNr57/ERP-GSPM1B-+/user/update"
```

**动作：消息重发布**

- **重发布 Topic：** `/gspm1b/${device_id}/up`
- **Payload：** `${payload}`（原样转发 JSON 字节）

### 规则 2：JetLinks 下行 → 设备

**SQL：**

```sql
SELECT
  payload,
  nth(2, split(topic, '/')) AS device_id
FROM
  "/gspm1b/+/down"
```

**动作：消息重发布**

- **重发布 Topic：** `/a1ezmNNNr57/ERP-GSPM1B-${device_id}/user/get`
- **Payload：** `${payload}`

---

## 方案 B：EMQX 规则 + 桥接到 JetLinks Broker

若 JetLinks MQTT 与 EMQX 是 **两个独立 Broker**，可配置 EMQX **数据桥接**：

1. 创建 MQTT 桥接，指向 `JETLINKS_MQTT_HOST:JETLINKS_MQTT_PORT`
2. 上行规则桥接发布到 `/gspm1b/${device_id}/up`
3. 下行监听 `/gspm1b/+/down` 转发到设备 `user/get`

若设备 **直接连 JetLinks MQTT**（同一 Broker），优先用下方方案 C，不需要桥接。

---

## 方案 C：直接改设备 MQTT 配置（最简单）

通过设备「协议信息」相关指令，把 MQTT 指向 JetLinks，并修改 Topic：

**下发示例（经 JetLinks 功能或临时 MQTT 客户端发送）：**

```json
{
  "messageId": "202201241610366046",
  "type": "setting",
  "protocol": "mqtt",
  "server": "192.168.0.4",
  "port": "1883",
  "clientId": "c82b96f821e2",
  "username": "iot_user",
  "password": "your_password",
  "publish": "/gspm1b/c82b96f821e2/up",
  "subcribe": "/gspm1b/c82b96f821e2/down"
}
```

> 字段名 `subcribe` 为设备文档原文拼写，请勿改成 subscribe。

---

## 验证清单

1. 用 MQTTX 向 `/a1ezmNNNr57/ERP-GSPM1B-testmac/user/update` 发一条样例 JSON（或直连 `/gspm1b/testmac/up`）
2. 在 EMQX 规则追踪中确认已转发到 `/gspm1b/testmac/up`
3. JetLinks 设备实例 `testmac` 属性有更新
4. 在 JetLinks 调用功能 `powerControl`，确认下行出现在 `/gspm1b/testmac/down`，并经 EMQX（如有）转到 `user/get`
