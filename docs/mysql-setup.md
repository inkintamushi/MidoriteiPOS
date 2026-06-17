# MySQLセットアップ

## 1. データベース作成

MySQLにログインして、アプリ用DBを作成します。

```sql
CREATE DATABASE IF NOT EXISTS midoritei
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

## 2. 接続設定

デフォルト起動は内蔵H2 DBを使うため、MySQLなしで動きます。
MySQLを使う場合だけ、`mysql` プロファイルで起動します。

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/midoritei?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Tokyo&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_password"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

未設定の場合は、ユーザー `root`、パスワード空、DB `midoritei` に接続します。

MySQLを使わず単体起動する場合は、通常どおり起動します。

```powershell
.\mvnw.cmd spring-boot:run
```

## 3. 初期データ

アプリ起動時に以下が自動作成・投入されます。

- 従業員ログイン: `staff` / `1234`（DB上はSHA-256ハッシュ保存）
- 商品カテゴリ
- 初期商品
- 卓番号 1-9

テーブル定義は `demo/src/main/resources/schema.sql`、初期データは `demo/src/main/resources/data.sql` です。
