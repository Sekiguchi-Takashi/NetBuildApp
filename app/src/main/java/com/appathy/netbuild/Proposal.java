package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.List;

/**
 * 稼働後の判断は、細かい設定ではなく提案の選択で進める。
 * 各案は「費用」「満足度への影響」「弱点が減るか」で結果が変わる。
 */
public class Proposal {

    public static class Option {
        public final String label;
        public final String detail;
        public final int cost;
        public final int satisfaction;
        /** この案を選ぶと設計に加える変更。null なら設計は変わらない。 */
        public final Change change;

        public Option(String label, String detail, int cost, int satisfaction, Change change) {
            this.label = label;
            this.detail = detail;
            this.cost = cost;
            this.satisfaction = satisfaction;
            this.change = change;
        }
    }

    public interface Change {
        void apply(Design design);
    }

    public final String title;
    public final String situation;
    public final List<Option> options = new ArrayList<>();

    public Proposal(String title, String situation) {
        this.title = title;
        this.situation = situation;
    }

    public Proposal add(Option option) {
        options.add(option);
        return this;
    }

    /** 障害が起きたときに出す3択。応急・恒久・保留。 */
    public static Proposal forIncident(Site site, Incident.Cause cause) {
        Proposal p = new Proposal(site.name + "で障害",
                "「" + cause.symptom + "」");

        if (cause == Incident.Cause.RANSOMWARE) {
            if (site.design.backup) {
                p.add(new Option("バックアップから戻す",
                        "取っておいたバックアップから復旧します。数日で元に戻ります。",
                        180000, -4, null));
            } else {
                p.add(new Option("復旧業者に依頼する",
                        "バックアップが無いので、戻せる保証はありません。費用も期間もかさみます。",
                        900000, -25, null));
            }
            p.add(new Option("バックアップを整備し、外向き通信も監視する",
                    "戻す手段と、気づく手段の両方を作ります。次からは被害が小さく済みます。",
                    site.design.backup ? 300000 : 700000, 6, new Change() {
                public void apply(Design d) {
                    d.backup = true;
                    d.proxy = true;
                }
            }));
            return p;
        }

        p.add(new Option("応急処置で止める",
                "その場は収まりますが、原因は残ります。同じ障害がまた起きます。",
                150000, -6, null));

        Change fix = fixFor(cause);
        if (fix != null) {
            p.add(new Option("原因から直す（" + cause.fix + "）",
                    "費用はかかりますが、この原因の再発は止まります。",
                    300000, 8, fix));
        } else {
            p.add(new Option("業者を手配して復旧する",
                    "設備側の問題なので、こちらの設計では防げません。",
                    150000, 2, null));
        }

        p.add(new Option("今回は様子を見る",
                "費用はかかりませんが、止まっている間の損失が顧客に残ります。",
                0, -18, null));
        return p;
    }

    /**
     * 年次点検で見つかった弱点への提案。
     * 障害が起きてからではなく、起きる前に打つかどうかを問う。
     */
    public static Proposal forWeakness(Site site, String label, String reason,
                                       int cost, Change change) {
        Proposal p = new Proposal(site.name + "の年次点検",
                "点検で気になるところが見つかりました。\n\n" + reason);
        p.add(new Option("提案どおり手を打つ",
                "いま直せば、この原因の障害は起きなくなります。" + label,
                cost, 6, change));
        p.add(new Option("次の更新まで待つ",
                "そのときにまとめてやります。それまでは今の確率のままです。",
                0, -2, null));
        return p;
    }

    /** 更新時期が来たときの3択。 */
    public static Proposal forReplacement(Site site) {
        boolean cloud = site.scenario.boundary == Scenario.Boundary.SASE;
        Proposal p = new Proposal(site.name + "のシステム更新",
                (cloud ? "クラウド契約の3年" : "機器の5年")
                        + "が経ちました。このままでは保守が切れ、故障率も上がります。");

        p.add(new Option("同等のもので更新する",
                "いまと同じ構成で入れ替えます。無難ですが、増えた人数には対応しません。",
                cloud ? 320000 : 520000, 3, null));

        p.add(new Option("拡張して更新する",
                "将来の増員と、いまの弱点への手当てを含めます。",
                cloud ? 520000 : 820000, 10, new Change() {
            public void apply(Design design) {
                design.dnsRedundant = true;
                design.prefixLength = 24;
                design.proxy = true;
                design.redundantWan = true;
                design.backup = true;
            }
        }));

        p.add(new Option("延命して先送りする",
                "費用は抑えられますが、故障率が上がったまま次の年に入ります。",
                cloud ? 90000 : 140000, -5, null));
        return p;
    }

    private static Change fixFor(Incident.Cause cause) {
        switch (cause) {
            case LINK_DOWN:
            case WAN_DOWN:
                return new Change() {
                    public void apply(Design d) {
                        d.redundantWan = true;
                    }
                };
            case DNS_DOWN:
                return new Change() {
                    public void apply(Design d) {
                        d.dnsRedundant = true;
                    }
                };
            case GUEST_INTRUSION:
                return new Change() {
                    public void apply(Design d) {
                        d.guestVlan = true;
                    }
                };
            case WEB_COMPROMISE:
                return new Change() {
                    public void apply(Design d) {
                        d.dmz = true;
                    }
                };
            case SERVER_EXPOSED:
                return new Change() {
                    public void apply(Design d) {
                        d.serverSharedWithWeb = false;
                    }
                };
            case IP_EXHAUSTED:
                return new Change() {
                    public void apply(Design d) {
                        d.prefixLength = 24;
                    }
                };
            case MALWARE_C2:
                return new Change() {
                    public void apply(Design d) {
                        d.proxy = true;
                    }
                };
            default:
                return null;
        }
    }
}
