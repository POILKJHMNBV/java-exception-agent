# MVP 验收矩阵

初次核对日期：2026-11-10（Asia/Shanghai），T9.1；用户于 2026-11-11 验收并授权本次提交、推送及 CI。此表记录验证结果，不代替人工阶段验收；阶段状态唯一来源为 [plan.md](plan.md)。产品标准取自 [product.md](product.md)，未改写验收门槛。

## A1–A8 对照

| 验收项 | 实现与可执行验证 | 结果与证据边界 |
| --- | --- | --- |
| A1 HTTP 契约 | [DiagnosisApiTest](../src/test/java/dev/exceptionagent/api/DiagnosisApiTest.java) 46 项；[DiagnosisHttpTransportTest](../src/test/java/dev/exceptionagent/api/DiagnosisHttpTransportTest.java) 1 项。覆盖非空字符串、JSON/类型、16,000 码点（含 emoji）、200 行、128 KiB 正文、chunked、多一单位拒绝、完整/partial、关联 ID、错误映射 | Day 7 全量回归 47/47。打包应用加真实模型的五种成功请求和 400/413/415/503 演示 9/9；[HTTP 原始报告](evidence/day7/http-smoke-01.json)。502/504 与并发满载通过可控故障测试验证，未谎称均在真实模型上诱发 |
| A2 真实工具调用闭环 | [OllamaDiagnosisTest](../src/test/java/dev/exceptionagent/diagnosis/OllamaDiagnosisTest.java) 三类异常各三次，未知异常、恶意指令、不可识别文本各一次；真实 ChatClient、ToolCallingManager、两回调与报告校验 | [Day 6 批次 05](evidence/day6/batch-05.json) 12/12，每样本 3 次模型、2 次工具，parse → search，结构化调用、宿主执行状态、回传及报告均保存。Day 7 校验五批归档与四个最终源文件 SHA-256 一致；全量真实 profile 本阶段未重跑。Day 7 HTTP 演示没有逐工具观测，不能独立替代 A2 证据 |
| A3 堆栈解析 | [StackTraceParserTest](../src/test/java/dev/exceptionagent/tools/StackTraceParserTest.java) 24 项。主链最深主因、原消息/行号、最多五个关键帧、父帧补齐、空消息、线程/模块/类加载器、特殊位置、CRLF、Suppressed 隔离、省略与损坏结构、长名称 | Day 7 回归 24/24；实现与回归 Red/Green 见 [Day 3](day3-report.md)。不支持的结构保留警告，不能推测为可信帧 |
| A4 本地知识库 | [KnowledgeBaseTest](../src/test/java/dev/exceptionagent/tools/KnowledgeBaseTest.java) 45 项；[三类静态知识](../src/main/resources/knowledge/exceptions.json)。精确类型、稳定 ID 排序、最多三项、未知空命中、坏资源/重复 ID/非法查询/不可变结果/资源关闭 | Day 7 回归 45/45。坏资源在启动时失败；503 的知识不可用错误码映射另由 API 测试覆盖，不将两者混淆 |
| A5 报告证据 | [ReportValidatorTest](../src/test/java/dev/exceptionagent/diagnosis/ReportValidatorTest.java) 30 项，另有 API 真正业务链验证。拒绝非法 JSON、额外事实字段、重复字段、伪造/跨请求引用；类型、证据和状态由服务端事实生成；不确定性必填 | Day 7 回归 30/30，五份真实 HTTP 报告的输入行/知识全文/引用也逐项核对。结构与引用校验通过；自然语言推理有保留，详见下方局限 |
| A6 预算、超时与隔离 | [DiagnosisServiceTest](../src/test/java/dev/exceptionagent/diagnosis/DiagnosisServiceTest.java) 28 项、[DiagnosisCancellationTest](../src/test/java/dev/exceptionagent/diagnosis/DiagnosisCancellationTest.java) 6 项、[OllamaTransportTest](../src/test/java/dev/exceptionagent/diagnosis/OllamaTransportTest.java) 6 项。含批量/重复计数、白名单/错参/越序、缺工具、无重试、取消、剩余总时限、残留容量、跨请求隔离 | Day 7 回归 40/40；固定 4 工具/5 模型、120 秒总时限/60 秒模型/1 秒工具、3 秒连接、两槽位。传输测试断言真实 socket EOF，HTTP 层另覆盖满载 503。取消不保证 Ollama 底层推理立即结束 |
| A7 无 Ollama 的 CI | [POM](../pom.xml) 默认 `!ollama`，profile 显式包含真实集合；[工作流](../.github/workflows/ci.yml) 固定 JDK 17 / Maven 3.8.4，仅运行默认 verify，不安装/启动 Ollama | 已有真实 [Day 6 CI 42829839907](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42829839907) completed / success，提交 `70a0b4bb328f4ebaeac87fdc2ebf74621b6eb55b`，[归档](evidence/day6/day6-ci-run.json)。与当前工作树 src/POM/workflow 一致。**Day 7 远程 CI 未运行**，须验收及明确授权后验证本次提交 |
| A8 TDD、Review 与演示 | 历史 Red/Green 索引如下；[README](../README.md) 提供构建/启动/curl/故障/真实模型复验；[Day 7 报告](day7-report.md) 记录本次 Review、验证和偏差 | 本地交付资料齐全；188 项确定性测试通过、9 项 HTTP 演示通过。历史失败保留。本次提交/推送/远程 CI 未执行；此项远程部分仍待明确授权，不标为本次 CI 全绿 |

188 项 = HTTP 47 + 报告 30 + 编排/资源/传输 40 + 解析 24 + 知识库 45 + 应用启动 1 + 确定性 ChatClient 接线 1。没有跳过确定性用例；本机存在 Ollama 也不会使默认集合启用真实标签。

## 核心逻辑 Red → Green 索引

以下是已执行的历史事实，不是 Day 7 新运行的 Red。当前任务仅文档、合成样例和交付验证，无业务算法变更，不人为制造失败。

| 逻辑 / 任务 | Red 与 Green 事实 | 命令及详细报告 |
| --- | --- | --- |
| 接线 / T3 | 默认自动工具循环破坏宿主控制断言；关闭自动循环后通过 | `mvn -B -ntp "-Dtest=ChatClientWiringTest" test`；[Day 2](day2-report.md) |
| 解析 / T4 | 初始 21 项：17 断言失败、4 空结果错误 → 21/21；Review 新增 3 项复现空白丢失、帧错挂、长类型名栈溢出（2 失败、1 错误）→ 24/24 | `mvn -B -ntp "-Dtest=StackTraceParserTest" test`；[Day 3](day3-report.md) |
| 匹配/加载 / T5 | 45 项：41 断言失败、4 空命中通过 → 45/45 | `mvn -B -ntp "-Dtest=KnowledgeBaseTest" test`；[Day 3](day3-report.md) |
| 编排/预算 / T6 | 28 项全部业务断言失败 → 28/28；取消与容量 5 项断言失败 → 5/5，后补隔离至 6 项 | `mvn -B -ntp "-Dtest=DiagnosisServiceTest,DiagnosisCancellationTest" test`；[Day 4](day4-report.md) |
| 传输故障/重试 / T6 | 5 项中 2 断言失败、1 半正文取消错误、2 通过；首次修复仍失败，完整可中断传输与禁用重试后 5/5，后补协议闭环至 6 项 | `mvn -B -ntp "-Dtest=OllamaTransportTest" test`；[Day 4](day4-report.md) |
| 报告/引用 / T7.1 | 有效 Red 为 30 项全部断言失败 → 30/30；此前直接拒绝全部报告的占位结果不计有效 Red | `mvn -B -ntp "-Dtest=ReportValidatorTest" test`；[Day 5](day5-report.md) |
| HTTP/映射 / T7.2 | 有效 Red 为 43 项 HTTP 404 断言失败 → 43/43，Review 后 46/46；测试草稿编译错误不计业务 Red | `mvn -B -ntp "-Dtest=DiagnosisApiTest" test`；[Day 5](day5-report.md) |
| 真实模型 / T8 | 批次 01：2/12（7 INVALID_REPORT、3 MISSING_TOOLS）；02：10/12（两次 Markdown 围栏）；03–05：每批 12/12。只调整提示词，没有放宽校验 | `mvn -B -ntp -Pollama-it "-Dtest=OllamaDiagnosisTest" test`，最终完整 profile 201/201；[Day 6](day6-report.md) 与 [全部批次清单](evidence/day6/manifest.json) |

## Review 结论与未完成部分

本次为单 Agent 自查，未委派或声称独立评审。已核对边界、参数白名单、真实回调、每请求状态、取消及容量回收、严格报告引用、只读资源、显式 Java 类型与中文注释。未发现本阶段需要修复的确定性业务缺陷；未修改已验收需求、架构、固定版本或硬预算。

真实语义 Review 有保留：Day 7 NPE 报告把 name 推测成调用参数，IAE 包含未证实的 null 备选，NFE 仍泛化列出范围溢出；这些并非输入已经证明的事实。服务端引用校验不能保证解释充分，须结合源码人工核实。Day 6 已记录的默认值建议风险继续保留。

本机短样例不足以证明所有输入、注入和并发真实推理稳定；总时限从诊断服务入口开始，不包括上传正文；模型响应完整缓冲在内存，MVP 面向本机使用。知识库只有三类静态条目；知识坏文件阻止启动。HTTP 取消无法证明 Ollama 立即停止推理。

上述表格的远程状态为初次交付快照。用户于 2026-11-11 验收并授权本次提交、推送和远程 CI；提交前默认 verify 再次 188/188 通过，远程结果待执行后更新。完整真实 profile 未重跑；真实模型闭环依赖已验收且哈希一致的 Day 6 记录，另有本次真实 HTTP 复验。历史 CI 不能代替本次提交关联的验证。
