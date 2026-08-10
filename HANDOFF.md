# NetBuildApp HANDOFF

## 位置づけ

ネットワーク構築ゲーム本体（学習目的）。実機測定アプリ **RouteHQApp** とは別リポジトリで、
共有するのは `NetGraph` スキーマ（schema: netgraph/1）のみ。

- RouteHQApp = 実端末の状態を NetGraph にする
- NetBuildApp = ゲーム内の設計を NetGraph にする + どちらも同じ `RuleEngine` で評価する

RouteHQApp の「JSON共有」から本アプリを選ぶと、実測データがそのまま評価対象になる
（`ACTION_SEND` / text/plain のインテントフィルタで受信）。

## 現在のバージョン

v0.1

## 実装済み

- `NetGraph` — 共通スキーマ。`fromJson` で RouteHQApp の出力を読める
- `FirewallRule` — source/dest ゾーン、protocol、port、action、優先順位。先にマッチしたルールが勝つ（MD §14）
- `RuleEngine` — BFS による `canReach(src, dst)`（MD §13）
  - L2スイッチは VLAN が一致する場合のみ中継（MD §16）
  - Firewall ノード通過時にゾーン間ルールを評価。どれにも当たらなければ暗黙 Deny
- `Design` — プレイヤーの選択（来客VLAN分離 / DMZ / 来客Denyルール / サブネット幅）から構成グラフと FWルールを生成。コスト計算つき
- `Scenario` — 案件1件（サンプル商事）。表面要求と隠れた要求3件を分離して保持（MD §8 / §9）
- `Evaluator` — 設計レビュー（MD §30）と案件適合度スコア（MD §32）
- `DeviceReview` — 実測 NetGraph に対する指摘（デフォルトルート、DNS冗長性、VPN、キャプティブポータル、IP収容数、切り分け）

## 検証済みの挙動

| VLAN分離 | DMZ | 来客→社内PC | インターネット→社内PC |
|---|---|---|---|
| なし | なし | 到達（危険） | 到達（危険） |
| あり | なし | 遮断 | 到達（危険） |
| なし | あり | 到達（危険） | 遮断 |
| あり | あり | 遮断 | 遮断 |

来客Denyルールだけを入れて VLAN 分離をしない場合、通信はそもそも Firewall を通らないため
ルールは効かない。この場合は「効いていない」ことを指摘として表示する。

## 次の候補（v0.2 以降）

1. 障害生成（MD §25）— 設計の弱点から時間差で障害を起こす。現状の Evaluator の Finding をそのまま種にできる
2. 診断コマンド（MD §27）— ゲーム内で ping / traceroute を打ち、候補確率を更新する
3. 顧客性格（MD §7）— 同じ質問への回答を属性で変える
4. 案件の複数化と動的生成（MD §38）
5. LLM 接続は最後。会話生成だけを担当させ、判定は RuleEngine のまま維持する

## ビルド

GitHub Actions（.github/workflows/build.yml）で debug APK を出力。ローカルビルドはしない。
