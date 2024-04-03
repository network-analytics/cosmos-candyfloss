package com.swisscom.daisy.cosoms.candyfloss.protobufdecoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vavr.Tuple;
import java.util.*;
import java.util.stream.Collectors;
import telemetry.TelemetryOuterClass;

public class ProtoBufDecoder {
  private static final String TELEMETRY_DATA_KEY = "telemetry_data";
  private static final String SERIALIZATION_KEY = "serialization";
  private static final String GPB = "gpb";

  /***
   * Parse a TelemetryField into its most basic component.
   *
   * @param telemetryField TelemetryOuterClass.TelemetryField
   * @return basic value (integer, string, etc..) or Map<String, Object>
   */
  private Object getFieldValue(TelemetryOuterClass.TelemetryField telemetryField) {
    // Fields can hold timestamps
    var timestamp = telemetryField.getTimestamp();
    // Parse nested fields (if any)
    var fields =
        telemetryField.getFieldsList().stream()
            .map(field -> Tuple.of(field.getName(), getFieldValue(field)))
            .collect(HashMap::new, (m, tuple) -> m.put(tuple._1(), tuple._2()), HashMap::putAll);
    // Try to guess the basic value if any
    Object val = null;
    if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.BYTES_VALUE) {
      val = telemetryField.getBytesValue();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.STRING_VALUE) {
      val = telemetryField.getStringValue();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.BOOL_VALUE) {
      val = telemetryField.getBoolValue();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.UINT32_VALUE) {
      val = telemetryField.getUint32Value();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.UINT64_VALUE) {
      val = telemetryField.getUint64Value();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.SINT32_VALUE) {
      val = telemetryField.getSint32Value();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.SINT64_VALUE) {
      val = telemetryField.getSint64Value();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.DOUBLE_VALUE) {
      val = telemetryField.getDoubleValue();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.FLOAT_VALUE) {
      val = telemetryField.getFloatValue();
    } else if (telemetryField.getValueByTypeCase()
        == TelemetryOuterClass.TelemetryField.ValueByTypeCase.VALUEBYTYPE_NOT_SET) {
      val = null;
    }

    Map<String, Object> map = new HashMap<>();
    if (timestamp != 0) map.put("timestamp", timestamp);
    if (!fields.isEmpty()) map.put("fields", fields);
    if (val != null) map.put("value", val);

    if (map.isEmpty()) {
      return null;
    } else if (map.size() == 1) {
      // Field only contain one value, so just return it
      return map.entrySet().stream().findFirst().map(first -> first.getValue()).get();
    } else {
      return map;
    }
  }

  private Map<String, Object> getTelemetryMap(TelemetryOuterClass.Telemetry telemetry) {
    Map<String, Object> telemetryMap = new HashMap<>();
    telemetryMap.put("node_id", telemetry.getNodeIdStr());
    telemetryMap.put("subscription", telemetry.getSubscriptionIdStr());
    telemetryMap.put("encoding_path", telemetry.getEncodingPath());
    telemetryMap.put("collection_id", telemetry.getCollectionId());
    telemetryMap.put("collection_start_time", telemetry.getCollectionStartTime());
    telemetryMap.put("msg_timestamp", telemetry.getMsgTimestamp());
    telemetryMap.put("collection_end_time", telemetry.getCollectionEndTime());
    // data_gpbkv
    var gpbkv =
        telemetry.getDataGpbkvList().stream().map(this::getFieldValue).collect(Collectors.toList());
    if (!gpbkv.isEmpty()) telemetryMap.put("data_gpbkv", gpbkv);
    // TODO! We are not parsing data_gpb yet
    return telemetryMap;
  }

  public Map<String, Object> decode(Map<String, Object> input)
      throws InvalidProtocolBufferException, JsonProcessingException {
    if (input.containsKey(SERIALIZATION_KEY)
        && input.get(SERIALIZATION_KEY).equals(GPB)
        && input.containsKey(TELEMETRY_DATA_KEY)) {
      var singleLineString = ((String) input.remove(TELEMETRY_DATA_KEY)).replace("\n", "");
      var base64Decoded = Base64.getDecoder().decode(singleLineString);
      TelemetryOuterClass.Telemetry telemetry =
          TelemetryOuterClass.Telemetry.parseFrom(base64Decoded);
      input.put(TELEMETRY_DATA_KEY, getTelemetryMap(telemetry));
    }
    return input;
  }
}
