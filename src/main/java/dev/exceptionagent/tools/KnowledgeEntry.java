package dev.exceptionagent.tools;

import java.util.List;

/**
 * 从本地知识资源加载的不可变条目，内容只作为诊断参考数据。
 *
 * @param id 唯一条目标识，用于排序与证据引用
 * @param exceptionType 精确匹配的异常类型名
 * @param causes 候选原因描述，不表示已确定实际根因
 * @param suggestions 可执行的排查建议
 */
public record KnowledgeEntry(String id, String exceptionType, List<String> causes, List<String> suggestions) {
    /** 复制原因和建议列表，防止查询结果修改共享知识库。 */
    public KnowledgeEntry {
        causes = List.copyOf(causes);
        suggestions = List.copyOf(suggestions);
    }
}
