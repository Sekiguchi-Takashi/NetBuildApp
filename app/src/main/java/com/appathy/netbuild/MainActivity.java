package com.appathy.netbuild;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 画面に出しっぱなしにするのは図・状態帯・2人のキャラだけ。
 * 操作は「自社の社員」「クライアント」「図の機器と配線」を触って呼び出す。
 */
public class MainActivity extends AppCompatActivity {

    private final Scenario scenario = Scenario.office();
    private final Design design = new Design();
    private final Evaluator evaluator = new Evaluator();
    private final IncidentEngine incidents = new IncidentEngine();
    private final Diagnostics diagnostics = new Diagnostics();
    private final GameState state = new GameState();

    private TopologyView topology;
    private ImageView charaStaff;
    private ImageView charaClient;
    private TextView tvDay;
    private TextView tvTrust;
    private TextView tvCost;
    private TextView tvIncident;
    private TextView tvBubble;
    private TextView tvHint;

    private Incident current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        topology = findViewById(R.id.topology);
        charaStaff = findViewById(R.id.chara_staff);
        charaClient = findViewById(R.id.chara_client);
        tvDay = findViewById(R.id.tv_day);
        tvTrust = findViewById(R.id.tv_trust);
        tvCost = findViewById(R.id.tv_cost);
        tvIncident = findViewById(R.id.tv_incident);
        tvBubble = findViewById(R.id.tv_bubble);
        tvHint = findViewById(R.id.tv_hint);

        current = state.load(this, design, scenario);

        charaStaff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                staffMenu();
            }
        });
        charaClient.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clientMenu();
            }
        });
        charaStaff.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                confirmReset();
                return true;
            }
        });

        topology.setOnPickListener(new TopologyView.OnPickListener() {
            public void onNodePicked(String id) {
                nodeMenu(id);
            }

            public void onLinkPicked(String a, String b, String kind) {
                linkMenu(a, b, kind);
            }
        });

        refresh();
        if (!handleSharedJson(getIntent())) {
            say("「" + scenario.explicitRequirement + "」");
        }
    }

    // ------------------------------------------------------------------
    // 自社の社員 — 行動の選択
    // ------------------------------------------------------------------

    private void staffMenu() {
        final List<String> actions = new ArrayList<>();
        final List<Runnable> handlers = new ArrayList<>();

        actions.add("翌日へ進める");
        handlers.add(new Runnable() {
            public void run() {
                advanceDay();
            }
        });

        if (current != null && !current.resolved) {
            actions.add("障害を診断する");
            handlers.add(new Runnable() {
                public void run() {
                    chooseCommand();
                }
            });
            actions.add("原因を報告する");
            handlers.add(new Runnable() {
                public void run() {
                    chooseCause();
                }
            });
        }

        actions.add("設計をレビューする");
        handlers.add(new Runnable() {
            public void run() {
                runReview();
            }
        });
        actions.add("Firewallルールを確認する");
        handlers.add(new Runnable() {
            public void run() {
                show("Firewallルール", evaluator.describeRules(design));
            }
        });
        actions.add("案件の状況を確認する");
        handlers.add(new Runnable() {
            public void run() {
                showBrief();
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("何をしますか")
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        handlers.get(which).run();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    // クライアント — 要望・個性・ヒント
    // ------------------------------------------------------------------

    private void clientMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append(scenario.client).append('\n');
        sb.append("「").append(scenario.explicitRequirement).append("」\n\n");
        sb.append("予算 ").append(scenario.budget / 10000).append(" 万円 / 現在 ")
                .append(scenario.currentUsers).append(" 人\n\n");

        sb.append("[ 性格 ]\n").append(scenario.personality).append("\n\n");

        sb.append("[ 確認できている要望 ]\n");
        boolean any = false;
        for (Scenario.Hidden h : scenario.hidden) {
            if (h.revealed) {
                sb.append("・").append(h.requirement).append('\n');
                any = true;
            }
        }
        if (!any) {
            sb.append("まだ表面的な一言しか聞けていません\n");
        }

        int unknown = scenario.hidden.size() - scenario.revealedCount();
        if (unknown > 0) {
            sb.append("\n[ 気づいたこと（未確認 ").append(unknown).append(" 件）]\n");
            for (Scenario.Hidden h : scenario.hidden) {
                if (!h.revealed) {
                    sb.append("・").append(h.hint).append('\n');
                }
            }
            sb.append("\n聞けば要望として確定します");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("クライアント")
                .setMessage(sb.toString())
                .setNegativeButton("閉じる", null);
        if (unknown > 0) {
            builder.setPositiveButton("聞いてみる", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    askClient();
                }
            });
        }
        builder.show();
    }

    private void askClient() {
        Scenario.Hidden next = scenario.nextUnrevealed();
        if (next == null) {
            return;
        }
        next.revealed = true;
        say("「" + next.answer + "」");
        save();
        show("ヒアリング", "あなた: " + next.question + "\n\n"
                + scenario.client + ": " + next.answer + "\n\n"
                + "→ 要望に追加: " + next.requirement);
    }

    // ------------------------------------------------------------------
    // 図の機器と配線 — 触って設計を変える
    // ------------------------------------------------------------------

    private void nodeMenu(String id) {
        if ("sw".equals(id) || "guest".equals(id)) {
            toggleDialog("スイッチ / 来客セグメント",
                    "来客をVLANで分離するかどうか。分離しない場合、来客端末は社員PCと同じL2に載ります。\n\n現在: "
                            + (design.guestVlan ? "VLAN分離あり" : "分離なし"),
                    design.guestVlan ? "分離をやめる（-9万円）" : "VLANで分離する（+9万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.guestVlan = !design.guestVlan;
                                }
                            });
                        }
                    });
        } else if ("web".equals(id)) {
            toggleDialog("Webサーバー",
                    "公開サーバーの置き場所。内部セグメントに置くと、外部公開のために内部への穴を開けることになります。\n\n現在: "
                            + (design.dmz ? "DMZに設置" : "内部セグメントに設置"),
                    design.dmz ? "内部に戻す（-26万円）" : "DMZに移す（+26万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.dmz = !design.dmz;
                                }
                            });
                        }
                    });
        } else if ("fw".equals(id)) {
            toggleDialog("Firewall",
                    evaluator.describeRules(design) + "\n来客Denyルール: "
                            + (design.fwGuestDeny ? "あり" : "なし"),
                    design.fwGuestDeny ? "来客Denyルールを外す" : "来客Denyルールを追加する",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.fwGuestDeny = !design.fwGuestDeny;
                                }
                            });
                        }
                    });
        } else if ("pc".equals(id)) {
            toggleDialog("社員PC / 内部サブネット",
                    "内部セグメントの広さ。将来の台数に足りるかどうかを決めます。\n\n現在: /"
                            + design.prefixLength + "（" + design.usableHosts() + " 台まで）",
                    design.prefixLength <= 24 ? "/26 に狭める（-6万円）" : "/24 に広げる（+6万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.prefixLength = design.prefixLength <= 24 ? 26 : 24;
                                }
                            });
                        }
                    });
        } else if ("net".equals(id)) {
            show("インターネット", "ISPからの回線。ここは設計で変えられません。\n\n"
                    + "外部から内部への到達可否は、Firewallのルールと公開サーバーの置き場所で決まります。");
        }
    }

    private void linkMenu(String a, String b, String kind) {
        String title;
        String body;
        if ("guest".equals(kind)) {
            title = "来客端末 — スイッチ";
            body = design.guestVlan
                    ? "VLAN 20 のアクセスポートです。社員セグメントとはL2で分離されています。"
                    : "社員PCと同じVLAN 10 に載っています。この配線のままでは、Firewallのルールを足しても来客の通信は社内に届きます。";
        } else if ("server".equals(kind)) {
            title = "Webサーバー — スイッチ";
            body = design.dmz
                    ? "DMZ（VLAN 30）に収容されています。外部からは443のみ許可されます。"
                    : "内部セグメントに直結しています。外部公開するには内部への穴が必要になります。";
        } else if ("uplink".equals(kind)) {
            title = "スイッチ — Firewall";
            body = "内部と外部の境界です。ここが切れると社内通信は生きたままインターネットだけ落ちます。";
        } else if ("wan".equals(kind)) {
            title = "Firewall — インターネット";
            body = "WAN回線です。ここが切れるとゲートウェイまでは到達できますが外部には出られません。";
        } else {
            title = "社員PC — スイッチ";
            body = "内部セグメント（VLAN 10）の配線です。";
        }
        show(title, body + "\n\n配線そのものは、つながっている機器を触ると変えられます。");
    }

    /** 稼働開始後の設計変更は割増になる。 */
    private void changeDesign(Runnable mutation) {
        int before = design.cost();
        mutation.run();
        int after = design.cost();
        String extra = "";
        if (state.day > 0 && after > before) {
            int surcharge = (after - before) / 2;
            state.extraCost += surcharge;
            extra = "\n\n稼働後の改修のため割増 " + surcharge + " 円が発生しました";
        }
        save();
        show("設計変更", design.summary() + "\n\n費用 " + totalCost() + " 円 / 予算 "
                + scenario.budget + " 円" + extra);
    }

    // ------------------------------------------------------------------
    // 運用
    // ------------------------------------------------------------------

    private void advanceDay() {
        state.day++;
        if (current != null && !current.resolved) {
            state.trust -= 5;
            save();
            show("Day " + state.day, "障害が未解決のままです。顧客の信頼が下がりました。\n\n信頼 "
                    + state.trust + "\n\n" + current.describeBelief());
            return;
        }
        current = incidents.nextDay(state.day, scenario, design, state.occurredFaults);
        if (current == null) {
            state.trust = Math.min(100, state.trust + 2);
            say("「今日は問題なく使えています」");
            save();
            return;
        }
        boolean repeat = incidents.isWeaknessCause(current.cause)
                && state.occurredFaults.contains(current.cause.name());
        say("「" + current.cause.symptom + "」");
        save();
        show("Day " + state.day + (repeat ? " 障害発生（再発）" : " 障害発生"),
                "「" + current.cause.symptom + "」\n\n"
                        + (repeat ? "前と同じ症状です。設計を直していないので繰り返しています。\n\n" : "")
                        + current.describeBelief()
                        + "\n社員を押して診断してください");
    }

    private void chooseCommand() {
        final Diagnostics.Command[] commands = Diagnostics.Command.values();
        String[] labels = new String[commands.length];
        for (int i = 0; i < commands.length; i++) {
            labels[i] = commands[i].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("診断コマンド")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String text = diagnostics.run(current, commands[which]);
                        save();
                        show("診断結果", text);
                    }
                })
                .show();
    }

    private void chooseCause() {
        final List<Incident.Cause> candidates = incidents.candidates(scenario, design);
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = candidates.get(i).label;
        }
        new AlertDialog.Builder(this)
                .setTitle("原因はどれですか")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        answerCause(candidates.get(which));
                    }
                })
                .show();
    }

    private void answerCause(Incident.Cause answer) {
        StringBuilder sb = new StringBuilder();
        if (answer == current.cause) {
            current.resolved = true;
            int bonus = Math.max(4, 20 - current.log.size() * 3);
            state.trust = Math.min(100, state.trust + bonus);
            sb.append("正解: ").append(current.cause.label).append("\n\n");
            sb.append("対処: ").append(current.cause.fix).append("\n\n");
            sb.append("診断 ").append(current.log.size()).append(" 回で特定（信頼 +")
                    .append(bonus).append(" → ").append(state.trust).append("）");
            if (incidents.isWeaknessCause(current.cause)) {
                state.occurredFaults.add(current.cause.name());
                sb.append("\n\nこれは設計の弱点が原因です。図の機器を触って直さない限り再発率が上がります。");
            }
            say("「助かりました」");
        } else {
            state.trust -= 8;
            sb.append("不正解。実際の原因は別にあります。\n\n信頼 ").append(state.trust)
                    .append("\n\n").append(current.describeBelief());
            say("「まだ直っていないようですが……」");
        }
        save();
        show("原因報告", sb.toString());
    }

    private void runReview() {
        Evaluator.Result result = evaluator.evaluate(scenario, design, state.extraCost);
        StringBuilder sb = new StringBuilder(design.summary()).append("\n\n");
        for (Evaluator.Finding f : result.findings) {
            sb.append("[").append(f.level).append("] ").append(f.title).append('\n');
            sb.append("  ").append(f.detail).append("\n\n");
        }
        sb.append("要求適合 ").append(sign(result.requirementScore))
                .append(" / セキュリティ ").append(sign(result.securityScore))
                .append(" / 拡張性 ").append(sign(result.scalabilityScore))
                .append(" / コスト ").append(sign(result.costScore)).append('\n');
        sb.append("案件適合度 ").append(sign(result.fitness()));
        show("設計レビュー", sb.toString());
    }

    private void showBrief() {
        StringBuilder sb = new StringBuilder();
        sb.append("Day ").append(state.day).append(" / 信頼 ").append(state.trust).append("\n\n");
        sb.append(design.summary()).append('\n');
        sb.append("費用 ").append(totalCost()).append(" 円");
        if (state.extraCost > 0) {
            sb.append("（後追い改修の割増 ").append(state.extraCost).append(" 円を含む）");
        }
        sb.append("\n予算 ").append(scenario.budget).append(" 円\n");
        if (!state.occurredFaults.isEmpty()) {
            sb.append("\n過去に起きた設計起因の障害: ").append(state.occurredFaults.size()).append(" 種\n");
        }
        sb.append("\n（社員を長押しで最初からやり直せます）");
        show("案件の状況", sb.toString());
    }

    // ------------------------------------------------------------------

    private void refresh() {
        Incident.Cause active = current != null && !current.resolved ? current.cause : null;
        topology.update(design, active);
        tvDay.setText("Day " + state.day);
        tvTrust.setText("信頼 " + state.trust);
        tvCost.setText("費用 " + (totalCost() / 10000) + "万");
        tvIncident.setText(active == null ? "稼働中" : "障害中");

        int face = R.drawable.chara_normal;
        if (active != null) {
            face = R.drawable.chara_worry;
        }
        if (state.trust < 35) {
            face = R.drawable.chara_angry;
        }
        charaStaff.setImageResource(face);

        int unknown = scenario.hidden.size() - scenario.revealedCount();
        if (active != null) {
            tvHint.setText("図の赤い線が障害箇所です。社員を押して診断してください");
        } else if (unknown > 0) {
            tvHint.setText("クライアントを押すと、まだ聞けていない要望の手がかりが見られます");
        } else {
            tvHint.setText("図の機器や配線を押すと設計を変えられます");
        }
    }

    private void say(String line) {
        tvBubble.setText(line);
    }

    private void show(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private int totalCost() {
        return design.cost() + state.extraCost;
    }

    private void save() {
        state.save(this, design, scenario, current);
        refresh();
    }

    private String sign(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private void toggleDialog(String title, String body, String action, final Runnable onAction) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton("閉じる", null)
                .setPositiveButton(action, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onAction.run();
                    }
                })
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("最初からやり直しますか")
                .setMessage("Day・信頼・設計・障害の記録がすべて消えます")
                .setNegativeButton("やめる", null)
                .setPositiveButton("やり直す", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        state.reset(MainActivity.this);
                        current = null;
                        for (Scenario.Hidden h : scenario.hidden) {
                            h.revealed = false;
                        }
                        design.guestVlan = false;
                        design.dmz = false;
                        design.fwGuestDeny = false;
                        design.prefixLength = 26;
                        say("「" + scenario.explicitRequirement + "」");
                        refresh();
                    }
                })
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedJson(intent);
    }

    /** RouteHQApp の「JSON共有」から渡ってきた実測データを評価する。 */
    private boolean handleSharedJson(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return false;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null || !text.contains("netgraph")) {
            return false;
        }
        try {
            NetGraph g = NetGraph.fromJson(text);
            List<String> lines = new DeviceReview().review(g);
            StringBuilder sb = new StringBuilder();
            sb.append("ノード ").append(g.nodes.size())
                    .append(" / ルート ").append(g.routes.size())
                    .append(" / 測定 ").append(g.reachability.size()).append("\n\n");
            for (String line : lines) {
                sb.append("・").append(line).append('\n');
            }
            show("実機ネットワークのレビュー", sb.toString());
            return true;
        } catch (Exception e) {
            show("読み込み失敗", "JSONを解釈できませんでした: " + e.getClass().getSimpleName());
            return true;
        }
    }
}
