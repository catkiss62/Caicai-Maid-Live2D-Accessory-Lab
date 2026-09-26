# 菜菜女仆参数语义与未来动作接口

本文件是给后续 AI/JEV 控制器和移植工作的模型专属说明。可机读的逐项语义、范围、复合动作与优先级在 [`app/src/main/assets/maid-parameter-semantics-v1.json`](../app/src/main/assets/maid-parameter-semantics-v1.json)。ID 已与用户上传模型的 `maid.cdi3.json` 对照；范围与两端视觉含义来自用户逐项观察，**不是**读取 `.moc3` Core min/max 的结果。实际写参数时仍须用 Core 范围校验、限幅和插值。

## 参数全集（预设表情与动作以外）

| 类别 | 模型参数 ID | 观察范围 | - 端 / 0 端 | + 端 | 用法 |
| --- | --- | --- | --- | --- | --- |
| 呼吸 | `ParamBreath` | 0～1 | 呼吸一端 | 呼吸另一端 | 平滑 0→1→0 是完整一轮，持续自主运行。当前本地时钟用约 0.08～0.92 正弦；也驱动 Sen 尾巴原生物理。 |
| 嘴 | `ParamMouthForm` | -1～1 | 不高兴 | 顽皮笑 | 临时表情目标。 |
| 嘴 | `ParamMouthOpenY` | 0～1 | 闭嘴 | 张嘴 | TTS 播放时交给口型，不由 JEV 持续覆盖。 |
| 嘴 | `MOUTHX` | -1～1 | 左歪嘴 | 右歪嘴 | 真实 ID 全大写，不能按 Sen 的 `ParamMouthX` 推断。 |
| 嘴 | `OUT` | 0～1 | 无舌头 | 短舌伸出 | 需要吐舌就迅速到 1，短暂保持再收回，不必利用中间值。 |
| 嘴 | `SHRUG` | 0～1 | 正常 | 闭嘴撅嘴 | 真实 ID 不是 Sen 的 `ParamMouthShrug`。 |
| 脸型测试 | `PUCKER` | -1～1 | 负端待观察 | 1 看起来脸更圆 | v0.1.39 单独提供 `PUCKER=1` 开关，用户确认前不设为默认、不进待机。 |
| 头 | `ParamAngleZ` | -30～30 | 左歪头 | 右歪头 | 现有大幅动作还同步写 `ParamAngleZ2`。 |
| 头 | `ParamAngleX` | -30～30 | 向左侧头 | 向右侧头 | 名为“角度 X-捕”；现有可见大幅侧头用 `ParamAngleX3`，此 ID 单独写入画面响应较弱。 |
| 头 | `ParamAngleY` | -30～30 | 低头 | 抬头 | 名为“角度 Y-捕”；现有可见大幅俯仰用 `ParamAngleY2`。 |
| 身体 | `ParamBodyAngleX` | -10～10 | 身体/双胯运动一端 | 身体/双胯运动另一端 | 原动作会自动回中，动作控制不应把终点锁住。 |
| 身体 | `ParamBodyAngleY` | -10～10 | 下压身体 | 抬高/踮脚 | 可见上下抖动来源；本项目历史测试 `ParamBodyPositiony` 不明显。 |
| 身体 | `ParamBodyAngleZ` | -10～10 | 轻微向左转 | 轻微向右转 | 身体左右摆。 |
| 视线 | `ParamEyeBallX` | -1～1 | 看左 | 看右 | 平时由本地待机/注视驱动，明确关注目标可短暂接管。 |
| 视线 | `ParamEyeBallY` | -1～1 | 看下 | 看上 | 同上。 |
| 眼睑 | `ParamEyeLOpen` | 0～1 | 左眼闭 | 左眼睁 | 自主眨眼负责双眼；单侧闭合可做 wink。 |
| 眼睑 | `ParamEyeROpen` | 0～1 | 右眼闭 | 右眼睁 | 同上。 |
| 笑眼 | `ParamEyeLSmile` | 0～1 | 左眼角正常 | 左眼角微笑 | 设为 1 时配左眼闭合得到笑眼 wink。 |
| 笑眼 | `ParamEyeRSmile` | 0～1 | 右眼角正常 | 右眼角微笑 | 设为 1 时配右眼闭合得到笑眼 wink。 |
| 眉眼 | `ParamBrowLY` | -1～1 | 左侧皱眉感 | 左眼大睁感 | 眉毛会带动眼区，不能只当作眉毛坐标。 |
| 眉眼 | `ParamBrowRY` | -1～1 | 右侧皱眉感 | 右眼大睁感 | 同上。 |

一个可试的俏皮动作：左眼 `ParamEyeLOpen=0`、`ParamEyeLSmile=1`，可选预设 `2比耶` 和 `OUT=1`；完成后眼睑和舌头恢复原控制层。右眼 wink 使用对应右眼 ID。预设动作、眨眼、TTS 口型与该组合需要按通道仲裁，不要每帧把所有参数同时设置为常量。

## 当前实现与后续接入建议

- 本测试应用已有 E.V 忠实待机引擎，并有 Sen 原生/自然模式；本地循环覆盖头身微动、视线和自主眨眼。`SenLive2DModel.update()` 每帧把 `SenPerformanceEngine.getBreathValue()` 写给主模型，配件层也把它写给 Sen 供体尾巴物理。v0.1.39 不另起一套待机随机调度，也不让 AI 持续调用 API 来实现呼吸/眨眼。
- 当前大幅头部测试调用 `ParamAngleX3`、`ParamAngleY2`，与 CDI 的“捕”参数 `ParamAngleX`、`ParamAngleY` 并非同一可见响应。以后要让 JEV 控制可见头转，应在主模型实机验证驱动 ID；不要照标签机械替换。三配件的根点由女仆模型最终网格单向跟随，因此应先完成主模型更新，再计算配件投影。
- 建议先把此 JSON 用作模型专属参数白名单/提示资料，JEV 只给短时反应或说话关键帧目标（目标 ID、值、时长、可中断优先级）；本地待机在未覆盖的通道继续运行。显式预设优先于 JEV；TTS 占用张嘴参数；自发眨眼占用未请求的眼睑。计划结束后回到当时的下层状态，而不是强制把所有参数归零。
- SoulLink Emotion SDK 的[官方接入教程](https://github.com/nanlingyin/soullink-emotion-sdk/blob/main/docs/integration-tutorial.md)将 Idle 呼吸/眨眼/注视保留在本地 engine，把 JEV 作为说话期间部分真实参数 ID 的短时关键帧覆盖层。这是架构参考，当前 Android/Cubism Java 项目尚未接入其 TypeScript runtime，也没有配置 JEV。优先在 AI 伴侣项目已有回复、TTS、情绪链路中安排 JEV 请求时机，先用测试项目验证参数通道与优先级；不需要为每帧待机请求模型。

## 下轮决策点

真机先看 `PUCKER=1` 与原脸型对照；确认后才决定是否升为默认。之后再选一两个复合表情（例如 wink）验证主模型参数、预设动作和配件同时运行。JEV 调用策略应在 AI 伴侣的单 DeepSeek / DeepSeek+Gemini 现有请求编排中设计，避免在测试仓增设一条独立的每帧 API 路径。
