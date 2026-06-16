# Product Profile 配置结构

每款 GemeOpen 产品在协议 JAR 内对应一份 **Profile**（JSON，位于 `resources/gemeopen/profiles/{productId}.json`）。  
JetLinks 产品 ID 与 Profile 文件名一致，例如 `gspm1b.json`。

## 顶层结构

```json
{
  "profileVersion": "1",
  "productId": "gspm1b",
  "name": "GSPM1B智能插座",
  "deviceModelHint": "GSPM1B",
  "topic": {
    "upstream": "/{productId}/{deviceId}/up",
    "downstream": "/{productId}/{deviceId}/down"
  },
  "propertyMapping": {},
  "enumProperties": [],
  "intProperties": [],
  "events": {},
  "commandResponse": {},
  "functions": {},
  "writeProperties": {}
}
```

## 字段说明

### `propertyMapping`

设备 JSON 字段 → 物模型属性 ID。

```json
"propertyMapping": {
  "key": "switchState",
  "current": "current",
  "power": "power",
  "voltage": "voltage",
  "energy": "energy",
  "signal": "signal",
  "onState": "onState",
  "keyLock": "keyLock",
  "wifiLock": "wifiLock",
  "timerEnable": "timerEnable",
  "timerInterval": "timerInterval",
  "version": "version",
  "ip": "ip",
  "ssid": "ssid"
}
```

### `enumProperties` / `intProperties`

上报类型规范化（避免 `1.0`、enum 不匹配）：

```json
"enumProperties": ["switchState", "onState", "keyLock", "wifiLock", "timerEnable"],
"intProperties": ["signal", "timerInterval"]
```

### `commandResponse`

功能回复识别规则（规则之间为 OR）：

```json
"commandResponse": {
  "rules": [
    { "source": "command" },
    {
      "commandNameIn": ["info-all", "info-statistic"],
      "sourceNot": "auto",
      "requireMessageId": true
    }
  ]
}
```

### `events`

```json
"events": {
  "controllerEvent": {
    "match": { "commandName": "controller-event" },
    "payload": ["key", "onState", "mac"]
  }
}
```

### `functions`

物模型功能 ID → 下行 JSON。`messageId` 由编码器注入平台雪花 ID。

```json
"functions": {
  "powerControl": {
    "downstream": {
      "type": "event",
      "fields": { "key": "${inputs.key}" }
    }
  },
  "queryInfo": {
    "downstream": { "type": "info", "fields": {} }
  },
  "setTimerReport": {
    "downstream": {
      "type": "setting",
      "fields": {
        "timerEnable": "${inputs.timerEnable}",
        "timerInterval": "${inputs.timerInterval}"
      }
    }
  }
}
```

### `writeProperties`

```json
"writeProperties": {
  "switchState": {
    "downstream": {
      "type": "event",
      "fields": { "key": "${value}" }
    }
  }
}
```

## 新型号接入 checklist

1. 向厂商索取 JSON 样例（上行 auto/command、下行各 type）。  
2. 对比 GSPM1B，更新 `propertyMapping` / `functions` / `commandResponse`。  
3. 新建 JetLinks 产品与 `metadata.json`。  
4. 新增 `profiles/{productId}.json`，**无需改协议主代码**（除非全新报文结构）。  
5. MQTT 验证 + 功能 messageId 闭环 + 存储策略。

实现阶段可将 [../gspm1b/transparent-codec.js](../gspm1b/transparent-codec.js) 转为 `profiles/gspm1b.json`，物模型仍用 [../gspm1b/metadata.json](../gspm1b/metadata.json)。
