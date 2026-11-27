# Orb — Assistente Pessoal de IA para o seu bolso

Um assistente de voz e texto que roda **inteiro no seu Android**, com uma PWA
opcional que usa um backend Python. Conversa, pesquisa na web, consulta o clima,
lembra de fatos sobre você e dispara lembretes — com uma interface HUD feita para
a tela pequena.

Inspirado no **JARVIS (Mark LVII)**, de FatihMakes: a ideia da escada de
modelos do Gemini, a filosofia de memória e o tom do personagem vieram dele. No
lugar da camada desktop (PyQt, controle do computador), este projeto tem uma API
e um app móvel instalável — código escrito de forma independente.

<p align="center">
  <img src="docs/mobile.webp" alt="Orb rodando no celular" width="300">
</p>


> **Licença:** **Licença Orb 1.0** — © 2026 Yanikuaite. Você pode usar,
> modificar e distribuir este projeto, inclusive comercialmente, desde que
> mantenha o crédito. Leia `LICENSE`. A inspiração no JARVIS está creditada em
> `NOTICE`, e o projeto original continua sob CC BY-NC 4.0.

---

## Como funciona

```
┌─────────────────────────┐        HTTPS (só Google)      ┌──────────────────────┐
│  App Android (Kotlin)   │  ───────────────────────────▶ │  Gemini API          │
│  • HUD, chat e voz      │  ◀─────────────────────────── │  • texto e tools     │
│  • memória e lembretes  │                               │  • voz (TTS)         │
│  • chave criptografada  │                               └──────────────────────┘
└─────────────────────────┘

┌─────────────────────────┐        HTTPS                  ┌──────────────────────┐
│  PWA / Capacitor        │  ───────────────────────────▶ │  Servidor Python     │
│  (opcional)             │                               │  FastAPI + Gemini    │
└─────────────────────────┘                               └──────────────────────┘
```

O app Android é autônomo: fala direto com o Gemini, guarda memória, histórico e
lembretes no aparelho e **não precisa de PC nem de servidor**. O backend FastAPI
continua no repositório para a PWA/Capacitor. Como o Gemini é um serviço na
nuvem, o app precisa de internet e de uma chave do usuário — ela fica
criptografada no celular e é enviada só para o Google.

## O que ele faz

- **Conversa por voz ou texto**, com respostas curtas pensadas para serem ouvidas.
- **Memória de longo prazo** com painel para ver e apagar cada fato.
- **Pesquisa na web** com fontes (Gemini grounding, com fallback DuckDuckGo).
- **Clima** de qualquer cidade, via Open-Meteo (sem chave extra).
- **Lembretes** com alarme no Android, mesmo com o app fechado (na PWA, enquanto aberta).
- **Voz do aparelho** (grátis) ou **voz Gemini** (mais natural).
- **App Android nativo** em Kotlin + Jetpack Compose, com PWA instalável como alternativa.
- **Escolha de modelo de texto**: Gemini (também voz/live) ou Groq (só texto), com modelo editável em Ajustes.

## Começando (5 minutos)

> **Só quer o app Android?** Ele é autônomo: instale o APK, cole a chave Gemini
> (obrigatória para voz/live) e, se quiser, a chave Groq (texto). O backend abaixo
> é necessário apenas para a PWA/Capacitor.

### 1. Requisitos (backend opcional)

- Python 3.11+
- Uma chave do Google AI Studio: <https://aistudio.google.com/apikey>

### 2. Instalar e rodar

```bash
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r server/requirements.txt

cp .env.example .env
# edite .env e cole sua chave em ORB_API_KEY

python -m server
```

Abra <http://localhost:8000>. Sem chave configurada o app abre em modo de
configuração; cole a chave em **⚙ Ajustes → Chave da API Gemini** ou deixe no
`.env` e reinicie.

### 3. Abrir no celular

O microfone do navegador exige **HTTPS** (ou `localhost`). Na mesma rede, o
caminho mais rápido é um túnel HTTPS:

```bash
# opção A — Cloudflare Tunnel (gratuito, HTTPS automático)
cloudflared tunnel --url http://localhost:8000

# opção B — Tailscale (rede privada, HTTPS)
tailscale serve https / http://localhost:8000
```

Abra a URL HTTPS no celular e use **Compartilhar → Adicionar à tela de início**
(iOS) ou **Instalar aplicativo** (Android/Chrome). O app abre em tela cheia.

> Sem HTTPS, o chat por texto funciona, mas o microfone fica bloqueado pelo
> navegador. Para uso só em rede local confiável, `http://IP:8000` serve para
> texto.

## Deploy com Docker

```bash
cp .env.example .env    # preencha ORB_API_KEY e ORB_TOKEN
docker compose up -d --build
```

Coloque um proxy HTTPS na frente (Caddy é o mais simples):

```caddyfile
orb.seudominio.com {
    reverse_proxy 127.0.0.1:8000
}
```

### Proteção por token

Defina `ORB_TOKEN` com uma string longa e aleatória. Quando ele existe:

- a API responde `401` sem `Authorization: Bearer <token>`;
- o app pede o token uma vez em **⚙ Ajustes → Token de acesso**.

Isso é essencial se o servidor estiver exposto na internet.

## App Android nativo (Kotlin)

O cliente principal é um app **Android nativo em Kotlin + Jetpack Compose**, em
`native/`. Ele é **autônomo**: fala direto com a API Gemini (texto, voz e live) e,
se você configurar, com a API Groq (só texto); guarda memória, histórico e
lembretes no aparelho e **não precisa de PC, servidor ou token**. Foi feito para o
que a PWA não consegue entregar:

- chat com o HUD do Orb, voz (`SpeechRecognizer`) e resposta falada (TTS do
  aparelho ou voz Gemini);
- memória e lembretes;
- **alarmes que tocam com o app fechado** (`AlarmManager` + notificação);
- tema escuro, ícone adaptativo e splash nativos.

O APK de debug é compilado no GitHub Actions
(`.github/workflows/android-native.yml`): aba **Actions → Native Android APK →
artifact `orb-native-debug-apk`**. O APK de debug tem cerca de 20 MB, é assinado
com a chave de debug e instala direto no celular.

O CI também gera `orb-native-release-apk` (~14 MB, não debuggable). Sem os
segredos de keystore, ele usa a chave de debug — serve para sideload, mas builds
diferentes podem exigir desinstalar antes de atualizar. Para uma chave fixa,
adicione os segredos `ORB_KEYSTORE_BASE64`, `ORB_KEYSTORE_PASSWORD`,
`ORB_KEY_ALIAS` e `ORB_KEY_PASSWORD`; o workflow decodifica o `.jks` e o Gradle
assina o release com ele.

### Rodar no Android

Requisitos: JDK 17 + Android SDK 35 (Android Studio serve).

```bash
cd native
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# APK: native/app/build/outputs/apk/debug/app-debug.apk
```

Na primeira execução, cole sua chave do Google AI Studio (obrigatória) e, se
quiser, a chave Groq (opcional, só texto). As duas são validadas, ficam
criptografadas no aparelho (`EncryptedSharedPreferences`) e são enviadas só para
o provedor correspondente. Em **Ajustes → Modelo de texto**, escolha Gemini ou
Groq e o modelo (dá para digitar um modelo novo). O app não usa HTTP cleartext:
todas as chamadas são HTTPS. Sem internet, o chat não funciona; memória,
histórico e lembretes continuam locais.

### iOS

O app Kotlin é Android-only. Para iOS, o caminho atual é a PWA/Capacitor
(abaixo) ou um cliente Swift separado.

### PWA e Capacitor (alternativa)

O `mobile/www` continua sendo a PWA instalável e a base do antigo app híbrido
Capacitor (`mobile/android/`, `appId dev.orb.mobile`). Ele é útil para abrir no
navegador, no iPhone e para testar rápido sem instalar APK.

```bash
cd mobile
npm ci
npm run sync
npm run build:android        # APK híbrido, artifact orb-debug-apk
```

O APK híbrido é menor, mas depende do WebView e não tem alarmes nativos com o
app fechado. O app Kotlin é o recomendado para Android.

## Ferramentas disponíveis

| Ferramenta | O que faz |
|---|---|
| `web_search` | Pesquisa, notícias e pesquisa profunda com fontes |
| `get_weather` | Clima atual e previsão de 1 a 7 dias |
| `remember` / `forget` / `recall` | Memória de longo prazo |
| `now` | Data e hora do aparelho |
| `set_reminder` / `list_reminders` / `cancel_reminder` | Lembretes com alarme local no Android |

Essas ferramentas rodam **no próprio aparelho** no app Kotlin. Na
PWA/Capacitor, elas rodam no backend.

Ferramentas de desktop do JARVIS (abrir programas, controlar o mouse, capturar a
tela) **não** existem aqui: um celular não pode executá-las, e uma ferramenta que
promete o impossível é pior do que nenhuma.

## Configuração

`server/config.json` (criado na primeira alteração) aceita:

```json
{
  "assistant_name": "Orb",
  "user_name": "",
  "language": "pt-BR",
  "voice": "Charon",
  "tts_engine": "browser",
  "persona": "",
  "model_ladder": ["gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash"],
  "smart_ladder": ["gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash"],
  "tts_model": "gemini-2.5-flash-preview-tts"
}
```

- `tts_engine`: `browser` usa a voz do sistema (grátis e instantânea);
  `gemini` usa o TTS do Gemini (mais natural, consome cota).
- `model_ladder`: ordem de modelos tentados. Um modelo sem cota é posto de
  descanso por 5 minutos e o próximo assume — a conversa não cai.
- `persona`: instruções extras de personalidade.

Variáveis de ambiente têm prioridade para a chave: `ORB_API_KEY`,
`GEMINI_API_KEY` ou `GOOGLE_API_KEY`.

## Estrutura do projeto

```
server/
  main.py              # FastAPI: rotas, WebSocket, arquivos estáticos
  orb/
    settings.py        # configuração e chave
    llm.py             # cliente Gemini (escada, timeout, áudio, TTS)
    memory.py          # memória de longo prazo e histórico
    tools.py           # ferramentas e declarações
    agent.py           # laço de conversa com function calling
    prompts.py         # personalidade do Orb
  tests/               # 40 testes, sem rede
mobile/
  www/                 # PWA (HTML/CSS/JS, manifest, service worker, ícones)
  android/             # projeto Android híbrido (Capacitor)
  capacitor.config.json
  package.json
native/
  app/                 # app Android nativo (Kotlin + Compose)
    src/main/java/dev/orb/app/agent/   # OrbAgent, providers, ferramentas
    src/main/java/dev/orb/app/data/    # GeminiClient, GroqClient, stores, chaves
  gradle/              # wrapper e catálogo de versões
.github/workflows/     # testes Python e builds dos APKs
docs/
```

## Testes

```bash
python -m pytest server/tests -q
cd native && ./gradlew :app:testDebugUnitTest
```

No backend, os testes cobrem memória, ferramentas, o laço de ferramentas do
agente, o caminho real do SDK (com um Gemini local simulado) e todas as rotas da
API — inclusive o caminho sem chave. No app Kotlin, os testes cobrem o wrapper
WAV do TTS, o parser de datas de lembrete, a memória local e a conversão de schema
para o Groq.

## Segurança e privacidade

- No app Android, as chaves Gemini e Groq ficam criptografadas no aparelho
  (`EncryptedSharedPreferences`) e são enviadas só para o provedor correspondente.
  A Gemini é obrigatória porque também gera voz/live; a Groq é opcional e só texto.
- Na PWA/backend, a chave fica no servidor (`.env` ou `config.json`), nunca no
  navegador. Use `ORB_TOKEN` sempre que o backend estiver acessível pela internet.
- No app Android, memória e histórico são arquivos JSON em `filesDir/orb/`; nada
  vai para a nuvem além das mensagens enviadas ao Gemini.
- O app não guarda senhas nem executa comandos no seu computador.

## Limitações conhecidas

- O microfone exige HTTPS no navegador.
- A voz Gemini depende do modelo de TTS estar disponível para a sua chave; se
  falhar, o app cai automaticamente para a voz do aparelho.
- Na PWA, lembretes só notificam com o app aberto (sem push em segundo plano).
  No app Kotlin, os alarmes tocam com o app fechado via `AlarmManager`.
- O modelo de texto padrão é `gemini-3.5-flash`; no Groq, `llama-3.3-70b-versatile`.
  Há fallback automático se um modelo estiver indisponível. A lista fica em
  `native/app/src/main/java/dev/orb/app/data/Providers.kt` e o campo em Ajustes
  aceita qualquer nome de modelo.

## Licença e créditos

**Licença Orb 1.0** (veja `LICENSE`): use, modifique e distribua livremente,
inclusive para fins comerciais, mantendo o aviso de copyright, o `NOTICE` e esta
licença. O nome e a identidade visual do projeto não podem ser usados para
sugerir endosso.

Este projeto é uma **implementação independente** inspirada no **JARVIS (Mark
LVII)**, de [FatihMakes](https://www.youtube.com/@FatihMakes). Nenhum
código-fonte do JARVIS está incluído aqui. O JARVIS continua sob **CC BY-NC 4.0**
(uso não comercial), licença definida pelo autor original; ela vale para o
projeto dele, não para este repositório.

Outros créditos: Gemini (Google), clima (Open-Meteo, CC BY 4.0), busca
(DuckDuckGo). "Orb" é uma palavra comum, usada aqui como nome do projeto;
existem produtos homônimos, sem qualquer vínculo com eles.

> Nada aqui é aconselhamento jurídico. Para uso comercial sério, vale consultar
> um advogado e checar o nome no INPI: já existem produtos chamados "Orb".
