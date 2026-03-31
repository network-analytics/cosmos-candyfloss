package com.swisscom.daisy.cosmos.candyfloss.testutils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutputUpdaterUtils {
  private static final String SYS_LF = "\n";
  private static final String COMMENT_TRAILING_ANCHOR = "__COMMENT_TRAILING__";
  private static final Pattern FIELD_NAME_PATTERN =
      Pattern.compile("^\"((?:\\\\.|[^\"\\\\])+)\"\\s*:");

  /**
   * Recursively merges the newly generated JSON with the existing JSON to preserve field iteration
   * order and human input comments,
   */
  public static void writeExpectedOutput(
      Path expectedOutputPath,
      String originalContent,
      JsonNode currentOutputTree,
      JsonNode outputToWrite,
      ObjectMapper objectMapper)
      throws IOException {
    if (currentOutputTree != null) {
      if (canMerge(currentOutputTree, outputToWrite)) {
        mergePreservingOrder(currentOutputTree, outputToWrite, objectMapper);
        outputToWrite = currentOutputTree;
      }
    }

    DefaultPrettyPrinter customPrinter = new CustomPrettyPrinter();
    DefaultIndenter indenter = new DefaultIndenter("  ", SYS_LF);
    customPrinter.indentArraysWith(indenter);
    customPrinter.withObjectIndenter(indenter);

    String updatedContent = objectMapper.writer(customPrinter).writeValueAsString(outputToWrite);
    updatedContent = applyComments(originalContent, updatedContent);
    Files.writeString(expectedOutputPath, updatedContent);
  }

  /** Scans the newly generated JSON string line by line and safely injects extracted comments */
  private static String applyComments(String originalContent, String updatedContent) {
    if (originalContent == null || originalContent.isBlank()) {
      return updatedContent;
    }

    CommentContext context = extractAnchoredComments(originalContent);
    if (context.preceding.isEmpty() && context.inline.isEmpty()) {
      return updatedContent;
    }

    List<String> rewrittenLines = new ArrayList<>();
    String[] updatedLines = updatedContent.split("\\R");
    Map<String, Integer> anchorOccurrences = new HashMap<>();

    for (String line : updatedLines) {
      String fieldName = extractFieldName(line.trim());
      if (fieldName != null) {
        appendAnchoredComments(rewrittenLines, context.preceding, anchorOccurrences, fieldName);
      }
      if (line.trim().equals("}") || line.trim().equals("]")) {
        appendAnchoredComments(
            rewrittenLines, context.preceding, anchorOccurrences, COMMENT_TRAILING_ANCHOR);
      }

      String outputLine = line;
      if (fieldName != null) {
        int idx = anchorOccurrences.getOrDefault(fieldName, 0) - 1; // already incremented above
        Map<Integer, List<String>> inlinesMap = context.inline.get(fieldName);
        if (inlinesMap != null) {
          List<String> inlines = inlinesMap.get(idx);
          if (inlines != null && !inlines.isEmpty()) {
            String inline = inlines.get(0);
            if (inline != null) {
              outputLine = outputLine + " " + inline;
            }
          }
        }
      }

      rewrittenLines.add(outputLine);
    }

    Map<Integer, List<List<String>>> trailingCommentBlocksMap =
        context.preceding.get(COMMENT_TRAILING_ANCHOR);
    if (trailingCommentBlocksMap != null) {
      int usedTrailingBlocks = anchorOccurrences.getOrDefault(COMMENT_TRAILING_ANCHOR, 0);
      for (Map.Entry<Integer, List<List<String>>> entry : trailingCommentBlocksMap.entrySet()) {
        if (entry.getKey() >= usedTrailingBlocks) {
          for (List<String> block : entry.getValue()) {
            rewrittenLines.add(""); // Optional empty line spacer before trailing comments
            rewrittenLines.addAll(block);
          }
        }
      }
    }

    return String.join(System.lineSeparator(), rewrittenLines);
  }

  private static void appendAnchoredComments(
      List<String> rewrittenLines,
      Map<String, Map<Integer, List<List<String>>>> anchoredComments,
      Map<String, Integer> anchorOccurrences,
      String anchorKey) {
    Map<Integer, List<List<String>>> commentBlocksMap = anchoredComments.get(anchorKey);
    int occurrenceIndex = anchorOccurrences.getOrDefault(anchorKey, 0);

    if (commentBlocksMap != null) {
      List<List<String>> commentBlocks = commentBlocksMap.get(occurrenceIndex);
      if (commentBlocks != null) {
        for (List<String> block : commentBlocks) {
          rewrittenLines.addAll(block);
        }
      }
    }
    anchorOccurrences.put(anchorKey, occurrenceIndex + 1);
  }

  private static class CommentContext {
    Map<String, Map<Integer, List<List<String>>>> preceding = new LinkedHashMap<>();
    Map<String, Map<Integer, List<String>>> inline = new LinkedHashMap<>();
  }

  /**
   * Parses the original text-based JSON file line-by-line to extract standalone and inline
   * comments. Maps each comment block to the closest succeeding JSON field name (or trailing EOF
   * bracket),
   */
  private static CommentContext extractAnchoredComments(String originalContent) {
    CommentContext context = new CommentContext();
    List<String> pendingCommentBlock = new ArrayList<>();
    boolean insideBlockComment = false;

    Map<String, Integer> fieldOccurrences = new HashMap<>();

    for (String line : originalContent.split("\\R")) {
      String trimmed = line.trim();

      if (insideBlockComment) {
        pendingCommentBlock.add(line);
        if (trimmed.contains("*/")) {
          insideBlockComment = false;
        }
        continue;
      }

      if (trimmed.startsWith("//")) {
        pendingCommentBlock.add(line);
        continue;
      }

      if (trimmed.startsWith("/*")) {
        pendingCommentBlock.add(line);
        if (!trimmed.contains("*/")) {
          insideBlockComment = true;
        }
        continue;
      }

      String fieldName = extractFieldName(trimmed);
      String inlineComment = extractInlineComment(line);

      if (fieldName != null) {
        int occurrence = fieldOccurrences.getOrDefault(fieldName, 0);

        if (!pendingCommentBlock.isEmpty()) {
          context
              .preceding
              .computeIfAbsent(fieldName, ignored -> new HashMap<>())
              .computeIfAbsent(occurrence, ignored -> new ArrayList<>())
              .add(new ArrayList<>(pendingCommentBlock));
          pendingCommentBlock.clear();
        }

        if (inlineComment != null) {
          context
              .inline
              .computeIfAbsent(fieldName, ignored -> new HashMap<>())
              .computeIfAbsent(occurrence, ignored -> new ArrayList<>())
              .add(inlineComment);
        }

        fieldOccurrences.put(fieldName, occurrence + 1);
        continue;
      }

      if (!pendingCommentBlock.isEmpty()) {
        if (trimmed.equals("}") || trimmed.equals("]")) {
          int occurrence = fieldOccurrences.getOrDefault(COMMENT_TRAILING_ANCHOR, 0);
          context
              .preceding
              .computeIfAbsent(COMMENT_TRAILING_ANCHOR, ignored -> new HashMap<>())
              .computeIfAbsent(occurrence, ignored -> new ArrayList<>())
              .add(new ArrayList<>(pendingCommentBlock));
          pendingCommentBlock.clear();
          fieldOccurrences.put(COMMENT_TRAILING_ANCHOR, occurrence + 1);
        }
      }
    }

    if (!pendingCommentBlock.isEmpty()) {
      int occurrence = fieldOccurrences.getOrDefault(COMMENT_TRAILING_ANCHOR, 0);
      context
          .preceding
          .computeIfAbsent(COMMENT_TRAILING_ANCHOR, ignored -> new HashMap<>())
          .computeIfAbsent(occurrence, ignored -> new ArrayList<>())
          .add(new ArrayList<>(pendingCommentBlock));
    }

    return context;
  }

  /**
   * Inspects a single text line to identify and extract any inline comments (e.g. `// ...` or `/*
   * ... *\/`) located after the physical JSON value, ignoring data wrapped inside valid JSON string
   * literals.
   */
  private static String extractInlineComment(String line) {
    boolean inString = false;
    boolean escape = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (escape) {
        escape = false;
      } else if (c == '\\') {
        escape = true;
      } else if (c == '"') {
        inString = !inString;
      } else if (!inString && c == '/' && i + 1 < line.length()) {
        if (line.charAt(i + 1) == '/') {
          return line.substring(i).trim();
        }
        if (line.charAt(i + 1) == '*') {
          int end = line.indexOf("*/", i + 2);
          if (end != -1) {
            return line.substring(i, end + 2).trim();
          }
        }
      }
    }
    return null;
  }

  private static String extractFieldName(String trimmedLine) {
    Matcher matcher = FIELD_NAME_PATTERN.matcher(trimmedLine);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static boolean canMerge(JsonNode n1, JsonNode n2) {
    return (n1.isObject() && n2.isObject()) || (n1.isArray() && n2.isArray());
  }

  /**
   * Recursively updates existing JsonNode tree with values from a newly generated JsonNode tree.
   * Modifies the values in-place and removes deleted fields which preserve original physical order.
   */
  private static void mergePreservingOrder(
      JsonNode currentNode, JsonNode producedNode, ObjectMapper mapper) {
    if (currentNode.isObject() && producedNode.isObject()) {
      mergeObjectInPlace((ObjectNode) currentNode, (ObjectNode) producedNode, mapper);
    } else if (currentNode.isArray() && producedNode.isArray()) {
      mergeArrayInPlace((ArrayNode) currentNode, (ArrayNode) producedNode, mapper);
    }
  }

  private static void mergeObjectInPlace(
      ObjectNode currentNode, ObjectNode producedNode, ObjectMapper mapper) {
    List<String> currentKeys = new ArrayList<>();
    currentNode.fieldNames().forEachRemaining(currentKeys::add);
    List<String> producedKeys = new ArrayList<>();
    producedNode.fieldNames().forEachRemaining(producedKeys::add);

    List<String> keysToRemove = new ArrayList<>();
    for (String key : currentKeys) {
      if (!producedKeys.contains(key)) {
        keysToRemove.add(key);
      }
    }
    keysToRemove.forEach(currentNode::remove);

    for (String key : currentKeys) {
      if (producedKeys.contains(key)) {
        JsonNode currentField = currentNode.get(key);
        JsonNode producedField = producedNode.get(key);
        if (currentField.isObject() && producedField.isObject()) {
          mergeObjectInPlace((ObjectNode) currentField, (ObjectNode) producedField, mapper);
        } else if (currentField.isArray() && producedField.isArray()) {
          mergeArrayInPlace((ArrayNode) currentField, (ArrayNode) producedField, mapper);
        } else if (!currentField.equals(producedField)) {
          currentNode.set(key, producedField);
        }
      }
    }

    for (String key : producedKeys) {
      if (!currentNode.has(key)) {
        currentNode.set(key, producedNode.get(key));
      }
    }
  }

  private static void mergeArrayInPlace(
      ArrayNode currentNode, ArrayNode producedNode, ObjectMapper mapper) {
    int sharedSize = Math.min(currentNode.size(), producedNode.size());

    for (int i = 0; i < sharedSize; i++) {
      JsonNode currentElement = currentNode.get(i);
      JsonNode producedElement = producedNode.get(i);

      if (canMerge(currentElement, producedElement)) {
        mergePreservingOrder(currentElement, producedElement, mapper);
      } else {
        currentNode.set(i, producedElement);
      }
    }

    while (currentNode.size() > producedNode.size()) {
      currentNode.remove(currentNode.size() - 1);
    }

    for (int i = sharedSize; i < producedNode.size(); i++) {
      currentNode.add(producedNode.get(i));
    }
  }

  /**
   * Custom Jackson pretty printer to precisely control serialization formatting. (enforcing `"key":
   * value` instead of `"key" : value`).
   */
  private static class CustomPrettyPrinter extends DefaultPrettyPrinter {
    public CustomPrettyPrinter() {
      super();
      _objectFieldValueSeparatorWithSpaces = ": ";
    }

    public CustomPrettyPrinter(CustomPrettyPrinter base) {
      super(base);
    }

    @Override
    public DefaultPrettyPrinter createInstance() {
      return new CustomPrettyPrinter(this);
    }
  }
}
