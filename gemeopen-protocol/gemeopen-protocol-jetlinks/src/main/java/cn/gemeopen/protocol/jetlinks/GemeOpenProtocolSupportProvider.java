package cn.gemeopen.protocol.jetlinks;

import cn.gemeopen.protocol.profile.ProfileRegistry;
import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.route.MqttRoute;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import reactor.core.publisher.Mono;

import java.util.List;

public class GemeOpenProtocolSupportProvider implements ProtocolSupportProvider {

    public static final String PROTOCOL_ID = "gemeopen-mqtt";

    @Override
    public Mono<? extends ProtocolSupport> create(ServiceContext context) {
        ProfileRegistry profiles = new ProfileRegistry(GemeOpenProtocolSupportProvider.class.getClassLoader());
        GemeOpenMqttMessageCodec codec = new GemeOpenMqttMessageCodec(profiles);

        CompositeProtocolSupport support = new CompositeProtocolSupport();
        support.setId(PROTOCOL_ID);
        support.setName("GemeOpen MQTT JSON");
        support.setDescription("GemeOpen 系列 MQTT JSON 协议（Profile 驱动）");
        support.setMetadataCodec(new JetLinksDeviceMetadataCodec());
        support.addMessageCodecSupport(DefaultTransport.MQTT, () -> Mono.just(codec));
        support.addRoutes(DefaultTransport.MQTT, List.of(
            MqttRoute.builder("/{productId}/{deviceId}/up").upstream(true).qos(1).build(),
            MqttRoute.builder("/{productId}/{deviceId}/down").downstream(true).qos(1).build()
        ));
        return Mono.just(support);
    }
}
