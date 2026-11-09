# Java Exception Agent：产品规格

状态：Day 1 已由用户于 2026-11-09 验收；本文件定义目标行为，不表示功能已实现。产品需求变更须经用户明确批准。

## 目标与边界

一周内交付面向 Java 开发者的本地异常诊断 MVP：提交堆栈，获得有证据、可执行且承认不确定性的报告。
固定 JDK 17、Spring Boot 4.1.1、Spring AI 2.0.1、Maven 3.8.4、Ollama / qwen3.5:9b、GitHub Actions。
仅单 Agent、两个本地只读工具；不使用数据库、RAG、MCP、多 Agent、前端或付费 LLM API。

## HTTP 契约

- `POST /api/diagnoses`，`Content-Type: application/json`，请求为 `{"stackTrace":"java.lang.NullPointerException: ...\n\tat ..."}`。
- `stackTrace` 必须为非空字符串；上限 16,000 个 Unicode 码点、200 行；整个 HTTP 请求体上限 128 KiB。超限拒绝，不静默截断输入。
- 成功返回 HTTP 200；报告字段如下，数组可以为空，但字段不可缺失。

| 字段 | 约定 |
| --- | --- |
| `diagnosisId` | 服务端生成的请求关联 ID |
| `status` | `complete` 或 `partial`；complete 表示流程完整，不代表根因确定 |
| `exceptionType` | 解析得到的最深主因类型；不可识别时为 null |
| `evidence[]` | `{id, source, reference, text}`；source 为 stackTrace / knowledgeBase；reference 为输入行号或知识条目 ID |
| `possibleCauses[]` | `{description, evidenceIds[]}`；每项至少引用一条已验证证据，表述为假设 |
| `suggestions[]` | `{action, evidenceIds[]}`；可为通用排查步骤，此时 evidenceIds 可空 |
| `uncertainties[]` | 非空字符串数组；说明缺少源码、运行数据、知识未命中或解析局限等 |

错误响应统一为 `{diagnosisId, code, message}`，不泄露内部堆栈：

| HTTP | 条件 |
| --- | --- |
| 400 / 413 / 415 | 空或错误 JSON、类型错误 / 大小或行数超限 / 非 JSON 内容类型 |
| 502 | 工具失败、工具或模型预算耗尽、非法工具调用、模型未调用必需工具、报告无法校验 |
| 503 | Ollama 不可达、服务容量已满或知识库不可用 |
| 504 | 单步或请求整体超时 |

合法字符串但无法识别堆栈：仍需实际调用两个工具，以空类型查询返回空命中，返回 partial，不编造异常类型。
可解析但知识未命中、输入中存在无法解释的结构或被省略的证据：返回 partial；没有原因依据时 possibleCauses 为空。

## MVP 验收标准

| ID | 可观察的验收结果 |
| --- | --- |
| A1 | 合法请求得到上述报告；无效、超限及内容类型错误有对应 HTTP 状态；边界测试通过 |
| A2 | 对 NPE、IllegalArgumentException、NumberFormatException 三类样例，真实模型经 ChatClient 发出结构化调用，两个实际工具均执行且结果进入后续模型上下文；保存调用次数、顺序和结果摘要 |
| A3 | 解析类型、消息、关键帧与输入一致；覆盖 Caused by、空消息、Unknown Source、Native Method、模块前缀及不完整堆栈；Suppressed 不被误判为主因 |
| A4 | 知识库至少覆盖上述三类异常；命中顺序稳定；未知类型返回空列表，无伪造条目 |
| A5 | 报告证据可追溯至输入或本次知识命中；伪造引用被拒绝；始终给出不确定性说明 |
| A6 | 每请求最多执行 4 次工具、发起 5 次模型请求；重复、批量、非法调用不能绕过预算；工具异常、Ollama 不可达及超时可控退出 |
| A7 | GitHub Actions 在没有 Ollama 和模型文件时通过全部确定性测试；真实模型测试只在显式启用时运行 |
| A8 | 有每项核心逻辑的 Red→Green 记录、Review 结果、CI 结果和可复现的本地演示说明 |

Day 1 的原生协议探针仅是可行性证据，不满足 A2；真实模型验收不要求逐字相同的自然语言答案。
