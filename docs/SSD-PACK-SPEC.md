# Oracle SSD pack v1

O builder de desenvolvimento em `companion/desktop-pack-builder/pack_builder.py` cria uma pasta de pacote imutável com `pack-manifest.json`, `signatures/manifest.sig` e arquivos de dados. O manifesto é assinado em bytes UTF-8 exatos com Ed25519; cada arquivo tem SHA-256 e tamanho registrados. IDs/caminhos duplicados, links simbólicos, traversal e tipos executáveis são rejeitados.

Tipos aceitos: `gguf`, `knowledge` (TXT, Markdown, PDF), `index` (SQLite), `map` (PMTiles/MBTiles) e `license`. O PBF OSM bruto não é aceito como mapa instalável. Campos principais:

```json
{
  "schemaVersion": 1,
  "packId": "oracle-lite-model-dev",
  "version": "0.1.0",
  "minAndroidApi": 28,
  "packages": [{"id": "qwen35-08b-q4km", "type": "gguf", "path": "models/Qwen3.5-0.8B-Q4_K_M.gguf", "sha256": "...", "bytes": 532517120, "minRamMb": 4096}],
  "signature": {"algorithm": "Ed25519", "file": "signatures/manifest.sig", "keyId": "oracle-dev-2026"}
}
```

Gerar chave de **desenvolvimento** local e pacote Lite:

```sh
python3 companion/desktop-pack-builder/pack_builder.py init-key --private .oracle-keys/dev-private.pem --public data/keys/oracle-dev-public.pem
python3 companion/desktop-pack-builder/pack_builder.py build --plan companion/desktop-pack-builder/lite-pack-plan.json --source-root . --output oracle-ssd/oracle-lite-model-dev-0.1.0 --private .oracle-keys/dev-private.pem
python3 companion/desktop-pack-builder/pack_builder.py verify --pack oracle-ssd/oracle-lite-model-dev-0.1.0 --public data/keys/oracle-dev-public.pem
```

A chave privada não deve ser publicada. Essa chave de desenvolvimento não estabelece confiança para distribuição. O Android ainda precisa de um importador próprio que contenha a chave pública confiável, confirme assinatura/hashes, use SAF com escolha do usuário, verifique RAM/espaço/API e copie arquivos atomicamente antes de ativar capacidades. Portanto, criar o pack no Mac **não o instala no aparelho**.
