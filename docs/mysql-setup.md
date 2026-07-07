# MySQLセットアップ

## 1. データベース作成

MySQLにログインして、アプリ用DBを作成します。

```sql
CREATE DATABASE IF NOT EXISTS midoritei
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

## 2. 接続設定

`demo/src/main/resources/application.properties` は `MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` を見ます。設定方法は2通りあります。

### 方法A: `demo/.env` ファイル(推奨、ターミナルを開き直しても再設定不要)

```powershell
cd demo
cp .env.example .env
# .env を開いて値を書き換える
```

`.env` は git 管理対象外です(`demo/.gitignore` 参照)。

### 方法B: 環境変数

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/midoritei?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Tokyo&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_password"
```

環境変数は `.env` より優先されます。どちらも未設定の場合は、ユーザー `root`、パスワード空、DB `midoritei` に接続します。

## 3. 初期データ

アプリ起動時に以下が自動作成・投入されます。

- 従業員ログイン: `staff` / `1234`（DB上はSHA-256ハッシュ保存）
- 商品カテゴリ
- 初期商品
- 卓番号 1-9

テーブル定義は `demo/src/main/resources/schema.sql`、初期データは `demo/src/main/resources/data.sql` です。
