# Java Exception Agent：最小架构

状态：Day 1 设计已由用户于 2026-11-09 验收；实现以 [产品规格](product.md) 为准。架构决策变更须经用户明确批准。

## 结构与版本

一个 Maven 模块、一个 Spring Boot 进程，按 api / diagnosis / tools 三个小包组织；不引入通用 Agent 平台或持久化层。

```text
POST /api/diagnoses → 输入校验 → DiagnosisService（请求级有界循环）
                                      ↕ ChatClient → Ollama → qwen3.5:9b
                                      ↕ ToolCallingManager
                                parseStackTrace / searchKnowledgeBase
                                      ↓
                               报告校验 → HTTP 响应
```

- Boot parent 固定 4.1.1，Spring AI BOM 固定 2.0.1；使用 Ollama starter、Web MVC、校验及测试依赖。Java release=17，源码/资源 UTF-8；不另建依赖管理框架。
- Ollama 只连本地 `http://localhost:11434`，模型固定 `qwen3.5:9b`，禁止自动拉取模型。初始 temperature=0、think=false；业务报告输出预算初设 2,048 token，Day 6 实测。
- Boot 4.1.1 官方要求 Java ≥17、Maven ≥3.6.3；Spring AI v2.0.1 源码声明 Java 17、Boot 4.1.1。未发现版本冲突，实际解析与编译待 Day 2。
- Spring AI 自身源码构建要求的 JDK 补丁版本不等同于应用使用其发布包的要求；不因此自动修改本地 JDK。

## 两个只读工具

| 工具 | 输入 / 输出 | 确定性规则 |
| --- | --- | --- |
| `parseStackTrace` | 无模型参数；从请求 ToolContext 读取原始 stackTrace。返回主异常链、主因、关键帧、行号和 warnings | 解析顶层异常及 Caused by；选主链最深异常为主因；关键帧取该异常前 5 帧，再按原顺序补父异常帧至最多 5 帧；保留来源，不推测应用包名 |
| `searchKnowledgeBase` | `exceptionType: string`；返回最多 3 个知识条目及条目 ID | 仅允许查询本次已解析主因类型；无法解析时用空字符串。全限定名精确匹配，按 ID 排序；无匹配返回空列表 |

- parseStackTrace 的入口确由模型调用，服务端不提前运行解析器冒充工具调用；ToolContext 防止模型改写输入再制造证据。
- 解析支持线程前缀、CRLF/LF、空消息、模块/类加载器前缀；Unknown Source、Native Method 的文件及行号为 null。
- Suppressed 分支保留提示，不参与主因选择；`... n more` 不展开；无法识别的行保留行号并警告，报告 partial。
- 本地资源 `knowledge/exceptions.json`，条目形状为 `{id, exceptionType, causes[], suggestions[]}`。启动时校验必填字段和唯一 ID，加载为不可变集合；坏文件导致启动失败并明确日志。工具不接受路径或 URL，不读取任意文件、不执行命令。
- 工具返回业务结果或统一错误码；异常不得作为堆栈文本直接交给模型。

## Agent 循环与硬限制

1. ChatClient 只注册上述两工具。按 Spring AI 2.0.1 的用户控制模式关闭自动工具循环（`AdvisorParams.toolCallingAdvisorAutoRegister(false)`），由 DiagnosisService 检查 ChatResponse，再经 ToolCallingManager 执行。
2. 模型决定何时调用；要求先 parse 后 search，随后输出报告。执行前检查名称、参数、阶段、剩余次数和 deadline；同一响应内多个调用按顺序逐个检查执行。
3. 工具调用按每个 function 计数，不按轮数计数；最大 4 次，模型请求最大 5 次，计数均为请求私有。第 5 个工具绝不执行；未知工具、非法参数、越序调用立即 502。无自动重试。
4. 仅当两工具均成功执行，才接受最终报告；未调用工具、只输出“正在调用”的文本、非法 JSON 或引用不存在的证据，均 502，不伪装成功。
5. `exceptionType`、证据来源和引用由服务端核验并组装；模型只生成解释、建议和不确定性。引用必须来自本次解析/知识命中；无法验证的文字不成为事实证据。不能靠引用校验保证推理正确，仍需人工评估。

| 限制 | 默认值与行为 |
| --- | --- |
| 请求总时限 | 120 秒，使用单调时钟；开始每步前检查，不能因新一轮而重置 |
| 模型调用 | 每次最多 60 秒，连接超时 3 秒，实际等待取剩余总时限与单步上限的较小值 |
| 本地工具 | 每次最多 1 秒；超时 504，其他执行异常 502，终止循环 |
| 并发 | 初版最多 2 个诊断请求，无无限等待队列；满载 503 |

超时同时终止后续调度、取消任务并关闭客户端请求；使用有界执行器，不使用仅 Future.get 超时却无限遗留后台任务的方案。HTTP 取消不保证 Ollama 立即停止底层推理；通过传输超时、并发限制和故障测试控制资源风险。

## 测试、证据与数据边界

- 确定性测试：解析、查找、预算、报告校验、HTTP 契约均先 Red 后实现。编排测试用脚本化 ChatModel 返回真实结构的 tool_calls，保持 ChatClient / ToolCallingManager / 实际工具参与执行；不能只 mock 整个诊断服务。
- 超时测试注入时钟或可控阻塞桩，验证实际退出、取消和无后续调用，不等待真实 120 秒。
- 本地真实测试单独 `@Tag("ollama")` 与 `ollama-it` profile，默认排除。CI 替换模型并禁用 Ollama 自动连接和拉取；不得因 Ollama 缺失跳过确定性测试。
- 仅记录 diagnosisId、工具名、次数、耗时、状态；默认不记录原始堆栈、完整提示词或模型思考内容。堆栈与知识文本均为数据，不能改变工具白名单或执行约束。
- 默认只监听本机，MVP 不提供公网服务或身份体系。禁止跨请求共享可变调用历史、预算及 ToolContext。

## 版本依据

- [Boot 4.1.1 系统要求](https://github.com/spring-projects/spring-boot/blob/v4.1.1/documentation/spring-boot-docs/src/docs/antora/modules/ROOT/pages/system-requirements.adoc)
- [Spring AI 2.0.1 POM](https://github.com/spring-projects/spring-ai/blob/v2.0.1/pom.xml)
- [Spring AI 2.0.1 工具执行](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)
- [Ollama 工具调用协议](https://docs.ollama.com/capabilities/tool-calling)

核对日期：2026-11-09。实现前用固定版本编译验证 API，不能直接照搬滚动版文档。
