package com.appathy.netbuild;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 画面に出しっぱなしにするのは図・状態帯・2人のキャラだけ。
 * 操作は「自社の社員」「クライアント」「図の機器と配線」を触って呼び出す。
 */
public class MainActivity extends AppCompatActivity {

    private Scenario scenario = Scenario.office();
    private final Design design = new Design();
    private final Evaluator evaluator = new Evaluator();
    private final IncidentEngine incidents = new IncidentEngine();
    private final Diagnostics diagnostics = new Diagnostics();
    private final GameState state = new GameState();
    private final RealDiagnosis realDiagnosis = new RealDiagnosis();
    private final RealQuiz realQuiz = new RealQuiz();
    private List<RealQuiz.Question> quiz;
    private int quizIndex;
    private int quizCorrect;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TopologyView topology;
    private ImageView charaStaff;
    private ImageView charaPartner;
    private TextView tvPartnerName;
    private TextView tvPartnerRole;
    private TextView tvAllyName;
    private TextView tvAllyRole;
    private TextView tvDay;
    private TextView tvTrust;
    private TextView tvCost;
    private TextView tvIncident;
    private TextView tvBubble;
    private TextView tvHint;

    private Incident current;
    private Stakeholder speaker = Stakeholder.CLIENT;
    private Ally ally = Ally.STAFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // targetSdk 35 では既定でエッジツーエッジになるため、システムバーぶんの余白を自前で確保する
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), new OnApplyWindowInsetsListener() {
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });

        topology = findViewById(R.id.topology);
        charaStaff = findViewById(R.id.chara_staff);
        charaPartner = findViewById(R.id.chara_partner);
        tvPartnerName = findViewById(R.id.tv_partner_name);
        tvPartnerRole = findViewById(R.id.tv_partner_role);
        tvAllyName = findViewById(R.id.tv_ally_name);
        tvAllyRole = findViewById(R.id.tv_ally_role);
        tvDay = findViewById(R.id.tv_day);
        tvTrust = findViewById(R.id.tv_trust);
        tvCost = findViewById(R.id.tv_cost);
        tvIncident = findViewById(R.id.tv_incident);
        tvBubble = findViewById(R.id.tv_bubble);
        tvHint = findViewById(R.id.tv_hint);

        current = state.load(this, design, scenario);

        charaStaff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ally == Ally.NEWBIE) {
                    newbieMenu();
                } else {
                    staffMenu();
                }
            }
        });
        charaPartner.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                partnerMenu();
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
        if (state.day == 0 && scenario.revealedCount() == 0) {
            tvHint.setText("初めてなら、社員を押して「マニュアルを読む」から始めてください");
        }
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
                show("Firewallルール", evaluator.describeRules(scenario, design));
            }
        });
        actions.add("案件の状況を確認する");
        handlers.add(new Runnable() {
            public void run() {
                showBrief();
            }
        });
        actions.add("いま使っているネットワークを見る");
        handlers.add(new Runnable() {
            public void run() {
                realDiagnosisMenu();
            }
        });
        actions.add("案件を切り替える");
        handlers.add(new Runnable() {
            public void run() {
                switchScenario();
            }
        });
        actions.add("マニュアルを読む");
        handlers.add(new Runnable() {
            public void run() {
                manualMenu();
            }
        });
        actions.add("機器の説明を読む");
        handlers.add(new Runnable() {
            public void run() {
                deviceMenu();
            }
        });
        actions.add(state.easyMode ? "通常モードに戻す" : "簡単モードにする");
        handlers.add(new Runnable() {
            public void run() {
                toggleEasyMode();
            }
        });
        actions.add("新人に交代する（用語の解説）");
        handlers.add(new Runnable() {
            public void run() {
                ally = Ally.NEWBIE;
                refresh();
                newbieMenu();
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

    /** 案件ごとに前提（境界をどこに置くか）が違う。進行も案件ごとに分けて保存される。 */
    /**
     * ゲームで扱っている観点を、いま自分がつないでいるネットワークに当てる。
     * 練習と実物を同じ目線で見るための機能。
     */
    private void realDiagnosisMenu() {
        final String[] items = {
                "設定を見る（通信しない）",
                "疎通も測る（ping・名前解決）",
                "この環境で出題してもらう",
                "権限について"
        };
        new AlertDialog.Builder(this)
                .setTitle("いま使っているネットワーク")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 2) {
                            requestNetworkPermissions();
                            startRealQuiz();
                            return;
                        }
                        if (which == 3) {
                            show("権限について",
                                    "接続先のSSIDを読むには位置情報の権限が要ります。"
                                            + "Androidの仕様で、Wi-Fi情報が位置の手がかりになるためです。\n\n"
                                            + "許可しない場合もIPアドレスやルーティングは読めますが、"
                                            + "SSIDは「不明」と表示されます。\n\n"
                                            + "取得した内容はこの端末の中だけで扱います。");
                            return;
                        }
                        requestNetworkPermissions();
                        runRealDiagnosis(which == 1);
                    }
                })
                .show();
    }

    /** 測定してから、その環境に合わせた問題を出す。 */
    private void startRealQuiz() {
        show("準備中", "この環境を測ってから出題します。");
        worker.execute(new Runnable() {
            public void run() {
                final RealDiagnosis.Report report = realDiagnosis.probe(MainActivity.this);
                final List<RealQuiz.Question> made = realQuiz.build(report.graph);
                main.post(new Runnable() {
                    public void run() {
                        if (made.isEmpty()) {
                            show("出題できません",
                                    "接続情報を十分に読めませんでした。"
                                            + "ネットワークにつないだ状態で、位置情報の権限を許可してから試してください。");
                            return;
                        }
                        quiz = made;
                        quizIndex = 0;
                        quizCorrect = 0;
                        askQuizQuestion();
                    }
                });
            }
        });
    }

    private void askQuizQuestion() {
        if (quizIndex >= quiz.size()) {
            show("結果", quiz.size() + " 問中 " + quizCorrect + " 問正解でした。\n\n"
                    + (quizCorrect == quiz.size()
                    ? "いま使っているネットワークの性質は把握できています。"
                    : "間違えたところは、社員を押して「マニュアルを読む」か"
                    + "新人に交代して用語を確認してみてください。"));
            return;
        }
        final RealQuiz.Question q = quiz.get(quizIndex);
        new AlertDialog.Builder(this)
                .setTitle("第 " + (quizIndex + 1) + " 問 / " + quiz.size())
                .setMessage(q.text)
                .setItems(q.choices.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        boolean right = which == q.answer;
                        if (right) {
                            quizCorrect++;
                        }
                        quizIndex++;
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(right ? "正解" : "不正解")
                                .setMessage((right ? "" : "正解は「" + q.choices.get(q.answer) + "」です。\n\n")
                                        + q.explanation)
                                .setPositiveButton(quizIndex >= quiz.size() ? "結果を見る" : "次へ",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface d, int w) {
                                                askQuizQuestion();
                                            }
                                        })
                                .setCancelable(false)
                                .show();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void runRealDiagnosis(final boolean probe) {
        show("測定中", probe ? "疎通を確認しています。数秒かかります。" : "接続情報を読んでいます。");
        worker.execute(new Runnable() {
            public void run() {
                final RealDiagnosis.Report report = probe
                        ? realDiagnosis.probe(MainActivity.this)
                        : realDiagnosis.inspect(MainActivity.this);
                main.post(new Runnable() {
                    public void run() {
                        show(probe ? "実機の診断結果" : "実機の接続情報",
                                realDiagnosis.format(report, probe));
                    }
                });
            }
        });
    }

    private void requestNetworkPermissions() {
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 1001);
        }
    }

    private void switchScenario() {
        final List<Scenario> all = Scenario.all();
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Scenario sc = all.get(i);
            labels[i] = sc.client + "（" + sc.boundary.label + "）"
                    + (sc.id.equals(scenario.id) ? "  ← 対応中" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("案件を選ぶ")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Scenario picked = all.get(which);
                        if (picked.id.equals(scenario.id)) {
                            return;
                        }
                        scenario = picked;
                        design.customRules = null;
                        current = state.load(MainActivity.this, design, scenario);
                        refresh();
                        show(scenario.client,
                                "「" + scenario.explicitRequirement + "」\n\n"
                                        + "[ 前提 ]\n" + scenario.boundary.label + "\n"
                                        + scenario.boundary.detail + "\n\n"
                                        + "この方針は契約時に決まっています。設計で選び直すものではありません。\n\n"
                                        + "予算 " + scenario.budget + " 円");
                        say("「" + scenario.explicitRequirement + "」");
                    }
                })
                .show();
    }

    private void manualMenu() {
        final List<Manual> sections = Manual.sections();
        String[] titles = new String[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            titles[i] = sections.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle("マニュアル")
                .setItems(titles, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Manual m = sections.get(which);
                        if (which == sections.size() - 1) {
                            deviceMenu();
                            return;
                        }
                        show(m.title, m.body);
                    }
                })
                .show();
    }

    private void deviceMenu() {
        final List<DeviceGuide> guides = DeviceGuide.entries();
        String[] titles = new String[guides.size()];
        for (int i = 0; i < guides.size(); i++) {
            DeviceGuide g = guides.get(i);
            titles[i] = g.name + (DeviceGuide.PLANNED.equals(g.status) ? "（未実装）" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("機器の説明")
                .setItems(titles, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        DeviceGuide g = guides.get(which);
                        show(g.name, g.body());
                    }
                })
                .show();
    }

    /** 図の機器から、その機器の説明を開く。 */
    private void showDeviceGuide(String key) {
        DeviceGuide g = DeviceGuide.byKey(key);
        if (g != null) {
            show(g.name, g.body());
        }
    }

    private void toggleEasyMode() {
        state.easyMode = !state.easyMode;
        save();
        show(state.easyMode ? "簡単モード" : "通常モード",
                state.easyMode
                        ? "依頼者がネットワークに詳しい担当者に変わります。\n\n"
                        + "話しかけるたびに、いま何をすべきかを具体的に教えてくれます。"
                        + "言われたとおりに進めれば満点（" + evaluator.maxFitness(scenario, state.extraBudget) + " 点）に届きます。"
                        : "依頼者は通常どおり、聞かれたことにしか答えません。\n\n"
                        + "手がかりは総務担当が、障害の一次情報は現場担当が持っています。");
    }

    /** 状況に応じて、いま話す相手を決める。 */
    private Stakeholder pickSpeaker() {
        if (state.easyMode) {
            return Stakeholder.CLIENT;
        }
        if (current != null && !current.resolved) {
            return Stakeholder.IT_STAFF;
        }
        if (totalCost() > budget() || state.trust < 40) {
            return Stakeholder.BOSS;
        }
        if (guestReachesInternal()) {
            return Stakeholder.INTERN;
        }
        if (scenario.revealedCount() < scenario.hidden.size()) {
            return Stakeholder.OFFICE;
        }
        return Stakeholder.CLIENT;
    }

    /** 来客セグメントから社内に届く状態か。インターンの登場条件に使う。 */
    private boolean guestReachesInternal() {
        NetGraph g = design.buildGraph(scenario);
        if (g.find("guest") == null) {
            return false;
        }
        return new RuleEngine(design.buildRules(scenario)).canReach(g, "guest", "pc").reachable;
    }

    private void partnerMenu() {
        if (speaker == Stakeholder.BOSS) {
            bossMenu();
        } else if (speaker == Stakeholder.IT_STAFF) {
            itStaffMenu();
        } else if (speaker == Stakeholder.OFFICE) {
            officeMenu();
        } else if (speaker == Stakeholder.INTERN) {
            internMenu();
        } else {
            clientMenu();
        }
    }

    /** 総務担当。現場の運用実態を知っていて、未確認要望の手がかりを持つ。 */
    private void officeMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("日々の運用を見ている立場からお伝えします。\n\n");
        int unknown = scenario.hidden.size() - scenario.revealedCount();
        if (unknown > 0) {
            sb.append("[ 気づいていること（未確認 ").append(unknown).append(" 件）]\n");
            for (Scenario.Hidden h : scenario.hidden) {
                if (!h.revealed) {
                    sb.append("・").append(h.hint).append('\n');
                }
            }
            sb.append("\n正式な要望にするには、依頼者に確認してください。");
        } else {
            sb.append("お伝えしたいことは全部お話ししました。");
        }
        show("総務担当（先方の運用担当）", sb.toString());
    }

    /** インターン。悪気なく危ないことをする。設計の穴がそのまま被害になる。 */
    private void internMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("「来客用のWi-Fi、私も使っていいですか？ 自分のノートPCも持ってきていて……」\n\n");
        sb.append("[ いまの設計だと ]\n");
        sb.append("来客セグメントから社内PCに到達できます。");
        sb.append("悪意のない人が私物端末をつないだだけで、社内が同じネットワークに晒されます。\n\n");
        if (design.fwGuestDeny && !design.guestVlan) {
            sb.append("Firewallに来客Denyルールを入れていますが、来客と社員が同じセグメントなので通信はFirewallを通りません。");
            sb.append("止めるならスイッチ側でVLANを分ける必要があります。");
        } else {
            sb.append("図のスイッチか来客端末を押して、VLANで分離してください。");
        }
        show("インターン（先方の学生スタッフ）", sb.toString());
    }

    /** 新人。プレイヤーの代わりに素朴な質問をして、用語をその場の状態で説明する。 */
    private void newbieMenu() {
        final String[] topics = {
                "VLANって何ですか？",
                "DMZは何のためにあるんですか？",
                "Firewallの暗黙Denyって？",
                "サブネットの /24 と /26 の違いは？",
                "DNSを2台にする意味は？",
                "社内サーバーはどこに置くんですか？",
                "プロキシは何のために入れるんですか？",
                "ルールが多いと何が困るんですか？",
                "VPNって結局なんですか？",
                "保守業者の接続はどう扱うんですか？",
                "いまの設計はどこが危ないんですか？",
                "先輩に戻ってもらう"
        };
        new AlertDialog.Builder(this)
                .setTitle("新人が聞いてきた")
                .setItems(topics, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == topics.length - 1) {
                            ally = Ally.STAFF;
                            refresh();
                            return;
                        }
                        show(topics[which], explain(which));
                    }
                })
                .show();
    }

    private String explain(int index) {
        switch (index) {
            case 0:
                return "1本のスイッチを、通信できないグループに区切る仕組みです。\n\n"
                        + "いまは来客が VLAN " + (design.guestVlan ? "20、社員が VLAN 10 で分かれています。"
                        + "別々のVLANはL2では中継されないので、スイッチを通っても社内には届きません。"
                        : "10 で社員と同じです。同じVLANなので、来客の端末は社員PCと直接やりとりできます。");
            case 1:
                return "外に公開するサーバーを、社内とは別の区画に置く考え方です。\n\n"
                        + (design.dmz
                        ? "いまはWebサーバーがDMZにあります。外部から入れるのは443だけで、そこから社内へは行けません。"
                        : "いまはWebサーバーが社内と同じ区画にあります。外部公開のために社内向けの穴を開けている状態で、"
                        + "サーバーが乗っ取られると社内がそのまま危険になります。");
            case 2:
                return "どのルールにも当てはまらなかった通信は通さない、という決まりです。\n\n"
                        + "許可を書き忘れると通らない代わりに、書き忘れた危ない通信も通りません。"
                        + "ルールは上から順に見て、最初に当たったものが適用されます。";
            case 4:
                return "1台だと、そこが止まった瞬間に全社が名前解決できなくなります。\n\n"
                        + (design.dnsRedundant
                        ? "いまは2台あるので、片方が落ちてももう片方が答えます。"
                        : "いまは1台です。単一障害点というやつで、"
                        + "この状態だと「サイトが開かない」障害が起きる余地が残っています。")
                        + "\n\n止まる確率を下げるのではなく、止まっても困らない形にするのが冗長化です。";
            case 5:
                return "外に見せるサーバーと、社内だけで使うサーバーは、別の区画に置きます。\n\n"
                        + (design.serverSharedWithWeb
                        ? "いまは同じ区画にあります。公開サーバーは外から突かれる前提の場所なので、"
                        + "そこが破られると、隣にある顧客リストや図面まで同じ手が届く範囲に入ります。"
                        : "いまは分離できています。公開側が破られても、社内サーバーへは境界をもう一度越える必要があります。")
                        + "\n\n守り方の基本は、壊される前提で被害の範囲を区切ることです。";
            case 6:
                return "社内から外へ出る通信を1か所にまとめて、記録を残す装置です。\n\n"
                        + (design.proxy
                        ? "いまは導入済みです。端末が知らない宛先へ通信し始めたら、ログで気づけます。"
                        : "いまは入っていません。端末が外部と勝手に通信していても、記録がないので気づけません。"
                        + "障害が起きたときに「プロキシのログを確認」しても、"
                        + "そもそも記録が無いので何も分かりません。")
                        + "\n\n防ぐことと同じくらい、起きたときに見えることが大事です。";
            case 7:
                return "困るのは2つです。\n\n"
                        + "ひとつは過剰な許可。業務で要らない通信を通すルールは、そのまま侵入経路になります。"
                        + "「とりあえず通しておく」が一番危ないです。\n\n"
                        + "もうひとつは効かないルール。上のルールで全部拾われる位置に書くと、"
                        + "そのルールは一度も評価されません。書いた本人は守れているつもりでいるので、"
                        + "抜けているルールより見つけにくいです。\n\n"
                        + "いまのルールは " + design.buildRules(scenario).size() + " 件です。";
            case 8:
                return "公衆回線の上に、暗号化した通り道を作る仕組みです。\n\n"
                        + (design.remoteVpn
                        ? "いまは在宅の人がVPN経由で社内に入れます。"
                        : "いまは用意していないので、在宅の人は社内システムを使えません。")
                        + "\n\n気をつけるのは、つないだ端末を「社内と同じ」扱いにしてしまうことです。"
                        + "家庭で感染した端末がそのまま社内に入ります。"
                        + "VPN装置そのものの脆弱性が侵入経路になった事例も多いです。";
            case 9:
                return "相手は自社の管理が及ばない会社です。"
                        + "その会社が侵害されたら、こちらへの接続経路がそのまま入口になります。\n\n"
                        + (design.vendorOnDemand
                        ? "いまは必要なときだけ開ける運用です。開いている時間が短いほど、狙われる時間も短くなります。"
                        : "いまは常時つなぎっぱなしです。保守はたまにしか使わないのに、"
                        + "経路は24時間開いています。")
                        + "\n\nサプライチェーン攻撃と呼ばれる筋道で、実際に起きています。";
            case 3:
                return "使えるアドレスの数が変わります。\n\n"
                        + "/26 は 62 台、/24 は 254 台。いまは /" + design.prefixLength
                        + " なので " + design.usableHosts() + " 台まで。\n\n"
                        + "足りなくなってから広げると、アドレスの振り直しが必要になって高くつきます。";
            default:
                StringBuilder sb = new StringBuilder();
                Evaluator.Result r = evaluator.evaluate(scenario, design, state.extraCost, state.extraBudget);
                boolean any = false;
                for (Evaluator.Finding f : r.findings) {
                    if ("危険".equals(f.level) || "将来リスク".equals(f.level)) {
                        sb.append("・").append(f.title).append('\n');
                        sb.append("  ").append(f.detail).append("\n\n");
                        any = true;
                    }
                }
                return any ? sb.toString() : "いまのところ、大きな穴は見当たりません。";
        }
    }

    /** 決裁者。金額と、その金額で何が防げるのかを突く。 */
    private void bossMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("予算 ").append(budget()).append(" 円");
        if (state.extraBudget > 0) {
            sb.append("（承認済みの増額 ").append(state.extraBudget).append(" 円を含む）");
        }
        sb.append('\n');
        sb.append("見積 ").append(totalCost()).append(" 円\n");
        if (state.extraCost > 0) {
            sb.append("うち後追い改修の割増 ").append(state.extraCost).append(" 円\n");
        }
        sb.append('\n');
        if (totalCost() > budget()) {
            sb.append("超過 ").append(totalCost() - budget())
                    .append(" 円。この金額で何が防げるのか説明が要ります。\n\n");
        } else {
            sb.append("予算内です。ただし削った項目のリスクは残ります。\n\n");
        }
        sb.append("[ 信頼 ").append(state.trust).append(" ]\n");
        if (state.trust < 40) {
            sb.append("障害が続いていると聞いています。原因は設計側にありませんか。");
        } else {
            sb.append("問題なく動いているうちは口を出しません。");
        }
        if (!state.occurredFaults.isEmpty()) {
            sb.append("\n\n設計が原因の障害がこれまでに ")
                    .append(state.occurredFaults.size()).append(" 種類起きています。");
        }
        new AlertDialog.Builder(this)
                .setTitle("決裁者（依頼者の上司）")
                .setMessage(sb.toString())
                .setNegativeButton("閉じる", null)
                .setPositiveButton("増額を交渉する", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        negotiateBudget();
                    }
                })
                .show();
    }

    /**
     * 予算の増額交渉。
     * 通るかどうかは「守る理由を説明できるか」と「これまでの信頼」で決まる。
     */
    private void negotiateBudget() {
        if (state.negotiations >= 2) {
            show("交渉", "これ以上は出せません。\n\n"
                    + "「今期の枠はもう動かせません。この範囲でやってください」");
            return;
        }
        Evaluator.Result now = evaluator.evaluate(scenario, design, state.extraCost, state.extraBudget);
        int risks = 0;
        for (Evaluator.Finding f : now.findings) {
            if ("危険".equals(f.level) || "将来リスク".equals(f.level)) {
                risks++;
            }
        }
        if (risks == 0) {
            show("交渉", "「いま困っていないものに、追加で出す理由が分かりません」\n\n"
                    + "残っているリスクを示せないと増額は通りません。"
                    + "設計レビューで指摘が出ている状態で交渉してください。");
            return;
        }
        if (state.trust < 40) {
            state.negotiations++;
            save();
            show("交渉は不調", "「まず今の障害を止めてからにしてください」\n\n"
                    + "信頼 " + state.trust + " では通りません。"
                    + "障害対応で信頼を戻してから、もう一度話してください。（残り "
                    + (2 - state.negotiations) + " 回）");
            return;
        }
        int granted = state.trust >= 55 ? 200000 : 100000;
        state.extraBudget += granted;
        state.negotiations++;
        save();
        show("増額が承認されました", "「" + risks + " 件の指摘が残っているのは分かりました。"
                + granted + " 円までなら出します」\n\n"
                + "予算 " + budget() + " 円\n"
                + "見積 " + totalCost() + " 円\n\n"
                + (state.trust >= 55
                ? "信頼が高いぶん、通る額も大きくなりました。"
                : "信頼がもう少し高ければ、通る額も上がります。")
                + "（残り " + (2 - state.negotiations) + " 回）");
    }

    /** 現場担当。観察でわかる手がかりと、障害の一次情報を持っている。 */
    private void itStaffMenu() {
        StringBuilder sb = new StringBuilder();
        if (current != null && !current.resolved) {
            sb.append("[ 現場からの一次情報 ]\n");
            sb.append(current.cause.symptom).append("\n\n");
            if (current.log.isEmpty()) {
                sb.append("まだ何も調べていません。社員を押して診断してください。\n\n");
            } else {
                sb.append("これまでに試したこと\n");
                for (String entry : current.log) {
                    sb.append("・").append(entry).append('\n');
                }
                sb.append('\n');
            }
            sb.append(current.describeBelief());
        } else {
            sb.append("いまは特に不具合の報告はありません。\n\n");
        }
        show("現場担当（先方の情シス）", sb.toString());
    }

    private void clientMenu() {
        if (state.easyMode) {
            easyClientMenu();
            return;
        }
        normalClientMenu();
    }

    /** 簡単モードの依頼者。ネットワークに詳しく、次の一手を必ず示す。 */
    private void easyClientMenu() {
        Evaluator.Result now = evaluator.evaluate(scenario, design, state.extraCost, state.extraBudget);
        int max = evaluator.maxFitness(scenario, state.extraBudget);
        final int unknown = scenario.hidden.size() - scenario.revealedCount();

        StringBuilder sb = new StringBuilder();
        sb.append("いまの案件適合度 ").append(now.fitness()).append(" / 満点 ").append(max).append("\n\n");
        sb.append("[ 次にやること ]\n").append(nextAdvice(unknown));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("依頼者（ネットワークに詳しい）")
                .setMessage(sb.toString())
                .setNegativeButton("閉じる", null);
        if (unknown > 0) {
            builder.setPositiveButton("その話を聞く", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    askClient();
                }
            });
        } else if (current != null && !current.resolved) {
            builder.setPositiveButton("診断する", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    chooseCommand();
                }
            });
        }
        builder.show();
    }

    /** 状態を見て、いま一番やるべきことを1つだけ返す。 */
    private String nextAdvice(int unknown) {
        if (unknown > 0) {
            Scenario.Hidden next = scenario.nextUnrevealed();
            return "まだ話していない条件があります。\n\n"
                    + "「" + next.question + "」と聞いてください。\n"
                    + "この話を確認するだけで加点されますし、"
                    + "確認しないまま設計しても要求を満たしているか判定できません。";
        }
        if (current != null && !current.resolved) {
            List<java.util.Map.Entry<Incident.Cause, Double>> ranked = current.ranked();
            double top = ranked.get(0).getValue();
            if (top > 0.8) {
                return "原因はほぼ絞れています。\n\n"
                        + "「" + ranked.get(0).getKey().label + "」の可能性が "
                        + Math.round(top * 100) + "% です。社員に原因を報告させてください。";
            }
            Diagnostics.Command suggestion = diagnostics.suggest(current, design.proxy);
            if (suggestion == null) {
                return "打てる診断は出し尽くしました。"
                        + "いちばん確率の高い「" + ranked.get(0).getKey().label + "」で報告してみてください。";
            }
            return "まだ原因が絞れていません。\n\n"
                    + "次は「" + suggestion.label + "」を試してください。\n"
                    + "この結果なら候補をだいたい半分に割れます。";
        }
        Evaluator.Result check = evaluator.evaluate(scenario, design, state.extraCost, state.extraBudget);
        for (Evaluator.Finding f : check.findings) {
            if ("過剰な許可があります".equals(f.title) || "効かないルールがあります".equals(f.title)) {
                return "Firewallのルールに問題があります。\n\n" + f.detail
                        + "\n\n図のFirewallを押して、ルールを直してください。";
            }
        }
        int needed = evaluator.fullProtectionCost(scenario);
        if (needed > budget() && state.negotiations < 2 && state.trust >= 40) {
            return "守りを全部入れると " + needed + " 円かかりますが、予算は " + budget() + " 円です。\n\n"
                    + "決裁者に増額を交渉してください。"
                    + "設計レビューで指摘が出ている状態で話すと、根拠として通りやすくなります。";
        }
        Design best = evaluator.bestDesign(scenario, state.extraBudget);
        if (design.remoteVpn != best.remoteVpn) {
            return best.remoteVpn
                    ? "在宅から社内システムへ入る手段がありません。図の在宅端末を押してVPNを用意してください。\n\n"
                    + "週2で在宅の人がいると聞いています。手段が無いままだと要求未達です。"
                    : "リモートVPNは不要です。";
        }
        if (design.vendorOnDemand != best.vendorOnDemand) {
            return best.vendorOnDemand
                    ? "保守業者の接続を、必要なときだけ開ける運用に変えてください。図の保守業者を押します。\n\n"
                    + "常時つなぎっぱなしだと、業者側が侵害されたときに24時間いつでも入れる入口になります。"
                    : "保守接続は常時のままで構いません。";
        }
        if (design.proxy != best.proxy) {
            return best.proxy
                    ? "プロキシを導入してください。図のFirewallを押すと選べます。\n\n"
                    + "外向き通信の記録が残らないと、端末が感染しても気づけません。"
                    + "止めるためではなく、見えるようにするための投資です。"
                    : "いまの予算ではプロキシまでは手が回りません。先に予算の交渉が要ります。";
        }
        if (design.guestVlan != best.guestVlan) {
            return best.guestVlan
                    ? "来客を VLAN で分離してください。図のスイッチか来客端末を押します。\n\n"
                    + "Firewall のルールでは同じセグメント内の通信は止められません。分離は L2 側の仕事です。"
                    : "来客の VLAN 分離は不要です。";
        }
        if (design.dmz != best.dmz) {
            return best.dmz
                    ? "公開する Web サーバーを DMZ に移してください。図のサーバーを押します。\n\n"
                    + "内部に置いたまま外部公開すると、サーバーが破られた時点で社内が同じ区画にあります。"
                    : "DMZ は不要です。";
        }
        if (design.serverSharedWithWeb != best.serverSharedWithWeb) {
            return best.serverSharedWithWeb
                    ? "社内サーバーは同居のままで構いません。"
                    : "社内サーバーを内部セグメントに分離してください。図の社内サーバーを押します。\n\n"
                    + "公開サーバーと同じ区画にあると、公開側が破られた時点で顧客リストも同じ場所にあります。"
                    + "外に出すものと出さないものは、置き場所で分けます。";
        }
        if (design.dnsRedundant != best.dnsRedundant) {
            return best.dnsRedundant
                    ? "DNSを2台にしてください。図のDNSを押します。\n\n"
                    + "1台構成だと、そこが止まった時点で全社が名前解決できなくなります。"
                    + "止めない努力より、止まっても続く形にするほうが確実です。"
                    : "DNSの冗長化は不要です。";
        }
        if (design.prefixLength != best.prefixLength) {
            return "内部サブネットを /" + best.prefixLength + " に変えてください。図の社員PCを押します。\n\n"
                    + "将来 " + scenario.futureUsers + " 人まで増える計画なので、/26 の 62 台では足りません。"
                    + "後から広げるとアドレスの振り直しになります。";
        }
        if (state.extraCost > 0) {
            return "構成は最適です。\n\n"
                    + "ただし稼働後に直したぶんの割増が " + state.extraCost + " 円 残っています。"
                    + "次の案件では、稼働前に決め切ると同じ点数がもっと安く出せます。";
        }
        return "この構成で満点です。\n\n"
                + "来客は分離済み、公開サーバーは DMZ、将来の台数ぶんのアドレスも確保できています。"
                + "あとは運用で信頼を落とさないことだけです。";
    }

    private void normalClientMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append(scenario.client).append('\n');
        sb.append("「").append(scenario.explicitRequirement).append("」\n\n");
        sb.append("予算 ").append(budget() / 10000).append(" 万円 / 現在 ")
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
            sb.append("\n未確認の要望が ").append(unknown)
                    .append(" 件あります。現場担当が手がかりを持っています。");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("依頼者（発注担当）")
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
                    }, "sw");
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
                    }, "web");
        } else if ("fw".equals(id)) {
            if (!design.proxy) {
                firewallMenu();
                return;
            }
            toggleDialog("Firewall",
                    evaluator.describeRules(scenario, design) + "\n来客Denyルール: "
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
                    }, "fw");
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
                    }, "pc");
        } else if ("proxy".equals(id) || ("sw".equals(id) && false)) {
            toggleDialog("プロキシ",
                    "社内から外へ出る通信を、代わりに中継して記録する装置です。\n\n現在: 導入済み",
                    "撤去する（-15万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.proxy = false;
                        design.remoteVpn = false;
                        design.vendorOnDemand = false;
                        design.customRules = null;
                                }
                            });
                        }
                    }, "proxy");
        } else if ("sase".equals(id)) {
            toggleDialog("SASE",
                    "検査をクラウド側でまとめて行う仕組みです。"
                            + "この案件では最初から使う前提で契約しています。\n\n現在: "
                            + (design.saseBypass
                            ? "一部の通信がSASEを通らずに直接出ています"
                            : "全通信がSASEを通っています"),
                    design.saseBypass ? "例外をなくす（+5万円）" : "例外を戻す（-5万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.saseBypass = !design.saseBypass;
                                }
                            });
                        }
                    }, "sase");
        } else if ("vendor".equals(id)) {
            toggleDialog("保守業者",
                    "業務システムの保守で、社内に入ってくる相手です。\n\n現在: "
                            + (design.vendorOnDemand
                            ? "必要なときだけ開ける運用（申請ベース）"
                            : "常時つなぎっぱなし"),
                    design.vendorOnDemand ? "常時接続に戻す（-3万円）" : "必要時のみに変える（+3万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.vendorOnDemand = !design.vendorOnDemand;
                                }
                            });
                        }
                    }, "vendor_server");
        } else if ("home".equals(id) && scenario.boundary == Scenario.Boundary.SASE) {
            toggleDialog("社員端末",
                    "事務所に残る受発注システムへ、どうやって入るかを決めます。\n\n現在: "
                            + (design.ztna
                            ? "SASE経由の認証つきアクセス"
                            : "接続手段なし（事務所のシステムを使えません）"),
                    design.ztna ? "やめる（-12万円）" : "SASE経由のアクセスを設定する（+12万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.ztna = !design.ztna;
                                }
                            });
                        }
                    }, "sase");
        } else if ("home".equals(id)) {
            toggleDialog("在宅端末",
                    "家から社内システムを使う端末です。\n\n現在: "
                            + (design.remoteVpn
                            ? "リモートアクセスVPNで接続"
                            : "接続手段なし（社内には入れません）"),
                    design.remoteVpn ? "VPNを撤去する（-12万円）" : "リモートVPNを用意する（+12万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.remoteVpn = !design.remoteVpn;
                                }
                            });
                        }
                    }, "vpn");
        } else if ("cloud".equals(id)) {
            show("クラウド", "業務システムを動かしている、社外の事業者の設備です。"
                    + "ここは設計で置き換えられません。\n\n"
                    + "機器の故障は事業者が見ますが、公開範囲や権限の設定は利用者側の責任のままです。"
                    + "社内から出ていく通信の扱いは、プロキシと境界のルールで決まります。");
        } else if ("srv".equals(id)) {
            toggleDialog("社内サーバー",
                    "顧客リストや図面が入っている、社内の人だけが使うサーバーです。\n\n現在: "
                            + (design.serverSharedWithWeb
                            ? "公開Webサーバーと同じ区画に置いています"
                            : "内部セグメントに分離しています"),
                    design.serverSharedWithWeb ? "内部に分離する（+8万円）" : "同居に戻す（-8万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.serverSharedWithWeb = !design.serverSharedWithWeb;
                                }
                            });
                        }
                    }, "internal_server");
        } else if (id.startsWith("dns")) {
            toggleDialog("DNSサーバー",
                    "名前をIPアドレスに変える役です。止まると「IPなら通じるのに名前で開けない」状態になります。\n\n現在: "
                            + (design.dnsRedundant ? "2台構成（冗長化あり）" : "1台構成（単一障害点）"),
                    design.dnsRedundant ? "1台に戻す（-4万円）" : "2台に増やす（+4万円）",
                    new Runnable() {
                        public void run() {
                            changeDesign(new Runnable() {
                                public void run() {
                                    design.dnsRedundant = !design.dnsRedundant;
                                }
                            });
                        }
                    }, "dns");
        } else if ("net".equals(id)) {
            new AlertDialog.Builder(this)
                    .setTitle("インターネット")
                    .setMessage("ISPからの回線。ここは設計で変えられません。\n\n"
                            + "外部から内部への到達可否は、Firewallのルールと公開サーバーの置き場所で決まります。")
                    .setNegativeButton("閉じる", null)
                    .setNeutralButton("ルーターについて", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            showDeviceGuide("router");
                        }
                    })
                    .show();
        }
    }

    /** 境界まわりの操作をまとめる。 */
    private void firewallMenu() {
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (design.customRules == null) {
            items.add(design.fwGuestDeny ? "来客Denyルールを外す" : "来客Denyルールを追加する");
            actions.add(new Runnable() {
                public void run() {
                    changeDesign(new Runnable() {
                        public void run() {
                            design.fwGuestDeny = !design.fwGuestDeny;
                        }
                    });
                }
            });
            items.add("ルールを手で編集する");
            actions.add(new Runnable() {
                public void run() {
                    design.customRules = new ArrayList<>(design.defaultRules());
                    save();
                    ruleEditor();
                }
            });
        } else {
            items.add("ルールを編集する（" + design.customRules.size() + "件）");
            actions.add(new Runnable() {
                public void run() {
                    ruleEditor();
                }
            });
            items.add("自動生成に戻す");
            actions.add(new Runnable() {
                public void run() {
                    design.customRules = null;
                    save();
                    show("Firewall", "設計の選択からルールを自動生成する状態に戻しました。");
                }
            });
        }

        if (!design.proxy) {
            items.add("プロキシを導入する（+15万円）");
            actions.add(new Runnable() {
                public void run() {
                    changeDesign(new Runnable() {
                        public void run() {
                            design.proxy = true;
                        }
                    });
                }
            });
        }
        items.add("ルール一覧を見る");
        actions.add(new Runnable() {
            public void run() {
                show("Firewallルール", evaluator.describeRules(scenario, design));
            }
        });
        items.add("Firewallについて");
        actions.add(new Runnable() {
            public void run() {
                showDeviceGuide("fw");
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Firewall / 境界")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    /** ルールの一覧。並び順そのものが設計なので、順序を動かせるようにする。 */
    private void ruleEditor() {
        final List<FirewallRule> rules = design.customRules;
        final String[] labels = new String[rules.size() + 1];
        for (int i = 0; i < rules.size(); i++) {
            labels[i] = rules.get(i).describe(i + 1);
        }
        labels[rules.size()] = "＋ ルールを追加する";

        new AlertDialog.Builder(this)
                .setTitle("ルール（上から順に評価）")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == rules.size()) {
                            addRuleSource();
                        } else {
                            ruleActions(which);
                        }
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void ruleActions(final int index) {
        final List<FirewallRule> rules = design.customRules;
        final String[] items = {"1つ上へ", "1つ下へ", "このルールを削除", "戻る"};
        new AlertDialog.Builder(this)
                .setTitle(rules.get(index).describe(index + 1))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0 && index > 0) {
                            FirewallRule moved = rules.remove(index);
                            rules.add(index - 1, moved);
                        } else if (which == 1 && index < rules.size() - 1) {
                            FirewallRule moved = rules.remove(index);
                            rules.add(index + 1, moved);
                        } else if (which == 2) {
                            rules.remove(index);
                        }
                        save();
                        ruleEditor();
                    }
                })
                .show();
    }

    private void addRuleSource() {
        final String[] zones = scenario.zones;
        new AlertDialog.Builder(this)
                .setTitle("どこからの通信ですか")
                .setItems(zones, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        addRuleDest(zones[which]);
                    }
                })
                .show();
    }

    private void addRuleDest(final String source) {
        final String[] zones = scenario.zones;
        new AlertDialog.Builder(this)
                .setTitle(source + " からどこへ")
                .setItems(zones, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        addRuleAction(source, zones[which]);
                    }
                })
                .show();
    }

    private void addRuleAction(final String source, final String dest) {
        new AlertDialog.Builder(this)
                .setTitle(source + " → " + dest)
                .setMessage("この通信をどうしますか。\n\n追加したルールは一番下に入ります。"
                        + "順序が効くので、必要なら上に動かしてください。")
                .setNegativeButton("Deny（通さない）", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        design.customRules.add(new FirewallRule(source, dest, "any", "any", false));
                        save();
                        ruleEditor();
                    }
                })
                .setPositiveButton("Allow（通す）", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        design.customRules.add(new FirewallRule(source, dest, "any", "any", true));
                        save();
                        ruleEditor();
                    }
                })
                .show();
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
        int before = design.cost(scenario);
        mutation.run();
        int after = design.cost(scenario);
        String extra = "";
        if (state.day > 0 && after > before) {
            int surcharge = (after - before) / 2;
            state.extraCost += surcharge;
            extra = "\n\n稼働後の改修のため割増 " + surcharge + " 円が発生しました";
        }
        save();
        show("設計変更", design.summary(scenario) + "\n\n費用 " + totalCost() + " 円 / 予算 "
                + budget() + " 円" + extra);
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
        boolean repeat = incidents.isWeaknessCause(current.cause, design)
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
                        String text = diagnostics.run(current, commands[which], design.proxy);
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
            if (incidents.isWeaknessCause(current.cause, design)) {
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
        Evaluator.Result result = evaluator.evaluate(scenario, design, state.extraCost, state.extraBudget);
        StringBuilder sb = new StringBuilder(design.summary(scenario)).append("\n\n");
        for (Evaluator.Finding f : result.findings) {
            sb.append("[").append(f.level).append("] ").append(f.title).append('\n');
            sb.append("  ").append(f.detail).append("\n\n");
        }
        sb.append("要求適合 ").append(sign(result.requirementScore))
                .append(" / セキュリティ ").append(sign(result.securityScore))
                .append(" / 拡張性 ").append(sign(result.scalabilityScore))
                .append(" / コスト ").append(sign(result.costScore)).append('\n');
        int max = evaluator.maxFitness(scenario, state.extraBudget);
        sb.append("案件適合度 ").append(result.fitness()).append(" / 満点 ").append(max);
        if (result.fitness() >= max) {
            sb.append("\n\n満点です。");
        }
        show("設計レビュー", sb.toString());
    }

    private void showBrief() {
        StringBuilder sb = new StringBuilder();
        sb.append("Day ").append(state.day).append(" / 信頼 ").append(state.trust).append("\n\n");
        sb.append(design.summary(scenario)).append('\n');
        sb.append("費用 ").append(totalCost()).append(" 円");
        if (state.extraCost > 0) {
            sb.append("（後追い改修の割増 ").append(state.extraCost).append(" 円を含む）");
        }
        sb.append("\n予算 ").append(budget()).append(" 円\n");
        if (!state.occurredFaults.isEmpty()) {
            sb.append("\n過去に起きた設計起因の障害: ").append(state.occurredFaults.size()).append(" 種\n");
        }
        sb.append("\n（社員を長押しで最初からやり直せます）");
        show("案件の状況", sb.toString());
    }

    // ------------------------------------------------------------------

    private void refresh() {
        Incident.Cause active = current != null && !current.resolved ? current.cause : null;
        topology.update(design, active, scenario);
        tvDay.setText("Day " + state.day + (state.easyMode ? " 簡単" : ""));
        tvTrust.setText("信頼 " + state.trust);
        tvCost.setText("費用 " + (totalCost() / 10000) + "万");
        tvIncident.setText(active == null ? "稼働中" : "障害中");

        charaStaff.setImageResource(ally.face(active != null, state.trust));
        tvAllyName.setText(ally.name);
        tvAllyRole.setText(ally.role);

        speaker = pickSpeaker();
        charaPartner.setImageResource(speaker.face(active != null, state.trust));
        tvPartnerName.setText(speaker.name);
        tvPartnerRole.setText(speaker.role);

        int unknown = scenario.hidden.size() - scenario.revealedCount();
        if (state.easyMode) {
            tvHint.setText("依頼者に話しかけると、次にやることを教えてくれます");
        } else if (active != null) {
            tvHint.setText("図の赤い線が障害箇所です。社員を押して診断、現場担当を押すと一次情報");
        } else if (speaker == Stakeholder.BOSS) {
            tvHint.setText("決裁者が出てきました。押すと金額と信頼への評価が聞けます");
        } else if (speaker == Stakeholder.INTERN) {
            tvHint.setText("来客セグメントから社内に届く状態です。インターンを押すと何が起きるか分かります");
        } else if (unknown > 0) {
            tvHint.setText("総務担当が手がかりを持っています。見てから依頼者に確認すると確実です");
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
        return design.cost(scenario) + state.extraCost;
    }

    private int budget() {
        return scenario.budget + state.extraBudget;
    }

    private void save() {
        state.save(this, design, scenario, current);
        refresh();
    }

    private String sign(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private void toggleDialog(String title, String body, String action,
                              final Runnable onAction, final String guideKey) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton("閉じる", null)
                .setNeutralButton("この機器について", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        showDeviceGuide(guideKey);
                    }
                })
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
                        state.reset(MainActivity.this, scenario);
                        current = null;
                        for (Scenario.Hidden h : scenario.hidden) {
                            h.revealed = false;
                        }
                        design.guestVlan = false;
                        design.dmz = false;
                        design.fwGuestDeny = false;
                        design.dnsRedundant = false;
                        design.serverSharedWithWeb = true;
                        design.proxy = false;
                        design.remoteVpn = false;
                        design.vendorOnDemand = false;
                        design.customRules = null;
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
