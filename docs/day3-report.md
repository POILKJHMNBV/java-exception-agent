# Day 3 交付记录（2026-11-09）

用户已明确验收 Day 2 并授权开始 Day 3。阶段状态仅以 [plan.md](plan.md) 状态表为准。本记录区分实现结果、本地测试与真实远程 CI；不代表用户已验收。

## 范围、结果与文件

依赖：T2/T3 已经人工验收；本次仅 T4/T5，对应 A3/A4 的确定性能力。product.md、architecture.md、POM、固定版本与 CI 工作流未修改。

| 任务/验收 | 完成内容 | 文件 |
| --- | --- | --- |
| T4 / A3 | 不可变主异常链、主因、关键帧、来源行与警告；支持线程/模块/类加载器前缀、空消息、CRLF/LF、Unknown Source、Native Method；隔离 Suppressed、不展开省略帧 | [StackTraceParser.java](../src/main/java/dev/exceptionagent/tools/StackTraceParser.java)、[ParsedStackTrace.java](../src/main/java/dev/exceptionagent/tools/ParsedStackTrace.java) |
| T5 / A4 | 固定 classpath JSON 在启动时加载；字段/唯一 ID 校验；不可变条目、精确匹配、ID 排序、最多 3 项；非法查询拒绝、未知类型空命中 | [KnowledgeBase.java](../src/main/java/dev/exceptionagent/tools/KnowledgeBase.java)、[KnowledgeEntry.java](../src/main/java/dev/exceptionagent/tools/KnowledgeEntry.java)、[exceptions.json](../src/main/resources/knowledge/exceptions.json) |
| T4/T5 测试 | 24 个解析用例、45 个知识库用例；启动测试核对真实 Bean 和打包资源 | [StackTraceParserTest.java](../src/test/java/dev/exceptionagent/tools/StackTraceParserTest.java)、[KnowledgeBaseTest.java](../src/test/java/dev/exceptionagent/tools/KnowledgeBaseTest.java)、[ApplicationSmokeTest.java](../src/test/java/dev/exceptionagent/ApplicationSmokeTest.java) |
| 进度与证据 | 记录 Day 2 人工验收、Day 3 授权/任务拆分/结果；保留原始测试日志 | [plan.md](plan.md)、本文件、`docs/evidence/day3/day3-*.log` |

## 输出契约细节

- 所有 `lineNumber` 为输入的 1 起始行号；`text` 保留该行原文（不含换行符），不做路径读取或命令执行。
- 主因是 `exceptionChain` 最后一个节点；无法识别时为 null。主因前 5 帧优先，再从最近父异常向外补足，保持每个异常内帧的原始顺序；原始节点仍保留全部已识别帧。
- 帧分别保留声明类、方法名、模块/类加载器前缀、文件名与源码行号。Unknown Source / Native Method 的文件和行号为 null；仅有文件名时源码行号也为 null。
- 异常消息只移除标准 `: ` 分隔符中的一个空格，其余空白保留。Suppressed 分支、`... n more`、无法识别行、无帧异常都有带来源行的警告。
- 拼接的第二段顶层堆栈或损坏的 Caused by 不会把后续帧冒充第一段已识别异常的帧。不能判断的结构保守保留警告，供 Day 5 决定 partial。
- 知识查询接收服务端解析结果，查询类型必须等于最深主因；无法识别时只能查空字符串。Day 4 将负责把该解析结果绑定到实际工具执行的请求 ToolContext，不接受模型提供的解析结果。

## Red → Green 与最终验证

在项目根目录运行；环境为 Oracle JDK 17.0.11、Maven 3.8.4、Boot 4.1.1、Spring AI 2.0.1。测试源码/资源 UTF-8，不依赖本地 Ollama。所有 Red 均已完成编译并实际执行测试。

| 步骤 | 命令 | 实际结果与证据 |
| --- | --- | --- |
| T4 Red | `mvn -B -ntp -Dtest=StackTraceParserTest test` | 21 项：17 断言失败、4 因占位解析结果为空而产生的错误；[原始日志](evidence/day3/day3-t4-red.log) |
| T4 Green | 同上 | 21/21，0 失败/错误/跳过；[日志](evidence/day3/day3-t4-green.log) |
| T5 Red | `mvn -B -ntp -Dtest=KnowledgeBaseTest test` | 45 项：41 断言失败；4 个空命中用例通过；[日志](evidence/day3/day3-t5-red.log) |
| T5 Green | 同上 | 45/45，0 失败/错误/跳过；[日志](evidence/day3/day3-t5-green.log) |
| 首轮全量 | `mvn -B -ntp verify` | 68/68；[日志](evidence/day3/day3-verify.log) |
| Review 回归 Red | `mvn -B -ntp -Dtest=StackTraceParserTest test` | 新增 3 项复现消息空白丢失、损坏主因后帧归属错误、14,009 字符合法类型名触发 StackOverflowError；共 24 项，2 失败、1 错误；[日志](evidence/day3/day3-t4-review-red.log) |
| Review 回归 Green | 同上 | 24/24；保留消息空白、隔离损坏主因后的帧、用迭代码点扫描取代递归正则匹配；[日志](evidence/day3/day3-t4-review-green.log) |
| 最终全量 | `mvn -B -ntp verify` | **71/71，0 失败/错误/跳过，打包成功**；2026-11-09 21:19（Asia/Shanghai）；[日志](evidence/day3/day3-final-verify.log) |

最终 71 项 = 24 解析 + 45 知识库 + 1 应用启动 + 1 Day 2 确定性 ChatClient 接线。坏知识库启动用例会有预期的 Spring 上下文取消 WARN；测试明确断言启动失败，该日志不代表构建失败。未运行本次真实模型探针或业务真实模型验收，不计 A2 通过。

## Review

- 范围：单模块、tools 包、两个只读能力的确定性核心；未增加模型工具或外部服务，没有提前实现 Day 4/5。
- 证据真实性：没有提前替模型调用工具；当前没有生产 ToolCallback Bean。测试断言原始行、主因选择、主/抑制分支隔离、关键帧来源及未知输入结果。
- 资源与状态：解析所有可变列表只存在于一次调用中，结果深层不可变；知识库只加载一次并关闭输入流，加载/格式失败明确抛错阻止启动；检索不改共享集合。
- 边界：三类样例、空消息、特殊帧、嵌套 Suppressed、省略、未知行、截断、非法类型查询、JSON 类型错误/缺字段/空字段/重复字段/尾随文档/重复 ID、I/O 失败均覆盖。长名称栈溢出已修复并有回归。
- 编码：Java 局部变量均为显式类型；`rg -n '\bvar\b' src -g '*.java'` 无命中；源码、Markdown 与配置的 diff 空白检查通过，新增文件另做 UTF-8 检查。原始 Maven 日志包含工具输出自带的行尾空格，归档时保留原文；暂存区空白检查仅排除这些 `.log` 文件，证据完整性另以内容哈希检查。
- 预算与超时：本阶段解析采用有界输入下的迭代扫描；请求/工具计数、1 秒工具超时及取消、并发上限属于 T6，尚未实现，不能称通过。

## CI、偏差与下一步

- 本地默认验证通过；按用户当次授权，Day 3 提交 `7bc2efd2bd4b4948f7f99ac9575ed9f701ae9e12` 已推送至 `origin/master`。[真实 CI 42647624769](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42647624769) 为 completed / success，固定 Maven 安装/版本校验、无 Ollama 的 verify、报告上传步骤均通过；[原始状态与步骤归档](evidence/day3/day3-ci-run.json)。
- AGENTS.md 已按用户要求更新：后续每次完成本地工作后默认等待用户验收，不自动提交、推送或运行远程 CI；当次明确授权仅适用于当次。Day 3 已完成本次授权范围，等待用户验收。
- 未修改已验收规格或架构。保守解析会对损坏缩进、拼接堆栈、隐藏类等非支持结构给出警告；默认包下不以 Exception/Error/Throwable 结尾的自定义类型不推断为异常。知识仅覆盖三类异常；模型编排与 HTTP/报告规则尚在后续阶段。
- Day 4 仍为待开始，必须在 Day 3 人工验收并另获启动授权后执行。

## 中文注释与证据归档修正

- 用户于 2026-11-09 要求本次仅补注释、分类证据、更新 AGENTS.md，并明确授权提交/推送/远程 CI。原有 Day 3 业务实现纳入此次提交，不作新的行为变更。
- 将 2 份 Day 2 JSON 证据与 8 份 Day 3 测试日志分别归入 `docs/evidence/day2/`、`docs/evidence/day3/`；同步本文、Day 2 报告及计划中的路径。归档内容不变，10 份原文件 SHA-256 校验一致。
- 为 11 个 Java 文件的类型、字段、构造器、方法及 record 组件补充中文文档注释，并说明关键解析分支、工具回传、资源关闭等代码片段。修改前后逐文件比较非注释 Java 词法序列，全部一致，业务逻辑、字符串及测试断言未改动。
- AGENTS.md 固化上述归档/注释要求，并统一流程为本地完成后等待用户验收；远程操作仅在用户明确授权时执行。
- 本次仅注释和归档变更，不新增业务测试或伪造 Red。`mvn -B -ntp verify` 于 2026-11-09 23:39（Asia/Shanghai）通过：71 项、0 失败/错误/跳过，JAR 打包成功；[本次验证日志](evidence/day3/day3-comments-verify.log)。Review 核对注释覆盖、词法一致、证据内容不变及文档引用，无规格或版本调整。
