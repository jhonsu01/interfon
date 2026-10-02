# 📞 Interfon — 与你的 AI 智能体通话的内部电话

[Español](../../README.md) · [English](README.en.md) · **中文** · [Português](README.pt.md) · [한국어](README.ko.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · [Français](README.fr.md)

**Interfon** 把你的电脑和 Android 手机变成一部语音内部电话：智能体可以**给你打电话**、**和你对话**、**给你发送语音**；
你也可以回拨它，或使用**对讲机**模式。一切都在**本地**（你的 WiFi 网络）运行，使用你自己的推理 API（Unsloth Studio），不依赖任何第三方服务。

```
   ┌───────────────────────── 电脑 (Windows) ───────────────────────┐
   │                                                                │
   │  智能体 (CLI) ──┐                                              │
   │                 ▼                                              │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Interfon 服务器         │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · Piper 语音 (Daniela)  │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + 音频)，本地 WiFi — 一台或多台电脑
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  Interfon 应用 (Compose，深色模式)                              │
   │  · 接听来电（在锁屏上响铃）                                     │
   │  · 与智能体语音对话（实时字幕）                                 │
   │  · 对讲机：按住说话                                             │
   │  · 即使应用关闭也能收到智能体的语音                             │
   └─────────────────────────────────────────────────────────────────┘
```

## 功能

| 功能 | 工作方式 |
| --- | --- |
| 📲 来电 | 智能体运行 `call_user.py` → 手机**响铃**（全屏、振动、铃声）→ 接听后即可对话。 |
| 📞 呼出 | 在应用中点击“呼叫智能体” → 立即开始语音对话。 |
| 🎙️ 对讲机 | 按住按钮说话，松开发送；智能体用语音回复。 |
| 📻 推送语音 | `send_audio.py "文本"` → 应用即使在后台也会播放（断线时排队等待）。 |
| 🔤 语音转文字 | 通过本地 API 使用 `qwen3-asr-0.6b`（每句约 1.6 秒）。 |
| 🧠 大脑 | `gemma-4-E2B-it`（可配置），使用 `enable_thinking:false` 以获得简短的口语回答。 |
| 🗣️ 智能体语音 | **Piper `es_AR-daniela-high`**，备用 SAPI（Sabina es-MX）。其他语音：`python scripts/download_voice.py --list`。 |
| 🌓 深色模式 | 强制开启，Material 3，蓝黑底绿色主题。 |
| ✍️ 字幕 | 双方说的话都会显示在屏幕上，并记录到 `server/logs/`。 |
| 📡 自动发现 | 应用会扫描局域网，自动添加找到的**所有**服务器（无需配置 IP）。 |
| 🖧 多台服务器 | 同时连接多台电脑。任何一台都可以给你打电话或发语音；你的呼叫和对讲走列表中第一台已连接的服务器，它掉线时由下一台接替（故障转移）。每台服务器都可以**自定义名称**。 |
| 📋 可折叠列表 | 在首页，服务器卡片可以折叠，折叠时显示 `已连接/总数` 计数；展开时显示每台的状态。 |
| 🌐 8 种语言 | Español、English、中文、Português、한국어、Русский、日本語、Français。首次启动时选择，之后可在**设置 → 语言**中更改（Android 13+ 也可在*系统设置 → 应用语言*中更改）。 |

## 快速开始

### 1. 电脑：服务器

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + 依赖 + Piper 语音（约 114 MB，来自 HuggingFace）
# 可选（以管理员身份）允许手机通过 WiFi 连接：
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

要求：**Unsloth Studio**（或任何兼容 OpenAI 的 API）运行在 `127.0.0.1:8888`，且无需密码。
在 `server/.env` 中配置地址和模型。

你可以在多台电脑上重复以上步骤：应用会同时连接所有服务器。

### 2. Android：应用

从最新的 [release](https://github.com/jhonsu01/interfon/releases) 安装 APK
（`Interfon-vX.Y.Z.apk`，已签名）。打开后：

1. 选择语言（仅首次）。
2. 授予麦克风和通知权限。
3. **就这样**：应用会自动发现服务器（扫描局域网，验证 `/api/status` 并连接所有服务器）。
   如果没有任何服务器连接，它会自动重新搜索。也可以点击 **🔎 在网络中搜索服务器** 手动触发。
4. **服务器**卡片显示 `已连接/总数`；点击即可折叠或展开。

#### 多台服务器

在**设置 → 服务器**中你可以：

- 手动**添加**服务器（`192.168.1.50`、`192.168.1.50:8765` 或完整 URL）。
- 用 ✏️ **重命名**（例如“办公室电脑”、“笔记本”）或修改 URL。
- 用 ⬆️ **提高优先级**：列表中第一台已连接的服务器负责你的呼出和对讲；如果它掉线，应用会自动切换到下一台。
- 在同一个编辑对话框中**删除**服务器。

所有服务器都可以同时给你打电话和发送语音。如果你正在与一台通话，而另一台尝试呼叫你，应用会以“忙线”拒绝。

> 通过 USB 时，也可以使用 `adb reverse tcp:8765 tcp:8765` 并添加服务器 `http://127.0.0.1:8765`。

### 3. 通话

```bash
# 在电脑上（server/ 目录下）：
./.venv/Scripts/python.exe call_user.py "有空吗？"                  # 呼叫手机
./.venv/Scripts/python.exe send_audio.py "编译成功完成"              # 推送语音
./.venv/Scripts/python.exe send_audio.py "我累了" --reply           # 由 LLM 撰写回复
./.venv/Scripts/python.exe status.py                               # 总体状态
```

在应用中：**呼叫智能体** 进行对话，**对讲机** 用于简短交流。

## 🤖 AI 智能体指南（会话集成）

任何能访问这台电脑的智能体都可以用 Interfon **与用户交谈**：听用户说了什么、用语音回复、给用户打电话或发送播客。
全部通过 HTTP 访问 `http://127.0.0.1:8765`（局域网，无认证）。示例中 `$S` = `server/.venv/Scripts/python.exe`。

### 系统状态（务必先检查）

```bash
$S server/status.py        # 或：curl http://127.0.0.1:8765/api/status
```

关键字段：`phone_connected`（应用是否在线？）、`llm.loaded`、`session.active`。

### 智能体 → 人：发送语音

| 我想要 | 调用 |
| --- | --- |
| 发送语音（来电效果） | `POST /api/message` `{"text": "..."}` |
| 呼叫手机（在锁屏上响铃） | `POST /api/call` `{"text": "原因"}` — 接听时朗读 |
| 由本地 LLM 撰写回复的语音 | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "编译完成，全部通过"     # 推送语音
$S server/call_user.py "有空吗？"               # 呼叫
```

### 人 → 智能体：听用户说了什么

所有通话内容（通话、对讲）都会转写到 `server/logs/transcripts-YYYY-MM-DD.jsonl`：

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "成绩是多少..."}
```

`kind`：`stt`（人的语音）· `agent`（语音回复）· `message_pushed` · `telegram` · 通话事件。
要继续对话：对当天文件执行 `tail` 并筛选 `kind=stt`，然后用 `send_audio.py` 回复——这就是“在智能体会话中通过电话对话”的完整循环。

### 提问但不发送语音

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "麦德林的天气"}'
# → {"reply": "麦德林现在 20 度……", "grounded": true}
```

与手机相同的能力：日期/时间/设备状态、天气、新闻、互联网/维基百科搜索、哥伦比亚节假日（其余由 LLM 回答）。

### 论文播客（papercast）

1. 选择一篇近期且排名靠前的论文（arXiv/热门），把它总结成 4–8 个**口语化部分**
   （每部分约 700 字符，简单类比，数字保持准确——“讲给小孩听”的风格）。
2. 把各部分保存到 `.txt` 中，用 `---` 行分隔。
3. 播放：

```bash
$S server/papercast.py script.txt   # 每部分作为一次来电，间隔发送
```

### 纸杯电话（两台服务器对话）

两台 Interfon 服务器互相对话，手机会用说话一方的声音播放每一轮。每个智能体都用**自己的 LLM** 思考，只知道对方告诉它的内容（会收到最近的对话历史）。

```bash
python scripts/vaso.py "你好，我们一起编个故事" \
    --a http://192.168.1.50:8765 --nombre-a "五十号" \
    --b http://192.168.1.8:8765  --nombre-b "开发电脑" --rondas 3
```

会触发技能的词（天气、新闻、维基百科、日期/时间）在历史中会被替换成同义词，确保始终由 LLM 回答。

### Telegram（可选）

在 `server/.env` 中设置 `TELEGRAM_BOT_TOKEN`（热加载，无需重启）。用户用 `/start` 授权自己的聊天；
机器人具备相同能力。要从电脑给用户发消息：`$S server/telegram_send.py "文本"`。

### 运行规则

- **半双工**：通话进行中（`session.active`）不要发送语音。
- 延迟：STT 约 1.6 秒 · 本地 LLM 2–10 秒（首次加载约 90 秒）· TTS 约 1–4 秒。
- 长语音：拆分成少于 900 字符的部分（收听节奏更好）。
- 服务器是核心：如果重启电脑，需要重新运行 `serve.py`。

## 实测延迟（Ryzen 5 3400G，核显）

| 步骤 | 时间 |
| --- | --- |
| STT（约 5 秒的句子） | 约 1.6 秒 |
| LLM gemma-4-E2B（短回复，已预热） | 约 2-10 秒 |
| TTS Piper daniela-high | 约 1-4 秒 |
| **每次交流总计** | **约 5-15 秒**（小型本地模型：这是预期的延迟） |

## 安全

- 一切都在**局域网本地**：智能体数据不会发送到互联网（只有从 HuggingFace 下载语音）。
- 服务器**没有认证**：只在可信网络中使用。
- 签名密钥库和 `.env` 存放在 `.secrets/` 中，**绝不**提交到仓库。

## 结构

```
Interfon/
├── android/               # Kotlin + Compose 应用（深色模式、自适应图标、8 种语言）
├── server/                # FastAPI + WebSocket + 提供方（Unsloth API、Piper、SAPI）
│   ├── interfon_server/   #   main（协议）、providers、vad、wavutil、state、config
│   ├── serve.py           #   启动
│   ├── call_user.py       #   CLI：呼叫手机
│   ├── send_audio.py      #   CLI：发送语音
│   └── status.py          #   CLI：状态
├── scripts/
│   ├── setup_server.ps1   #   电脑准备
│   ├── download_voice.py  #   从 HuggingFace 下载 Piper 语音
│   ├── publish_release.py #   升版本 + 构建 + 发布 GitHub release
│   └── vaso.py            #   两台服务器之间的纸杯电话
├── docs/arquitectura.md   # 详细的 WebSocket 协议
├── docs/i18n/             # 本 README 的其他 7 种语言版本
└── directives/            # 项目操作规程
```

## 发布新版本

```bash
python scripts/publish_release.py patch   # 或 minor / major
```

提升 `versionCode`，构建签名 APK，创建标签并在 GitHub 发布 release。

## 许可证

MIT — 见 [LICENSE](../../LICENSE)。
