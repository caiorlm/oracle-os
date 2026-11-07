# Asset source and redistribution notes

## Qwen3.5 0.8B GGUF

- Original model: https://huggingface.co/Qwen/Qwen3.5-0.8B
- GGUF conversion and vision projector: https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF
- Both model pages identify the license as Apache-2.0. The local license copy is `Qwen3.5-0.8B-LICENSE.txt`, downloaded from the original repository at commit `2fc06364715b967f1860aea9cf38778875588b17`:
  `https://huggingface.co/Qwen/Qwen3.5-0.8B/resolve/2fc06364715b967f1860aea9cf38778875588b17/LICENSE`
- License copy SHA-256: `bbedc3fda3305820b977265f01b8619d87570a6739de3a5582c3464840f1e57a`.
- Keep the license with any redistributed model package and review other upstream notices before release.

## Greater London OpenStreetMap extract

- Source: https://download.geofabrik.de/europe/united-kingdom/england/greater-london.html
- Dated extract and official MD5 are pinned in `../downloads-manifest.json`.
- Geofabrik says its OSM-derived data is under ODbL 1.0: https://www.geofabrik.de/data/download.html
- Attribution should name OpenStreetMap contributors and Geofabrik. Derived map databases may carry ODbL share-alike obligations. The downloaded PBF is source data, not an app-ready tile package.
