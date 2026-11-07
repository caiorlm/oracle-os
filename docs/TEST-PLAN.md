# Plano de validação

## Gates desta versão — executados em 7/11/2026

- `:core:testDebugUnitTest`: capacidades, orçamento de inferência e fallback.
- `:knowledge:testDebugUnitTest`: tokenização, chunks e ranking sem banco nativo.
- `:knowledge:connectedDebugAndroidTest`: FTS5, integridade e citações no Android.
- `:app:assembleDebug`: APK compilável.
- Realme RMX5313 (Android API 35, ARM64, 3.854.300 kB RAM): instalar e abrir o APK de debug.
- Importar o GGUF Lite pelo seletor Android, verificar SHA-256 e carregar o modelo na CPU.
- Perguntar “GPS” com Wi-Fi e dados móveis desligados: resposta local concluída em cerca de 22 s, com texto progressivo e fonte recuperada.
- Importar PMTiles de Londres pelo seletor Android e abrir o mapa com Wi-Fi e dados móveis desligados.
- Abrir o mapa com o modelo carregado: sem crash; PSS do processo aproximadamente 838 MB.

Os testes `:knowledge:connectedDebugAndroidTest` executaram 2 casos no RMX5313. A build final `:app:assembleDebug` passou. Wi-Fi e dados móveis foram restaurados após os testes.

## Ainda pendente

- Medir cold start, geração longa, cancelamento, unload repetido, bateria baixa e limite térmico.
- Verificar que o app e biblioteca continuam acessíveis após falha de SSD e servidor quando esses módulos existirem.
- Testar remoção do SSD durante leitura e pacote corrupto/não assinado.
- Implementar e validar roteamento offline, busca no mapa e mais regiões.
- Testar emulador API 29, GPS negado e ausência de sensores.

Resultados de emulador não substituem esses testes de hardware.
