/*
 * Copyright 2025 JetLinks https://www.jetlinks.cn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetlinks.community.device.events.handler;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.metadata.DeviceMetadataCodec;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.device.events.DeviceProductDeployEvent;
import org.jetlinks.community.device.service.LocalDeviceProductService;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.community.device.service.data.DeviceLatestDataService;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;

/**
 * 处理设备型号发布事件，并在启动/发布时把物模型存储（properties、device_log 等）注册到 TimescaleDB ORM 元数据。
 * <p>
 * 注意：库中已有表 ≠ 进程内已注册 metric；重启后须在本类 {@link #run} 或 deploy 事件中完成注册，
 * 否则写日志时会报 {@code metric [device_log_xxx] not found}。
 * <p>
 * 启动顺序：{@link org.jetlinks.community.protocol.manager.LocalProtocolSupportManager}（Order 10）
 * → 本类（Order 50）→ {@link org.jetlinks.community.timescaledb.impl.DefaultTimescaleDBOperations}（最后）。
 *
 * @author bsetfeng
 * @author zhouhao
 * @since 1.0
 **/
@Component
@Slf4j
@Order(50) // 须在 LocalProtocolSupportManager（@Order 10）加载完协议后再 register 产品，否则报「不支持的协议」
public class DeviceProductDeployHandler implements CommandLineRunner {

    private final LocalDeviceProductService productService;

    private final DeviceMetadataCodec codec = new JetLinksDeviceMetadataCodec();

    private final DeviceDataService dataService;

    private final DeviceLatestDataService latestDataService;
    private final EventBus eventBus;
    /** 启动/集群同步时注册产品模板，供物模型存储注册使用 */
    private final DeviceRegistry deviceRegistry;

    private final Disposable disposable;

    @Autowired
    public DeviceProductDeployHandler(LocalDeviceProductService productService,
                                      DeviceDataService dataService,
                                      EventBus eventBus,
                                      DeviceLatestDataService latestDataService,
                                      DeviceRegistry deviceRegistry) {
        this.productService = productService;
        this.dataService = dataService;
        this.eventBus = eventBus;
        this.latestDataService = latestDataService;
        this.deviceRegistry = deviceRegistry;
        //监听其他服务器上的物模型变更
        disposable = eventBus
            .subscribe(Subscription
                           .builder()
                           .subscriberId("product-metadata-upgrade")
                           .topics("/_sys/product-upgrade")
                           .justBroker()
                           .build(), String.class)
            .flatMap(id -> this
                .reloadMetadata(id)
                .onErrorResume((err) -> {
                    log.warn("handle product upgrade event error", err);
                    return Mono.empty();
                }))
            .subscribe();
    }

    @PreDestroy
    public void shutdown() {
        disposable.dispose();
    }

    @EventListener
    public void handlerEvent(DeviceProductDeployEvent event) {
        event.async(
            productService
                .findById(event.getId())
                .flatMap(this::registerProductAndMetadata)
                .switchIfEmpty(Mono.defer(() -> {
                    // 产品已被删除时仍收到 deploy 事件，避免空指针
                    log.warn("产品 [{}] 不存在，跳过物模型存储注册", event.getId());
                    return Mono.empty();
                }))
                .then(eventBus.publish("/_sys/product-upgrade", event.getId()))
        );
    }

    protected Mono<Void> reloadMetadata(String productId) {
        return productService
            .findById(productId)
            // 集群物模型升级：须先 register 产品模板，再 reload 表结构到 ORM 元数据
            .flatMap(product -> registerProduct(product)
                .then(doReloadMetadata(productId, product.getMetadata())))
            .then();
    }

    /**
     * 发布/启用产品：先注册到 {@link DeviceRegistry}，再注册 TimescaleDB 表元数据（含 device_log_*）。
     * deploy 事件与「禁用后再启用」走此路径，效果等同手动应用配置。
     */
    private Mono<Void> registerProductAndMetadata(DeviceProductEntity product) {
        return registerProduct(product)
            .then(doRegisterMetadata(product.getId(), product.getMetadata()))
            .doOnSuccess(v -> log.debug("产品 [{}] 物模型存储元数据注册成功", product.getId()))
            .doOnError(err -> log.error("产品 [{}] 物模型存储元数据注册失败", product.getId(), err));
    }

    /**
     * 将产品注册到 {@link DeviceRegistry} / ThingsRegistry。
     * <p>
     * {@link DeviceDataService#registerMetadata} 依赖已注册的产品物模型模板；
     * 若仅调用 {@link #doRegisterMetadata} 而未 register，启动阶段会静默失败，
     * 导致 {@code properties_{productId}} 等时序表未创建、属性无法入库。
     */
    private Mono<Void> registerProduct(DeviceProductEntity product) {
        return deviceRegistry
            .register(product.toProductInfo())
            .then();
    }

    protected Mono<Void> doReloadMetadata(String productId, String metadataString) {
        return codec
            .decode(metadataString)
            .flatMap(metadata -> Flux
                .concatDelayError(dataService.reloadMetadata(productId, metadata),
                                 latestDataService.reloadMetadata(productId, metadata))
                .then());
    }

    protected Mono<Void> doRegisterMetadata(String productId, String metadataString) {
        return codec
            .decode(metadataString)
            .flatMap(metadata -> Flux
                .concatDelayError(dataService.registerMetadata(productId, metadata),
                       latestDataService.upgradeMetadata(productId, metadata))
                .then());
    }


    /**
     * 启动时为所有「已启用」产品同步注册物模型存储元数据。
     * <p>
     * 使用 {@code block()} 等待完成，避免与 {@link org.jetlinks.community.timescaledb.impl.DefaultTimescaleDBDataWriter}
     * 启动竞态：库中虽有 device_log_* 表，但 ORM 未注册时会报 metric not found。
     */
    @Override
    public void run(String... args) {
        try {
            productService
                .createQuery()
                .fetch()
                .filter(product -> Byte.valueOf((byte) 1).equals(product.getState()))
                .concatMap(product -> registerProductAndMetadata(product)
                    .onErrorResume(err -> {
                        log.error("启动时注册产品 [{}] 物模型存储失败，请检查 TimescaleDB 与物模型配置", product.getId(), err);
                        return Mono.empty();
                    }))
                .then()
                .block();
            log.info("已启用产品的物模型存储元数据注册流程已完成");
        } catch (Exception err) {
            log.error("启动时批量注册产品物模型存储失败", err);
        }
    }
}
