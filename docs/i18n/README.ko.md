# 📞 Interfon — AI 에이전트와 통화하는 인터폰

[Español](../../README.md) · [English](README.en.md) · [中文](README.zh.md) · [Português](README.pt.md) · **한국어** · [Русский](README.ru.md) · [日本語](README.ja.md) · [Français](README.fr.md)

**Interfon**은 PC와 Android 휴대폰을 음성 인터폰으로 바꿔 줍니다. 에이전트는 **전화를 걸고**,
**대화하고**, **음성 메시지를 보낼** 수 있으며, 사용자는 에이전트에게 전화하거나 **무전기** 모드를
쓸 수 있습니다. 모든 것이 **로컬**(내 WiFi 네트워크)에서 내 추론 API(Unsloth Studio)로 동작하며,
외부 서비스는 쓰지 않습니다.

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  에이전트 (CLI) ──┐                                            │
   │                   ▼                                            │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Interfon 서버           │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · Piper 음성 (Daniela)  │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + 오디오), 로컬 WiFi — PC 한 대 또는 여러 대
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  Interfon 앱 (Compose, 다크 모드)                               │
   │  · 전화 수신 (잠금 화면 위에서 벨 울림)                          │
   │  · 에이전트와 음성 대화 (실시간 자막)                            │
   │  · 무전기: 길게 눌러 말하기                                      │
   │  · 앱이 닫혀 있어도 에이전트 음성 수신                           │
   └─────────────────────────────────────────────────────────────────┘
```

## 기능

| 기능 | 동작 방식 |
| --- | --- |
| 📲 수신 전화 | 에이전트가 `call_user.py` 실행 → 휴대폰이 **울림**(전체 화면, 진동, 벨소리) → 받아서 대화. |
| 📞 발신 전화 | 앱의 "에이전트에게 전화" 버튼 → 바로 음성 대화. |
| 🎙️ 무전기 | 버튼을 누른 채 말하고 떼면 전송; 에이전트가 음성으로 답합니다. |
| 📻 푸시 음성 | `send_audio.py "텍스트"` → 앱이 백그라운드에서도 재생(연결이 끊겨 있으면 대기열에 보관). |
| 🔤 음성 인식 | 로컬 API를 통한 `qwen3-asr-0.6b` (문장당 약 1.6초). |
| 🧠 두뇌 | `gemma-4-E2B-it`(변경 가능), 짧은 음성 답변을 위해 `enable_thinking:false` 사용. |
| 🗣️ 에이전트 음성 | **Piper `es_AR-daniela-high`**, 예비로 SAPI(Sabina es-MX). 다른 음성: `python scripts/download_voice.py --list`. |
| 🌓 다크 모드 | 항상 적용, Material 3, 푸른빛 검정 바탕에 초록 테마. |
| ✍️ 자막 | 양쪽이 한 말이 모두 화면에 표시되고 `server/logs/`에 기록됩니다. |
| 📡 자동 검색 | 앱이 로컬 네트워크를 스캔해 찾은 **모든** 서버를 자동으로 추가합니다(IP 설정 불필요). |
| 🖧 여러 서버 | 여러 PC에 동시에 연결. 어느 서버든 전화하거나 음성을 보낼 수 있고, 내 통화와 무전기는 목록에서 처음 연결된 서버를 통해 나가며, 끊기면 다음 서버가 이어받습니다(페일오버). 서버마다 **이름을 정할 수** 있습니다. |
| 📋 접히는 목록 | 홈 화면의 서버 카드는 접을 수 있고, 접으면 `연결됨/전체` 개수를, 펼치면 서버별 상태를 보여 줍니다. |
| 🌐 8개 언어 | Español, English, 中文, Português, 한국어, Русский, 日本語, Français. 처음 실행할 때 고르고 **설정 → 언어**에서 바꿀 수 있습니다(Android 13+는 *시스템 설정 → 앱 언어*에서도 가능). |

## 시작하기

### 1. PC: 서버

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + 의존성 + Piper 음성 (HuggingFace에서 약 114 MB)
# 선택 사항 (관리자 권한): WiFi로 휴대폰 연결 허용
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

요구 사항: 비밀번호 없이 `127.0.0.1:8888`에서 실행 중인 **Unsloth Studio**(또는 OpenAI 호환 API).
주소와 모델은 `server/.env`에서 설정합니다.

여러 PC에서 반복해도 됩니다. 앱이 모두에 동시에 연결합니다.

### 2. Android: 앱

최신 [release](https://github.com/jhonsu01/interfon/releases)에서 APK
(`Interfon-vX.Y.Z.apk`, 서명됨)를 설치합니다. 앱을 열면:

1. 언어를 고릅니다(처음 한 번만).
2. 마이크와 알림 권한을 허용합니다.
3. **끝**: 앱이 서버를 스스로 찾습니다(로컬 네트워크 스캔, `/api/status` 확인 후 모두에 연결).
   연결된 서버가 없으면 자동으로 다시 찾습니다. **🔎 네트워크에서 서버 찾기**로 직접 실행할 수도 있습니다.
4. **서버** 카드에 `연결됨/전체`가 표시되며, 누르면 접거나 펼칠 수 있습니다.

#### 여러 서버

**설정 → 서버**에서 다음을 할 수 있습니다.

- 서버를 직접 **추가** (`192.168.1.50`, `192.168.1.50:8765` 또는 전체 URL).
- ✏️로 **이름 변경**(예: "사무실 PC", "노트북") 또는 URL 변경.
- ⬆️로 **우선순위 올리기**: 목록에서 처음 연결된 서버가 발신 통화와 무전기를 맡고,
  끊기면 앱이 알아서 다음 서버로 넘어갑니다.
- 같은 편집 창에서 **삭제**.

모든 서버가 동시에 전화하고 음성을 보낼 수 있습니다. 한 서버와 통화 중에 다른 서버가 전화하면
앱이 "통화 중"으로 거절합니다.

> USB로는 `adb reverse tcp:8765 tcp:8765` 후 서버 `http://127.0.0.1:8765`를 추가해도 됩니다.

### 3. 대화하기

```bash
# PC에서 (server/ 안에서):
./.venv/Scripts/python.exe call_user.py "잠깐 시간 돼?"                  # 휴대폰에 전화
./.venv/Scripts/python.exe send_audio.py "빌드가 정상 완료됐어"           # 푸시 음성
./.venv/Scripts/python.exe send_audio.py "피곤해" --reply                # LLM이 답변 작성
./.venv/Scripts/python.exe status.py                                    # 전체 상태
```

앱에서: 대화는 **에이전트에게 전화**, 짧은 주고받기는 **무전기**.

## 🤖 AI 에이전트 가이드 (세션 통합)

PC에 접근할 수 있는 에이전트라면 Interfon으로 **사용자와 이야기**할 수 있습니다. 사용자가 음성으로
한 말을 듣고, 음성으로 답하고, 전화하거나 팟캐스트를 보낼 수 있습니다. 모두 `http://127.0.0.1:8765`
대상 HTTP입니다(LAN, 인증 없음). 예시에서 `$S` = `server/.venv/Scripts/python.exe`.

### 시스템 상태 (항상 먼저 확인)

```bash
$S server/status.py        # 또는: curl http://127.0.0.1:8765/api/status
```

핵심 필드: `phone_connected`(앱이 살아 있나?), `llm.loaded`, `session.active`.

### 에이전트 → 사람: 음성 보내기

| 하고 싶은 것 | 호출 |
| --- | --- |
| 음성 보내기 (수신 전화 효과) | `POST /api/message` `{"text": "..."}` |
| 휴대폰에 전화 (잠금 화면 위에서 울림) | `POST /api/call` `{"text": "용건"}` — 받으면 읽어 줌 |
| 로컬 LLM이 쓴 답변 음성 | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "빌드 끝, 전부 통과"     # 푸시 음성
$S server/call_user.py "잠깐 시간 돼?"           # 전화
```

### 사람 → 에이전트: 사용자가 한 말 듣기

말한 내용(통화, 무전기)은 모두 `server/logs/transcripts-YYYY-MM-DD.jsonl`에 기록됩니다.

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "성적이 어떻게 돼..."}
```

`kind`: `stt`(사람 음성) · `agent`(음성 답변) · `message_pushed` · `telegram` · 통화 이벤트.
대화를 이어 가려면 당일 파일을 `tail` 하고 `kind=stt`로 거른 뒤 `send_audio.py`로 답하면 됩니다.
이것이 "에이전트 세션에서 전화로 대화하기"의 전체 흐름입니다.

### 음성 없이 질문하기

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "메데인 날씨"}'
# → {"reply": "메데인은 지금 20도입니다...", "grounded": true}
```

휴대폰과 같은 기능: 날짜/시간/PC 상태, 날씨, 뉴스, 인터넷/위키백과 검색, 콜롬비아 공휴일(나머지는 LLM).

### 논문 팟캐스트 (papercast)

1. 최근에 순위가 높은 논문(arXiv/트렌딩)을 골라 4–8개의 **구어체 파트**로 요약합니다
   (파트당 약 700자, 쉬운 비유, 숫자는 그대로 — "아이에게 설명하듯" 스타일).
2. 파트를 `---` 줄로 구분해 `.txt`에 저장합니다.
3. 재생:

```bash
$S server/papercast.py script.txt   # 각 파트가 간격을 두고 전화처럼 도착
```

### 종이컵 전화 (두 서버의 대화)

두 Interfon 서버가 서로 대화하고, 휴대폰은 말하는 쪽의 목소리로 매 차례를 들려줍니다. 각 에이전트는 **자기 LLM**으로 생각하며 상대가 한 말만 압니다(최근 대화 기록을 받습니다).

```bash
python scripts/vaso.py "안녕, 같이 이야기를 지어 보자" \
    --a http://192.168.1.50:8765 --nombre-a "50번" \
    --b http://192.168.1.8:8765  --nombre-b "개발 PC" --rondas 3
```

기능을 호출하는 단어(날씨, 뉴스, 위키백과, 날짜/시간)는 기록에서 동의어로 바뀌어 항상 LLM이 답합니다.

### Telegram (선택)

`server/.env`의 `TELEGRAM_BOT_TOKEN` (실행 중 감지, 재시작 불필요). 사용자는 `/start`로 채팅을
승인하고, 봇은 같은 기능으로 답합니다. PC에서 메시지 보내기: `$S server/telegram_send.py "텍스트"`.

### 운영 규칙

- **반이중**: 통화 중(`session.active`)에는 음성을 보내지 마세요.
- 지연: STT 약 1.6초 · 로컬 LLM 2–10초(첫 로딩 약 90초) · TTS 약 1–4초.
- 긴 음성: 900자 미만으로 나눠 보내세요(듣기 리듬이 좋아집니다).
- 서버가 중심입니다: PC를 재시작하면 `serve.py`를 다시 실행해야 합니다.

## 측정한 지연 시간 (Ryzen 5 3400G, 내장 GPU)

| 단계 | 시간 |
| --- | --- |
| STT (약 5초 문장) | 약 1.6초 |
| LLM gemma-4-E2B (짧은 답변, 워밍업 후) | 약 2-10초 |
| TTS Piper daniela-high | 약 1-4초 |
| **주고받기 1회 합계** | **약 5-15초** (작은 로컬 모델: 예상된 지연) |

## 보안

- 모든 것이 **LAN 로컬**입니다. 에이전트 데이터는 인터넷으로 나가지 않습니다(HuggingFace 음성 다운로드 제외).
- 서버에는 **인증이 없습니다**. 신뢰할 수 있는 네트워크에서만 사용하세요.
- 서명 키스토어와 `.env`는 `.secrets/`에 있으며 **절대** 저장소에 올리지 않습니다.

## 구조

```
Interfon/
├── android/               # Kotlin + Compose 앱 (다크 모드, 적응형 아이콘, 8개 언어)
├── server/                # FastAPI + WebSocket + 제공자 (Unsloth API, Piper, SAPI)
│   ├── interfon_server/   #   main(프로토콜), providers, vad, wavutil, state, config
│   ├── serve.py           #   시작
│   ├── call_user.py       #   CLI: 휴대폰에 전화
│   ├── send_audio.py      #   CLI: 음성 보내기
│   └── status.py          #   CLI: 상태
├── scripts/
│   ├── setup_server.ps1   #   PC 준비
│   ├── download_voice.py  #   HuggingFace의 Piper 음성
│   ├── publish_release.py #   버전 올리기 + 빌드 + GitHub release
│   └── vaso.py            #   두 서버 간 종이컵 전화
├── docs/arquitectura.md   # 자세한 WebSocket 프로토콜
├── docs/i18n/             # 이 README의 다른 7개 언어판
└── directives/            # 프로젝트 운영 절차
```

## 새 버전 배포

```bash
python scripts/publish_release.py patch   # 또는 minor / major
```

`versionCode`를 올리고, 서명된 APK를 빌드하고, 태그를 만들어 GitHub에 release를 게시합니다.

## 라이선스

MIT — [LICENSE](../../LICENSE) 참고.
