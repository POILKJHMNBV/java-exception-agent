package dev.exceptionagent.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.exceptionagent.tools.ParsedStackTrace.ExceptionNode;
import dev.exceptionagent.tools.ParsedStackTrace.ParseWarning;
import dev.exceptionagent.tools.ParsedStackTrace.StackFrame;

/** 无状态堆栈解析器，仅提取主异常链与可追溯帧，不执行输入中的任何指令。 */
@Component
public final class StackTraceParser {
    /** JVM 输出的线程异常头格式。 */
    private static final Pattern THREAD = Pattern.compile("^Exception in thread \"[^\"]*\"\\s+(.+)$");
    /** 方法定位与括号内源码位置的帧格式。 */
    private static final Pattern FRAME = Pattern.compile("^at\\s+([^\\s()]+)\\(([^()]*)\\)$");
    /** 父帧省略标记；只记录提示，不据此补造帧。 */
    private static final Pattern ELISION = Pattern.compile("^\\.\\.\\.\\s+\\d+\\s+more$");

    /** 解析原始堆栈，保留行号与警告；null 输入属于调用错误。 */
    public ParsedStackTrace parse(String stackTrace) {
        if (stackTrace == null) {
            throw new IllegalArgumentException("stackTrace must not be null");
        }
        // 所有可变状态都属于当前调用，单例解析器不持有跨请求的异常链。
        List<NodeBuilder> nodes = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();
        NodeBuilder current = null;
        int suppressedIndent = -1;
        boolean outsideMainChain = false;
        String[] lines = stackTrace.split("\\r\\n|\\n|\\r", -1);
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String line = raw.stripLeading();
            int lineNumber = index + 1;
            if (line.isEmpty()) {
                continue;
            }
            // 利用缩进隔离整个 Suppressed 分支，包含其内部的 Caused by。
            if (suppressedIndent >= 0) {
                if (indentation(raw) >= suppressedIndent) {
                    warnings.add(new ParseWarning(lineNumber, "SUPPRESSED_BRANCH", raw));
                    continue;
                }
                suppressedIndent = -1;
            }
            if (line.startsWith("Suppressed:")) {
                suppressedIndent = indentation(raw);
                warnings.add(new ParseWarning(lineNumber, "SUPPRESSED_BRANCH", raw));
                continue;
            }
            boolean causedBy = line.startsWith("Caused by:");
            String header = causedBy ? line.substring("Caused by:".length()).stripLeading() : line;
            Matcher thread = THREAD.matcher(header);
            if (!causedBy && thread.matches()) {
                header = thread.group(1);
            }
            NodeBuilder parsed = parseHeader(header, lineNumber, raw);
            if (parsed != null) {
                if (!outsideMainChain && ((nodes.isEmpty() && !causedBy) || (current != null && causedBy))) {
                    nodes.add(parsed);
                    current = parsed;
                }
                else {
                    // 拼接的另一段堆栈不能作为第一段主因或帧的依据。
                    outsideMainChain = true;
                    warnings.add(new ParseWarning(lineNumber, "UNRECOGNIZED_LINE", raw));
                }
                continue;
            }
            if (causedBy) {
                // 主因头损坏后无法确认帧归属，不能将后续帧误挂到父异常。
                outsideMainChain = true;
            }
            if (current != null && !outsideMainChain) {
                StackFrame frame = parseFrame(line.stripTrailing(), lineNumber, raw);
                if (frame != null) {
                    current.frames.add(frame);
                    continue;
                }
                if (ELISION.matcher(line.stripTrailing()).matches()) {
                    warnings.add(new ParseWarning(lineNumber, "ELIDED_FRAMES", raw));
                    continue;
                }
            }
            warnings.add(new ParseWarning(lineNumber, "UNRECOGNIZED_LINE", raw));
        }
        List<ExceptionNode> chain = new ArrayList<>();
        for (NodeBuilder node : nodes) {
            chain.add(node.build());
            if (node.frames.isEmpty()) {
                warnings.add(new ParseWarning(node.lineNumber, "MISSING_FRAMES", node.text));
            }
        }
        if (chain.isEmpty() && warnings.isEmpty()) {
            warnings.add(new ParseWarning(1, "UNRECOGNIZED_LINE", lines[0]));
        }
        warnings.sort(Comparator.comparingInt(ParseWarning::lineNumber));
        // 主因帧优先，不足五帧时向最近父异常回溯；每个节点内保持输入顺序。
        List<StackFrame> keyFrames = new ArrayList<>();
        for (int index = chain.size() - 1; index >= 0 && keyFrames.size() < 5; index--) {
            for (StackFrame frame : chain.get(index).frames()) {
                if (keyFrames.size() == 5) {
                    break;
                }
                keyFrames.add(frame);
            }
        }
        return new ParsedStackTrace(chain, keyFrames, warnings);
    }

    /** 识别异常头并保留消息空白；无可信类型格式时返回 null。 */
    private static NodeBuilder parseHeader(String header, int lineNumber, String raw) {
        int separator = header.indexOf(':');
        String type = separator < 0 ? header.stripTrailing() : header.substring(0, separator);
        if (!isQualifiedIdentifier(type) || (!type.contains(".")
                && !type.endsWith("Exception") && !type.endsWith("Error") && !type.endsWith("Throwable"))) {
            return null;
        }
        String message = separator < 0 ? "" : header.substring(separator + 1);
        // 只去掉标准冒号分隔符后的一个空格，其他空白属于原消息。
        if (message.startsWith(" ")) {
            message = message.substring(1);
        }
        return new NodeBuilder(lineNumber, raw, type, message);
    }

    /** 解析普通帧、模块前缀和特殊位置；格式损坏或行号越界时返回 null。 */
    private static StackFrame parseFrame(String line, int lineNumber, String raw) {
        Matcher matcher = FRAME.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String qualifiedMethod = matcher.group(1);
        // 最后一个斜杠区分模块/类加载器前缀与实际类方法名。
        int prefixEnd = qualifiedMethod.lastIndexOf('/');
        String prefix = prefixEnd < 0 ? null : qualifiedMethod.substring(0, prefixEnd);
        String method = qualifiedMethod.substring(prefixEnd + 1);
        int methodSeparator = method.lastIndexOf('.');
        if (methodSeparator <= 0 || methodSeparator == method.length() - 1) {
            return null;
        }
        String declaringClass = method.substring(0, methodSeparator);
        String methodName = method.substring(methodSeparator + 1);
        if (!isQualifiedIdentifier(declaringClass) || !(isQualifiedIdentifier(methodName)
                || methodName.equals("<init>") || methodName.equals("<clinit>"))) {
            return null;
        }
        String location = matcher.group(2);
        String file = null;
        Integer sourceLine = null;
        String kind;
        // 特殊位置不具备可信文件名与行号，保留 null 而非构造默认值。
        if (location.equals("Unknown Source")) {
            kind = "UNKNOWN_SOURCE";
        }
        else if (location.equals("Native Method")) {
            kind = "NATIVE_METHOD";
        }
        else {
            kind = "SOURCE";
            int colon = location.lastIndexOf(':');
            file = colon < 0 ? location : location.substring(0, colon);
            if (file.isBlank()) {
                return null;
            }
            if (colon >= 0) {
                try {
                    sourceLine = Integer.valueOf(location.substring(colon + 1));
                }
                catch (NumberFormatException exception) {
                    return null;
                }
                if (sourceLine < 0) {
                    return null;
                }
            }
        }
        return new StackFrame(lineNumber, raw, declaringClass, methodName, prefix, file, sourceLine, kind);
    }

    /** 计算分支缩进宽度，制表符按四个空格计。 */
    private static int indentation(String line) {
        int width = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\t') {
                width += 4;
            }
            else if (character == ' ') {
                width++;
            }
            else {
                break;
            }
        }
        return width;
    }

    /** 按 Unicode 码点迭代检查限定标识符，避免长类型名导致递归正则栈溢出。 */
    private static boolean isQualifiedIdentifier(String value) {
        boolean atStart = true;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '.' && !atStart) {
                atStart = true;
            }
            else if (atStart ? Character.isJavaIdentifierStart(codePoint) : Character.isJavaIdentifierPart(codePoint)) {
                atStart = false;
            }
            else {
                return false;
            }
        }
        return !atStart;
    }

    /** 仅在单次解析内部使用的节点构建器，完成后转换为不可变节点。 */
    private static final class NodeBuilder {
        /** 异常头对应的输入行号。 */
        private final int lineNumber;
        /** 未改写的异常头原文。 */
        private final String text;
        /** 已识别的异常类型名。 */
        private final String type;
        /** 保留有效空白的异常消息。 */
        private final String message;
        /** 按原顺序收集、归属明确的堆栈帧。 */
        private final List<StackFrame> frames = new ArrayList<>();

        /** 保存异常头信息，等待后续行补充所属帧。 */
        private NodeBuilder(int lineNumber, String text, String type, String message) {
            this.lineNumber = lineNumber;
            this.text = text;
            this.type = type;
            this.message = message;
        }

        /** 生成不可变节点，将可变收集状态封闭在本次解析中。 */
        private ExceptionNode build() {
            return new ExceptionNode(lineNumber, text, type, message, frames);
        }
    }
}
