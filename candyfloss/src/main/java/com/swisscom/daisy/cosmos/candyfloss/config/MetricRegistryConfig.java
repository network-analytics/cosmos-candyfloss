package com.swisscom.daisy.cosmos.candyfloss.config;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricRegistryConfig {
  private final PrometheusMeterRegistry prometheusRegistry;

  private JvmGcMetrics jvmGcMetrics;

  private HTTPServer server;

  private final Logger logger = LoggerFactory.getLogger(MetricRegistryConfig.class);

  public MetricRegistryConfig() {
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  }

  public void bindJvm() {
    if (jvmGcMetrics == null) {
      jvmGcMetrics = new JvmGcMetrics();
    }
    jvmGcMetrics.bindTo(prometheusRegistry);
    new ProcessorMetrics().bindTo(prometheusRegistry);
    new JvmThreadMetrics().bindTo(prometheusRegistry);
    new JvmMemoryMetrics().bindTo(prometheusRegistry);
  }

  public void start() {
    try {
      server =
          new HTTPServer(
              new InetSocketAddress(8090), prometheusRegistry.getPrometheusRegistry(), true);
      Metrics.addRegistry(prometheusRegistry);
    } catch (IOException e) {
      logger.error("Registry service is not properly started", e);
    }
  }

  public void close() {
    jvmGcMetrics.close();
    server.close();
  }
}
