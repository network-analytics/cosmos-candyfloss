package com.swisscom.daisy.cosmos.candyfloss.config;

import io.micrometer.core.instrument.*;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

public class CFMetricReporter implements MetricsReporter {
  @Override
  public void init(List<KafkaMetric> metricList) {
    metricList.forEach(this::metricChange);
  }

  @Override
  public void metricChange(KafkaMetric metric) {
    MetricName metricName = metric.metricName();

    if (metric.metricValue() instanceof Number) {
      Tags tags = tagsConvert(metricName);

      if (isCounter(metricName)) {
        FunctionCounter.builder(
                cleanCounterName(metricName), metric, m -> ((Number) m.metricValue()).doubleValue())
            .tags(tags)
            .register(Metrics.globalRegistry);
      } else {
        Gauge.builder(metricName.name(), metric, m -> ((Number) m.metricValue()).doubleValue())
            .description(metricName.description())
            .tags(tags)
            .register(Metrics.globalRegistry);
      }
    }
  }

  @Override
  public void metricRemoval(KafkaMetric metric) {
    MetricName metricName = metric.metricName();
    Tags tags = tagsConvert(metricName);

    Metrics.globalRegistry
        .find(metricName.name())
        .tags(tags)
        .meters()
        .forEach(Metrics.globalRegistry::remove);
  }

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> config) {}

  private boolean isCounter(MetricName metricName) {
    String name = metricName.name();
    return name.endsWith("-total") || name.endsWith("-count");
  }

  private Tags tagsConvert(MetricName metricName) {
    return Tags.of(
        metricName.tags().entrySet().stream()
            .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
            .toList());
  }

  private String cleanCounterName(MetricName metricName) {
    return metricName.name().replace("-total", "").replace("-count", "");
  }
}
