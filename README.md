```
   ██████╗ ██████╗  █████╗  ██████╗██╗     ███████╗     ██████╗ ███████╗
  ██╔═══██╗██╔══██╗██╔══██╗██╔════╝██║     ██╔════╝    ██╔═══██╗██╔════╝
  ██║   ██║██████╔╝███████║██║     ██║     █████╗      ██║   ██║███████╗
  ██║   ██║██╔══██╗██╔══██║██║     ██║     ██╔══╝      ██║   ██║╚════██║
  ╚██████╔╝██║  ██║██║  ██║╚██████╗███████╗███████╗    ╚██████╔╝███████║
   ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚══════╝╚══════╝     ╚═════╝ ╚══════╝
            O F F L I N E   S U R V I V A L   S Y S T E M
```

<p align="center">
<b>Sobreviver e recomeçar — sem internet, sem rede, só com um telemóvel Android.</b><br>
IA no próprio telemóvel · guias de sobrevivência · mapas e rotas offline · bússola · plantas pela câmara<br>
poupança extrema de energia · guias para imprimir · e um jogo: <b>GENESIS</b>
</p>

<p align="center">
<a href="https://github.com/caiorlm/oracle-os/releases/latest/download/OracleOS.apk"><b>⬇️ DESCARREGAR O ORACLE (APK)</b></a>
&nbsp;·&nbsp;
<a href="https://github.com/caiorlm/oracle-os/releases/latest/download/GENESIS.apk"><b>🎮 DESCARREGAR O JOGO GENESIS (APK)</b></a>
&nbsp;·&nbsp;
<a href="https://github.com/caiorlm/oracle-os/releases/latest"><b>📦 TODOS OS FICHEIROS</b></a>
</p>

> 🇬🇧 *English version further down.*

---

## 📲 Instalar em 3 passos (para quem nunca usou o GitHub)

Não precisa de saber programar. **Faça isto no telemóvel Android, enquanto ainda tem internet.**

```
 ┌──────────────────────────┐   ┌──────────────────────────┐   ┌──────────────────────────┐
 │  PASSO 1                 │   │  PASSO 2                 │   │  PASSO 3                 │
 │                          │   │                          │   │                          │
 │  Toque no link azul:     │   │  Abra o ficheiro         │   │  Abra o ORACLE.          │
 │                          │   │  OracleOS.apk            │   │                          │
 │  ⬇️ DESCARREGAR O ORACLE │ → │  (aparece em             │ → │  Já funciona SEM         │
 │                          │   │   "Transferências")      │   │  internet. ✔             │
 │  (no topo desta página)  │   │  e toque em INSTALAR.    │   │                          │
 └──────────────────────────┘   └──────────────────────────┘   └──────────────────────────┘
```

1. **Toque em [⬇️ DESCARREGAR O ORACLE](https://github.com/caiorlm/oracle-os/releases/latest/download/OracleOS.apk)**. O telemóvel descarrega o ficheiro `OracleOS.apk` (cerca de 120 MB).
2. **Abra o ficheiro** (na notificação ou em *Ficheiros → Transferências*) e toque em **Instalar**.
   - Se o Android disser *"Por segurança, o telemóvel não pode instalar apps desta origem"*: toque em **Definições → Permitir desta origem**, volte atrás e toque **Instalar** outra vez.
   - Se o *Play Protect* avisar que a app é desconhecida: toque em **Mais detalhes → Instalar mesmo assim**. (A app é aberta: o código está todo neste repositório.)
3. **Abra o Oracle.** Os **71 guias** (português e inglês) já estão lá dentro. A bússola, as plantas, a emergência e a poupança de energia funcionam logo, sem internet.

### Extras (opcionais, mas recomendados)

| O quê | Para quê | Como |
|---|---|---|
| 🎮 [`GENESIS.apk`](https://github.com/caiorlm/oracle-os/releases/latest/download/GENESIS.apk) | O jogo (fazenda + bichinho + linhagem) | Instale igual ao Oracle. Abre-se no Oracle pela aba **JOGO** ▶ |
| 🧠 [`Qwen3.5-0.8B-Q4_K_M.gguf`](https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf) | A **IA** que responde no telemóvel, sem internet (508 MB) | Descarregue → no Oracle: **Sistema → Importar GGUF** → escolha o ficheiro → aba **IA** → Ativar |
| 🖨️ [`Oracle-Impressao-PT.pdf`](https://github.com/caiorlm/oracle-os/releases/latest/download/Oracle-Impressao-PT.pdf) | Todos os guias em papel (A4), por ordem de prioridade | Imprima **já**, guarde em saco plástico, em 2 sítios |
| 🖨️ [`Oracle-Print-EN.pdf`](https://github.com/caiorlm/oracle-os/releases/latest/download/Oracle-Print-EN.pdf) | The same, in English | Print it now |

> 🧠 O modelo é descarregado do **Hugging Face** (o mesmo ficheiro oficial, SHA-256 `bd258782e35f7f45…`, conferido com o nosso `SHA256SUMS.txt`).

> 💡 **Papel não precisa de bateria.** Imprima pelo menos os guias 1–3: primeiros socorros, água e o plano de 50 anos.

---

## 🧭 O que tem dentro — e o que cada parte faz

```
                         ┌───────────────────────────────┐
                         │          ORACLE OS            │
                         │   (tudo dentro do telemóvel)  │
                         └───────────────┬───────────────┘
     ┌──────────────┬──────────────┬─────┴────────┬──────────────┬──────────────┐
     ▼              ▼              ▼              ▼              ▼              ▼
 ┌────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
 │ ⌂ INÍCIO│  │ ◇  IA    │   │ ⌖  MAPA  │   │ ▤ ACERVO │   │ ▶  JOGO  │   │ ⚙ SISTEMA│
 └────┬───┘   └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘
  painel de     responde       mapa offline   guias e         GENESIS       energia,
  estado,       perguntas      + GPS +        manuais em      (fazenda,     impressão,
  EMERGÊNCIA,   com as         bússola +      árvore:         bichinho,     modelos de
  plantas,      fontes do      locais úteis   saúde, água,    linhagem)     IA, idioma
  bússola,      acervo         e rotas        ofícios…
  rádio
```

| Ícone | Parte | O que faz | Precisa de internet? |
|:-:|---|---|:-:|
| ⚠ | **Emergência** | Botão grande no Início: discador (112/999/911), SMS com as suas coordenadas, rádio FM | ❌ |
| ◇ | **IA local** | Faça perguntas ("como purificar água?") — a IA responde no telemóvel e mostra **de que guia** tirou a resposta | ❌ |
| ▤ | **Acervo** | Biblioteca em árvore: *Saúde · Sobrevivência · Água · Ofícios · Navegação · Espírito · Sociedade · Reconstrução*. Abre PDFs e guias mesmo sem IA | ❌ |
| ⌖ | **Mapa** | Mapa offline, a sua posição GPS, locais úteis (hospitais, abrigos, água) e rotas | ❌ * |
| ⊕ | **Bússola** | Norte verdadeiro, seguir um rumo, aviso de calibração | ❌ |
| ✿ | **Plantas** | Fotografe uma planta → nome provável + artigo para confirmar (nunca coma só com base na app) | ❌ |
| ⚡ | **Energia** | Poupança extrema: ecrã preto, brilho mínimo, IA e GPS desligados, dicas para durar semanas | ❌ |
| ⎙ | **Imprimir** | Os guias e manuais vitais em PDF A4, prontos a imprimir | ❌ |
| ▶ | **Jogo** | GENESIS — para passar o tempo, dos 5 anos em diante | ❌ |
| 🎬 | **Introdução** | Animação 3D estilo PS1 ao abrir (com sinal passa sempre; sem sinal só 1 vez) | ❌ |

\* *O mapa detalhado e as enciclopédias grandes (Wikipedia, medicina, iFixit…) são pacotes de vários GB: veja "Conteúdos grandes" abaixo.*

### 📚 Os 71 guias incluídos (PT + EN)

```
 🩹 Primeiros socorros      💧 Água potável           📋 Plano dos 50 anos
 🔥 Fogo                    🏠 Apagão, frio e abrigo   🍲 Cozinhar sem luz
 🥫 Conservar comida        🧼 Higiene com pouca água  ☢️ Guerra e ataques
 🚑 Busca e resgate         🤱 Parto e plantas med.    🌿 Plantas silvestres
 🎣 Pesca                   🪤 Caça e armadilhas       🌾 Sementes e cereais
 🐐 Animais e veterinária   🧱 Construção natural      ☀️ Energia sem rede
 ⚗️ Química básica          🔨 Ferraria/olaria/carpint. 🧵 Têxteis e couro
 🪢 Cordas e nós            🧭 Navegação               🌊 Rios, lagos e mar
 📻 Comunicação sem energia 📡 Rádio                   🌦️ Tempo e calendário
 🤝 Comunidade e educação   🛡️ Proteger a família       🧠 Saúde mental
 🤸 Alongamentos e dores    🥋 Defesa pessoal          🧘 Yoga · Meditação · Chakras
```

---

## 🎮 GENESIS — Legado da Vida

```
   ┌─────────────────────────────────────────┐
   │ Dia 3 · 14:30 · Primavera · céu limpo   │
   │ VIDA ████████  FOME █████  ÁGUA ██████  │
   │                                         │
   │   🏠        ┌┬┬┬┬┬┬┬┬┬┐       ≈≈≈≈       │
   │   🪣  🧍 🐾  │🌱🌱🥕🥕🥬│       ≈≈🌉≈ 🌲🌲   │
   │            └┴┴┴┴┴┴┴┴┴┘       ≈≈≈≈ 🌲🌲🌲  │
   │                          👵 Vó Rosa      │
   │   [◀][▲][▼][▶]                  (B) (A)  │
   └─────────────────────────────────────────┘
```

Um jogo **original**, 100% offline, inspirado nos clássicos de fazenda e de bichinhos virtuais:

- 🧍 **Crie a sua personagem** — o aspeto é gerado a partir de um **ID único (SHA-256 + 256 bits aleatórios)**: ninguém tem uma igual.
- 🐾 **Cuide do seu bichinho** — fome, água, alegria, energia, limpeza e saúde.
- 🌱 **Fazenda** — 8 culturas (alface, cenoura, batata, milho, feijão, tomate, abóbora, morango): cavar, plantar, regar, colher.
- ⏰ **O tempo corre sempre** — 1 hora do jogo = 6 minutos reais; 1 dia real = 10 dias do jogo. Com o telemóvel fechado, as plantas crescem e a personagem come e bebe da mochila.
- 🥚 **Linhagem** — no dia 100 aparece o ovo da família; 10% do que recolhe vai para ele; os descendentes continuam a história.
- 👶 **Modo 5–10 anos** — botões grandes, pouco texto, alertas simples ("O seu bichinho está com sede! DAR ÁGUA").

**Controlos:** setas = andar · **A** = agir (cavar, plantar, regar, colher, falar) · **B** = menu.

---

## 🗂️ Como o projeto está organizado (para quem quer mexer)

```
oracle-os/
├── app/                    ← App Android (Kotlin + Jetpack Compose)
│   └── src/main/
│       ├── java/…/feature/ ← cada ecrã: chat, maps, knowledge, plants, intro, game, print…
│       └── assets/guias/   ← os 71 guias que vêm dentro do APK
├── core/                   ← regras, contratos, políticas de energia e de IA
├── knowledge/              ← base de dados dos documentos + pesquisa (SQLite FTS5)
├── inference/              ← IA local (llama.cpp, modelos GGUF)
├── genesis/                ← 🎮 o jogo (Godot 4.7, GDScript)
│   ├── autoload/Game.gd    ←   estado, relógio, simulação, gravação
│   ├── core/               ←   matemática, identidade/genoma, sprites e tiles gerados em código
│   ├── ui/ world/          ←   ecrãs, controlos de toque, o vale 64×64
│   └── tests/              ←   559 testes automáticos + partida automática
├── companion/
│   ├── library-builder/    ← catálogos de fontes, descarregador, pacotes assinados, guias (.md)
│   └── i18n/               ← traduções PT/EN da interface
├── scripts/                ← automação: compilar, descarregar, instalar por USB
└── docs/                   ← arquitetura, permissões, testes, formato dos pacotes
```

| Quero… | Onde |
|---|---|
| Compilar o Oracle | [docs/DESENVOLVIMENTO.md](docs/DESENVOLVIMENTO.md) — `./gradlew :app:assembleDebug` (JDK 17, Android SDK 35, NDK 27) |
| Abrir o jogo no Godot | Godot **4.7.2** → *Importar* → `genesis/project.godot` |
| Correr os testes do jogo | `godot --headless --path genesis --script res://tests/run_tests.gd` |
| Trocar o modelo de IA | Qualquer GGUF compatível com llama.cpp → **Sistema → Modelos / Importar GGUF** |
| Escrever/editar um guia | `companion/library-builder/guias/*.md` (Markdown simples) |
| Gerar os PDFs de impressão | `python3 companion/library-builder/build_print_pack.py` |

### 📦 Conteúdos grandes (não estão no GitHub)

As enciclopédias offline (Wikipedia, medicina, iFixit, Wikibooks…), o mapa detalhado e os manuais de terceiros somam **mais de 70 GB** e alguns não podem ser redistribuídos. Por isso **não** estão aqui: os scripts em `companion/library-builder/` (`fetch.py` + catálogos `survival-catalog*.json`) descarregam-nos **das fontes originais**, verificam SHA-256 e constroem pacotes assinados (Ed25519) que a app confere antes de usar.

```
 fontes originais ──fetch.py──▶ downloads/ ──build_knowledge_pack.py──▶ pacote assinado ──USB──▶ 📱
 (Kiwix, Gutenberg,            (SHA-256)                               (Ed25519)
  OMS, MSF, FAO…)
```

---

## ⚖️ Licenças e avisos

- **Código** do Oracle OS e do GENESIS: **[GPL-3.0](LICENSE)** — pode usar, estudar, mudar e partilhar; versões alteradas distribuídas têm de continuar abertas.
- **Guias Oracle** (`guias/*.md`): GPL-3.0, como o resto do projeto.
- **Modelo Qwen 3.5**: Apache-2.0 (equipa Qwen / Alibaba Cloud).
- **Conteúdos de terceiros** mantêm a licença de origem (registada em cada catálogo).
- ⚠️ **Os guias não substituem formação nem profissionais.** Em emergência, ligue para os serviços locais (112 / 999 / 911) se existirem. Nunca coma uma planta só porque a app a identificou.

---

# 🇬🇧 English

**Oracle OS** is a **100% offline** Android app to survive and rebuild: on-device AI, offline maps and routes, a survival & rebuilding library (71 built-in guides in Portuguese and English), compass, plant ID by camera, FM radio, extreme power saving and a print-ready guide pack. It includes **GENESIS — Legacy of Life**, an original offline farm + virtual pet + lineage game (ages 5+).

### Install in 3 steps
1. On your Android phone, tap **[⬇️ download Oracle (APK)](https://github.com/caiorlm/oracle-os/releases/latest/download/OracleOS.apk)**.
2. Open `OracleOS.apk` and tap **Install** (allow "install from this source" if asked; on Play Protect choose *Install anyway*).
3. Open Oracle — the guides are already inside and work without internet.

Optional: **[GENESIS.apk](https://github.com/caiorlm/oracle-os/releases/latest/download/GENESIS.apk)** (the game, opens from the **GAME** tab), the **[AI model](https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf)** (import it in **System → Import GGUF**) and the **[printable guides](https://github.com/caiorlm/oracle-os/releases/latest/download/Oracle-Print-EN.pdf)**. Do it while you still have internet.

**For developers:** Android app in `app/` (Kotlin/Compose/llama.cpp), game in `genesis/` (Godot 4.7, 559 automated tests), library tooling in `companion/`. Code is **GPL-3.0**; the Qwen model is Apache-2.0; large third-party packs are fetched from their original sources by `companion/library-builder/fetch.py`.
