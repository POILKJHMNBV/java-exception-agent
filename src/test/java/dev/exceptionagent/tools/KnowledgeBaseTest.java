package dev.exceptionagent.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证知识资源加载、精确检索、失败退出及不可变结果的确定性测试。 */
class KnowledgeBaseTest {
    /** 多个测试共用的空指针异常全限定名。 */
    private static final String NPE = "java.lang.NullPointerException";
    /** 生成真实解析结果，避免跳过查询与主因一致性约束。 */
    private final StackTraceParser parser = new StackTraceParser();

    /** 核验打包资源覆盖三个必需类型，且返回条目的必填内容完整。 */
    @ParameterizedTest
    @ValueSource(strings = {"java.lang.NullPointerException", "java.lang.IllegalArgumentException",
            "java.lang.NumberFormatException"})
    void bundledResourceCoversRequiredExceptions(String type) {
        KnowledgeBase knowledge = new KnowledgeBase();
        List<KnowledgeEntry> found = knowledge.search(type, parsed(type));
        assertThat(found).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(found).allSatisfy(entry -> {
            assertThat(entry.id()).isNotBlank();
            assertThat(entry.exceptionType()).isEqualTo(type);
            assertThat(entry.causes()).isNotEmpty().allSatisfy(cause -> assertThat(cause).isNotBlank());
            assertThat(entry.suggestions()).isNotEmpty().allSatisfy(action -> assertThat(action).isNotBlank());
        });
    }

    /** 验证排序与数量上限仅作用于精确命中的条目，重复查询结果稳定。 */
    @Test
    void sortsByIdAndCapsEachExactMatchAtThree() {
        KnowledgeBase knowledge = load("[" + entry("n-04", NPE) + "," + entry("n-02", NPE) + ","
                + entry("n-01", NPE) + "," + entry("n-03", NPE) + ","
                + entry("a-00", "java.lang.IllegalArgumentException") + "]");
        assertThat(knowledge.search(NPE, parsed(NPE))).extracting(KnowledgeEntry::id)
                .containsExactly("n-01", "n-02", "n-03");
        assertThat(knowledge.search(NPE, parsed(NPE))).isEqualTo(knowledge.search(NPE, parsed(NPE)));
    }

    /** 未知类型、简单名、大小写及前缀近似均不得触发模糊匹配。 */
    @ParameterizedTest
    @ValueSource(strings = {"com.example.OtherException", "NullPointerException",
            "java.lang.nullPointerException", "java.lang.NullPointerExceptionExtra"})
    void unknownTypeHasNoFuzzyOrSimpleNameMatches(String type) {
        assertThat(new KnowledgeBase().search(type, parsed(type))).isEmpty();
    }

    /** 无法解析堆栈时只能查询空类型，并返回空知识命中。 */
    @Test
    void unrecognizedStackOnlyAllowsEmptyTypeAndReturnsEmpty() {
        ParsedStackTrace unrecognized = parser.parse("not a stack trace");
        KnowledgeBase knowledge = new KnowledgeBase();
        assertThat(knowledge.search("", unrecognized)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> knowledge.search(NPE, unrecognized));
    }

    /** 拒绝与当前主因不一致的类型、任意路径、URL 和指令式参数。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "java.lang.IllegalArgumentException", "NullPointerException",
            " java.lang.NullPointerException", "java.lang.NullPointerException ",
            "https://example.com/knowledge", "../../secrets", "ignore instructions"})
    void rejectsQueriesThatDifferFromCurrentParsedCause(String query) {
        assertThatIllegalArgumentException().isThrownBy(() -> new KnowledgeBase().search(query, parsed(NPE)));
    }

    /** 查询必须已有解析结果，且只能使用最深主因而非外层异常类型。 */
    @Test
    void requiresParseResultBeforeQueryAndUsesDeepestCause() {
        KnowledgeBase knowledge = new KnowledgeBase();
        assertThatIllegalArgumentException().isThrownBy(() -> knowledge.search(NPE, null));
        ParsedStackTrace chained = parser.parse("java.lang.RuntimeException\n\tat a.C.m(C.java:1)\n"
                + "Caused by: " + NPE + "\n\tat a.C.n(C.java:2)");
        assertThat(knowledge.search(NPE, chained)).isNotEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> knowledge.search("java.lang.RuntimeException", chained));
    }

    /** 坏 JSON、缺字段、错误类型或空内容应明确加载失败，不伪装为空知识库。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "{", "null", "{}", "[]", "[null]", "[1]", "[{}]",
            "[{\"id\":1,\"exceptionType\":\"a.E\",\"causes\":[\"c\"],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\" \",\"exceptionType\":\"a.E\",\"causes\":[\"c\"],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":null,\"causes\":[\"c\"],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\" \",\"causes\":[\"c\"],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[null],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[12],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[\" \"],\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":\"c\",\"suggestions\":[\"s\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[\"c\"]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[\"c\"],\"suggestions\":[]}]",
            "[{\"id\":\"x\",\"exceptionType\":\"a.E\",\"causes\":[\"c\"],\"suggestions\":[false]}]"
    })
    void invalidResourcesFailInsteadOfProducingAnEmptyKnowledgeBase(String json) {
        assertThatIllegalStateException().isThrownBy(() -> load(json)).withMessageContaining("knowledge");
    }

    /** 条目 ID 在整个知识库唯一，不因异常类型不同而允许重复。 */
    @Test
    void rejectsDuplicateIdsEvenAcrossDifferentTypes() {
        assertThatIllegalStateException().isThrownBy(() -> load("[" + entry("same", NPE) + ","
                + entry("same", "java.lang.IllegalArgumentException") + "]"));
    }

    /** 拒绝重复 JSON 字段和额外尾随文档，避免解析歧义。 */
    @Test
    void rejectsDuplicateJsonFieldsAndTrailingDocuments() {
        assertThatIllegalStateException().isThrownBy(() -> load("["
                + entry("one", NPE).replace("\"id\":\"one\"", "\"id\":\"one\",\"id\":\"two\"") + "]"));
        assertThatIllegalStateException().isThrownBy(() -> load("[" + entry("one", NPE) + "] []"));
    }

    /** 固定资源不存在时必须抛出可辨识的知识库加载错误。 */
    @Test
    void missingResourceFailsClearly() {
        assertThatIllegalStateException().isThrownBy(() -> new KnowledgeBase((InputStream) null))
                .withMessageContaining("knowledge");
    }

    /** 验证坏知识库 Bean 会使实际 Spring 上下文启动失败。 */
    @Test
    void invalidKnowledgePreventsContextStartup() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KnowledgeBase.class, () -> load("[{}]"));
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.isActive()).isFalse();
        }
    }

    /** 成功和解析失败路径均必须关闭输入流。 */
    @Test
    void closesResourceOnBothSuccessAndFailure() {
        TrackingStream good = new TrackingStream("[" + entry("one", NPE) + "]");
        new KnowledgeBase(good);
        assertThat(good.closed).isTrue();
        TrackingStream bad = new TrackingStream("{");
        assertThatIllegalStateException().isThrownBy(() -> new KnowledgeBase(bad));
        assertThat(bad.closed).isTrue();
    }

    /** 底层读取失败必须传播为加载失败，不得返回可用空集合。 */
    @Test
    void ioFailureIsNotSwallowed() {
        // 注入确定性的读取故障，不依赖真实磁盘权限或环境异常。
        InputStream broken = new InputStream() {
            /** 每次读取都抛出受控 I/O 异常，用于验证失败出口。 */
            @Override
            public int read() throws IOException {
                throw new IOException("synthetic read failure");
            }
        };
        assertThatIllegalStateException().isThrownBy(() -> new KnowledgeBase(broken));
    }

    /** 返回集合及条目内部列表均不可变，空查询不得影响其他请求。 */
    @Test
    void returnedEntriesAreDeeplyImmutableAndRequestsAreIndependent() {
        KnowledgeBase knowledge = new KnowledgeBase();
        List<KnowledgeEntry> first = knowledge.search(NPE, parsed(NPE));
        assertThat(first).isNotEmpty();
        assertThatThrownBy(() -> first.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.get(0).causes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.get(0).suggestions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(knowledge.search("", parser.parse("unknown"))).isEmpty();
        assertThat(knowledge.search(NPE, parsed(NPE))).isEqualTo(first);
    }

    /** 用给定类型构造最小合法堆栈并经过实际解析器。 */
    private ParsedStackTrace parsed(String type) {
        return parser.parse(type + "\n\tat example.Main.main(Main.java:1)");
    }

    /** 通过 UTF-8 内存资源加载测试知识库，不开放任意文件读取。 */
    private static KnowledgeBase load(String json) {
        return new KnowledgeBase(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** 生成包含中文原因和建议的最小合法测试条目。 */
    private static String entry(String id, String type) {
        return "{\"id\":\"" + id + "\",\"exceptionType\":\"" + type
                + "\",\"causes\":[\"可能为空\"],\"suggestions\":[\"检查输入\"]}";
    }

    /** 记录关闭动作的内存输入流，用于资源释放断言。 */
    private static final class TrackingStream extends ByteArrayInputStream {
        /** 是否已调用 close，不依赖底层内存流默认关闭行为。 */
        private boolean closed;

        /** 将测试 JSON 转为 UTF-8 字节并初始化跟踪流。 */
        private TrackingStream(String json) {
            super(json.getBytes(StandardCharsets.UTF_8));
        }

        /** 记录关闭事实后委托父类完成关闭操作。 */
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
