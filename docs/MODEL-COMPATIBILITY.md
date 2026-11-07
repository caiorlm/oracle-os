# Compatibilidade do modelo Lite — medição inicial

Teste em 7/11/2026 no RMX5313, Android API 35, ARM64, RAM física informada pelo kernel de 3.854.300 kB. O modelo `Qwen3.5-0.8B-Q4_K_M.gguf` tem 532.517.120 bytes e SHA-256 `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`.

Configuração usada: contexto de 1024 tokens, até 96 tokens de saída, 2 threads CPU, pesos via mmap e núcleo nativo compilado com `-O2` também no APK de debug. O modelo foi importado por SAF para armazenamento privado e conferido por tamanho/hash antes da carga.

Na pergunta “GPS” após atualizar o guia incluído, a carga terminou sem erro. O prompt consumiu 161 tokens; o primeiro token saiu em 14.284 ms e a resposta terminou após 45 tokens em 21.548 ms. Em teste anterior sem otimização nativa, a mesma tarefa excedeu 120 segundos; por isso a otimização é necessária nesse hardware. A geração com rede desligada também terminou em 22.281 ms para 62 tokens e mostrou saída progressiva.

Com IA e mapa de Londres abertos no mesmo processo, o Android reportou PSS total de aproximadamente 838 MB. O orçamento de memória prévio mantém reserva mínima de 384 MB, bloqueia a IA em modo Sobrevivência, bateria baixa e estado térmico severo. Esses últimos limites e o cancelamento ainda requerem testes específicos no aparelho.

O arquivo `mmproj` foi baixado e verificado para uma etapa futura; visão/câmera não está integrada ao APK atual. O app não contém o GGUF dentro do APK.
