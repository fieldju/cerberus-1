package com.nike.cerberus.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class LoggingMetricsService implements MetricsService {

  private final MetricRegistry metricRegistry;

  public LoggingMetricsService(Slf4jReporter.LoggingLevel level, long period, TimeUnit timeUnit) {

    metricRegistry = new MetricRegistry();

    Slf4jReporter
      .forRegistry(metricRegistry)
      .outputTo(log)
      .withLoggingLevel(level)
      .scheduleOn(Executors.newSingleThreadScheduledExecutor())
      .build()
      .start(period, timeUnit);
  }

  @Override
  public Counter getOrCreateCounter(String name, Map<String, String> dimensions) {
    return metricRegistry.counter(getMetricNameFromNameAndDimensions(name, dimensions));
  }

  @Override
  public Gauge getOrCreateCallbackGauge(String name, Supplier<Number> supplier, Map<String, String> dimensions) {
    return metricRegistry.gauge(getMetricNameFromNameAndDimensions(name, dimensions), () -> supplier::get);
  }

  private String getMetricNameFromNameAndDimensions(String name, Map<String, String> optionalDimensions) {
    var metricNameBuilder = new StringBuilder(name);
    Optional.ofNullable(optionalDimensions)
      .ifPresent(dimensions -> {
        metricNameBuilder.append('(');
        dimensions.forEach((key, value) -> metricNameBuilder.append('[').append(key).append(":").append(value).append(']'));
        metricNameBuilder.append(')');
      });
    return metricNameBuilder.toString();
  }
}