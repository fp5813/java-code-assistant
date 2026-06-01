package com.moma.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.*;

import java.util.Iterator;
import java.util.Map;

/**
 * JSON Schema → LangChain4j JsonObjectSchema 解析器。
 * 支持 JSON Schema 的子集：string, integer, number, boolean, array, object 类型。
 */
public class JsonSchemaParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemaParser() {}

    /**
     * 将 JSON Schema 字符串解析为 JsonObjectSchema。
     */
    public static JsonObjectSchema parse(String schemaJson) {
        try {
            JsonNode root = MAPPER.readTree(schemaJson);
            return parseObject(root);
        } catch (Exception e) {
            throw new RuntimeException("JSON Schema 解析失败: " + e.getMessage(), e);
        }
    }

    private static JsonObjectSchema parseObject(JsonNode node) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                builder.addProperty(fieldName, parseProperty(fieldSchema));
            }
        }

        // required fields are not supported in LangChain4j 0.36.x JsonObjectSchema.Builder
        // They will be added in a future update

        // description for the object itself
        JsonNode description = node.get("description");
        if (description != null) {
            builder.description(description.asText());
        }

        return builder.build();
    }

    private static JsonSchemaElement parseProperty(JsonNode schema) {
        String type = schema.has("type") ? schema.get("type").asText() : "string";
        String description = schema.has("description") ? schema.get("description").asText() : null;

        return switch (type) {
            case "string" -> {
                JsonStringSchema.Builder b = JsonStringSchema.builder();
                if (description != null) b.description(description);
                yield b.build();
            }
            case "integer" -> {
                JsonIntegerSchema.Builder b = JsonIntegerSchema.builder();
                if (description != null) b.description(description);
                yield b.build();
            }
            case "number" -> {
                JsonNumberSchema.Builder b = JsonNumberSchema.builder();
                if (description != null) b.description(description);
                yield b.build();
            }
            case "boolean" -> {
                JsonBooleanSchema.Builder b = JsonBooleanSchema.builder();
                if (description != null) b.description(description);
                yield b.build();
            }
            case "array" -> {
                JsonArraySchema.Builder b = JsonArraySchema.builder();
                if (description != null) b.description(description);
                if (schema.has("items")) {
                    b.items(parseProperty(schema.get("items")));
                }
                yield b.build();
            }
            case "object" -> {
                if (schema.has("properties")) {
                    yield parseObject(schema);
                }
                yield JsonObjectSchema.builder().build();
            }
            default -> {
                // 默认当作 string
                JsonStringSchema.Builder b = JsonStringSchema.builder();
                if (description != null) b.description(description);
                yield b.build();
            }
        };
    }
}
