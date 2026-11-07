# Oracle OS

Aplicativo Android nativo offline-first. `app` contém Compose, importadores e mapa; `core` define políticas e contratos; `knowledge` guarda documentos e busca FTS5; `inference` integra llama.cpp. O APK básico abre sem rede ou modelo. Pacotes GGUF e PMTiles são opcionais e ficam fora do APK.

## Estado atual

- Telas Início, IA, Mapa, Acervo, Sistema e Emergência em tema escuro.
- Guia incluído no APK, importação TXT/Markdown e pesquisa local com trechos de origem.
- GGUF Qwen3.5-0.8B Lite importado por SAF, verificado por tamanho e SHA-256 e executado em CPU ARM64.
- GPS, bússola, diagnósticos do aparelho, discador e SMS por ações do usuário.
- Mapa de Londres convertido de OSM para PMTiles, importado por SAF e renderizado localmente com MapLibre.
- Builder de pacotes SSD de desenvolvimento com assinatura Ed25519; a importação de pacotes assinados no Android está pendente.

Veja [arquitetura](docs/ARCHITECTURE.md), [permissões](docs/ANDROID-PERMISSIONS.md), [plano de testes](docs/TEST-PLAN.md) e [formato SSD](docs/SSD-PACK-SPEC.md).

## Build no macOS

Requisitos: Android SDK API 35, NDK 27.0.12077973, CMake 3.22.1, JDK 17+ e Gradle Wrapper 8.12. Execute `scripts/bootstrap-llama.sh` se a pasta `third_party/llama.cpp` estiver ausente. O primeiro build precisa de rede para resolver Maven; depois use `--offline`.

```sh
cd /Users/caiomonteiro/Desktop/ORACLE-OS
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/caiomonteiro/Library/Android/sdk'
./gradlew :app:assembleDebug
```

O APK de debug fica em `app/build/outputs/apk/debug/`. Para instalar em um aparelho conectado:

```sh
'/Users/caiomonteiro/Library/Android/sdk/platform-tools/adb' install -r app/build/outputs/apk/debug/app-debug.apk
```

O arquivo `local.properties` aponta para o SDK desta máquina e não deve entrar no controle de versão. Downloads opcionais de modelos e dados ficam fora do APK básico.

## Instalar os dados opcionais no telefone

1. Copie `downloads/models/Qwen3.5-0.8B-Q4_K_M.gguf` para `Download` no aparelho. Abra **Sistema → Importar GGUF** e escolha o arquivo. A aba **IA** permite ativar o modelo quando houver memória suficiente.
2. Copie `downloads/maps/greater-london.pmtiles` para `Download`. Abra **Mapa → Importar PMTiles de Londres** e escolha o arquivo. Depois use **Abrir Londres offline**.

Os arquivos são copiados para armazenamento privado do app apenas após conferência de tamanho e SHA-256. O mapa cobre somente a região de Londres; não fornece dados de trânsito ou risco atual. O modelo Lite gera texto localmente, mas as respostas devem ser conferidas nas fontes exibidas.
