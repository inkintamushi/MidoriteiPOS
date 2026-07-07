# MidoriteiPOS

居酒屋「みどり亭」向けモバイルオーダーシステム(MOS: Mobile Ordering System)。
利用客のスマートフォンからの注文・店員呼出と、従業員側の卓管理・注文管理・商品管理をWebアプリとして提供する。

アプリ本体は `demo/` ディレクトリ配下の Spring Boot プロジェクト。設計ドキュメント一式は `docs/` を参照。

## 技術スタック

| 項目 | 内容 |
|---|---|
| 言語 | Java 23 |
| フレームワーク | Spring Boot 4.1.0 (Web MVC, JDBC, Thymeleaf) |
| DB | MySQL 8 (本番) / H2 (ローカル・テスト、MySQL互換モード) |
| ビルド | Maven (Wrapper同梱: `mvnw` / `mvnw.cmd`) |
| DBアクセス | `JdbcTemplate` (ORM未使用。スキーマは `schema.sql`、初期データは `data.sql` で管理) |

## 動作環境

- JDK 23 が必要(`demo/pom.xml` の `java.version` が 23 指定)。
  - `mvnw -version` で使われる Java のバージョンを確認できる。23 未満の JDK がデフォルトになっている場合は、実行時に一時的に `JAVA_HOME` を JDK 23 に向ける。
    ```powershell
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
    ```
- Maven は Wrapper 同梱のため別途インストール不要。

## 起動方法

### 1. ローカルで動かす(H2、DB準備不要・最も簡単)

`local` プロファイルを使うと、インメモリDB(H2、MySQL互換モード)にアプリ起動のたびにスキーマと初期データが作成される。

```powershell
cd demo
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

起動後 http://localhost:8080/login.html にアクセス。H2コンソール(`http://localhost:8080/h2-console`、JDBC URL: `jdbc:h2:mem:midoritei-local`)でテーブルの中身を直接確認できる。

### 2. MySQLに接続して動かす(本番相当)

事前に MySQL のセットアップが必要。手順は [`mysql-setup.md`](mysql-setup.md) を参照(DB作成など)。

接続情報(`MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD`)は `demo/.env` ファイルで設定できる。毎回 `$env:` で環境変数をセットし直す必要がない。

```powershell
cd demo
cp .env.example .env
# .env を開いて MYSQL_PASSWORD などを実際の値に書き換える
```

`.env` は git 管理対象外(`demo/.gitignore` 参照)。未作成の場合や項目が空の場合は、`root` ユーザー・パスワード空・DB名 `midoritei` で `localhost:3306` に接続する。従来どおり `$env:MYSQL_PASSWORD` などの環境変数でも設定可能で、その場合は環境変数が `.env` より優先される。

```powershell
cd demo
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
.\mvnw.cmd spring-boot:run
```

### ログイン情報(共通)

| 項目 | 値 |
|---|---|
| 店員ID | `staff` |
| パスワード | `1234` |

起動時に `data.sql` から自動投入される。DB上はSHA-256ハッシュで保存。

### ポートを変更したい場合

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--server.port=8081"
```

既に8080番を使うプロセスが起動している場合など。

### 終了方法

`.\mvnw.cmd spring-boot:run` を実行しているターミナルで `Ctrl+C` を押すとアプリが停止する。

バックグラウンドで起動した、またはターミナルを閉じてしまいプロセスだけ残っている場合は、ポート8080を使っているプロセスを探して停止する。

```powershell
# ポート8080を使っているプロセスのPIDを確認
netstat -ano | findstr :8080

# 該当PIDを停止(<PID> は上記で確認した番号に置き換える)
Stop-Process -Id <PID> -Force
```

## プロジェクト構成

```
MidoriteiPOS/
├── demo/                    Spring Bootアプリ本体
│   ├── .env.example         MySQL接続用の環境変数テンプレート(.envとしてコピーして使う)
│   ├── src/main/java/com/example/demo/   Controller等
│   ├── src/main/resources/
│   │   ├── schema.sql / data.sql          DBスキーマ・初期データ
│   │   ├── application.properties         本番用(MySQL)設定
│   │   ├── application-local.properties   local プロファイル(H2)設定
│   │   ├── static/                        CSS/JS
│   │   └── templates/                     Thymeleafテンプレート(画面)
│   └── src/test/                          テスト(H2、testプロファイル)
├── docs/                    要件定義書・基本設計書・詳細設計(DB設計等)
├── mysql-setup.md           MySQL接続のセットアップ手順
└── screen-transition.md     画面遷移図(Mermaid)
```

## 参考ドキュメント

- [`mysql-setup.md`](mysql-setup.md) — MySQL接続のセットアップ手順
- [`screen-transition.md`](screen-transition.md) — 画面(HTML)間の遷移図
- [`docs/要件定義書/`](docs/要件定義書/) — 要件定義書
- [`docs/基本設計書/`](docs/基本設計書/) — 基本設計書、機能一覧、画面設計、E-R図
- [`docs/詳細設計/`](docs/詳細設計/) — DB詳細設計(`schema.sql` の元になっている設計書)
