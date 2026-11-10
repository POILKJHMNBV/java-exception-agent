package dev.exceptionagent.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** 在 JSON 转换前限制原始请求体，未知 Content-Length 也不能绕过内存边界。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class DiagnosisRequestFilter extends OncePerRequestFilter {
    /** 仅在当前 Servlet 请求内使用的服务端关联标识。 */
    static final String ID_ATTRIBUTE = DiagnosisRequestFilter.class.getName() + ".diagnosisId";
    /** 整个 HTTP 请求体最多 128 KiB。 */
    private static final int MAX_BODY = 128 * 1024;
    /** 安全错误记录的序列化器。 */
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 只拦截产品定义的诊断 POST，避免改变无关端点的请求流。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + "/api/diagnoses").equals(request.getRequestURI());
    }

    /** 有界读取到上限加一个字节，拒绝超限而不是截断后继续诊断。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ID_ATTRIBUTE, id);
        response.setHeader("X-Diagnosis-Id", id);
        if (request.getContentLengthLong() > MAX_BODY) {
            reject(response, id, "INPUT_TOO_LARGE");
            return;
        }
        byte[] body;
        try { body = request.getInputStream().readNBytes(MAX_BODY + 1); }
        catch (IOException exception) { reject(response, id, "INVALID_REQUEST"); return; }
        if (body.length > MAX_BODY) { reject(response, id, "INPUT_TOO_LARGE"); return; }
        // 原始流由 Servlet 容器管理；下游只能读取这个有界快照，不会再次读取网络正文。
        chain.doFilter(new BodyRequest(request, body), response);
    }

    /** 过滤器早期拒绝也使用与 MVC 一致的 JSON 错误契约。 */
    private void reject(HttpServletResponse response, String id, String code) throws IOException {
        response.setStatus(ApiError.status(code));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ApiError.of(id, code));
    }

    /** 将已读取的有界正文提供给同步 MVC 转换器。 */
    private static final class BodyRequest extends HttpServletRequestWrapper {
        /** 仅属于当前请求的有界原始字节。 */
        private final byte[] body;
        /** 绑定 Servlet 元数据和当前正文快照。 */
        private BodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        /** 返回实际读取到的长度，不信任原始长度声明。 */
        @Override
        public int getContentLength() { return body.length; }
        /** 长整型长度与字节快照保持一致。 */
        @Override
        public long getContentLengthLong() { return body.length; }
        /** 同步字节流不持有网络连接或外部文件句柄。 */
        @Override
        public ServletInputStream getInputStream() { return new BodyStream(body); }
        /** 文本读取同样只访问有限快照。 */
        @Override
        public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }

    /** 用于同步 MVC 的内存 Servlet 流。 */
    private static final class BodyStream extends ServletInputStream {
        /** 当前读取位置独立于其他请求。 */
        private final ByteArrayInputStream input;
        /** 绑定已经过长度验证的字节数组。 */
        private BodyStream(byte[] body) { this.input = new ByteArrayInputStream(body); }
        /** 逐字节读取直到快照结尾。 */
        @Override
        public int read() { return input.read(); }
        /** 批量读取不产生额外无界缓冲区。 */
        @Override
        public int read(byte[] target, int offset, int length) { return input.read(target, offset, length); }
        /** 直接查询本地快照剩余长度。 */
        @Override
        public boolean isFinished() { return input.available() == 0; }
        /** 内存快照读取不会等待网络。 */
        @Override
        public boolean isReady() { return true; }
        /** 本端点只支持同步 MVC，不隐式启用异步 Servlet 读取。 */
        @Override
        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request body"); }
    }
}
