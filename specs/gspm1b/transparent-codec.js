/**
 * GSPM1B 透传编解码脚本
 *
 * 在 JetLinks: 产品/设备详情 -> 数据解析 页签，或 POST /device/transparent-codec/gspm1b
 * provider: jsr223, configuration.lang: js
 *
 * 下行 JSON 的 messageId 须使用平台 context.message().getMessageId()，勿自造 ID；
 * 平台侧 SimpleTransparentMessageCodec 对 Map/字符串做整型 JSON 纠正。
 * MQTT：设备 publish /{productId}/{mac}/up，subscribe /{productId}/{mac}/down（下行 Topic 由平台 Connector 设置）。
 * 接入说明见 specs/gspm1b/接入全过程.md
 */

/** 部署后可用 GET /device/transparent-codec/gspm1b 在脚本中搜索此版本号，确认已生效 */
var CODEC_SCRIPT_VERSION = "gspm1b-20260602-up-down-topic-v3";

var PROPERTY_FIELDS = {
    key: "switchState",
    current: "current",
    energy: "energy",
    power: "power",
    voltage: "voltage",
    signal: "signal",
    onState: "onState",
    keyLock: "keyLock",
    wifiLock: "wifiLock",
    timerEnable: "timerEnable",
    timerInterval: "timerInterval",
    version: "version",
    ip: "ip",
    ssid: "ssid"
};

/** 对应物模型 enum 类型属性，上报值须为字符串 */
var ENUM_PROPERTIES = {
    switchState: true,
    onState: true,
    keyLock: true,
    wifiLock: true,
    timerEnable: true
};

/** 对应物模型 int 类型属性 */
var INT_PROPERTIES = {
    signal: true,
    timerInterval: true
};

/** 从 EncodeContext.message() 取平台 messageId（异步功能回复须与此一致） */
function platformMessageId(context) {
    var msg = context.message();
    if (msg === null || msg === undefined) {
        return null;
    }
    // 优先：JetLinks 脚本环境注入的 JSON.stringify → utils.toJsonString(平台消息)
    try {
        var json = JSON.parse(JSON.stringify(msg));
        if (json !== null && json !== undefined && json.messageId !== null && json.messageId !== undefined) {
            var fromJson = String(json.messageId);
            if (fromJson !== "" && fromJson !== "undefined" && fromJson !== "null") {
                return fromJson;
            }
        }
    } catch (e1) {
        // ignore
    }
    // 备选：Nashorn 访问 JavaBean
    var id = null;
    try {
        id = msg.messageId;
    } catch (e2) {
        // ignore
    }
    if (id === null || id === undefined || String(id) === "") {
        try {
            id = msg.getMessageId();
        } catch (e3) {
            // ignore
        }
    }
    if (id === null || id === undefined || String(id) === "") {
        return null;
    }
    return String(id);
}

/** 组装下行 JSON，messageId 每次从 context 读取平台 ID */
function withPlatformMessageId(context, obj) {
    var messageId = platformMessageId(context);
    if (!messageId) {
        throw new Error("missing platform messageId on downstream encode");
    }
    obj.messageId = messageId;
    return toDevicePayload(obj);
}

function toInt(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    return parseInt(value, 10);
}

/**
 * 物模型 enum 的 elements.value 为字符串 "0"/"1"，须上报字符串；
 * 若上报为 1.0（Double）则无法匹配枚举，界面只显示数字。
 */
function toEnumValue(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    var text = String(parseInt(value, 10));
    try {
        // 强制 Java String，避免 Nashorn/Graal 在 Map 里保留为 Number(1.0)
        return Java.type("java.lang.String").valueOf(text);
    } catch (e) {
        return text;
    }
}

/** 上报 int 属性时用 Java Integer，避免 JSR223 转成 Double 显示为 1.0 */
function toJavaInt(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    var n = parseInt(value, 10);
    try {
        return Java.type("java.lang.Integer").valueOf(n);
    } catch (e) {
        return n;
    }
}

function toFloat(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    return parseFloat(value);
}

function putIfPresent(target, key, value) {
    if (value !== null && value !== undefined && value !== "") {
        target[key] = value;
    }
}

/** GSPM 下行整数字段（必须输出 1 而不是 1.0） */
var DOWNSTREAM_INT_KEYS = {
    key: true,
    timerEnable: true,
    timerInterval: true,
    keyLock: true,
    onState: true,
    wifiLock: true
};

/** 下行 payload：手写 JSON，保证 key 等字段为整数格式 */
function toDevicePayload(obj) {
    var parts = [];
    for (var k in obj) {
        if (!obj.hasOwnProperty(k)) {
            continue;
        }
        var v = obj[k];
        if (v === null || v === undefined) {
            continue;
        }
        if (DOWNSTREAM_INT_KEYS[k]) {
            parts.push('"' + k + '":' + parseInt(v, 10));
        } else if (typeof v === "number") {
            parts.push('"' + k + '":' + v);
        } else {
            parts.push('"' + k + '":' + JSON.stringify(String(v)));
        }
    }
    return "{" + parts.join(",") + "}";
}

/** context.json() 可能是 Java Map，需兼容 get(key) 与 obj[key] */
function getField(data, key) {
    if (data === null || data === undefined) {
        return null;
    }
    if (typeof data.get === "function") {
        return data.get(key);
    }
    return data[key];
}

/** 设备 ID：优先 JSON.mac，否则用 MQTT Topic 解析出的 deviceId */
function resolveDeviceId(context, data) {
    var mac = getField(data, "mac");
    if (mac !== null && mac !== undefined && mac !== "") {
        return String(mac);
    }
    var msg = context.message();
    if (msg !== null && typeof msg.getDeviceId === "function") {
        var id = msg.getDeviceId();
        if (id !== null && id !== undefined && id !== "") {
            return String(id);
        }
    }
    return null;
}

function mapProperties(data) {
    var props = {};

    for (var src in PROPERTY_FIELDS) {
        if (!PROPERTY_FIELDS.hasOwnProperty(src)) {
            continue;
        }
        var dest = PROPERTY_FIELDS[src];
        var raw = getField(data, src);
        if (raw === null || raw === undefined || raw === "") {
            continue;
        }
        if (ENUM_PROPERTIES[dest]) {
            putIfPresent(props, dest, toEnumValue(raw));
        } else if (INT_PROPERTIES[dest]) {
            putIfPresent(props, dest, toJavaInt(raw));
        } else if (src === "current" || src === "energy" || src === "power" || src === "voltage") {
            putIfPresent(props, dest, toFloat(raw));
        } else {
            putIfPresent(props, dest, raw);
        }
    }

    var type = getField(data, "type");
    if (type) {
        putIfPresent(props, "deviceType", type);
    }
    var commandName = getField(data, "commandName");
    if (commandName) {
        putIfPresent(props, "lastCommandName", commandName);
    }
    var source = getField(data, "source");
    if (source) {
        putIfPresent(props, "lastSource", source);
    }

    return props;
}

function buildEvent(data) {
    if (getField(data, "commandName") !== "controller-event") {
        return null;
    }
    return {
        messageType: "EVENT",
        event: "controllerEvent",
        data: {
            key: toJavaInt(getField(data, "key")),
            onState: toJavaInt(getField(data, "onState")),
            mac: getField(data, "mac")
        }
    };
}

/** 是否为平台下发指令的响应（非定时上报 auto / 本地按键） */
function isCommandResponse(data) {
    var source = getField(data, "source");
    if (source === "command") {
        return true;
    }
    var commandName = getField(data, "commandName");
    if (source !== "auto" && commandName) {
        if (commandName === "info-all" || commandName === "info-statistic") {
            var messageId = getField(data, "messageId");
            return messageId !== null && messageId !== undefined && messageId !== "";
        }
    }
    return false;
}

/**
 * 异步功能调用完成后，平台须收到 INVOKE_FUNCTION_REPLY 且 messageId 与下发一致，
 * 否则界面会一直显示「消息已发往设备,处理中...」。
 */
function buildFunctionReply(data) {
    if (!isCommandResponse(data)) {
        return null;
    }
    var messageId = getField(data, "messageId");
    if (messageId === null || messageId === undefined || messageId === "") {
        return null;
    }
    var props = mapProperties(data);
    return {
        messageType: "INVOKE_FUNCTION_REPLY",
        messageId: String(messageId),
        success: true,
        output: Object.keys(props).length > 0 ? props : true
    };
}

// ========== 上行：设备 -> 平台 ==========
codec.onUpstream(function (context) {
    var data = context.json();
    if (!data) {
        return null;
    }
    if (!resolveDeviceId(context, data)) {
        return null;
    }

    var functionReply = buildFunctionReply(data);
    if (functionReply) {
        var replyProps = mapProperties(data);
        if (functionReply.output === true && Object.keys(replyProps).length > 0) {
            functionReply.output = replyProps;
        }
        if (Object.keys(replyProps).length > 0) {
            return [functionReply, replyProps];
        }
        return functionReply;
    }

    var props = mapProperties(data);
    var event = buildEvent(data);

    if (event && Object.keys(props).length > 0) {
        return [event, props];
    }
    if (event) {
        return event;
    }
    if (Object.keys(props).length === 0) {
        return null;
    }

    return props;
});

// ========== 下行：平台 -> 设备 ==========
codec.onDownstream(function (context) {
    context.whenFunction("powerControl", function (inputs) {
        return withPlatformMessageId(context, {
            key: toInt(inputs.key),
            type: "event"
        });
    });

    context.whenWriteProperty("switchState", function (val) {
        return withPlatformMessageId(context, {
            key: toInt(val),
            type: "event"
        });
    });

    context.whenFunction("restart", function () {
        return withPlatformMessageId(context, {
            system: "restart",
            type: "setting"
        });
    });

    context.whenFunction("queryInfo", function () {
        return withPlatformMessageId(context, {
            type: "info"
        });
    });

    context.whenFunction("queryStatistic", function () {
        return withPlatformMessageId(context, {
            type: "statistic"
        });
    });

    context.whenFunction("setTimerReport", function (inputs) {
        return withPlatformMessageId(context, {
            timerEnable: toInt(inputs.timerEnable),
            timerInterval: toInt(inputs.timerInterval),
            type: "setting"
        });
    });

    context.whenFunction("setKeyLock", function (inputs) {
        return withPlatformMessageId(context, {
            keyLock: toInt(inputs.keyLock),
            type: "setting"
        });
    });

    context.whenFunction("setOnState", function (inputs) {
        return withPlatformMessageId(context, {
            onState: toInt(inputs.onState),
            type: "setting"
        });
    });

    context.whenFunction("setWifiLock", function (inputs) {
        return withPlatformMessageId(context, {
            wifiLock: toInt(inputs.wifiLock),
            type: "setting"
        });
    });
});
