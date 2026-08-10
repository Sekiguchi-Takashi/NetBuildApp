package com.appathy.netbuild;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final Scenario scenario = Scenario.office();
    private final Design design = new Design();
    private final Evaluator evaluator = new Evaluator();
    private final IncidentEngine incidents = new IncidentEngine();
    private final Diagnostics diagnostics = new Diagnostics();
    private final GameState state = new GameState();

    private TextView console;
    private ScrollView scroller;
    private CheckBox cbVlan;
    private CheckBox cbDmz;
    private CheckBox cbDeny;
    private CheckBox cbWideSubnet;

    private Incident current;
    private boolean loading = true;

    private TopologyView topology;
    private ImageView charaSmall;
    private ImageView chara;
    private TextView tvDay;
    private TextView tvTrust;
    private TextView tvCost;
    private TextView tvIncident;
    private TextView tvBubble;
    private View panelDesign;
    private View panelClient;
    private View panelOps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        console = findViewById(R.id.console);
        scroller = findViewById(R.id.scroller);
        cbVlan = findViewById(R.id.cb_vlan);
        cbDmz = findViewById(R.id.cb_dmz);
        cbDeny = findViewById(R.id.cb_deny);
        cbWideSubnet = findViewById(R.id.cb_subnet);
        topology = findViewById(R.id.topology);
        charaSmall = findViewById(R.id.chara_small);
        chara = findViewById(R.id.chara);
        tvDay = findViewById(R.id.tv_day);
        tvTrust = findViewById(R.id.tv_trust);
        tvCost = findViewById(R.id.tv_cost);
        tvIncident = findViewById(R.id.tv_incident);
        tvBubble = findViewById(R.id.tv_bubble);
        panelDesign = findViewById(R.id.panel_design);
        panelClient = findViewById(R.id.panel_client);
        panelOps = findViewById(R.id.panel_ops);

        current = state.load(this, design, scenario);

        loading = true;
        cbVlan.setChecked(design.guestVlan);
        cbDmz.setChecked(design.dmz);
        cbDeny.setChecked(design.fwGuestDeny);
        cbWideSubnet.setChecked(design.prefixLength <= 24);
        loading = false;

        CompoundButton.OnCheckedChangeListener sync = new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                applyDesignChange();
            }
        };
        cbVlan.setOnCheckedChangeListener(sync);
        cbDmz.setOnCheckedChangeListener(sync);
        cbDeny.setOnCheckedChangeListener(sync);
        cbWideSubnet.setOnCheckedChangeListener(sync);

        bind(R.id.btn_brief, new Runnable() {
            public void run() {
                showBrief();
            }
        });
        bind(R.id.btn_ask, new Runnable() {
            public void run() {
                askClient();
            }
        });
        bind(R.id.btn_rules, new Runnable() {
            public void run() {
                print(evaluator.describeRules(design));
            }
        });
        bind(R.id.btn_review, new Runnable() {
            public void run() {
                runReview();
            }
        });
        bind(R.id.btn_next, new Runnable() {
            public void run() {
                advanceDay();
            }
        });
        bind(R.id.btn_diag, new Runnable() {
            public void run() {
                chooseCommand();
            }
        });
        bind(R.id.btn_answer, new Runnable() {
            public void run() {
                chooseCause();
            }
        });

        bind(R.id.tab_design, new Runnable() {
            public void run() {
                showPanel(0);
            }
        });
        bind(R.id.tab_client, new Runnable() {
            public void run() {
                showPanel(1);
            }
        });
        bind(R.id.tab_ops, new Runnable() {
            public void run() {
                showPanel(2);
            }
        });

        findViewById(R.id.btn_brief).setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                confirmReset();
                return true;
            }
        });

        refresh();
        if (!handleSharedJson(getIntent())) {
            showBrief();
        }
    }

    private void showPanel(int index) {
        panelDesign.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelClient.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelOps.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    /** ステータス盤の更新。設計と障害の状態をそのまま図と帯に反映する。 */
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
        charaSmall.setImageResource(face);
        chara.setImageResource(face);
    }

    private void say(String line) {
        tvBubble.setText(line);
    }

    private void bind(int id, final Runnable action) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                action.run();
            }
        });
    }

    /** 稼働開始後の設計変更は割増になる。後から直すほど高くつく。 */
    private void applyDesignChange() {
        if (loading) {
            return;
        }
        int before = design.cost();
        design.guestVlan = cbVlan.isChecked();
        design.dmz = cbDmz.isChecked();
        design.fwGuestDeny = cbDeny.isChecked();
        design.prefixLength = cbWideSubnet.isChecked() ? 24 : 26;
        int after = design.cost();

        if (state.day > 0 && after > before) {
            int surcharge = (after - before) / 2;
            state.extraCost += surcharge;
            print("設計変更（稼働中）\n  " + design.summary()
                    + "\n\n  稼働後の改修のため割増 " + surcharge + " 円が発生\n"
                    + "  累計 " + totalCost() + " 円 / 予算 " + scenario.budget + " 円\n");
        }
        save();
    }

    private int totalCost() {
        return design.cost() + state.extraCost;
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
            StringBuilder sb = new StringBuilder("実機ネットワークのレビュー\n");
            sb.append("  ノード ").append(g.nodes.size())
                    .append(" / ルート ").append(g.routes.size())
                    .append(" / 測定 ").append(g.reachability.size()).append("\n\n");
            for (String line : lines) {
                sb.append("  ").append(line).append('\n');
            }
            sb.append("\n  同じ観点を案件モードの設計にも当てられます\n");
            print(sb.toString());
            return true;
        } catch (Exception e) {
            print("JSONを読み込めませんでした: " + e.getClass().getSimpleName());
            return true;
        }
    }

    private void showBrief() {
        StringBuilder sb = new StringBuilder();
        sb.append("案件  ").append(scenario.client).append('\n');
        sb.append("  「").append(scenario.explicitRequirement).append("」\n\n");
        sb.append("  予算 ").append(scenario.budget).append(" 円 / 現在 ")
                .append(scenario.currentUsers).append(" 人\n\n");
        sb.append("確認済みの要求\n");
        boolean any = false;
        for (Scenario.Hidden h : scenario.hidden) {
            if (h.revealed) {
                sb.append("  CONFIRMED  ").append(h.requirement).append('\n');
                any = true;
            }
        }
        if (!any) {
            sb.append("  まだありません。表面的な要求だけが分かっています\n");
        }
        int unknown = scenario.hidden.size() - scenario.revealedCount();
        if (unknown > 0) {
            sb.append("  UNKNOWN    未確認 ").append(unknown).append(" 件\n");
        }
        sb.append("\n現在の設計\n  ").append(design.summary()).append('\n');
        sb.append("  費用 ").append(totalCost()).append(" 円");
        if (state.extraCost > 0) {
            sb.append("（後追い改修の割増 ").append(state.extraCost).append(" 円を含む）");
        }
        sb.append('\n');
        sb.append("\n運用  Day ").append(state.day).append(" / 信頼 ").append(state.trust).append('\n');
        if (current != null && !current.resolved) {
            sb.append("  対応中の障害があります\n");
        }
        if (!state.occurredFaults.isEmpty()) {
            sb.append("  過去に設計起因の障害 ").append(state.occurredFaults.size()).append(" 種\n");
        }
        sb.append("\n  （案件ボタンを長押しで最初からやり直せます）\n");
        say("「" + scenario.explicitRequirement + "」");
        print(sb.toString());
    }

    private void askClient() {
        Scenario.Hidden next = scenario.nextUnrevealed();
        if (next == null) {
            print("これ以上の確認事項はありません。設計を評価してください。");
            return;
        }
        next.revealed = true;
        say("「" + next.answer + "」");
        save();
        StringBuilder sb = new StringBuilder("ヒアリング\n");
        sb.append("  あなた: ").append(next.question).append('\n');
        sb.append("  顧客  : ").append(next.answer).append("\n\n");
        sb.append("  → 要求に追加: ").append(next.requirement).append('\n');
        int rest = scenario.hidden.size() - scenario.revealedCount();
        sb.append("  残りの未確認: ").append(rest).append(" 件\n");
        print(sb.toString());
    }

    private void advanceDay() {
        state.day++;
        if (current != null && !current.resolved) {
            state.trust -= 5;
            save();
            print("Day " + state.day + "\n  障害が未解決のままです。顧客の信頼が下がりました（信頼 "
                    + state.trust + "）\n\n" + symptomBlock());
            return;
        }
        current = incidents.nextDay(state.day, scenario, design, state.occurredFaults);
        if (current != null) {
            say("「" + current.cause.symptom + "」");
        } else {
            say("「今日は問題なく使えています」");
        }
        if (current == null) {
            state.trust = Math.min(100, state.trust + 2);
            save();
            print("Day " + state.day + "\n  障害なし。安定して稼働しています（信頼 " + state.trust + "）\n");
            return;
        }
        boolean repeat = incidents.isWeaknessCause(current.cause)
                && state.occurredFaults.contains(current.cause.name());
        save();
        print("Day " + state.day + (repeat ? "  障害発生（再発）" : "  障害発生") + "\n\n"
                + symptomBlock()
                + (repeat ? "\n  前と同じ症状です。設計を直していないので繰り返しています\n" : "")
                + "\n  「診断」で調べ、「原因回答」で答えてください\n");
    }

    private String symptomBlock() {
        if (current == null) {
            return "";
        }
        return "  顧客からの連絡\n  「" + current.cause.symptom + "」\n\n"
                + current.describeBelief();
    }

    private void chooseCommand() {
        if (current == null || current.resolved) {
            print("対応中の障害はありません。「翌日へ」で日を進めてください。");
            return;
        }
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
                        print(text);
                    }
                })
                .show();
    }

    private void chooseCause() {
        if (current == null || current.resolved) {
            print("対応中の障害はありません。");
            return;
        }
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
            sb.append("正解  ").append(current.cause.label).append('\n');
            sb.append("  対処: ").append(current.cause.fix).append('\n');
            sb.append("  診断 ").append(current.log.size()).append(" 回で特定（信頼 +")
                    .append(bonus).append(" → ").append(state.trust).append("）\n");
            if (incidents.isWeaknessCause(current.cause)) {
                state.occurredFaults.add(current.cause.name());
                sb.append("\n  この障害は設計の弱点が原因です\n");
                sb.append("  設計を直さない限り再発率が上がります。直す場合は稼働後の割増がかかります\n");
            }
        } else {
            state.trust -= 8;
            sb.append("不正解  実際の原因は別にあります\n");
            sb.append("  誤った対処は状況を悪化させます（信頼 ").append(state.trust).append("）\n\n");
            sb.append(current.describeBelief());
        }
        save();
        print(sb.toString());
    }

    private void runReview() {
        Evaluator.Result result = evaluator.evaluate(scenario, design, state.extraCost);
        StringBuilder sb = new StringBuilder("設計レビュー\n  ").append(design.summary()).append("\n\n");
        for (Evaluator.Finding f : result.findings) {
            sb.append("  [").append(f.level).append("] ").append(f.title).append('\n');
            sb.append("      ").append(f.detail).append('\n');
        }
        sb.append("\nスコア\n");
        sb.append("  要求適合  ").append(sign(result.requirementScore)).append('\n');
        sb.append("  セキュリティ ").append(sign(result.securityScore)).append('\n');
        sb.append("  拡張性    ").append(sign(result.scalabilityScore)).append('\n');
        sb.append("  コスト    ").append(sign(result.costScore))
                .append("  (").append(result.cost).append(" 円)\n");
        sb.append("  ------------------------\n");
        sb.append("  案件適合度 ").append(sign(result.fitness())).append('\n');
        print(sb.toString());
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
                        loading = true;
                        cbVlan.setChecked(false);
                        cbDmz.setChecked(false);
                        cbDeny.setChecked(false);
                        cbWideSubnet.setChecked(false);
                        loading = false;
                        refresh();
                        showBrief();
                    }
                })
                .show();
    }

    private void save() {
        state.save(this, design, scenario, current);
        refresh();
    }

    private String sign(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private void print(String text) {
        console.setText(text);
        scroller.post(new Runnable() {
            public void run() {
                scroller.fullScroll(View.FOCUS_UP);
            }
        });
    }
}
