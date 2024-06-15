package westmeijer.oskar.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.CloudEventUtils;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.jackson.PojoCloudEventDataMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import westmeijer.oskar.model.Product;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductsCEStructuredConsumer {

  @Getter
  private Product latestMsg;

  private final Validator validator;

  private final ObjectMapper objectMapper;

  private final MeterRegistry meterRegistry;

  @KafkaListener(topics = "${kafka.servers.products.consumers.products-ce-structured.topic-name}",
      containerFactory = "productsCEStructuredContainerFactory")
  public void listenToCEStructuredProducts(ConsumerRecord<String, CloudEvent> message) {
    log.info("Received message from products-ce-structured topic. key: {}, value: {}, message: {}", message.key(), message.value(),
        message);

    PojoCloudEventData<Product> deserializedData = CloudEventUtils
        .mapData(message.value(), PojoCloudEventDataMapper.from(objectMapper, Product.class));

    latestMsg = deserializedData.getValue();
    Instant ceTime = message.value().getTime().toInstant();
    log.info("Deserialized structuredCE. value: {}, ce_time: {}", latestMsg, ceTime);

    var validationErrors = validator.validate(deserializedData.getValue());
    if (!validationErrors.isEmpty()) {
      log.error("Message had validation errors! errors: {}", validationErrors);
      throw new IllegalArgumentException(validationErrors.toString());
    }

    meterRegistry.counter("products-ce-structured.consumed").increment();
  }

}