# NeoForge 1.21.1 backport patches

このディレクトリは **NeoForge 1.21.1 で Hydraulic を動かすためのパッチ一式**です。

## 構成

| ファイル | 内容 |
|---|---|
| `geyser-2.4.4.patch` | Geyser 2.4.4（commit d61ad7b）へのパッチ。カスタムブロック登録の範囲外クラッシュ修正 |
| `creative-1.7.0.patch` | creative 1.7.0 へのパッチ。1.21.4形式のデータ（名前空間付きatlas型・カスタムdisplay型）を安全に処理 |
| `inject-geyser-patch.py` | 公式 Geyser-NeoForge jar にパッチ済みクラスを注入するスクリプト |

## 背景

- Hydraulic は upstream が 1.21.4 以降のみ対応（1.21.1 は存在しない）
- 1.21.1 で動かすには `backport/1.21.1` ブランチ（1.21.4 ベースの API ダウングレード済み）を使う
- Geyser-NeoForge 2.4.4-b705 は 1.21.1 対応の最終版だが、Hydraulic が登録する
  カスタムブロック（バニラ範囲外の runtime ID）でクラッシュするためパッチが必要

## Geyser パッチの適用（公式jarへの注入）

```bash
# 1. Geyser ソースを取得（d61ad7b = 2.4.4-b705 と同一）
git clone https://github.com/GeyserMC/Geyser.git
cd Geyser && git checkout d61ad7b

# 2. パッチ適用
git apply /path/to/Hydraulic/patches/geyser-2.4.4.patch

# 3. core のみコンパイル（JDK 21）
./gradlew :core:compileJava

# 4. 公式 jar に注入
python /path/to/Hydraulic/patches/inject-geyser-patch.py \
    Geyser-NeoForge.jar \
    core/build/classes/java/main \
    geyser-neoforge-2.4.4-b705.jar
```

## creative パッチの適用

```bash
git clone https://github.com/unnamed/creative.git
cd creative && git checkout v1.7.0
git apply /path/to/Hydraulic/patches/creative-1.7.0.patch

# JDK 17 でビルドして mavenLocal に公開
./gradlew :creative-serializer-minecraft:publishToMavenLocal -PnoSigning -x test

# Gradle キャッシュに上書き（キャッシュ優先のため）
cp ~/.m2/repository/team/unnamed/creative-serializer-minecraft/1.7.0/creative-serializer-minecraft-1.7.0.jar \
   ~/.gradle/caches/modules-2/files-2.1/team.unnamed/creative-serializer-minecraft/1.7.0/*/creative-serializer-minecraft-1.7.0.jar
```

## サーバー起動オプション

- JDK 21（NeoForge 1.21.1 は Java 21）
- `user_jvm_args.txt` に `-DGeyser.ShowResourcePackLengthWarning=false` 推奨
- `server.properties` の `max-tick-time=-1`（初回の大量変換で Watchdog が発動するため）

## 実績（2026-08-13）

- Twilight Forest 1.21.1 (4.8.3345) で検証:
  - 17,255 non-vanilla block overrides / 523 custom blocks / 660 custom items
  - ERROR 0 / WARN 3（NeoForge 本体の union スキーマ 2 + ターミナル 1 のみ）
  - Bedrock ping 応答確認（プロトコル 748）
