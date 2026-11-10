# Day 5：T7 API 与报告交付记录

执行日期：2026-11-10（Asia/Shanghai）。人工阶段状态以 [plan.md](plan.md) 为唯一记录。初次交付仅获 Day 5 开发授权；用户随后明确表示“Day5我验收通过，提交、推送，远程 CI 本次变更”，已授权本次提交、推送及远程 CI，未授权启动 Day 6。

## 任务与验收结果

| 任务 / 验收项 | 本地完成结果 |
| --- | --- |
| T7.1 / A5 报告校验 | 只接受包含 possibleCauses、suggestions、uncertainties 的严格模型 JSON；拒绝重复字段、尾随文档、错误类型、缺失字段、额外事实字段和伪造引用。原因必须带本次有效引用，并标注为待验证假设；通用建议允许空引用；不确定性必填 |
| T7.1 / A5 证据与完整性 | 服务端从实际解析主链及全部已识别帧构造 `stack-<输入行号>`，从本次实际知识命中构造 `kb-<条目ID>`。输入证据逐行保持原文，知识文本为实际条目的 JSON，模型不能自填证据文本、类型、状态或关联 ID。无法识别、知识未命中、解析警告均返回 partial，并追加具体局限 |
| T7.2 / A1 HTTP 输入 | `POST /api/diagnoses` 仅接收 application/json；空/非法 JSON、非字符串、空白、重复字段、尾随文档或额外字段返回 400。超过 16,000 Unicode 码点、200 行或 128 KiB 原始正文返回 413。CRLF 计一次换行，结尾空行计入行数；不截断输入 |
| T7.2 / A1 HTTP 输出 | 成功 200；错误统一 `{diagnosisId, code, message}`，非 JSON 415，工具/模型失败及非法报告 502，不可达/满载/知识不可用分类 503，超时 504；未知内部分类安全兜底 500。响应头 X-Diagnosis-Id 与 JSON 标识一致，不采信客户端 ID |
| T7.2 请求隔离 | MockMvc 保留真实 ChatClient、ToolCallingManager、解析器和知识库，仅替换 ChatModel。覆盖完整闭环、未知输入两工具执行、连续及双请求交错隔离；实际占满两槽位时第三请求立即 503；报告拒绝后容量正常归还 |
| T7.3 真实 HTTP 与 Review | 内嵌服务器随机本地端口、脚本模型，真实 HTTP/1.1 chunked 请求在 128 KiB 精确边界成功，多一个字节 413；证明过滤器自动注册和未知 Content-Length 限额均生效。全量确定性验证 188/188 通过 |

complete 只表示流程完整，不代表根因已确认。没有可验证原因依据时 possibleCauses 为空；未识别输入的 exceptionType 为 null。报告保留所有已识别主链行，不因最多五个关键帧而丢弃其余已解析行；Suppressed、省略和未识别行只作为局限，不成为可引用的原因事实。

## 变更文件

| 文件 | 内容 |
| --- | --- |
| [DiagnosisController.java](../src/main/java/dev/exceptionagent/api/DiagnosisController.java) | 严格输入 JSON、码点/行数校验及公开报告入口 |
| [DiagnosisRequestFilter.java](../src/main/java/dev/exceptionagent/api/DiagnosisRequestFilter.java) | 服务端关联 ID、128 KiB 有界读取和请求正文快照 |
| [ApiError.java](../src/main/java/dev/exceptionagent/api/ApiError.java)、[DiagnosisErrorHandler.java](../src/main/java/dev/exceptionagent/api/DiagnosisErrorHandler.java) | 集中错误分类、固定安全消息及统一响应 |
| [DiagnosisReport.java](../src/main/java/dev/exceptionagent/diagnosis/DiagnosisReport.java)、[ReportValidator.java](../src/main/java/dev/exceptionagent/diagnosis/ReportValidator.java) | 不可变公开结构、证据目录、引用/JSON 校验和 partial 判定 |
| [DiagnosisService.java](../src/main/java/dev/exceptionagent/diagnosis/DiagnosisService.java)、[RequestState.java](../src/main/java/dev/exceptionagent/diagnosis/RequestState.java) | 模型输出协议、HTTP 关联 ID 贯穿、在原有容量及总 deadline 内校验报告；保留唯一有界循环 |
| [AgentResult.java](../src/main/java/dev/exceptionagent/diagnosis/AgentResult.java) | 更新内部结果注释，不改变其数据行为 |
| [ReportValidatorTest.java](../src/test/java/dev/exceptionagent/diagnosis/ReportValidatorTest.java) | 30 项报告确定性测试 |
| [DiagnosisApiTest.java](../src/test/java/dev/exceptionagent/api/DiagnosisApiTest.java) | 46 项真实业务链 MockMvc 测试 |
| [DiagnosisHttpTransportTest.java](../src/test/java/dev/exceptionagent/api/DiagnosisHttpTransportTest.java) | 1 项真实容器 chunked 边界测试 |
| [plan.md](plan.md)、本文件 | 阶段依据、任务拆分、真实验证结果与验收记录 |

## Red → Green 与最终验证

以下均在固定 JDK 17.0.11、Maven 3.8.4、Spring Boot 4.1.1、Spring AI 2.0.1 下执行。默认排除 ollama 标签，新增测试不连接 Ollama。

| 步骤 | 实际命令 | 实际结果 |
| --- | --- | --- |
| T7.1 首次占位检查 | `mvn -B -ntp -Dtest=ReportValidatorTest test` | 直接拒绝所有报告的早期占位使 21 项非法报告用例通过、9 项合法路径错误；不作为有效断言 Red |
| T7.1 Red | `mvn -B -ntp -Dtest=ReportValidatorTest test` | 改用返回空报告的可编译占位后，30 项全部为预期断言失败，0 错误；报告字段、局限及拒绝逻辑尚未实现 |
| T7.1 Green | 同上 | 实现严格校验及来源组装后 30/30，0 失败/错误/跳过 |
| T7.2 测试草稿修正 | `mvn -B -ntp -Dtest=DiagnosisApiTest test` | MockHttpServletRequest 没有 setContentLength 方法，测试编译失败；改用请求 spy 覆盖长度查询，不计为业务 Red |
| T7.2 Red | 同上 | 端点尚未实现返回 404，43 项全部为预期 HTTP 断言失败，0 错误 |
| T7.2 Green | 同上 | 实现 HTTP 层及关联 ID 贯穿后 43/43，0 失败/错误/跳过 |
| Review 补充回归 | `mvn -B -ntp '-Dtest=DiagnosisApiTest,ReportValidatorTest' test` | 添加实际并发、引用拒绝/容量回收及异常链脱敏检查后 76/76；PowerShell 首次未给逗号参数加引号导致命令解析失败，修正引号后实际执行通过 |
| 真实容器复核 | `mvn -B -ntp -Dtest=DiagnosisHttpTransportTest test` | 1/1；真实 chunked 请求的精确正文边界及错误关联标识通过 |
| 最终本地验证 | `mvn -B -ntp verify` | 2026-11-10 15:26:20（Asia/Shanghai），188 项，0 失败/错误/跳过，BUILD SUCCESS，可执行 JAR 打包成功；其中本阶段新增 77 项 |
| 注释及静态 Review | `jshell scripts/review-java.jsh`、`rg -n '\bvar\b' src`、`git diff --check` | 32 个 Java 文件 Javadoc/record 组件检查，缺失 0；var 零命中；diff 无空白错误。JShell 的本机注册表偏好写入警告不影响解析检查，未更改权限或环境 |

控制台过程输出只临时存于已忽略的 target，不归档为验收证据。docs/evidence 仅保留必要闭环与 CI JSON；本阶段确定性结果可由本报告及测试重现，验收后仅新增真实 CI 的结构化结果。

## Review、CI 与剩余边界

- Review 已检查输入大小/类型、每请求目录隔离、引用真实性、不可变结果、先两工具后报告、固定错误脱敏、并发容量与失败释放、总 deadline 不重置、显式 Java 类型及中文注释。既有 T6 预算、取消、超时及传输故障测试全部回归通过。
- 限额读取最多缓存 128 KiB + 1 字节，超限立即拒绝；Servlet 网络流由容器管理，后续 MVC 仅读取有界内存快照。报告校验仍在诊断槽位内，最终返回前复核原有总 deadline。
- 本阶段未修改已验收产品需求、架构决策、固定版本、工具数量或 CI 配置；没有增加依赖。知识库损坏依旧导致启动失败，503 分类不表示启动失败的服务还能处理请求。
- 初次本地交付时远程 CI 未运行，提交、推送未执行。2026-11-10 用户验收后已完成本次提交、推送，真实 CI completed / success，详见下方提交及 CI 记录。
- **真实 Ollama 业务验收：未运行。** JSON 输出稳定性、三类样例各三次、未知异常及恶意指令的真实模型表现仍属 Day 6。证据引用通过不能证明自然语言推理正确，原因假设仍需人工核对。
- 本地 T7/A1/A5 工作已完成，用户于 2026-11-10 明确验收通过，并单独明确授权本次提交、推送及远程 CI。未启动 Day 6。

## 验收后提交与 CI 记录

- 提交前复验：2026-11-10 20:43:45（Asia/Shanghai），`mvn -B -ntp verify` 实际 188/188，0 失败/错误/跳过，可执行 JAR 打包成功。代码与已验收范围一致，未新增业务变更；本次文档更新无需新增 Red。
- 提交前检查：`git diff --check` 通过；远程 master 与本地基线 `a3d88b0fa47a56150fcdd4837ded902e36de1e99` 一致。
- 已提交并推送至 origin/master：`9ac9e8be1291ce6cfaf82c301752426c0d4f2c6c`，提交标题 `Complete accepted Day 5 diagnosis API and verified reports`，包含本阶段 14 个文件。
- [真实 GitHub Actions 42818708943](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42818708943) 的 head_sha 与上述提交完全一致，event 为 push，实际 completed / success；verify job、Maven 3.8.4 安装、工具版本校验、Verify without Ollama、测试报告上传均 success。[必要结构化证据](evidence/day5/day5-ci-run.json) 保留真实运行链接、提交 SHA、最终状态和步骤摘要，不包含构建过程日志。
- CI 结果归档以单独文档提交补充，不更改已验收业务代码或测试。Day 6 仍未启动。
