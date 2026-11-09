package dev.exceptionagent.tools;

import java.util.List;

/**
 * 单次请求的不可变解析结果，输入引用均使用从 1 开始的行号。
 *
 * @param exceptionChain 按顶层异常到最深主因排列的主异常链
 * @param keyFrames 主因优先、最多五项的关键帧
 * @param warnings 带来源行的解析局限和省略提示
 */
public record ParsedStackTrace(List<ExceptionNode> exceptionChain, List<StackFrame> keyFrames,
                               List<ParseWarning> warnings) {
    /** 防御性复制各集合，避免调用方修改结果或污染其他请求。 */
    public ParsedStackTrace {
        exceptionChain = List.copyOf(exceptionChain);
        keyFrames = List.copyOf(keyFrames);
        warnings = List.copyOf(warnings);
    }

    /** 返回已识别的最深主因；没有异常节点时返回 null，不推测类型。 */
    public ExceptionNode rootCause() {
        return exceptionChain.isEmpty() ? null : exceptionChain.get(exceptionChain.size() - 1);
    }

    /**
     * 主异常链上的一个节点，保留异常头与所属帧的输入依据。
     *
     * @param lineNumber 异常头在输入中的行号
     * @param text 异常头原文，不含换行符
     * @param exceptionType 已识别的异常类型名
     * @param message 去除标准分隔符后的异常消息，可为空
     * @param frames 明确归属于此异常的全部已识别帧
     */
    public record ExceptionNode(int lineNumber, String text, String exceptionType, String message,
                                List<StackFrame> frames) {
        /** 固定节点帧列表，禁止通过结果修改解析状态。 */
        public ExceptionNode {
            frames = List.copyOf(frames);
        }
    }

    /**
     * 一个已识别的堆栈帧，区分输入行号和源码行号。
     *
     * @param lineNumber 帧在输入中的行号
     * @param text 帧的原文，不含换行符
     * @param declaringClass 声明方法的类名
     * @param methodName 方法名，支持构造器及静态初始化器
     * @param prefix 模块或类加载器前缀，未提供时为 null
     * @param fileName 源文件名，未知位置或本地方法为 null
     * @param sourceLine 源码行号，未提供时为 null
     * @param locationKind 源码位置、未知位置或本地方法的分类
     */
    public record StackFrame(int lineNumber, String text, String declaringClass, String methodName,
                             String prefix, String fileName, Integer sourceLine, String locationKind) {
    }

    /**
     * 可追溯到输入的解析警告，供后续报告判定解析是否完整。
     *
     * @param lineNumber 需要提示的输入行号
     * @param code 稳定的警告分类码
     * @param text 对应输入行的原文
     */
    public record ParseWarning(int lineNumber, String code, String text) {
    }
}
