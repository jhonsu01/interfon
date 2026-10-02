# 📞 Interfon — Interfone com o seu agente de IA

[Español](../../README.md) · [English](README.en.md) · [中文](README.zh.md) · **Português** · [한국어](README.ko.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · [Français](README.fr.md)

O **Interfon** transforma seu PC e seu Android em um interfone de voz: o agente pode
**ligar para você**, **conversar com você** e **enviar áudios**; você pode ligar para ele ou usar o
modo **walkie-talkie**. Tudo roda **localmente** (sua rede WiFi) contra a sua própria API de
inferência (Unsloth Studio), sem serviços de terceiros.

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  Agente (CLI) ──┐                                              │
   │                 ▼                                              │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Servidor Interfon       │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · voz Piper (Daniela)   │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + áudio), WiFi local — um ou vários PCs
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  App Interfon (Compose, modo escuro)                           │
   │  · Recebe chamadas (toca sobre a tela de bloqueio)             │
   │  · Conversa por voz com o agente (transcrição ao vivo)         │
   │  · Walkie-talkie: segure para falar                            │
   │  · Recebe áudios do agente mesmo com o app fechado             │
   └─────────────────────────────────────────────────────────────────┘
```

## Recursos

| Recurso | Como funciona |
| --- | --- |
| 📲 Chamadas recebidas | O agente executa `call_user.py` → o celular **toca** (tela cheia, vibração, toque) → você atende e conversa. |
| 📞 Chamadas feitas | Botão "Ligar para o agente" no app → conversa de voz imediata. |
| 🎙️ Walkie-talkie | Segure o botão, solte para enviar; o agente responde por voz. |
| 📻 Áudios push | `send_audio.py "texto"` → o app reproduz o áudio mesmo em segundo plano (fica na fila se estiver desconectado). |
| 🔤 Voz para texto | `qwen3-asr-0.6b` pela sua API local (~1,6 s por frase). |
| 🧠 Cérebro | `gemma-4-E2B-it` (configurável) com `enable_thinking:false` para respostas faladas curtas. |
| 🗣️ Voz do agente | **Piper `es_AR-daniela-high`** com SAPI de reserva (Sabina es-MX). Outras vozes: `python scripts/download_voice.py --list`. |
| 🌓 Modo escuro | Forçado, Material 3, tema verde sobre preto azulado. |
| ✍️ Transcrição | Tudo o que é dito (pelos dois lados) aparece na tela e fica registrado em `server/logs/`. |
| 📡 Descoberta automática | O app varre a rede local e adiciona sozinho **todos** os servidores que encontrar (sem configurar IP). |
| 🖧 Vários servidores | Conexão simultânea com vários PCs. Qualquer um pode ligar para você ou enviar áudios; suas chamadas e o walkie saem pelo primeiro servidor conectado da lista e, se ele cair, o próximo assume (failover). Cada servidor tem um **nome personalizável**. |
| 📋 Lista recolhível | Na tela inicial, o cartão de servidores se recolhe e mostra o contador `conectados/total`; expandido, o estado de cada um. |
| 🌐 8 idiomas | Español, English, 中文, Português, 한국어, Русский, 日本語 e Français. Escolhido na primeira abertura e alterável em **Configurações → Idioma** (no Android 13+ também em *Configurações do sistema → Idioma do app*). |

## Primeiros passos

### 1. PC: servidor

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + dependências + voz Piper (~114 MB do HuggingFace)
# Opcional (como admin) para aceitar conexões do celular pelo WiFi:
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

Requisitos: **Unsloth Studio** (ou qualquer API compatível com OpenAI) rodando em `127.0.0.1:8888`
sem senha. Configure a base e os modelos em `server/.env`.

Você pode repetir isso em vários PCs: o app se conecta a todos ao mesmo tempo.

### 2. Android: o app

Instale o APK da última [release](https://github.com/jhonsu01/interfon/releases)
(`Interfon-vX.Y.Z.apk`, assinado). Ao abrir:

1. Escolha o idioma (só na primeira vez).
2. Conceda microfone e notificações.
3. **Só isso**: o app descobre os servidores sozinho (varre a rede local, valida
   `/api/status` e conecta a todos). Se nenhum estiver conectado, procura de novo
   automaticamente. Você também pode forçar com **🔎 Procurar servidores na rede**.
4. O cartão **Servidores** mostra `conectados/total`; toque nele para recolher ou expandir.

#### Vários servidores

Em **Configurações → Servidores** você pode:

- **Adicionar** um servidor manualmente (`192.168.1.50`, `192.168.1.50:8765` ou a URL completa).
- **Renomeá-lo** (ex.: "PC do escritório", "Notebook") ou mudar a URL com ✏️.
- **Aumentar a prioridade** com ⬆️: o primeiro servidor conectado da lista atende suas chamadas
  e o walkie; se ele cair, o app passa para o próximo sem você fazer nada.
- **Excluí-lo** no mesmo diálogo de edição.

Todos os servidores podem ligar para você e enviar áudios ao mesmo tempo. Se você estiver em
uma chamada com um e outro tentar ligar, o app recusa como "ocupado".

> Por USB também funciona `adb reverse tcp:8765 tcp:8765` e adicionar o servidor
> `http://127.0.0.1:8765`.

### 3. Falar

```bash
# no PC (dentro de server/):
./.venv/Scripts/python.exe call_user.py "Tem um minuto?"                # ligar para o celular
./.venv/Scripts/python.exe send_audio.py "A compilação terminou OK"     # áudio push
./.venv/Scripts/python.exe send_audio.py "estou cansado" --reply        # o LLM escreve a resposta
./.venv/Scripts/python.exe status.py                                    # estado geral
```

No app: **Ligar para o agente** para conversar, **Walkie-Talkie** para trocas curtas.

## 🤖 Guia para agentes de IA (integração de sessão)

Qualquer agente com acesso ao PC pode usar o Interfon para **falar com o usuário**: ouvir o que
ele disse por voz, responder com áudios, ligar para ele ou enviar podcasts. Tudo é HTTP contra
`http://127.0.0.1:8765` (LAN, sem autenticação). Nos exemplos,
`$S` = `server/.venv/Scripts/python.exe`.

### Estado do sistema (sempre verificar primeiro)

```bash
$S server/status.py        # ou: curl http://127.0.0.1:8765/api/status
```

Campos-chave: `phone_connected` (o app está vivo?), `llm.loaded`, `session.active`.

### Agente → humano: enviar voz

| Quero | Chamada |
| --- | --- |
| Enviar um áudio (efeito de chamada recebida) | `POST /api/message` `{"text": "..."}` |
| Ligar para o celular (toca sobre o bloqueio) | `POST /api/call` `{"text": "motivo"}` — dito ao atender |
| Áudio com resposta escrita pelo LLM local | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "Build terminado, tudo verde"     # áudio push
$S server/call_user.py "Tem um minuto?"                   # chamada
```

### Humano → agente: ouvir o que ele disse

Tudo o que é falado (chamada, walkie) é transcrito em
`server/logs/transcripts-AAAA-MM-DD.jsonl`:

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "Qual é a nota..."}
```

`kind`: `stt` (voz do humano) · `agent` (resposta falada) · `message_pushed` · `telegram` ·
eventos de chamada. Para retomar uma conversa: `tail` do dia e filtrar `kind=stt`. Depois responda
com `send_audio.py` — esse é o ciclo completo de "conversar pelo telefone a partir de uma sessão de agente".

### Perguntar sem enviar áudio

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "clima em medellin"}'
# → {"reply": "Em Medellín faz 20 graus...", "grounded": true}
```

As mesmas habilidades do celular: data/hora/estado do PC, clima, notícias, busca na
internet/Wikipédia, feriados da Colômbia (e o LLM para o resto).

### Podcast de papers (papercast)

1. Escolha um paper recente e bem ranqueado (arXiv/trending) e resuma em 4–8
   **partes coloquiais** (~700 caracteres cada, analogias simples, números intactos —
   estilo "explique como para uma criança").
2. Salve as partes em um `.txt` separadas por linhas `---`.
3. Reproduza:

```bash
$S server/papercast.py roteiro.txt   # cada parte chega como chamada, espaçada
```

### Telegram (opcional)

`TELEGRAM_BOT_TOKEN` em `server/.env` (detecção a quente, sem reiniciar). O usuário autoriza o
chat com `/start`; o bot responde com as mesmas habilidades. Para escrever do PC:
`$S server/telegram_send.py "texto"`.

### Regras de operação

- **Half-duplex**: não envie áudios durante uma chamada ativa (`session.active`).
- Latências: STT ~1,6 s · LLM local 2–10 s (primeira carga ~90 s) · TTS ~1–4 s.
- Áudios longos: divida em partes de menos de 900 caracteres (melhor ritmo de escuta).
- Quem manda é o servidor: se reiniciar o PC, `serve.py` precisa rodar de novo.

## Latências medidas (Ryzen 5 3400G, iGPU)

| Etapa | Tempo |
| --- | --- |
| STT (frase de ~5 s) | ~1,6 s |
| LLM gemma-4-E2B (resposta curta, aquecido) | ~2-10 s |
| TTS Piper daniela-high | ~1-4 s |
| **Total por troca** | **~5-15 s** (modelo local pequeno: é o atraso esperado) |

## Segurança

- Tudo é **local na LAN**: nenhum dado do agente vai para a internet (só o download da voz
  do HuggingFace).
- O servidor **não tem autenticação**: use apenas em uma rede de confiança.
- O keystore de assinatura e o `.env` ficam em `.secrets/` e **nunca** vão para o repositório.

## Estrutura

```
Interfon/
├── android/               # App Kotlin + Compose (modo escuro, ícones adaptativos, 8 idiomas)
├── server/                # FastAPI + WebSocket + provedores (Unsloth API, Piper, SAPI)
│   ├── interfon_server/   #   main (protocolo), providers, vad, wavutil, state, config
│   ├── serve.py           #   inicialização
│   ├── call_user.py       #   CLI: ligar para o celular
│   ├── send_audio.py      #   CLI: enviar áudio
│   └── status.py          #   CLI: estado
├── scripts/
│   ├── setup_server.ps1   #   preparação do PC
│   ├── download_voice.py  #   vozes Piper do HuggingFace
│   └── publish_release.py #   bump + build + release no GitHub
├── docs/arquitectura.md   # protocolo WebSocket detalhado
├── docs/i18n/             # este README em mais 7 idiomas
└── directives/            # procedimento de operação do projeto
```

## Publicar uma nova versão

```bash
python scripts/publish_release.py patch   # ou minor / major
```

Sobe o `versionCode`, compila o APK assinado, cria a tag e publica a release no GitHub.

## Licença

MIT — veja [LICENSE](../../LICENSE).
