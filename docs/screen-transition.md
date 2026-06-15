# HTML画面遷移図

対象: `demo/src/main/resources/templates/*.html`

## 全体画面遷移

```mermaid
flowchart LR
  switch["switch.html<br/>画面切替"] --> order["order.html<br/>利用者側 注文画面"]
  switch --> orderHistory["order_history.html<br/>利用者側 注文履歴"]
  switch --> login["login.html<br/>従業員側 ログイン"]
  switch --> taku["taku.html<br/>卓選択"]
  switch --> tyuumonn["tyuumonn.html<br/>従業員側 注文"]
  switch --> rireki["rireki.html<br/>注文履歴"]
  switch --> mihaizen["mihaizen_rireki.html<br/>未配膳"]
  switch --> kyaku["kyakuannnai.html<br/>案内"]
  switch --> shouhin["shouhinnkannri.html<br/>商品管理"]
  switch --> taiou["taioujoukyou.html<br/>対応状況"]

  login -- ログイン成功 --> taku

  order -- 履歴 --> orderHistory
  orderHistory -- 注文 --> order
  orderCodex["order_codex.html<br/>Codex版 注文画面"] -- 履歴 --> orderHistory

  taku -- 未配膳タブ --> mihaizen
  taku -- 案内タブ --> kyaku
  taku -- 商品管理タブ --> shouhin
  taku -- 対応中/要対応/未対応/なし/使用中止 --> taiou
  taku -- 注文 --> tyuumonn
  taku -- 履歴 --> rireki
  taku -- QR再発行 --> order

  taiou -- 未配膳タブ --> mihaizen
  taiou -- 案内タブ --> kyaku
  taiou -- 商品管理タブ --> shouhin
  taiou -- 対応状況ボタン --> taiou
  taiou -- 注文 --> tyuumonn
  taiou -- 戻る --> taku

  tyuumonn -- 卓選択タブ --> taku
  tyuumonn -- 未配膳タブ --> mihaizen
  tyuumonn -- 案内タブ --> kyaku
  tyuumonn -- 商品管理タブ --> shouhin

  rireki -- 卓選択タブ --> taku
  rireki -- 未配膳タブ --> mihaizen
  rireki -- 案内タブ --> kyaku
  rireki -- 商品管理タブ --> shouhin

  mihaizen -- 卓選択タブ --> taku
  mihaizen -- 案内タブ --> kyaku
  mihaizen -- 商品管理タブ --> shouhin

  kyaku -- 卓選択タブ/戻る --> taku
  kyaku -- 未配膳タブ --> mihaizen
  kyaku -- 商品管理タブ --> shouhin
  kyaku -- QR発行 --> order

  shouhin -- 卓選択タブ --> taku
  shouhin -- 未配膳タブ --> mihaizen
  shouhin -- 案内タブ --> kyaku
```

## 利用者側画面

```mermaid
flowchart TD
  order["order.html<br/>注文画面"] --> orderModal["注文確認モーダル"]
  orderModal -- No --> order
  orderModal -- Yes --> complete["注文完了モーダル"]
  complete -- ok --> order
  order --> payment["会計確認モーダル"]
  payment -- No --> order
  payment -- Yes --> paymentDone["精算待機モーダル"]
  paymentDone -- ok --> order
  order --> call["店員呼出確認モーダル"]
  call -- No --> order
  call -- Yes --> callDone["店員呼出待機モーダル"]
  callDone -- ok --> order
  order --> historyConfirm["履歴確認モーダル"]
  historyConfirm -- No --> order
  historyConfirm -- Yes --> orderHistory["order_history.html"]
  orderHistory -- 注文 --> order

  orderCodex["order_codex.html<br/>Codex版 注文画面"] --> orderCodexHistory["order_history.html"]
```

## 従業員側メイン導線

```mermaid
flowchart TD
  login["login.html"] -- ログイン成功 --> taku["taku.html<br/>卓選択"]

  taku -- 卓状態 --> taiou["taioujoukyou.html<br/>対応状況"]
  taku -- 注文 --> tyuumonn["tyuumonn.html<br/>従業員側注文"]
  taku -- 履歴 --> rireki["rireki.html<br/>注文履歴"]
  taku -- 再発行 --> qr["QRモーダル"]
  qr -- 閉じる --> taku
  qr -- QRリンク --> order["order.html<br/>利用者側注文"]

  taku -- 未配膳 --> mihaizen["mihaizen_rireki.html"]
  taku -- 案内 --> kyaku["kyakuannnai.html"]
  taku -- 商品管理 --> shouhin["shouhinnkannri.html"]

  taiou -- 注文 --> tyuumonn
  taiou -- 戻る --> taku
  tyuumonn -- 卓選択 --> taku
  rireki -- 卓選択 --> taku
  mihaizen -- 卓選択 --> taku
  kyaku -- 戻る/卓選択 --> taku
  shouhin -- 卓選択 --> taku
```

## 各HTML内の画面・モーダル遷移

### `kyakuannnai.html`

```mermaid
flowchart TD
  top["s-top<br/>案内トップ"] --> annaiInput["s-annai-input<br/>新規案内入力"]
  top --> idouSelect["s-idou-select<br/>座席移動選択"]
  top -- 戻る --> taku["taku.html"]

  annaiInput -- No --> annaiInput
  annaiInput -- Yes --> annaiConfirm["案内確認"]
  annaiConfirm -- No --> annaiInput
  annaiConfirm -- Yes --> qr["s-qr<br/>QR表示"]
  qr -- 印刷 --> top
  qr -- QRリンク --> order["order.html"]

  idouSelect --> idouInput["s-idou-input<br/>卓移動入力"]
  idouSelect --> koukanInput["s-koukan-input<br/>卓交換入力"]
  idouSelect -- 戻る --> top

  idouInput -- No --> idouSelect
  idouInput -- Yes --> idouDone["卓移動完了"]
  idouDone -- Ok --> top

  koukanInput -- No --> idouSelect
  koukanInput -- Yes --> koukanDone["卓交換完了"]
  koukanDone -- Ok --> top
```

### `tyuumonn.html`

```mermaid
flowchart TD
  main["screen-main<br/>カテゴリ一覧"] --> yaki["screen-yaki<br/>焼き鳥商品一覧"]
  main --> qr["QR再発行モーダル"]
  yaki --> confirm["注文確定モーダル"]
  yaki -- 戻る --> main
  confirm -- No --> yaki
  confirm -- Yes --> done["注文完了モーダル"]
  done -- ok --> main
  qr -- 印刷 --> main
```

### `shouhinnkannri.html`

```mermaid
flowchart TD
  list["screen-list<br/>カテゴリ一覧"] --> category["screen-category<br/>商品一覧"]
  list --> addCategory["カテゴリー追加"]
  category --> editConfirm["商品管理確認"]
  category --> addProduct["商品追加"]
  category -- 戻る --> list

  editConfirm -- No --> category
  editConfirm -- Yes --> editDone["商品管理完了"]
  editDone -- ok --> list

  addProduct -- No --> category
  addProduct -- Yes --> addDone["商品追加完了"]
  addDone -- ok --> list

  addCategory -- No --> list
  addCategory -- Yes --> categoryDone["カテゴリー追加完了"]
  categoryDone -- ok --> list
```

### `rireki.html`

```mermaid
flowchart TD
  list["注文履歴一覧"] --> detail["履歴詳細/編集モーダル"]
  detail --> haizen["配膳取り消し確認"]
  detail --> cancel["注文キャンセル確認"]
  detail -- 戻る --> list
  haizen -- No --> list
  haizen -- Yes --> haizenDone["配膳取り消し完了"]
  haizenDone -- ok --> list
  cancel -- No --> list
  cancel -- Yes --> cancelDone["注文キャンセル完了"]
  cancelDone -- ok --> list
```

### `mihaizen_rireki.html`

```mermaid
flowchart TD
  list["未配膳注文一覧"] --> confirm["配膳確認モーダル"]
  confirm -- No --> list
  confirm -- Yes --> done["配膳完了モーダル"]
  done -- ok --> list
```

### `taioujoukyou.html`

```mermaid
flowchart TD
  list["対応状況一覧"] --> status["対応状況変更モーダル"]
  status -- ok --> list
  list -- 対応中/要対応/未対応 --> list
  list -- 注文 --> tyuumonn["tyuumonn.html"]
  list -- 戻る --> taku["taku.html"]
```

## 備考

- `/` と `/switch` は `switch.html` を表示します。
- `/order` と `/order.html` は `order.html` を表示します。
- `/order_codex` と `/order_codex.html` は `order_codex.html` を表示します。
- `/order_history` と `/order_history.html` は `order_history.html` を表示します。
- `order_codex.html` は `order.html` のコピー版として追加されたUI改善版です。
