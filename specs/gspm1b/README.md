# GSPM1B 接入 JetLinks 操作手册

本目录包含智鸟科技 GSPM1B 智能插座接入 JetLinks 社区版所需的全部配置文件。

> **全过程总结（含问题排查与本仓库补丁说明）** 请参阅 **[接入全过程.md](./接入全过程.md)**。本文档侧重分步操作。

## 文件说明

| 文件 | 用途 |
|------|------|
| [状态摘要-2026-06-02.md](./状态摘要-2026-06-02.md) | **最新** 接入状态、阻塞项与待办（2026-06-02） |
| [状态摘要.md](./状态摘要.md) | 历史摘要（enum、messageId 初版结论） |
| [接入全过程.md](./接入全过程.md) | 从 0 到可用的完整总结、验证清单、社区版补丁索引 |
| [metadata.json](./metadata.json) | 产品物模型，导入到 JetLinks 产品 |
| [transparent-codec.js](./transparent-codec.js) | 透传编解码脚本（jsr223） |
| [emqx-bridge.md](./emqx-bridge.md) | EMQX Topic 桥接规则（设备 Topic 无法修改时使用） |

## 接入架构

```
GSPM1B 设备
    │  MQTT 私有 JSON
    ▼
[可选] EMQX Topic 转换
    ▼
JetLinks MQTT 网关  上行 /{productId}/{deviceId}/up  下行 /{productId}/{deviceId}/down
    ▼
透传编解码脚本  →  标准物模型属性/功能/事件
    ▼
JetLinks 平台（规则引擎、告警、可视化）
```

## 第一步：创建产品与物模型

1. **设备管理 → 产品 → 新建**
   - 产品 ID：`gspm1b`（必须与 metadata.json 中 id 一致）
   - 名称：`GSPM1B智能插座`
   - 消息协议：`JetLinks V2.0`
   - 接入方式：MQTT 服务网关（下一步创建）

2. **物模型 → 导入**
   - 复制 [metadata.json](./metadata.json) 全部内容
   - 粘贴到产品物模型编辑器，或「导入 JSON」

3. **产品 → 设备接入配置 → MQTT 认证**（可选）
   - 若设备 username/password 固定，可在产品配置中设置 `secureId` / `secureKey`
   - 测试阶段也可在网关侧放宽认证（按你当前 JetLinks 配置）

## 第二步：MQTT 网络与网关

1. **设备接入 → 网络组件 → 新建 MQTT 服务**
   - 端口：如 `1883`（与 docker-compose 中映射端口一致）
   - 绑定地址：`0.0.0.0`

2. **设备接入 → 设备网关 → 新建**
   - 类型：MQTT 服务网关
   - 绑定上一步网络组件
   - 消息协议：`JetLinks V2.0`

3. 回到产品，**接入方式** 选择刚创建的网关

## 第三步：配置透传编解码

> **说明**：JetLinks 2.10 **没有**左侧菜单「透传消息解析」。理论上入口在产品/设备详情里的 **「数据解析」** 页签，但社区版自带的 `jetlinks-official-protocol-3.0-SNAPSHOT.jar` **未向平台注册** `transparentCodec` 特性（尽管已支持 `/*/direct` 透传 Topic），因此即使用 **JetLinks 官方协议 + MQTT Broker 接入**（与你截图一致）也可能 **看不到该页签**。此时请直接用下方 **方式 B（API）** 保存脚本，功能同样生效。

### 方式 A：界面（推荐）

**产品级（对该产品下所有设备生效）**

1. **物联网 → 设备管理 → 产品** → 打开 `gspm1b` 产品详情
2. 若已正确绑定「JetLinks 官方协议」网关，应能看到 **「数据解析」** 页签
3. 在编辑器中粘贴 `transparent-codec.js` 全文，provider 选 `jsr223`，保存

**设备级（仅覆盖单台设备，可选）**

1. **设备管理 → 设备** → 进入某台 GSPM 设备详情
2. 同样进入 **「数据解析」** 页签（名称与产品详情一致）

若看不到「数据解析」：检查产品 **接入方式** 是否为绑定了 **JetLinks 官方协议** 的网关，并确认产品已 **启用**。

### 方式 B：API / Swagger（无页签时可用）

`POST /device/transparent-codec/gspm1b`

**注意**：`configuration.script` 必须是 **JSON 字符串**。脚本里有大量 `"`，**不要**在 Swagger 里手工拼 JSON 粘贴全文，否则引号/换行会导致 JSON 非法。请用下面任一方式 **自动生成请求体**。

#### 推荐：Python 生成 JSON 再提交

在项目根目录执行（先登录平台，把 token 填到 `JETLINKS_TOKEN`）：

```bash
export JETLINKS_URL=http://127.0.0.1:8848
export JETLINKS_TOKEN=<登录后拿到的 token>
export PRODUCT_ID=gspm1b

python3 -c "
import json, pathlib
script = pathlib.Path('specs/gspm1b/transparent-codec.js').read_text(encoding='utf-8')
print(json.dumps({
    'provider': 'jsr223',
    'configuration': {'lang': 'js', 'script': script}
}, ensure_ascii=False))
" > /tmp/gspm1b-codec.json

curl -X POST \"\$JETLINKS_URL/device/transparent-codec/\$PRODUCT_ID\" \\
  -H 'Content-Type: application/json' \\
  -H \"X-Access-Token: \$JETLINKS_TOKEN\" \\
  -d @/tmp/gspm1b-codec.json
```

`json.dumps` 会自动把脚本里的 `"`、换行等转成合法 JSON（例如 `\"`、`\n`），服务端收到的是原始 JS 源码。

#### 备选：jq（若已安装）

```bash
jq -n --rawfile script specs/gspm1b/transparent-codec.js \
  '{provider:"jsr223",configuration:{lang:"js",script:$script}}' \
  > /tmp/gspm1b-codec.json
```

#### Swagger / doc.html

1. 先用上面命令生成 `/tmp/gspm1b-codec.json`
2. 打开 `http://127.0.0.1:8848/doc.html` → **设备透传消息解析配置** → `POST /device/transparent-codec/{productId}`
3. 把 **整个 JSON 文件内容** 复制进请求体（不要只复制 `.js` 文件）

验证：`GET /device/transparent-codec/gspm1b` 应返回已保存的 `provider` 与 `configuration`（`script` 为完整脚本文本）。

需 `transparent-codec` 的 save 权限（admin 默认具备）。

保存后一般会自动热加载；若无效应重启对应设备网关。

## 第四步：注册设备

每台 GSPM1B 在 JetLinks 中创建设备实例：

| 字段 | 值 |
|------|-----|
| 设备 ID | 设备 MAC，如 `c82b96f821e2`（与 JSON 中 mac 一致） |
| 产品 | `gspm1b` |
| 名称 | 自定义 |

**MQTT 连接参数建议：**

| 参数 | 值 |
|------|-----|
| clientId | 与设备 ID 相同（JetLinks 默认用 clientId 识别设备） |
| server | JetLinks MQTT 地址 |
| port | 1883 |

## 第五步：Topic 配置（二选一）

### 选项 A：直接改设备 Topic（推荐）

参考 [emqx-bridge.md 方案 C](./emqx-bridge.md)，把设备 Topic 改为：

```
publish:  /gspm1b/{mac}/up
subscribe: /gspm1b/{mac}/down
```

### 选项 B：EMQX 桥接

设备保持原厂 Topic，按 [emqx-bridge.md](./emqx-bridge.md) 配置 EMQX 规则。

## 第六步：验证

### 1. 开启定时上报

在 JetLinks 设备详情 → **功能调用** → `setTimerReport`：

```json
{
  "timerEnable": 1,
  "timerInterval": 15
}
```

### 2. 查看属性

15 秒内应看到 `current`、`power`、`voltage`、`energy` 等属性更新。

### 3. 通断电测试

调用 `powerControl`：

```json
{ "key": 0 }
```

```json
{ "key": 1 }
```

### 4. 查询设备信息

调用 `queryInfo`，属性 `version`、`ip`、`ssid` 等应刷新。

## 指令对照表

| JetLinks 功能/属性 | GSPM 下发 JSON |
|-------------------|----------------|
| `powerControl` / 写 `switchState` | `{"key":0/1,"messageId":"...","type":"event"}` |
| `restart` | `{"messageId":"...","system":"restart","type":"setting"}` |
| `setTimerReport` | `{"messageId":"...","timerEnable":1,"timerInterval":15,"type":"setting"}` |
| `queryInfo` | `{"messageId":"...","type":"info"}` |
| `queryStatistic` | `{"messageId":"...","type":"statistic"}` |
| `setKeyLock` | `{"messageId":"...","keyLock":0/1,"type":"setting"}` |
| `setOnState` | `{"messageId":"...","onState":0/1/2,"type":"setting"}` |
| `setWifiLock` | `{"messageId":"...","wifiLock":0/1,"type":"setting"}` |

| GSPM 上报 commandName | JetLinks 映射 |
|----------------------|---------------|
| `device-timer-task` | 属性上报（current/power/voltage/energy 等） |
| `controller-event` | 事件 `controllerEvent` + 属性更新 |
| `info-all` / `info-statistic` 等 | 属性上报 |

## 常见问题

**Q: 设备在线但无数据？**  
检查设备 publish 是否为 `/gspm1b/{mac}/up`、subscribe 是否为 `/gspm1b/{mac}/down`，以及透传编解码是否绑定到产品。

**Q: 功能调用无反应？**  
确认下行 Topic 设备能收到；若用 EMQX，检查下行规则是否生效。

**Q: 物模型导入报错？**  
检查 JSON 格式；也可在 UI 中手动创建属性/功能，参照 metadata.json 字段。

**Q: 多款 GSPM 型号？**  
可共用同一产品与脚本；若 commandName 差异大，再按型号拆产品或脚本内分支。

**Q: curl GET 查不到脚本，但重启后仍像旧脚本在执行？**  
1. 库表 `dev_transparent_codec` 里可能仍有旧记录（API 按 `productId`+`deviceId` 算 md5 查 id，与历史脏数据 id 可能对不上）。  
2. 先清库再保存（见下「透传脚本维护」）。  
3. 设备级规则优先于产品级：`GET .../gspm1b/{mac}` 有数据时会覆盖产品脚本。

### 透传脚本维护（删 / 查 / 存）

| 操作 | 方法 |
|------|------|
| 删设备级 | `DELETE /device/transparent-codec/gspm1b/{mac}` |
| 删产品级 | `DELETE /device/transparent-codec/gspm1b` |
| 存产品级 | `POST /device/transparent-codec/gspm1b` |
| 存设备级 | `POST /device/transparent-codec/gspm1b/{mac}` |
| 查（设备无则回落产品） | `GET /device/transparent-codec/gspm1b/{mac}` |
| 只查产品级 | `GET /device/transparent-codec/gspm1b` |

查不到时看 HTTP 状态：未登录为 **401**；无记录常为 **404** 或 `result` 为空（不是 `status:200` 且带脚本）。

**彻底清理后重装（推荐）：**

```sql
-- PostgreSQL，库名见 application-default.yml 中 DB_DATABASE
SELECT id, product_id, device_id, provider, modify_time
FROM dev_transparent_codec
WHERE product_id = 'gspm1b';

DELETE FROM dev_transparent_codec WHERE product_id = 'gspm1b';
```

然后重新 `POST` 产品级脚本，并用 `GET` 确认 `configuration.script` 中含 `platform-messageId-v2`、不含 `nowMessageId`。
