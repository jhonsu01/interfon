# 📞 Interfon — L’interphone de votre agent IA

[Español](../../README.md) · [English](README.en.md) · [中文](README.zh.md) · [Português](README.pt.md) · [한국어](README.ko.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · **Français**

**Interfon** transforme votre PC et votre téléphone Android en interphone vocal : l’agent peut
**vous appeler**, **discuter avec vous** et **vous envoyer des messages audio** ; vous pouvez
l’appeler à votre tour ou utiliser le mode **talkie-walkie**. Tout tourne **en local** (votre
réseau WiFi) avec votre propre API d’inférence (Unsloth Studio), sans service tiers.

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  Agent (CLI) ──┐                                               │
   │                ▼                                               │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Serveur Interfon        │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · voix Piper (Daniela)  │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + audio), WiFi local — un ou plusieurs PC
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  Application Interfon (Compose, mode sombre)                   │
   │  · Reçoit les appels (sonne par-dessus l’écran verrouillé)     │
   │  · Conversation vocale avec l’agent (transcription en direct)  │
   │  · Talkie-walkie : maintenez pour parler                       │
   │  · Reçoit l’audio de l’agent même application fermée           │
   └─────────────────────────────────────────────────────────────────┘
```

## Fonctionnalités

| Fonction | Fonctionnement |
| --- | --- |
| 📲 Appels entrants | L’agent lance `call_user.py` → le téléphone **sonne** (plein écran, vibration, sonnerie) → vous répondez et discutez. |
| 📞 Appels sortants | Bouton « Appeler l’agent » dans l’application → conversation vocale immédiate. |
| 🎙️ Talkie-walkie | Maintenez le bouton, relâchez pour envoyer ; l’agent répond à voix haute. |
| 📻 Audio push | `send_audio.py "texte"` → l’application lit l’audio même en arrière-plan (mis en file d’attente si déconnectée). |
| 🔤 Reconnaissance vocale | `qwen3-asr-0.6b` via votre API locale (~1,6 s par phrase). |
| 🧠 Cerveau | `gemma-4-E2B-it` (configurable) avec `enable_thinking:false` pour des réponses orales courtes. |
| 🗣️ Voix de l’agent | **Piper `es_AR-daniela-high`**, avec SAPI en secours (Sabina es-MX). Autres voix : `python scripts/download_voice.py --list`. |
| 🌓 Mode sombre | Forcé, Material 3, thème vert sur noir bleuté. |
| ✍️ Transcription | Tout ce qui est dit (des deux côtés) s’affiche à l’écran et est enregistré dans `server/logs/`. |
| 📡 Découverte automatique | L’application analyse le réseau local et ajoute d’elle-même **tous** les serveurs trouvés (aucune IP à configurer). |
| 🖧 Plusieurs serveurs | Connexion simultanée à plusieurs PC. N’importe lequel peut vous appeler ou vous envoyer de l’audio ; vos appels et le talkie-walkie passent par le premier serveur connecté de la liste et, s’il tombe, le suivant prend le relais (failover). Chaque serveur porte un **nom personnalisable**. |
| 📋 Liste repliable | Sur l’accueil, la carte des serveurs se replie et affiche le compteur `connectés/total` ; dépliée, l’état de chacun. |
| 🌐 8 langues | Español, English, 中文, Português, 한국어, Русский, 日本語 et Français. Choisie au premier lancement et modifiable dans **Réglages → Langue** (sur Android 13+ aussi dans *Paramètres système → Langue de l’application*). |

## Démarrage

### 1. PC : serveur

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + dépendances + voix Piper (~114 Mo depuis HuggingFace)
# Facultatif (en administrateur) pour accepter les connexions du téléphone en WiFi :
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

Prérequis : **Unsloth Studio** (ou toute API compatible OpenAI) tournant sur `127.0.0.1:8888`
sans mot de passe. Configurez l’adresse et les modèles dans `server/.env`.

Vous pouvez répéter l’opération sur plusieurs PC : l’application se connecte à tous à la fois.

### 2. Android : l’application

Installez l’APK de la dernière [release](https://github.com/jhonsu01/interfon/releases)
(`Interfon-vX.Y.Z.apk`, signé). À l’ouverture :

1. Choisissez la langue (la première fois seulement).
2. Autorisez le micro et les notifications.
3. **C’est tout** : l’application découvre les serveurs toute seule (analyse le réseau local,
   vérifie `/api/status` et se connecte à tous). Si aucun n’est connecté, elle relance la recherche
   automatiquement. Vous pouvez aussi la forcer avec **🔎 Rechercher des serveurs sur le réseau**.
4. La carte **Serveurs** affiche `connectés/total` ; touchez-la pour la replier ou la déplier.

#### Plusieurs serveurs

Dans **Réglages → Serveurs**, vous pouvez :

- **Ajouter** un serveur à la main (`192.168.1.50`, `192.168.1.50:8765` ou l’URL complète).
- Le **renommer** (ex. « PC du bureau », « Portable ») ou changer son URL avec ✏️.
- **Augmenter sa priorité** avec ⬆️ : le premier serveur connecté de la liste gère vos appels
  sortants et le talkie-walkie ; s’il tombe, l’application passe au suivant sans rien vous demander.
- Le **supprimer** depuis la même fenêtre d’édition.

Tous les serveurs peuvent vous appeler et vous envoyer de l’audio en même temps. Si vous êtes en
appel avec l’un et qu’un autre tente de vous appeler, l’application le refuse comme « occupé ».

> En USB, `adb reverse tcp:8765 tcp:8765` puis l’ajout du serveur `http://127.0.0.1:8765`
> fonctionne aussi.

### 3. Parler

```bash
# sur le PC (depuis server/) :
./.venv/Scripts/python.exe call_user.py "Tu as une minute ?"            # appeler le téléphone
./.venv/Scripts/python.exe send_audio.py "La compilation est terminée"  # audio push
./.venv/Scripts/python.exe send_audio.py "je suis fatigué" --reply      # le LLM rédige la réponse
./.venv/Scripts/python.exe status.py                                    # état général
```

Dans l’application : **Appeler l’agent** pour discuter, **Talkie-walkie** pour les échanges courts.

## 🤖 Guide pour agents IA (intégration de session)

Tout agent ayant accès au PC peut utiliser Interfon pour **parler à l’utilisateur** : entendre ce
qu’il a dit à voix haute, répondre en audio, l’appeler ou lui envoyer des podcasts. Tout passe par
HTTP sur `http://127.0.0.1:8765` (LAN, sans authentification). Dans les exemples,
`$S` = `server/.venv/Scripts/python.exe`.

### État du système (toujours vérifier d’abord)

```bash
$S server/status.py        # ou : curl http://127.0.0.1:8765/api/status
```

Champs clés : `phone_connected` (l’application est-elle en ligne ?), `llm.loaded`, `session.active`.

### Agent → humain : envoyer de la voix

| Je veux | Appel |
| --- | --- |
| Envoyer un audio (effet d’appel entrant) | `POST /api/message` `{"text": "..."}` |
| Appeler le téléphone (sonne par-dessus le verrouillage) | `POST /api/call` `{"text": "motif"}` — lu au décroché |
| Audio dont la réponse est rédigée par le LLM local | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "Build terminé, tout est vert"     # audio push
$S server/call_user.py "Tu as une minute ?"                # appel
```

### Humain → agent : entendre ce qu’il a dit

Tout ce qui est dit (appel, talkie-walkie) est transcrit dans
`server/logs/transcripts-AAAA-MM-JJ.jsonl` :

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "Quelle est la note..."}
```

`kind` : `stt` (voix de l’humain) · `agent` (réponse orale) · `message_pushed` · `telegram` ·
événements d’appel. Pour reprendre une conversation : `tail` du fichier du jour et filtre `kind=stt`.
Répondez ensuite avec `send_audio.py` — c’est le cycle complet « converser par téléphone depuis une
session d’agent ».

### Poser une question sans envoyer d’audio

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "météo à medellin"}'
# → {"reply": "Il fait 20 degrés à Medellín...", "grounded": true}
```

Les mêmes compétences que le téléphone : date/heure/état du PC, météo, actualités, recherche
internet/Wikipédia, jours fériés de Colombie (et le LLM pour le reste).

### Podcast d’articles (papercast)

1. Choisissez un article récent et bien classé (arXiv/tendances) et résumez-le en 4 à 8
   **parties familières** (~700 caractères chacune, analogies simples, chiffres intacts —
   façon « explique-le à un enfant »).
2. Enregistrez les parties dans un `.txt`, séparées par des lignes `---`.
3. Lancez la lecture :

```bash
$S server/papercast.py script.txt   # chaque partie arrive comme un appel, espacée
```

### Téléphone à gobelets (deux serveurs discutent)

Deux serveurs Interfon discutent entre eux et le téléphone entend chaque tour avec la voix de celui qui parle. Chaque agent réfléchit avec **son propre LLM** et ne sait que ce que l’autre lui a dit (il reçoit l’historique récent de la conversation).

```bash
python scripts/vaso.py "Salut, inventons une histoire ensemble" \
    --a http://192.168.1.50:8765 --nombre-a "le cinquante" \
    --b http://192.168.1.8:8765  --nombre-b "le PC de dev" --rondas 3
```

Les mots qui déclenchent des compétences (météo, actualités, Wikipédia, date/heure) sont remplacés par des synonymes dans l’historique pour que le LLM réponde toujours.

### Telegram (facultatif)

`TELEGRAM_BOT_TOKEN` dans `server/.env` (détection à chaud, sans redémarrage). L’utilisateur
autorise sa conversation avec `/start` ; le bot répond avec les mêmes compétences. Pour lui écrire
depuis le PC : `$S server/telegram_send.py "texte"`.

### Règles de fonctionnement

- **Half-duplex** : n’envoyez pas d’audio pendant un appel actif (`session.active`).
- Latences : STT ~1,6 s · LLM local 2–10 s (premier chargement ~90 s) · TTS ~1–4 s.
- Audios longs : découpez-les en parties de moins de 900 caractères (meilleur rythme d’écoute).
- C’est le serveur qui commande : si vous redémarrez le PC, `serve.py` doit être relancé.

## Latences mesurées (Ryzen 5 3400G, iGPU)

| Étape | Durée |
| --- | --- |
| STT (phrase de ~5 s) | ~1,6 s |
| LLM gemma-4-E2B (réponse courte, à chaud) | ~2-10 s |
| TTS Piper daniela-high | ~1-4 s |
| **Total par échange** | **~5-15 s** (petit modèle local : latence attendue) |

## Sécurité

- Tout reste **sur le réseau local** : aucune donnée de l’agent ne part sur internet (seul le
  téléchargement de la voix depuis HuggingFace).
- Le serveur **n’a pas d’authentification** : utilisez-le uniquement sur un réseau de confiance.
- Le keystore de signature et le `.env` vivent dans `.secrets/` et ne sont **jamais** envoyés au dépôt.

## Structure

```
Interfon/
├── android/               # Application Kotlin + Compose (mode sombre, icônes adaptatives, 8 langues)
├── server/                # FastAPI + WebSocket + fournisseurs (Unsloth API, Piper, SAPI)
│   ├── interfon_server/   #   main (protocole), providers, vad, wavutil, state, config
│   ├── serve.py           #   démarrage
│   ├── call_user.py       #   CLI : appeler le téléphone
│   ├── send_audio.py      #   CLI : envoyer de l’audio
│   └── status.py          #   CLI : état
├── scripts/
│   ├── setup_server.ps1   #   préparation du PC
│   ├── download_voice.py  #   voix Piper depuis HuggingFace
│   ├── publish_release.py #   montée de version + build + release GitHub
│   └── vaso.py            #   téléphone à gobelets entre deux serveurs
├── docs/arquitectura.md   # protocole WebSocket détaillé
├── docs/i18n/             # ce README dans 7 autres langues
└── directives/            # procédure d’exploitation du projet
```

## Publier une nouvelle version

```bash
python scripts/publish_release.py patch   # ou minor / major
```

Augmente le `versionCode`, compile l’APK signé, crée le tag et publie la release sur GitHub.

## Licence

MIT — voir [LICENSE](../../LICENSE).
