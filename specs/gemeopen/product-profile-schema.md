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
  "stringEnumProperties": [],
  "intProperties": [],
  "floatProperties": [],
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

### `enumProperties` / `stringEnumProperties` / `intProperties` / `floatProperties`

上报类型规范化（避免 `1.0`、enum 不匹配）：

```json
"enumProperties": ["switchState", "onState", "keyLock", "wifiLock", "timerEnable"],
"stringEnumProperties": ["playerMode"],
"intProperties": ["signal", "timerInterval"],
"floatProperties": ["current", "power", "voltage", "energy"]
```

- `enumProperties`：设备上报为 **整型** 的枚举，解码为字符串 `"0"` / `"1"` 等（对齐 JetLinks enum 物模型）。
- `stringEnumProperties`：设备上报为 **字符串** 的枚举（如 `playerMode=OnePlay`），原样透传，不做 `Integer.parseInt`。
- 同一属性不要同时出现在 `enumProperties` 与 `intProperties` 中。

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

物模型功能 ID → 下行 JSON。`messageId` 默认由编码器在模板渲染后注入；Profile 中也可写 `${messageId}` 占位符（与平台 ID 一致）。`fields` 支持 **嵌套对象**（如红外 `data.no`、插座倒计时 `finishCommand`）。

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


### 占位符

| 占位符 | 含义 |
|--------|------|
| `${inputs.xxx}` | 功能入参 |
| `${value}` | 写属性值（`writeProperties`） |
| `${messageId}` | 平台下发的 messageId（可选；未写时编码器自动追加） |

### `propertyMapping` 点路径

源字段可写 `command.key` 形式：解码时先查顶层键，再按 `.` 进入嵌套 Map（如定时任务 `command.key` → `timerTaskCommandKey`）。

## 已接入 Profile 清单（2026-06）

| productId | 说明 | 物模型 |
|-----------|------|--------|
| gspm1b | 单路插座 | `specs/metadata/gspm1b-metadata.json` |
| gscw1m2p | 双路开关 | `specs/metadata/gscw1m2p-metadata.json` |
| gspw1b / gspw1b2 | 86 插座 | `specs/metadata/gspw1b*-metadata.json` |
| gscu1b | 红外控制器 | `specs/metadata/gscu1b-metadata.json` |
| gssm0b | 音频播放器 | `specs/metadata/gssm0b-metadata.json` |
