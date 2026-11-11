#!/bin/bash
# Oracle OS — etapa final automática para uso em apagão:
# espera o acervo (ZIM + pacotes assinados) terminar, instala tudo no telemóvel, incluindo
# o leitor Kiwix e o Organic Maps, e corre o teste do mapa e do acervo. Pode deixar a correr.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"; mkdir -p logs/dados
exec > >(tee -a logs/dados/finalizar.log) 2>&1
caffeinate -dimsu -w $$ &
echo "=== Finalizar — $(date '+%F %T'): à espera do acervo e da instalação de texto ==="
until grep -q "Acervo concluído" logs/dados/acervo.log 2>/dev/null; do sleep 60; done
until grep -q "Teste concluído" logs/instalar/teste.log 2>/dev/null; do sleep 60; done
echo "--- $(date '+%F %T') acervo pronto:"
tail -5 logs/dados/pacotes.log
ls oracle-ssd
echo "--- instalar APK, pacotes, ZIM e apps de apoio"
ORACLE_APPS=1 bash scripts/oracle-instalar.command
bash scripts/oracle-telefone-teste.command
echo "=== Finalizado $(date '+%F %T'). Teste final: modo de avião no telemóvel e abrir Mapa e Acervo. ==="
