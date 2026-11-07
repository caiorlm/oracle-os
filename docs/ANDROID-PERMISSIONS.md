# Permissões Android — versão 0.1

| Permissão declarada | Uso atual | Quando solicitada |
| --- | --- | --- |
| `ACCESS_FINE_LOCATION` | Coordenadas GPS | Ao abrir navegação e tocar em permitir |
| `ACCESS_COARSE_LOCATION` | Fallback de localização aproximada | Junto com localização precisa |
| `INTERNET` (manifesto mesclado pelo MapLibre) | Capacidade da biblioteca de mapas; o estilo Oracle atual usa apenas PMTiles local | Permissão normal, sem diálogo |
| `ACCESS_NETWORK_STATE` (manifesto mesclado pelo MapLibre) | Estado de conectividade da biblioteca de mapas | Permissão normal, sem diálogo |

O seletor de documentos (`ACTION_OPEN_DOCUMENT`) permite importar TXT, Markdown, GGUF e PMTiles sem acesso amplo ao armazenamento. O aplicativo copia o conteúdo selecionado para seu espaço privado. O discador (`ACTION_DIAL`) e SMS (`ACTION_SENDTO`) são abertos sem `CALL_PHONE` ou permissões de leitura/escrita de SMS.

Sem câmera, microfone, serviço em primeiro plano ou Device Owner nesta versão. A permissão `INTERNET` vem da dependência MapLibre, mas a rota atual de mapa/IA não exige rede. Servidor local ainda não está implementado.
