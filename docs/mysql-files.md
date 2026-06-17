# MySQLデータファイルメモ

## `#innodb_redo`

場所:

```text
demo/mysql-data/#innodb_redo
```

内容:

- MySQL 8.4 のInnoDB redoログ領域です。
- `#ib_redo...` ファイルはMySQLが自動管理します。
- 現在は32ファイル、合計 `104857600` bytes です。
- この合計は `innodb_redo_log_capacity = 104857600` と一致しています。

注意:

- 手動で結合、移動、削除しないでください。
- MySQL起動中に触るとDB破損の原因になります。
- バックアップ対象に含める場合は、MySQLを停止してから `demo/mysql-data` 全体をコピーしてください。

## 起動中プロセス

`demo/mysql-data/OCS5422.pid` が現在のMySQLプロセスIDです。
同じ `--datadir=demo/mysql-data` の `mysqld.exe` が複数起動している場合は危険なので、PIDファイルと `netstat` で待受中のプロセスを確認してください。
