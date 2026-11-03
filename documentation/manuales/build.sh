#!/usr/bin/env bash
#
# Regenera los diagramas (PlantUML → PNG) y compila los manuales a PDF
# (pandoc + weasyprint). Uso: ./build.sh
#
# Requisitos: plantuml, pandoc y weasyprint en el PATH.

set -Eeuo pipefail

cd "$(dirname "$0")"

echo "== Diagramas =="
plantuml -tpng diagramas/*.puml
ls -1 diagramas/*.png

echo "== PDF =="
for doc in manual-tecnico manual-usuario plan-coordinacion; do
  pandoc "$doc.md" -o "$doc.pdf" \
    --pdf-engine=weasyprint \
    --css=estilo.css \
    --resource-path=. \
    --toc --toc-depth=2 \
    -s
  echo "  $doc.pdf"
done

echo "== Listo =="
