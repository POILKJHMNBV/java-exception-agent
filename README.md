# Java Exception Agent

提交 Java 异常堆栈，使用本地 Ollama 获取可追溯证据、原因假设、排查建议及局限说明。单 Maven 模块、单 Agent，仅有 `parseStackTrace` 与 `searchKnowledgeBase` 两个本地只读业务工具。

当前交付核对见 [A1–A8 验收矩阵](docs/acceptance.md) 与 [Day 7 报告](docs/day7-report.md)；阶段状态以 [计划](docs/plan.md) 为准。需求与设计分别见 [产品规格](docs/product.md)、[架构](docs/architecture.md)。

## 环境与构建

固定 JDK 17、Maven 3.8.4、Spring Boot 4.1.1、Spring AI 2.0.1、Ollama / `qwen3.5:9b`。以下命令从仓库根目录执行，以 Windows PowerShell 为例：

```powershell
java -version
javac -version
mvn -version
mvn -B -ntp verify
```

默认构建运行确定性测试并生成 `target/java-exception-agent-0.0.1-SNAPSHOT.jar`，无需 Ollama 或模型文件。首次构建需要能访问 Maven 依赖仓库；不要把网络/依赖解析故障当作业务测试失败，也不要通过换版本绕过问题。工程和测试使用 UTF-8。

真实诊断需要本机已安装模型并运行 Ollama。检查已有环境，不自动下载模型：

```powershell
ollama --version
ollama list
ollama show qwen3.5:9b
curl.exe --noproxy "*" --max-time 5 http://localhost:11434/api/version
```

服务未运行时，在独立终端启动 `ollama serve`；已有服务则无需重复启动。若缺少模型，先完成本地环境准备，应用不会自动拉取，也不会切换模型或调用云端 API。

## 启动与请求

终端 A 前台启动应用，完成后用 Ctrl+C 退出：

```powershell
java "-Dfile.encoding=UTF-8" -jar target/java-exception-agent-0.0.1-SNAPSHOT.jar
```

默认监听 `127.0.0.1:8080`，Ollama 地址是 `http://localhost:11434`。启动日志出现 `Started ExceptionAgentApplication` 仅表示应用已启动，不代表模型可用；没有健康检查或前端页面。端口占用时可附加 `--server.port=18080` 并对应修改请求 URL。保持本机访问，不作为公网服务部署。

终端 B 执行真实 NPE 请求（使用文件避免 PowerShell 转义引号的问题）：

```powershell
curl.exe --noproxy "*" --silent --show-error --include --max-time 130 -H "Content-Type: application/json" --data-binary "@examples/npe.json" http://127.0.0.1:8080/api/diagnoses
```

macOS/Linux 使用同一命令，将 `curl.exe` 换成 `curl`。五份文件均为合成样本，可逐项替换 `@examples/npe.json`：

| 请求文件 | 成功响应的预期 |
| --- | --- |
| [npe.json](examples/npe.json) | HTTP 200，complete，NullPointerException，知识 npe-001 |
| [iae.json](examples/iae.json) | HTTP 200，complete，IllegalArgumentException，知识 iae-001 |
| [nfe.json](examples/nfe.json) | HTTP 200，complete，NumberFormatException，知识 nfe-001 |
| [unknown.json](examples/unknown.json) | HTTP 200，partial，example.WidgetException，知识未命中 |
| [unrecognized.json](examples/unrecognized.json) | HTTP 200，partial，exceptionType=null，evidence 与 possibleCauses 为空 |

输入必须是仅包含非空字符串 `stackTrace` 的 JSON 对象，最多 16,000 个 Unicode 码点、200 行、128 KiB 请求体。超限会拒绝，不截断；换行计数含结尾空行，CRLF 算一个换行。

成功报告始终包含 `diagnosisId`、`status`、`exceptionType`、`evidence[]`、`possibleCauses[]`、`suggestions[]`、`uncertainties[]`。响应头 `X-Diagnosis-Id` 与响应体 ID 对应。`stack-N` 证据引用输入第 N 行，`kb-条目ID` 引用本次知识命中；原因必须引用已验证证据，通用建议可以不带引用。

`complete` 表示流程完整，不能理解成已确定根因。`partial` 表示不可识别、知识未命中或解析有局限；两工具仍须实际执行。Suppressed 不作为主因，省略帧不展开，无法解释的行会进入局限说明。自然语言会变化，格式或工具调用不合规时服务会拒绝，不能保证每次真实推理成功。

## 验证与证据

```powershell
# 默认确定性集合，CI 使用此命令，不运行真实模型
mvn -B -ntp verify

# 已有本机模型与服务后，显式启用全部真实模型验收
mvn -B -ntp -Pollama-it verify

# 仅复验 12 个业务真实样本
mvn -B -ntp -Pollama-it "-Dtest=OllamaDiagnosisTest" test
```

真实业务测试保留生产 ChatClient、ToolCallingManager、两工具、硬预算及报告校验；记录结构化请求 → 宿主执行 → 工具结果回传 → 最终报告。每次新结果写入 `target/day6/<UTC时间-UUID>/`（沿用测试目录命名，不表示当前阶段变化），成败均保留。归档时使用当前阶段的新批次，不能覆盖历史证据。只有测试通过或模型口头宣称调用，不能代替闭环证据。

[Day 6 批次与失败记录](docs/day6-report.md) 包含三类异常各三次、未知异常、恶意指令和不可识别文本。历史 GitHub Actions 结果与当前阶段是否运行 CI 分开记录在验收矩阵。完整控制台、测试与启动日志仅放 `target/`，不提交到证据目录。

## 故障排查

错误响应为 `{diagnosisId, code, message}`，不返回内部异常或原始模型正文。先保留 ID、HTTP 状态、错误码和复现条件，避免记录真实敏感堆栈或模型思考。

| HTTP / code | 含义与处理 |
| --- | --- |
| 400 / INVALID_REQUEST | JSON 损坏、缺字段、额外字段、重复字段、空值或类型错误；检查请求文件 |
| 413 / INPUT_TOO_LARGE | 超过字节、码点或行数上限；提交相关的精简堆栈并明确上下文缺失 |
| 415 / UNSUPPORTED_MEDIA_TYPE | 使用 `Content-Type: application/json` |
| 502 / MISSING_TOOLS、ILLEGAL_TOOL_CALL | 模型未完成工具或发出非法调用；不能据自然语言宣称视为成功 |
| 502 / INVALID_REPORT | JSON 或证据引用无法校验；查看合成样本是否可复现，不能放宽校验掩盖失败 |
| 502 / TOOL_BUDGET_EXHAUSTED、MODEL_BUDGET_EXHAUSTED | 已触及 4 次工具或 5 次模型请求上限；本请求终止，无自动重试 |
| 502 / MODEL_FAILURE、TOOL_FAILURE | 模型协议/服务端错误或工具执行失败；检查本机 Ollama 状态和确定性故障测试。模型文件缺失导致 Ollama 返回错误时也可能为 MODEL_FAILURE |
| 503 / MODEL_UNAVAILABLE | 本地模型连接不可达；核对服务和 11434 端口 |
| 503 / CAPACITY_FULL | 两个诊断槽位已占满；待已有工作结束再试，超时残留任务未退出前仍占用容量 |
| 503 / KNOWLEDGE_UNAVAILABLE | 保留的知识不可用映射；当前知识资源损坏在启动时直接失败，不能期待从未启动的服务取得 HTTP 503 |
| 504 / TIMEOUT | 模型单步最多 60 秒、工具 1 秒、诊断循环总计 120 秒；每步受剩余总时限限制。检查本机负载，不自动扩大预算 |
| 500 / INTERNAL_ERROR | 未预期内部失败；响应脱敏，保留关联 ID 并复现定位 |

输入错误可以在应用启动后直接复现，不需要模型参与：

```powershell
# 400
curl.exe --noproxy "*" --include --max-time 5 -H "Content-Type: application/json" --data-binary "{}" http://127.0.0.1:8080/api/diagnoses
# 415
curl.exe --noproxy "*" --include --max-time 5 -H "Content-Type: text/plain" --data-binary "@examples/npe.json" http://127.0.0.1:8080/api/diagnoses
# 413：只在已忽略的 target 生成临时超限样本
$oversizedBody = @{stackTrace=('x' * 16001)} | ConvertTo-Json -Compress
[IO.File]::WriteAllText((Join-Path (Get-Location) 'target/oversized.json'), $oversizedBody, [Text.UTF8Encoding]::new($false))
curl.exe --noproxy "*" --include --max-time 5 -H "Content-Type: application/json" --data-binary "@target/oversized.json" http://127.0.0.1:8080/api/diagnoses
```

连接不可达演示使用单独的本机实例：先确认本机 11435 没有服务，再在另一个终端启动如下命令，对 18081 发送 NPE 请求，预期 503 / MODEL_UNAVAILABLE；完成后 Ctrl+C 停止该实例。此参数仅用于故障演示，正常配置仍是 localhost:11434。

```powershell
java "-Dfile.encoding=UTF-8" -jar target/java-exception-agent-0.0.1-SNAPSHOT.jar --server.port=18081 --spring.ai.ollama.base-url=http://127.0.0.1:11435
# 在另一终端执行
curl.exe --noproxy "*" --include --max-time 10 -H "Content-Type: application/json" --data-binary "@examples/npe.json" http://127.0.0.1:18081/api/diagnoses
```

超时、容量、非法工具、报告伪造和坏知识库用可控夹具复验，无需破坏真实模型或资源文件：

```powershell
mvn -B -ntp "-Dtest=DiagnosisServiceTest,DiagnosisCancellationTest,OllamaTransportTest,DiagnosisApiTest,KnowledgeBaseTest" test
```

取消会中断应用工作并关闭客户端 HTTP 交换，但不能保证 Ollama 立即停止底层推理。120 秒计时从诊断服务入口开始，不覆盖客户端上传正文的时间；本机 MVP 不提供公网防护或身份体系。

## 已知局限

知识库仅有三类异常的静态条目，精确匹配全限定名。报告可追溯不保证推理正确：既有真实样本中仍出现未经业务确认的默认值建议、未证实的参数备选与泛化范围推测。修复前须结合源码、实际值和业务约定审核；不能自动执行报告或堆栈里的命令。

12 个合成真实样本和本机短堆栈耗时不能证明长输入、所有注入形式或并发模型推理都可靠。`temperature=0` 不保证逐字一致。更改已验收需求、架构、固定版本或预算须另行决定；本次交付不引入数据库、RAG、MCP、多 Agent、前端或付费模型。
