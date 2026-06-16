package cn.gemeopen.protocol.jetlinks;

import cn.gemeopen.protocol.codec.DownstreamEncoder;
import cn.gemeopen.protocol.codec.TopicPaths;
import cn.gemeopen.protocol.codec.UpstreamDecoder;
import cn.gemeopen.protocol.codec.UpstreamPart;
import cn.gemeopen.protocol.profile.ProductProfile;
import cn.gemeopen.protocol.profile.ProfileRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.DeviceMessageCodec;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.FromDeviceMessageContext;
import org.jetlinks.core.message.codec.MessageCodecDescription;
import org.jetlinks.core.message.codec.MessageDecodeContext;
import org.jetlinks.core.message.codec.MessageEncodeContext;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.function.FunctionParameter;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.message.property.WritePropertyMessage;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GemeOpenMqttMessageCodec implements DeviceMessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ProfileRegistry profiles;

    public GemeOpenMqttMessageCodec(ProfileRegistry profiles) {
        this.profiles = profiles;
    }

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.MQTT;
    }

    @Nonnull
    @Override
    public Publisher<? extends Message> decode(@Nonnull MessageDecodeContext context) {
        return Flux.defer(() -> doDecode(context));
    }

    private Flux<Message> doDecode(MessageDecodeContext context) {
        if (!(context instanceof FromDeviceMessageContext fromCtx)) {
            return Flux.empty();
        }
        EncodedMessage encoded = context.getMessage();
        if (!(encoded instanceof MqttMessage mqtt)) {
            return Flux.empty();
        }
        return TopicPaths.parseUpstreamTopic(mqtt.getTopic())
            .map(topic -> decodeForTopic(fromCtx, mqtt, topic))
            .orElseGet(Flux::empty);
    }

    private Flux<Message> decodeForTopic(FromDeviceMessageContext fromCtx, MqttMessage mqtt, TopicPaths.TopicInfo topic) {
        // MqttClientDeviceGateway 解码阶段使用 UnknownDeviceMqttClientSession，此时尚无 DeviceOperator；
        // 须从 Topic 解析 productId/deviceId，否则上行（含 INVOKE_FUNCTION_REPLY）会被丢弃并导致功能调用超时。
        var device = fromCtx.getDevice();
        if (device == null) {
            return Flux.fromIterable(decodePayload(topic.productId(), topic.deviceId(), mqtt));
        }
        return device.getProduct()
            .flatMapMany(product -> {
                if (!product.getId().equals(topic.productId())) {
                    return Flux.empty();
                }
                return Flux.fromIterable(decodePayload(product.getId(), device.getDeviceId(), mqtt));
            });
    }

    private List<DeviceMessage> decodePayload(String productId, String deviceId, MqttMessage mqtt) {
        ProductProfile profile = profiles.getRequired(productId);
        Map<String, Object> data = parseJson(mqtt);
        if (data == null) {
            return List.of();
        }
        UpstreamDecoder decoder = new UpstreamDecoder(profile);
        List<UpstreamPart> parts = decoder.decode(data, deviceId);
        return parts.stream().map(part -> toDeviceMessage(part, deviceId)).toList();
    }

    private Map<String, Object> parseJson(MqttMessage mqtt) {
        ByteBuf buf = mqtt.getPayload();
        if (buf == null) {
            return null;
        }
        try {
            byte[] bytes = ByteBufUtil.getBytes(buf);
            return MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    private DeviceMessage toDeviceMessage(UpstreamPart part, String deviceId) {
        if (part instanceof UpstreamPart.PropertyReport report) {
            return ReportPropertyMessage.create()
                .deviceId(deviceId)
                .properties(report.properties());
        }
        if (part instanceof UpstreamPart.Event event) {
            return new EventMessage()
                .deviceId(deviceId)
                .event(event.eventId())
                .data(event.data());
        }
        if (part instanceof UpstreamPart.FunctionReply reply) {
            FunctionInvokeMessageReply msg = FunctionInvokeMessageReply.create()
                .deviceId(deviceId)
                .messageId(reply.messageId())
                .success();
            Object output = reply.output();
            if (output != null && !(output instanceof Boolean)) {
                msg.output(output);
            }
            return msg;
        }
        throw new IllegalStateException("Unknown part: " + part);
    }

    @Nonnull
    @Override
    public Publisher<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.defer(() -> doEncode(context));
    }

    private Mono<EncodedMessage> doEncode(MessageEncodeContext context) {
        Message message = context.getMessage();
        if (!(message instanceof DeviceMessage deviceMessage)) {
            return Mono.empty();
        }
        return context.getDeviceAsync()
            .flatMap(device -> device.getProduct()
                .flatMap(product -> {
                    java.util.Optional<EncodedMessage> encoded = encodeForDevice(
                        product.getId(),
                        device.getDeviceId(),
                        deviceMessage
                    );
                    return encoded.map(Mono::just).orElseGet(Mono::empty);
                }));
    }

    private java.util.Optional<EncodedMessage> encodeForDevice(String productId, String deviceId, DeviceMessage message) {
        ProductProfile profile = profiles.getRequired(productId);
        DownstreamEncoder encoder = new DownstreamEncoder(profile);
        String messageId = message.getMessageId();
        byte[] payload;
        if (message instanceof FunctionInvokeMessage invoke) {
            payload = encoder.encodeFunction(invoke.getFunctionId(), toInputMap(invoke), messageId).payload();
        } else if (message instanceof WritePropertyMessage write) {
            Map<String, Object> props = write.getProperties();
            if (props == null || props.size() != 1) {
                return java.util.Optional.empty();
            }
            Map.Entry<String, Object> entry = props.entrySet().iterator().next();
            payload = encoder.encodeWriteProperty(entry.getKey(), entry.getValue(), messageId).payload();
        } else if (message instanceof ReadPropertyMessage read) {
            payload = encoder.encodeReadProperty(read.getProperties(), messageId).payload();
        } else {
            return java.util.Optional.empty();
        }
        String topic = profile.getTopic().resolveDownstream(productId, deviceId);
        return java.util.Optional.of(
            SimpleMqttMessage.builder()
                .topic(topic)
                .payload(io.netty.buffer.Unpooled.wrappedBuffer(payload))
                .build()
        );
    }

    private Map<String, Object> toInputMap(FunctionInvokeMessage invoke) {
        Map<String, Object> inputs = new HashMap<>();
        if (invoke.getInputs() != null) {
            for (FunctionParameter parameter : invoke.getInputs()) {
                inputs.put(parameter.getName(), parameter.getValue());
            }
        }
        return inputs;
    }

    @Override
    public Mono<? extends MessageCodecDescription> getDescription() {
        return Mono.empty();
    }
}
