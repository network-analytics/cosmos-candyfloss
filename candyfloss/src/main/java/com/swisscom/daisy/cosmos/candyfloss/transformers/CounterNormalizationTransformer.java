package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.config.NormalizeCounterConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.TimeExtractorConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformers.exceptions.InvalidCounterKeysConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformers.exceptions.InvalidCounterValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterNormalizationTransformer
    implements Transformer<
        String, FlattenedMessage, KeyValue<String, ValueErrorMessage<FlattenedMessage>>> {

  private final Logger logger = LoggerFactory.getLogger(CounterNormalizationTransformer.class);

  private final Counter counterError =
      Counter.builder("json_streams_counter_normalization_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);
  private final String stateStoreName;
  private final PipelineConfig pipelineConfig;
  private TimestampedKeyValueStore<Bytes, Bytes> stateStore;
  private final long maxCounterCacheAge;
  private final int intCounterWrapAroundLimit;
  private final long longCounterWrapAroundLimit;
  private final long counterWrapAroundTimeMs;
  private final BigInteger maxUnsignedInt = new BigInteger(Integer.toUnsignedString(-1));
  private final BigInteger maxUnsignedLong = new BigInteger(Long.toUnsignedString(-1L));

  private final Timer timer =
      Timer.builder("json_streams_old_counter_cleaner_duration")
          .description("Time spent in cleaning the old counters from the state store")
          .publishPercentileHistogram()
          .register(Metrics.globalRegistry);
  private final AtomicInteger numKeysTotal = new AtomicInteger(0);
  private final AtomicInteger numKeysCleaned = new AtomicInteger(0);
  private final Gauge numKeysGauge =
      Gauge.builder("json_streams_old_counter_total_keys", () -> numKeysTotal)
          .description("Number of counter keys saved in the state store")
          .register(Metrics.globalRegistry);

  private final Gauge numKeysGaugeCleaned =
      Gauge.builder("json_streams_old_counter_cleaned_keys", () -> numKeysCleaned)
          .description("Number of old counter keys deleted from the state store")
          .register(Metrics.globalRegistry);

  private final Duration maxCounterAge;
  private final Duration scanFrequency;

  private ProcessorContext context;

  public CounterNormalizationTransformer(
      PipelineConfig pipelineConfig,
      String stateStoreName,
      long maxCounterCacheAge,
      int intCounterWrapAroundLimit,
      long longCounterWrapAroundLimit,
      long counterWrapAroundTimeMs,
      Duration maxCounterAge,
      Duration scanFrequency) {
    this.pipelineConfig = pipelineConfig;
    this.stateStoreName = stateStoreName;
    this.maxCounterCacheAge = maxCounterCacheAge;
    this.intCounterWrapAroundLimit = intCounterWrapAroundLimit;
    this.longCounterWrapAroundLimit = longCounterWrapAroundLimit;
    this.counterWrapAroundTimeMs = counterWrapAroundTimeMs;
    this.maxCounterAge = maxCounterAge;
    this.scanFrequency = scanFrequency;
  }

  private void cleanOldKeys(long currentTimestamp) {
    logger.info("Start: OldCountersCleaner is triggered");
    int deletedKeys = 0;
    int numKeys = 0;
    final long cutoff = currentTimestamp - maxCounterAge.toMillis();
    try (final var all = stateStore.all()) {
      while (all.hasNext()) {
        final var record = all.next();
        numKeys++;
        if (record.value.timestamp() < cutoff) {
          logger.debug("Deleting key: {}", record.key);
          stateStore.delete(record.key);
          deletedKeys++;
        }
      }
    }
    numKeysTotal.set(numKeys);
    numKeysCleaned.set(deletedKeys);
    logger.info("End: OldCountersCleaner deleted {} key(s)", deletedKeys);
  }

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
    this.stateStore = context.getStateStore(stateStoreName);
    context.schedule(
        scanFrequency, PunctuationType.STREAM_TIME, timer.record(() -> this::cleanOldKeys));
  }

  @Override
  public KeyValue<String, ValueErrorMessage<FlattenedMessage>> transform(
      String key, FlattenedMessage flattenedMessage) {
    if (flattenedMessage.getTag() == null) {
      return KeyValue.pair(key, new ValueErrorMessage<>(flattenedMessage));
    }
    try {
      return process(key, flattenedMessage);
    } catch (Exception e) {
      counterError.increment();
      var error =
          ErrorMessage.getError(
              context, getClass().getName(), key, flattenedMessage, e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(flattenedMessage, error));
    }
  }

  private KeyValue<String, ValueErrorMessage<FlattenedMessage>> process(
      String key, FlattenedMessage flattenedMessage)
      throws InvalidCounterValue, InvalidCounterKeysConfigurations {
    var normalizeCountersConfig =
        pipelineConfig.getSteps().get(flattenedMessage.getTag()).getNormalizeCountersConfig();

    if (normalizeCountersConfig.isPresent()) {
      for (var counterConfig : normalizeCountersConfig.get().getCounterConfigs()) {
        if (counterConfig.getMatch().matchContext(flattenedMessage.getValue())) {
          normalizeCounter(flattenedMessage, counterConfig);
        }
      }
    }
    return KeyValue.pair(key, new ValueErrorMessage<>(flattenedMessage));
  }

  private void normalizeCounter(
      FlattenedMessage flattenedMessage, NormalizeCounterConfig counterConfig)
      throws InvalidCounterKeysConfigurations, InvalidCounterValue {
    // Extract the message timestamp
    Instant timestamp = getTimestamp(flattenedMessage.getTag(), flattenedMessage.getValue());
    // Extract the counter key used in the key/value store
    Bytes counterKey = getCounterKey(flattenedMessage.getValue(), counterConfig);
    // Extract the counter value from the input message
    BigInteger counterValue = getCounterValue(flattenedMessage.getValue(), counterConfig);
    // The message doesn't contain the counter, return
    if (counterValue == null) {
      return;
    }
    logger.debug(
        "Extracted counter key '{}' and value '{}' with timestamp '{}' from message: '{}'",
        counterKey,
        counterValue,
        timestamp,
        flattenedMessage.getValue().json());
    // Find the old value of the counter in the store state.
    // if no value exists then the same counter value is returned
    ValueAndTimestamp<Bytes> savedCounterState =
        getCounterSavedState(counterKey, counterValue, timestamp);
    if (savedCounterState != null) {
      logger.debug(
          "Value in stateStore for counter key '{}' is '{}' with timestamp '{}' from message: '{}'",
          counterKey,
          savedCounterState.value(),
          savedCounterState.timestamp(),
          flattenedMessage.getValue().json());
      if (timestamp.toEpochMilli() < savedCounterState.timestamp()) {
        logger.warn(
            "Received a message with a timestamp {} older than saved in the counter store {}. Message: {}",
            timestamp.toEpochMilli(),
            savedCounterState.timestamp(),
            flattenedMessage.getValue().json());
        savedCounterState = null;
      } else {
        // Save the new counterValue to the store
        stateStore.put(
            counterKey,
            ValueAndTimestamp.make(
                Bytes.wrap(counterValue.toString().getBytes(StandardCharsets.UTF_8)),
                timestamp.toEpochMilli()));
      }
    }
    logger.debug(
        "Saved counter key '{}' and value '{}' with timestamp '{}' for message: '{}'",
        counterKey,
        counterValue,
        timestamp,
        flattenedMessage.getValue().json());

    // Normalize the counter
    BigInteger normalizedValue = null;
    if (savedCounterState != null) {
      var savedCounterValueBigInt = new BigInteger(new String(savedCounterState.value().get()));
      if (counterValue.compareTo(savedCounterValueBigInt) >= 0) {
        // New counter is an increment
        normalizedValue = counterValue.subtract(savedCounterValueBigInt).abs();
      } else {
        // Heuristic to figure out if the counter wrap around or was reset
        boolean inWrapAroundRange;
        BigInteger maxDiff;
        if (counterConfig.getCounterType() == NormalizeCounterConfig.CounterType.U32) {
          maxDiff = maxUnsignedInt.subtract(savedCounterValueBigInt).add(counterValue);
          inWrapAroundRange =
              maxDiff.intValue() >= 0 && maxDiff.intValue() < intCounterWrapAroundLimit;
        } else if (counterConfig.getCounterType() == NormalizeCounterConfig.CounterType.U64) {
          maxDiff = maxUnsignedLong.subtract(savedCounterValueBigInt).add(counterValue);
          inWrapAroundRange =
              maxDiff.longValue() >= 0 && maxDiff.longValue() < longCounterWrapAroundLimit;
        } else {
          throw new InvalidCounterValue("Invalid counter type: " + counterConfig.getCounterType());
        }

        if (inWrapAroundRange
            && (timestamp.toEpochMilli() - savedCounterState.timestamp())
                <= counterWrapAroundTimeMs) {
          normalizedValue = maxDiff;
          logger.warn("Counter: '{}' has been wrapped around", counterKey);
        } else {
          // TODO (AH): log counter counter resets
          // wrap around diff is not within expected range, consider this as a reset and send a zero
          logger.warn("Counter: '{}' has been reset to null", counterKey);
          normalizedValue = null;
        }
      }
    }

    // overwrite the counter value in the message with the normalized value
    flattenedMessage
        .getValue()
        .set(
            counterConfig.getValuePath(),
            normalizedValue == null ? null : normalizedValue.longValue());
  }

  /*** Extract the time stamp from the message */
  private Instant getTimestamp(String tag, DocumentContext context) {
    var timeExtractorConfig =
        pipelineConfig
            .getSteps()
            .get(tag)
            .getNormalizeCountersConfig()
            .get()
            .getTimeExtractorConfig();
    Object extractedTimestamp = context.read(timeExtractorConfig.getJsonPath());

    if (timeExtractorConfig.getValueType() == TimeExtractorConfig.TimestampType.RFC2822) {
      var dateTimeString = (String) extractedTimestamp;
      var formatter =
          new DateTimeFormatterBuilder()
              .append(DateTimeFormatter.ISO_LOCAL_DATE)
              .appendLiteral(' ')
              .append(DateTimeFormatter.ISO_LOCAL_TIME)
              .toFormatter();
      var time = LocalDateTime.parse(dateTimeString, formatter);
      var zoned = time.atZone(ZoneId.of(ZoneId.SHORT_IDS.get("ECT")));
      return zoned.toInstant();
    } else {
      Long longTimestamp;
      if (extractedTimestamp instanceof String) {
        longTimestamp = Long.parseUnsignedLong((String) extractedTimestamp);
      } else if (extractedTimestamp instanceof BigInteger) {
        longTimestamp = ((BigInteger) extractedTimestamp).longValue();
      } else {
        longTimestamp = (Long) extractedTimestamp;
      }
      if (timeExtractorConfig.getValueType() == TimeExtractorConfig.TimestampType.EpochMilli) {
        return Instant.ofEpochMilli(longTimestamp);
      } else if (timeExtractorConfig.getValueType()
          == TimeExtractorConfig.TimestampType.EpochSeconds) {
        return Instant.ofEpochSecond(longTimestamp);
      } else {
        logger.error("Unable to extract timestamp of type: {}", timeExtractorConfig.getValueType());
      }
    }
    return null;
  }

  /*** Get the previous value for the counter, if the counter didn't exist before then null is returned*/
  private ValueAndTimestamp<Bytes> getCounterSavedState(
      Bytes counterKey, BigInteger counterValue, Instant timestamp) {
    ValueAndTimestamp<Bytes> savedCounterState = null;
    try {
      savedCounterState = stateStore.get(counterKey);
    } catch (NullPointerException ignored) {
      // NullPointerException is raised when the key doesn't exist, we ignore it and pass the
      // counter as it
      // TODO (AH): log counter counter resets
    }
    if (savedCounterState == null) {
      logger.debug(
          "First time to see counter key '{}' and value '{}' with timestamp '{}'",
          counterKey,
          counterValue,
          timestamp);
    } else {
      var counterAge = timestamp.minusMillis(savedCounterState.timestamp()).toEpochMilli();
      if (counterAge > maxCounterCacheAge) {
        logger.debug(
            "Counter age '{}' is older than max age '{}' counter key '{}' and value '{}' with timestamp '{}'",
            counterAge,
            maxCounterCacheAge,
            counterKey,
            counterValue,
            timestamp);
      }
    }
    // If the counterKey didn't exist before or too old, then create it
    if (savedCounterState == null
        || timestamp.minusMillis(savedCounterState.timestamp()).toEpochMilli()
            > maxCounterCacheAge) {
      logger.debug(
          "Returning the same counter for counter key '{}' and value '{}' with timestamp '{}'",
          counterKey,
          counterValue,
          timestamp);
      stateStore.put(
          counterKey,
          ValueAndTimestamp.make(
              Bytes.wrap(counterValue.toString().getBytes(StandardCharsets.UTF_8)),
              timestamp.toEpochMilli()));
      savedCounterState = null;
    }
    return savedCounterState;
  }

  /*** Extract the counter value that is used to locate in key/value store based on the user-provided configurations*/
  private BigInteger getCounterValue(DocumentContext context, NormalizeCounterConfig counterConfig)
      throws InvalidCounterValue {
    // at this point we don't know the value type
    Object rawCounterValue = context.read(counterConfig.getValuePath());

    // Message doesn't contain the counter, return
    if (rawCounterValue == null) {
      return null;
    }
    // Guess the counter type
    if (rawCounterValue instanceof Integer) {
      // For integer, we upgrade them to long
      return new BigInteger(Integer.toUnsignedString((Integer) rawCounterValue));
    } else if (rawCounterValue instanceof Long) {
      return new BigInteger(Long.toUnsignedString((Long) rawCounterValue));
    } else if (rawCounterValue instanceof BigInteger) {
      return (BigInteger) rawCounterValue;
    } else if (rawCounterValue instanceof String) {
      return new BigInteger((String) rawCounterValue);
    } else {
      throw new InvalidCounterValue(
          "Counter value must be either integer or long, found: " + rawCounterValue);
    }
  }

  /*** Extract the counter key that is used to locate in key/value store based on the user-provided configurations*/
  private Bytes getCounterKey(DocumentContext context, NormalizeCounterConfig counterConfig)
      throws InvalidCounterKeysConfigurations {
    List<String> keyList =
        counterConfig.getKey().getKeyExtractors().stream()
            .map(
                x -> {
                  try {
                    return x.getKey(context);
                  } catch (Exception ex) {
                    return "null";
                  }
                })
            .sorted()
            .toList();
    if (keyList.isEmpty()) {
      throw new InvalidCounterKeysConfigurations(
          "Couldn't not extract any counter keys from the message.");
    }
    return Bytes.wrap(String.join(",", keyList).getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void close() {}
}
