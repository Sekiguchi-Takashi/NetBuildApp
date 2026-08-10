package com.appathy.netbuild;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final Scenario scenario = Scenario.office();
    private final Design design = new Design();
    private final Evaluator evaluator = new Evaluator();

    private TextView console;
    private ScrollView scroller;
    private CheckBox cbVlan;
    private CheckBox cbDmz;
    private CheckBox cbDeny;
    private CheckBox cbWideSubnet;

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

        CompoundButton.OnCheckedChangeListener sync = new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                design.guestVlan = cbVlan.isChecked();
                design.dmz = cbDmz.isChecked();
                design.fwGuestDeny = cbDeny.isChecked();
                design.prefixLength = cbWideSubnet.isChecked() ? 24 : 26;
            }
        };
        cbVlan.setOnCheckedChangeListener(sync);
        cbDmz.setOnCheckedChangeListener(sync);
        cbDeny.setOnCheckedChangeListener(sync);
        cbWideSubnet.setOnCheckedChangeListener(sync);

        ((Button) findViewById(R.id.btn_brief)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showBrief();
            }
        });
        ((Button) findViewById(R.id.btn_ask)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askClient();
            }
        });
        ((Button) findViewById(R.id.btn_rules)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                print(evaluator.describeRules(design));
            }
        });
        ((Button) findViewById(R.id.btn_review)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runReview();
            }
        });

        if (!handleSharedJson(getIntent())) {
            showBrief();
        }
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
        sb.append('\n').append("現在の設計\n  ").append(design.summary())
                .append("\n  概算 ").append(design.cost()).append(" 円\n");
        print(sb.toString());
    }

    private void askClient() {
        Scenario.Hidden next = scenario.nextUnrevealed();
        if (next == null) {
            print("これ以上の確認事項はありません。設計を評価してください。");
            return;
        }
        next.revealed = true;
        StringBuilder sb = new StringBuilder("ヒアリング\n");
        sb.append("  あなた: ").append(next.question).append('\n');
        sb.append("  顧客  : ").append(next.answer).append("\n\n");
        sb.append("  → 要求に追加: ").append(next.requirement).append('\n');
        int rest = scenario.hidden.size() - scenario.revealedCount();
        sb.append("  残りの未確認: ").append(rest).append(" 件\n");
        print(sb.toString());
    }

    private void runReview() {
        Evaluator.Result result = evaluator.evaluate(scenario, design);
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
