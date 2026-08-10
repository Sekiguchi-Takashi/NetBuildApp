package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.List;

/** MD §30 の設計レビューと §32 の SolutionFitness。 */
public class Evaluator {

    public static class Finding {
        public final String level;
        public final String title;
        public final String detail;

        Finding(String level, String title, String detail) {
            this.level = level;
            this.title = title;
            this.detail = detail;
        }
    }

    public static class Result {
        public final List<Finding> findings = new ArrayList<>();
        public int requirementScore;
        public int securityScore;
        public int scalabilityScore;
        public int costScore;
        public int cost;

        public int fitness() {
            return requirementScore + securityScore + scalabilityScore + costScore;
        }
    }

    public Result evaluate(Scenario scenario, Design design, int extraCost) {
        NetGraph g = design.buildGraph();
        RuleEngine engine = new RuleEngine(design.buildRules());
        Result r = new Result();
        r.cost = design.cost() + extraCost;

        RuleEngine.Path guestToInternal = engine.canReach(g, "guest", "pc");
        RuleEngine.Path guestToInternet = engine.canReach(g, "guest", "net");
        RuleEngine.Path internetToWeb = engine.canReach(g, "net", "web");
        RuleEngine.Path internetToPc = engine.canReach(g, "net", "pc");

        if (guestToInternal.reachable) {
            r.findings.add(new Finding("危険", "来客端末から社内PCへ到達できます",
                    guestToInternal.describe(g)));
            r.securityScore -= 30;
        } else {
            r.findings.add(new Finding("良", "来客から社内は遮断されています",
                    guestToInternal.blockedBy == null ? "経路なし" : guestToInternal.blockedBy));
            r.securityScore += 20;
        }

        if (guestToInternet.reachable) {
            r.requirementScore += 15;
        } else {
            r.findings.add(new Finding("要求未達", "来客がインターネットを使えません",
                    guestToInternet.blockedBy == null ? "経路なし" : guestToInternet.blockedBy));
            r.requirementScore -= 20;
        }

        if (internetToWeb.reachable) {
            r.requirementScore += 15;
        } else {
            r.findings.add(new Finding("要求未達", "Webサーバーを外部公開できていません",
                    internetToWeb.blockedBy == null ? "経路なし" : internetToWeb.blockedBy));
            r.requirementScore -= 15;
        }

        if (internetToPc.reachable) {
            r.findings.add(new Finding("危険", "インターネットから社内PCへ到達できます",
                    "PublicExposureRisk。公開サーバーはDMZに分離してください"));
            r.securityScore -= 35;
        } else if (design.dmz) {
            r.findings.add(new Finding("良", "公開サーバーがDMZに分離されています",
                    "内部セグメントへの直接到達なし"));
            r.securityScore += 20;
        }

        if (design.fwGuestDeny && !design.guestVlan) {
            r.findings.add(new Finding("注意", "Firewallの来客Denyルールが効いていません",
                    "来客と社員が同一セグメントのため通信はFirewallを通りません。分離はVLAN側で行う必要があります"));
        }

        long hosts = design.usableHosts();
        if (hosts < scenario.futureUsers) {
            r.findings.add(new Finding("将来リスク", "IPアドレスが将来不足します",
                    "収容 " + hosts + " 台 < 想定 " + scenario.futureUsers + " 台（ScalabilityPenalty）"));
            r.scalabilityScore -= 20;
        } else {
            r.scalabilityScore += 15;
        }

        if (r.cost > scenario.budget) {
            r.findings.add(new Finding("予算超過", "予算を超えています",
                    "BudgetOverrun。差額 " + (r.cost - scenario.budget) + " 円"
                            + (extraCost > 0 ? "（うち後追い改修の割増 " + extraCost + " 円）" : "")));
            r.costScore -= 25;
        } else {
            int margin = scenario.budget - r.cost;
            r.costScore += margin > 300000 ? 20 : 12;
        }

        int unheard = scenario.hidden.size() - scenario.revealedCount();
        if (unheard > 0) {
            r.findings.add(new Finding("ヒアリング不足", "未確認の要求が " + unheard + " 件あります",
                    "確認していない要求は評価に反映されません（UNKNOWN のまま）"));
        }

        return r;
    }

    public String describeRules(Design design) {
        StringBuilder sb = new StringBuilder("Firewallルール（上から評価）\n");
        List<FirewallRule> rules = design.buildRules();
        for (int i = 0; i < rules.size(); i++) {
            sb.append("  ").append(rules.get(i).describe(i + 1)).append('\n');
        }
        sb.append("  (どれにも当たらない通信は暗黙Deny)\n");
        return sb.toString();
    }
}
