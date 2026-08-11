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
        return evaluate(scenario, design, extraCost, 0);
    }

    public Result evaluate(Scenario scenario, Design design, int extraCost, int extraBudget) {
        int budget = scenario.budget + extraBudget;
        NetGraph g = design.buildGraph(scenario);
        RuleEngine engine = new RuleEngine(design.buildRules(scenario));
        Result r = new Result();
        r.cost = design.cost(scenario) + extraCost;

        // 要求の判定は案件が持っているリストから行う
        for (Scenario.Allowance a : scenario.allowances) {
            if (!a.scored) {
                continue;
            }
            RuleEngine.Path path = engine.canReach(g, a.fromNode, a.toNode);
            boolean confirmed = a.hiddenIndex < 0
                    || (a.hiddenIndex < scenario.hidden.size()
                    && scenario.hidden.get(a.hiddenIndex).revealed);
            if (path.reachable) {
                if (confirmed) {
                    r.requirementScore += a.points;
                } else {
                    r.findings.add(new Finding("注意", a.label + "は通っていますが根拠が未確認です",
                            "要望として確認していないので加点されません。"
                                    + "たまたま通っているのか、必要だから通したのかを区別できません"));
                }
            } else {
                r.findings.add(new Finding("要求未達", a.label + "ができません",
                        path.blockedBy == null ? "経路なし" : path.blockedBy));
                r.requirementScore -= a.penalty;
            }
        }

        for (Scenario.Prohibition p : scenario.prohibitions) {
            RuleEngine.Path path = engine.canReach(g, p.fromNode, p.toNode);
            if (path.reachable) {
                r.findings.add(new Finding("危険", p.label, p.detail + "\n" + path.describe(g)));
                r.securityScore -= p.penalty;
            } else {
                r.findings.add(new Finding("良", p.label.replace("到達できます", "は遮断されています"),
                        path.blockedBy == null ? "経路なし" : path.blockedBy));
                r.securityScore += p.reward;
            }
        }

        if (scenario.boundary == Scenario.Boundary.SASE) {
            if (design.saseBypass) {
                r.findings.add(new Finding("危険", "検査を迂回できる経路が残っています",
                        "SASEを通さずに直接インターネットへ出られます。"
                                + "そこだけ記録も遮断も効きません"));
                r.securityScore -= 20;
            } else {
                r.findings.add(new Finding("良", "全通信がSASEを通っています",
                        "社内にいても外にいても、同じ検査を通ります"));
                r.securityScore += 20;
            }
        }

        if (scenario.boundary == Scenario.Boundary.ON_PREM && design.fwGuestDeny && !design.guestVlan) {
            r.findings.add(new Finding("注意", "Firewallの来客Denyルールが効いていません",
                    "来客と社員が同一セグメントのため通信はFirewallを通りません。"
                            + "分離はVLAN側で行う必要があります"));
        }

        if (scenario.boundary == Scenario.Boundary.SASE) {
            // プロキシ相当の役目はSASEが持つので、ここでは判定しない
        } else if (design.proxy) {
            r.findings.add(new Finding("良", "外向き通信をプロキシに集約しています",
                    "誰がどこへ出たかの記録が残り、危険な宛先を遮断できます"));
            r.securityScore += 15;
        } else {
            r.findings.add(new Finding("将来リスク", "外向き通信の記録が残りません",
                    "端末が外部と勝手に通信していても気づけません"));
            r.securityScore -= 5;
        }

        if (design.dnsRedundant) {
            r.findings.add(new Finding("良", "DNSが冗長化されています",
                    "1台止まっても名前解決は続きます"));
            r.scalabilityScore += 10;
        } else {
            r.findings.add(new Finding("将来リスク", "DNSが1台しかありません",
                    "単一障害点。止まると全社が名前解決できなくなります"));
            r.scalabilityScore -= 10;
        }

        long hosts = design.usableHosts();
        if (hosts < scenario.futureUsers) {
            r.findings.add(new Finding("将来リスク", "IPアドレスが将来不足します",
                    "収容 " + hosts + " 台 < 想定 " + scenario.futureUsers + " 台（ScalabilityPenalty）"));
            r.scalabilityScore -= 20;
        } else {
            r.scalabilityScore += 15;
        }

        if (r.cost > budget) {
            r.findings.add(new Finding("予算超過", "予算を超えています",
                    "BudgetOverrun。差額 " + (r.cost - budget) + " 円"
                            + (extraCost > 0 ? "（うち後追い改修の割増 " + extraCost + " 円）" : "")));
            r.costScore -= 25;
        } else {
            int margin = budget - r.cost;
            r.costScore += margin > 300000 ? 20 : 12;
        }

        // 確認できた要求ぶんだけ加点する。聞かないままでは満点にならない。
        r.requirementScore += scenario.revealedCount() * 5;

        reviewRules(r, design, scenario);

        int unheard = scenario.hidden.size() - scenario.revealedCount();
        if (unheard > 0) {
            r.findings.add(new Finding("ヒアリング不足", "未確認の要求が " + unheard + " 件あります",
                    "確認していない要求は評価に反映されません（UNKNOWN のまま）"));
        }

        return r;
    }

    /** 全組み合わせを総当たりして、この案件で取りうる最良の設計を返す。 */
    public Design bestDesign(Scenario scenario) {
        return bestDesign(scenario, 0);
    }

    public Design bestDesign(Scenario scenario, int extraBudget) {
        Design best = null;
        int bestScore = Integer.MIN_VALUE;
        if (scenario.boundary == Scenario.Boundary.SASE) {
            boolean[] two = {false, true};
            for (boolean bypass : two) {
                for (boolean zt : two) {
                    for (boolean dns : two) {
                        for (int prefix : new int[]{24, 26}) {
                            Design c = new Design();
                            c.saseBypass = bypass;
                            c.ztna = zt;
                            c.dnsRedundant = dns;
                            c.prefixLength = prefix;
                            int sc = evaluate(scenario, c, 0, extraBudget).fitness();
                            if (sc > bestScore) {
                                bestScore = sc;
                                best = c;
                            }
                        }
                    }
                }
            }
            return best;
        }
        boolean[] flags = {false, true};
        int[] prefixes = {24, 26};
        for (boolean vlan : flags) {
            for (boolean dmz : flags) {
                for (boolean deny : flags) {
                    for (boolean dns : flags) {
                        for (boolean shared : flags) {
                            for (boolean px : flags) {
                                for (int prefix : prefixes) {
                                    for (boolean vpn : flags) {
                                        for (boolean onDemand : flags) {
                                            Design candidate = evaluateCandidate(
                                                    vlan, dmz, deny, dns, shared, px, prefix, vpn, onDemand);
                                            int sc = evaluate(scenario, candidate, 0, extraBudget).fitness();
                                            if (sc > bestScore) {
                                                bestScore = sc;
                                                best = candidate;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private Design evaluateCandidate(boolean vlan, boolean dmz, boolean deny, boolean dns,
                                     boolean shared, boolean px, int prefix,
                                     boolean vpn, boolean onDemand) {
        Design candidate = new Design();
        candidate.guestVlan = vlan;
        candidate.dmz = dmz;
        candidate.fwGuestDeny = deny;
        candidate.dnsRedundant = dns;
        candidate.serverSharedWithWeb = shared;
        candidate.proxy = px;
        candidate.prefixLength = prefix;
        candidate.remoteVpn = vpn;
        candidate.vendorOnDemand = onDemand;
        return candidate;
    }

    /** すべて聞き出し、最良の設計を、割増なしで組んだときの点数。 */
    public int maxFitness(Scenario scenario) {
        return maxFitness(scenario, 0);
    }

    public int maxFitness(Scenario scenario, int extraBudget) {
        int unheard = scenario.hidden.size() - scenario.revealedCount();
        return evaluate(scenario, bestDesign(scenario, extraBudget), 0, extraBudget).fitness()
                + unheard * 5;
    }

    /** すべての守りを入れた構成の費用。予算交渉が要るかの判断に使う。 */
    public int fullProtectionCost(Scenario scenario) {
        if (scenario.boundary == Scenario.Boundary.SASE) {
            Design s = new Design();
            s.saseBypass = false;
            s.ztna = true;
            s.dnsRedundant = true;
            s.prefixLength = 24;
            return s.cost(scenario);
        }
        return fullProtectionCost();
    }

    public int fullProtectionCost() {
        Design d = new Design();
        d.guestVlan = true;
        d.dmz = true;
        d.dnsRedundant = true;
        d.serverSharedWithWeb = false;
        d.proxy = true;
        d.remoteVpn = true;
        d.vendorOnDemand = true;
        d.prefixLength = 24;
        return d.cost();
    }

    /**
     * ルールそのものの点検（MD §14）。
     * 通信が通るかどうかとは別に、書き方の問題を見る。
     */
    private void reviewRules(Result r, Design design, Scenario scenario) {
        List<FirewallRule> rules = design.buildRules(scenario);
        List<String> covered = new ArrayList<>();
        int excess = 0;
        int shadowed = 0;

        for (FirewallRule rule : rules) {
            List<String> pairs = rule.coveredPairs(scenario.zones);

            boolean allCovered = !pairs.isEmpty();
            for (String pair : pairs) {
                if (!covered.contains(pair)) {
                    allCovered = false;
                }
            }
            if (allCovered) {
                shadowed++;
                r.findings.add(new Finding("注意", "効かないルールがあります",
                        rule.describe(rules.indexOf(rule) + 1)
                                + " — 上のルールで全部拾われるため、一度も評価されません"));
                r.securityScore -= 5;
            }

            if (rule.allow) {
                for (String pair : pairs) {
                    if (!covered.contains(pair) && !needed(scenario, pair)) {
                        excess++;
                        r.findings.add(new Finding("危険", "過剰な許可があります",
                                pair.replace(">", " → ") + " を許可しています。"
                                        + "業務で要らない通信を通すと、そこが侵入経路になります"));
                        r.securityScore -= 8;
                        break;
                    }
                }
            }
            covered.addAll(pairs);
        }

        if (excess == 0 && shadowed == 0) {
            r.findings.add(new Finding("良", "ルールに無駄も抜けもありません",
                    "必要な許可だけが、評価される順序で並んでいます"));
            r.securityScore += 10;
        }
    }

    /** その案件で業務上必要とされている通信か。案件ごとに違う。 */
    private boolean needed(Scenario scenario, String pair) {
        for (Scenario.Allowance a : scenario.allowances) {
            if (a.pair().equals(pair)) {
                return true;
            }
        }
        return false;
    }

    public String describeRules(Scenario scenario, Design design) {
        StringBuilder sb = new StringBuilder("Firewallルール（上から評価）\n");
        List<FirewallRule> rules = design.buildRules(scenario);
        for (int i = 0; i < rules.size(); i++) {
            sb.append("  ").append(rules.get(i).describe(i + 1)).append('\n');
        }
        sb.append("  (どれにも当たらない通信は暗黙Deny)\n");
        return sb.toString();
    }
}
