# 📞 Interfon — AI エージェントと話すためのインターホン

[Español](../../README.md) · [English](README.en.md) · [中文](README.zh.md) · [Português](README.pt.md) · [한국어](README.ko.md) · [Русский](README.ru.md) · **日本語** · [Français](README.fr.md)

**Interfon** は PC と Android スマートフォンを音声インターホンに変えます。エージェントは
**あなたに電話をかけ**、**会話し**、**音声メッセージを送る**ことができ、あなたはエージェントに
発信したり**トランシーバー**モードを使ったりできます。すべて**ローカル**（あなたの WiFi ネットワーク）で、
あなた自身の推論 API（Unsloth Studio）を使って動作し、外部サービスは使いません。

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  エージェント (CLI) ──┐                                        │
   │                       ▼                                        │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Interfon サーバー       │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · Piper 音声 (Daniela)  │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + 音声)、ローカル WiFi — PC 1 台または複数台
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  Interfon アプリ (Compose、ダークモード)                        │
   │  · 着信を受ける (ロック画面の上で鳴る)                          │
   │  · エージェントと音声で会話 (リアルタイム字幕)                  │
   │  · トランシーバー: 長押しで話す                                 │
   │  · アプリを閉じていてもエージェントの音声を受信                 │
   └─────────────────────────────────────────────────────────────────┘
```

## 機能

| 機能 | 仕組み |
| --- | --- |
| 📲 着信 | エージェントが `call_user.py` を実行 → スマホが**鳴る**（全画面、バイブ、着信音）→ 応答して会話。 |
| 📞 発信 | アプリの「エージェントに発信」ボタン → すぐに音声会話。 |
| 🎙️ トランシーバー | ボタンを押したまま話し、離すと送信。エージェントが音声で返答します。 |
| 📻 プッシュ音声 | `send_audio.py "テキスト"` → バックグラウンドでも再生（切断中はキューに保存）。 |
| 🔤 音声認識 | ローカル API 経由の `qwen3-asr-0.6b`（1 文あたり約 1.6 秒）。 |
| 🧠 頭脳 | `gemma-4-E2B-it`（変更可能）。短い音声回答のため `enable_thinking:false` を使用。 |
| 🗣️ エージェントの声 | **Piper `es_AR-daniela-high`**、予備に SAPI（Sabina es-MX）。他の声: `python scripts/download_voice.py --list`。 |
| 🌓 ダークモード | 常時オン、Material 3、青みがかった黒地に緑のテーマ。 |
| ✍️ 字幕 | 双方の発言がすべて画面に表示され、`server/logs/` に記録されます。 |
| 📡 自動検出 | アプリがローカルネットワークをスキャンし、見つけた**すべて**のサーバーを自動で追加します（IP 設定不要）。 |
| 🖧 複数サーバー | 複数の PC に同時接続。どのサーバーからも着信や音声を受け取れます。あなたの発信とトランシーバーはリストで最初に接続しているサーバーを使い、切断されると次のサーバーが引き継ぎます（フェイルオーバー）。サーバーごとに**名前を付けられます**。 |
| 📋 折りたたみリスト | ホーム画面のサーバーカードは折りたたむと `接続数/合計` のカウンターを、展開すると各サーバーの状態を表示します。 |
| 🌐 8 言語 | Español、English、中文、Português、한국어、Русский、日本語、Français。初回起動時に選び、**設定 → 言語**で変更できます（Android 13 以降は*システム設定 → アプリの言語*でも可）。 |

## はじめに

### 1. PC: サーバー

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + 依存関係 + Piper 音声（HuggingFace から約 114 MB）
# 任意（管理者として）: WiFi 経由のスマホ接続を許可
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

必要なもの: パスワードなしで `127.0.0.1:8888` で動作する **Unsloth Studio**（または OpenAI 互換 API）。
アドレスとモデルは `server/.env` で設定します。

複数の PC で同じ手順を繰り返せます。アプリはすべてに同時接続します。

### 2. Android: アプリ

最新の [release](https://github.com/jhonsu01/interfon/releases) から APK
（`Interfon-vX.Y.Z.apk`、署名済み）をインストールします。起動したら:

1. 言語を選びます（初回のみ）。
2. マイクと通知を許可します。
3. **以上**: アプリがサーバーを自動で見つけます（ローカルネットワークをスキャンし、`/api/status` を確認して
   すべてに接続）。どこにも接続していない場合は自動で再検索します。**🔎 ネットワークでサーバーを検索**
   で手動実行もできます。
4. **サーバー**カードに `接続数/合計` が表示され、タップで折りたたみ・展開できます。

#### 複数サーバー

**設定 → サーバー**では次のことができます。

- サーバーを手動で**追加**（`192.168.1.50`、`192.168.1.50:8765` または完全な URL）。
- ✏️ で**名前を変更**（例: 「オフィスの PC」「ノート PC」）または URL を変更。
- ⬆️ で**優先度を上げる**: リストで最初に接続しているサーバーが発信とトランシーバーを担当し、
  切断されるとアプリが自動で次へ切り替えます。
- 同じ編集ダイアログから**削除**。

すべてのサーバーが同時に電話や音声を送れます。あるサーバーと通話中に別のサーバーから着信があると、
アプリは「話し中」として拒否します。

> USB 接続では `adb reverse tcp:8765 tcp:8765` のうえでサーバー `http://127.0.0.1:8765` を追加しても動きます。

### 3. 話す

```bash
# PC 上で（server/ から）:
./.venv/Scripts/python.exe call_user.py "ちょっといい？"                  # スマホに発信
./.venv/Scripts/python.exe send_audio.py "ビルドが正常に終わったよ"         # プッシュ音声
./.venv/Scripts/python.exe send_audio.py "疲れた" --reply                 # LLM が返答を作成
./.venv/Scripts/python.exe status.py                                    # 全体の状態
```

アプリでは: 会話は**エージェントに発信**、短いやり取りは**トランシーバー**。

## 🤖 AI エージェント向けガイド（セッション統合）

PC にアクセスできるエージェントは、Interfon を使って**ユーザーと話す**ことができます。ユーザーが声で
言ったことを聞き、音声で返答し、電話をかけたりポッドキャストを送ったりできます。すべて
`http://127.0.0.1:8765` への HTTP です（LAN、認証なし）。例では `$S` = `server/.venv/Scripts/python.exe`。

### システムの状態（必ず最初に確認）

```bash
$S server/status.py        # または: curl http://127.0.0.1:8765/api/status
```

重要な項目: `phone_connected`（アプリは生きているか）、`llm.loaded`、`session.active`。

### エージェント → 人: 音声を送る

| したいこと | 呼び出し |
| --- | --- |
| 音声を送る（着信風） | `POST /api/message` `{"text": "..."}` |
| スマホに発信（ロック画面の上で鳴る） | `POST /api/call` `{"text": "用件"}` — 応答時に読み上げ |
| ローカル LLM が書いた返答の音声 | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "ビルド完了、すべて成功"     # プッシュ音声
$S server/call_user.py "ちょっといい？"             # 発信
```

### 人 → エージェント: 話した内容を聞く

話した内容（通話、トランシーバー）はすべて `server/logs/transcripts-YYYY-MM-DD.jsonl` に書き起こされます。

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "成績はどうなって..."}
```

`kind`: `stt`（人の声）· `agent`（音声での返答）· `message_pushed` · `telegram` · 通話イベント。
会話を再開するには、その日のファイルを `tail` して `kind=stt` で絞り込み、`send_audio.py` で返答します。
これが「エージェントのセッションから電話で会話する」一連の流れです。

### 音声を送らずに質問する

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "メデジンの天気"}'
# → {"reply": "メデジンは今 20 度です…", "grounded": true}
```

スマホと同じ機能: 日付・時刻・PC の状態、天気、ニュース、インターネット/Wikipedia 検索、
コロンビアの祝日（それ以外は LLM）。

### 論文ポッドキャスト（papercast）

1. 最近の評価の高い論文（arXiv/トレンド）を選び、4〜8 個の**話し言葉のパート**に要約します
   （各約 700 文字、簡単なたとえ、数字はそのまま — 「子どもに説明するように」）。
2. パートを `---` 行で区切って `.txt` に保存します。
3. 再生:

```bash
$S server/papercast.py script.txt   # 各パートが間隔を空けて着信として届く
```

### Telegram（任意）

`server/.env` の `TELEGRAM_BOT_TOKEN`（実行中に検出、再起動不要）。ユーザーは `/start` でチャットを
承認し、ボットは同じ機能で返答します。PC からメッセージを送るには: `$S server/telegram_send.py "テキスト"`。

### 運用ルール

- **半二重**: 通話中（`session.active`）は音声を送らないでください。
- 遅延: STT 約 1.6 秒 · ローカル LLM 2〜10 秒（初回ロード約 90 秒）· TTS 約 1〜4 秒。
- 長い音声: 900 文字未満のパートに分けてください（聞きやすくなります）。
- 主役はサーバーです: PC を再起動したら `serve.py` を再度起動してください。

## 実測の遅延（Ryzen 5 3400G、内蔵 GPU）

| ステップ | 時間 |
| --- | --- |
| STT（約 5 秒の文） | 約 1.6 秒 |
| LLM gemma-4-E2B（短い返答、ウォーム状態） | 約 2-10 秒 |
| TTS Piper daniela-high | 約 1-4 秒 |
| **1 往復の合計** | **約 5-15 秒**（小さなローカルモデルのため想定内の遅延） |

## セキュリティ

- すべて **LAN 内**で完結します。エージェントのデータはインターネットに出ません（HuggingFace からの音声ダウンロードを除く）。
- サーバーには**認証がありません**。信頼できるネットワークでのみ使ってください。
- 署名用キーストアと `.env` は `.secrets/` にあり、リポジトリには**決して**含めません。

## 構成

```
Interfon/
├── android/               # Kotlin + Compose アプリ（ダークモード、アダプティブアイコン、8 言語）
├── server/                # FastAPI + WebSocket + プロバイダー（Unsloth API、Piper、SAPI）
│   ├── interfon_server/   #   main（プロトコル）、providers、vad、wavutil、state、config
│   ├── serve.py           #   起動
│   ├── call_user.py       #   CLI: スマホに発信
│   ├── send_audio.py      #   CLI: 音声を送信
│   └── status.py          #   CLI: 状態
├── scripts/
│   ├── setup_server.ps1   #   PC の準備
│   ├── download_voice.py  #   HuggingFace の Piper 音声
│   └── publish_release.py #   バージョン更新 + ビルド + GitHub release
├── docs/arquitectura.md   # WebSocket プロトコルの詳細
├── docs/i18n/             # この README の他 7 言語版
└── directives/            # プロジェクトの運用手順
```

## 新しいバージョンの公開

```bash
python scripts/publish_release.py patch   # または minor / major
```

`versionCode` を上げ、署名済み APK をビルドし、タグを作成して GitHub に release を公開します。

## ライセンス

MIT — [LICENSE](../../LICENSE) を参照。
