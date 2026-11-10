package dev.exceptionagent.diagnosis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractBufferingClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

/** 非流式 Ollama 传输：整个正文读取均处于可中断的 JDK send 内，取消不遗留半读连接。 */
final class CancellableHttpRequestFactory implements ClientHttpRequestFactory {
    /** 由 JDK 根据请求体生成的头或禁止手动设置的协议头。 */
    private static final Set<String> MANAGED_HEADERS = Set.of("connection", "content-length", "expect", "host", "upgrade");
    /** 共享本地连接客户端；不自动跟随重定向。 */
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 创建一次缓冲请求，请求体只供本地 Ollama 协议使用。 */
    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod method) { return new Request(uri, method); }

    /** 将 Spring 的非流式请求映射为覆盖整个响应生命周期的 JDK 请求。 */
    private final class Request extends AbstractBufferingClientHttpRequest {
        /** 当前请求 URI，来自固定 Ollama API 配置。 */
        private final URI uri;
        /** 当前协议方法。 */
        private final HttpMethod method;

        /** 保存当前请求地址和方法。 */
        Request(URI uri, HttpMethod method) {
            this.uri = uri;
            this.method = method;
        }

        /** 返回 Spring 序列化使用的 HTTP 方法。 */
        @Override
        public HttpMethod getMethod() { return method; }

        /** 返回本次请求地址。 */
        @Override
        public URI getURI() { return uri; }

        /** 线程中断会取消 JDK 的整个 HTTP 交换，包括已经收到部分正文的情况。 */
        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] body) throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                    .method(method.name(), HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach((name, values) -> {
                if (!MANAGED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    for (String value : values) { builder.header(name, value); }
                }
            });
            try {
                // 不将网络 InputStream 交给下游 JSON 解析；否则响应头已完成时可能脱离取消控制。
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                return new Response(response);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Model request cancelled", exception);
            }
        }
    }

    /** 只有完整响应才创建本地结果；此时不再持有待读取的网络资源。 */
    private static final class Response implements ClientHttpResponse {
        /** JDK 返回的真实 HTTP 状态码。 */
        private final HttpStatusCode status;
        /** 防止下游修改的响应头。 */
        private final HttpHeaders headers;
        /** 已完整读取的响应正文流。 */
        private final ByteArrayInputStream body;

        /** 将完整响应转换为 Spring 消息转换器可读的结构。 */
        Response(HttpResponse<byte[]> response) {
            this.status = HttpStatusCode.valueOf(response.statusCode());
            HttpHeaders copied = new HttpHeaders();
            response.headers().map().forEach(copied::put);
            this.headers = HttpHeaders.readOnlyHttpHeaders(copied);
            this.body = new ByteArrayInputStream(response.body());
        }

        /** 返回真实状态，不根据模型正文推测成功。 */
        @Override
        public HttpStatusCode getStatusCode() { return status; }

        /** JDK 不暴露 reason phrase，返回固定状态数值供错误处理使用。 */
        @Override
        public String getStatusText() { return Integer.toString(status.value()); }

        /** 返回只读响应头。 */
        @Override
        public HttpHeaders getHeaders() { return headers; }

        /** 返回已脱离网络的正文流。 */
        @Override
        public InputStream getBody() { return body; }

        /** 结束内存流读取；网络资源已由完整读取或 JDK 取消处理。 */
        @Override
        public void close() { body.skip(body.available()); }
    }
}
