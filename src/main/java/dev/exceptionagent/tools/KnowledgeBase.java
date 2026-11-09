package dev.exceptionagent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 启动时仅加载打包的知识资源，查询不接受文件路径或 URL。 */
@Component
public final class KnowledgeBase {
    /** 校验通过后按 ID 排序的不可变条目集合。 */
    private final List<KnowledgeEntry> entries;

    /** 从固定 classpath 位置加载知识库，缺失或损坏时阻止启动。 */
    public KnowledgeBase() {
        this(KnowledgeBase.class.getResourceAsStream("/knowledge/exceptions.json"));
    }

    /** 加载并关闭给定资源流；包内入口便于测试损坏资源与 I/O 失败。 */
    KnowledgeBase(InputStream input) {
        if (input == null) {
            throw new IllegalStateException("Missing knowledge resource: knowledge/exceptions.json");
        }
        // 成功、格式错误或读取异常都必须关闭流，避免启动失败时泄漏资源。
        try (InputStream resource = input) {
            JsonMapper mapper = JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
            JsonNode root = mapper.readTree(resource);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw invalid("expected a nonempty array");
            }
            // 先完整校验再发布不可变集合，禁止把半加载结果作为可用知识库。
            List<KnowledgeEntry> loaded = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    throw invalid("entry must be an object");
                }
                String id = requiredText(item.get("id"));
                String type = requiredText(item.get("exceptionType"));
                List<String> causes = requiredTexts(item.get("causes"));
                List<String> suggestions = requiredTexts(item.get("suggestions"));
                if (!ids.add(id)) {
                    throw invalid("duplicate entry ID");
                }
                loaded.add(new KnowledgeEntry(id, type, causes, suggestions));
            }
            loaded.sort(Comparator.comparing(KnowledgeEntry::id));
            entries = List.copyOf(loaded);
        }
        catch (JacksonException | IOException exception) {
            // 使用明确的固定错误描述，避免底层异常将资源内容带入启动日志。
            throw invalid("cannot read or decode knowledge/exceptions.json");
        }
    }

    /** 按本次解析主因精确查询，返回按 ID 排序的至多三项；非法类型请求直接拒绝。 */
    public List<KnowledgeEntry> search(String exceptionType, ParsedStackTrace parsed) {
        if (exceptionType == null || parsed == null) {
            throw new IllegalArgumentException("Query requires a type and the current parse result");
        }
        // 无法解析时只允许空类型，防止模型改查其他异常来制造诊断依据。
        String expected = parsed.rootCause() == null ? "" : parsed.rootCause().exceptionType();
        if (!exceptionType.equals(expected)) {
            throw new IllegalArgumentException("Query type must equal the current parsed root cause");
        }
        if (exceptionType.isEmpty()) {
            return List.of();
        }
        return entries.stream().filter(entry -> entry.exceptionType().equals(exceptionType)).limit(3).toList();
    }

    /** 校验必填非空字符串，不将数字、布尔值等节点隐式转换为文本。 */
    private static String requiredText(JsonNode value) {
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw invalid("required text must be a nonblank string");
        }
        return value.asString();
    }

    /** 校验必填字符串数组及每个元素，并返回不可变副本。 */
    private static List<String> requiredTexts(JsonNode value) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw invalid("required list must be a nonempty string array");
        }
        List<String> strings = new ArrayList<>();
        for (JsonNode element : value) {
            strings.add(requiredText(element));
        }
        return List.copyOf(strings);
    }

    /** 生成统一的知识资源加载错误，供启动流程明确失败。 */
    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid knowledge resource: " + reason);
    }
}
