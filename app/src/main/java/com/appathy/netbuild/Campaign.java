package com.appathy.netbuild;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 10年（120か月）の運用。
 * カレンダーは自動で進み、障害か更新時期が来たところで止まる。
 */
public class Campaign {

    public static final int TOTAL_MONTHS = 120;
    private static final String PREF = "netbuild_campaign";
    private static final String KEY = "state";

    /** 月送りが止まった理由。 */
    public enum StopReason {
        NONE, INCIDENT, REPLACEMENT, PROPOSAL, FINISHED, NOT_RUNNING
    }

    /** 難しさ。受け持つ拠点の数と、手元の資金が変わる。 */
    public enum Difficulty {
        EASY("やさしい", 1, 3000000, 0),
        NORMAL("ふつう", 2, 3000000, 2),
        HARD("むずかしい", 3, 2600000, 5);

        public final String label;
        public final int siteCount;
        public final int startCash;
        public final int riskBonus;

        Difficulty(String label, int siteCount, int startCash, int riskBonus) {
            this.label = label;
            this.siteCount = siteCount;
            this.startCash = startCash;
            this.riskBonus = riskBonus;
        }
    }

    public static class Event {
        public final StopReason reason;
        public final Site site;
        public final Incident.Cause cause;

        Event(StopReason reason, Site site, Incident.Cause cause) {
            this.reason = reason;
            this.site = site;
            this.cause = cause;
        }
    }

    public final List<Site> sites = Site.defaultSites();
    public Difficulty difficulty = Difficulty.NORMAL;
    private final Random random = new Random();
    private final IncidentEngine incidents = new IncidentEngine();
    private final Evaluator evaluator = new Evaluator();
    private final java.util.Map<String, Design> bestCache = new java.util.HashMap<>();

    public int month;
    public long cash = 3000000;
    public long totalRevenue;
    public long totalCost;
    public int satisfaction = 60;
    public boolean started;
    /** 年次点検を出した月。同じ年に何度も出さないために持つ。 */
    private int lastCheckupMonth = -1;

    /** 難しさに応じて受け持つ拠点を絞る。 */
    public List<Site> activeSites() {
        List<Site> list = new ArrayList<>();
        for (int i = 0; i < sites.size() && i < difficulty.siteCount; i++) {
            list.add(sites.get(i));
        }
        return list;
    }

    public void setDifficulty(Difficulty d) {
        difficulty = d;
        cash = d.startCash;
    }

    public int year() {
        return month / 12 + 1;
    }

    public int monthOfYear() {
        return month % 12 + 1;
    }

    public Site site(String id) {
        for (Site s : sites) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return sites.get(0);
    }

    public boolean anyRunning() {
        for (Site s : activeSites()) {
            if (s.running()) {
                return true;
            }
        }
        return false;
    }

    /** 拠点を稼働させる。初期構築の費用をここで払う。 */
    public void start(Site site) {
        int cost = site.design.cost(site.scenario);
        cash -= cost;
        totalCost += cost;
        site.startedMonth = month;
        started = true;
    }

    /**
     * 1か月進める。何も起きなければ NONE を返す。
     * 障害や更新時期に当たったら、そこで止めて理由を返す。
     */
    public Event advanceMonth() {
        if (!anyRunning()) {
            return new Event(StopReason.NOT_RUNNING, null, null);
        }
        if (month >= TOTAL_MONTHS) {
            return new Event(StopReason.FINISHED, null, null);
        }
        month++;

        for (Site s : activeSites()) {
            if (!s.running()) {
                continue;
            }
            cash += s.monthlyRevenue;
            totalRevenue += s.monthlyRevenue;
            int upkeep = s.design.cost(s.scenario) / 200;
            cash -= upkeep;
            totalCost += upkeep;
            s.monthsSinceIncident++;
        }

        if (month % 12 == 0) {
            satisfaction = Math.min(100, satisfaction + 2);
        }

        for (Site s : activeSites()) {
            if (s.running() && s.monthsToReplacement(month) <= 0) {
                return new Event(StopReason.REPLACEMENT, s, null);
            }
        }

        // 年に1度、弱点が残っている拠点について提案を出す
        if (month % 12 == 0 && month != lastCheckupMonth) {
            for (Site s : activeSites()) {
                if (s.running() && weaknessOf(s) != null) {
                    lastCheckupMonth = month;
                    return new Event(StopReason.PROPOSAL, s, null);
                }
            }
            lastCheckupMonth = month;
        }

        for (Site s : activeSites()) {
            if (!s.running() || s.monthsSinceIncident < 2) {
                continue;
            }
            if (random.nextInt(100) < incidentChance(s)) {
                Incident incident = incidents.nextDay(month, s.scenario, s.design,
                        new java.util.HashSet<String>());
                Incident.Cause cause = incident == null
                        ? Incident.Cause.LINK_DOWN : incident.cause;
                s.monthsSinceIncident = 0;
                s.incidentCount++;
                return new Event(StopReason.INCIDENT, s, cause);
            }
        }

        if (month >= TOTAL_MONTHS) {
            return new Event(StopReason.FINISHED, null, null);
        }
        return new Event(StopReason.NONE, null, null);
    }

    /**
     * その拠点で1か月に障害が起きる確率（％）。
     * 設計レビューで残っている指摘の数がそのまま効く。
     */
    public int incidentChance(Site site) {
        Evaluator.Result r = evaluator.evaluate(site.scenario, site.design, 0, 0);
        int weakness = 0;
        for (Evaluator.Finding f : r.findings) {
            if ("危険".equals(f.level)) {
                weakness += 2;
            } else if ("将来リスク".equals(f.level)) {
                weakness += 1;
            }
        }
        int base = 3 + weakness * 2 + difficulty.riskBonus;
        int age = site.running() ? month - site.startedMonth : 0;
        if (age > site.replacementCycle()) {
            base += (age - site.replacementCycle()) / 6 + 3;
        }
        return Math.max(1, Math.min(35, base));
    }

    /**
     * その拠点にいま残っている一番大きな弱点。無ければ null。
     * 最良の設計と比べて、足りていない項目を1つ返す。
     */
    public Weakness weaknessOf(Site site) {
        Design best = bestCache.get(site.scenario.id);
        if (best == null) {
            best = evaluator.bestDesign(site.scenario, 0);
            bestCache.put(site.scenario.id, best);
        }
        Design d = site.design;
        if (site.scenario.boundary == Scenario.Boundary.SASE) {
            if (d.saseBypass != best.saseBypass) {
                return new Weakness("検査を通らない経路が残っています",
                        "SASEを迂回できる経路があると、そこだけ記録も遮断も効きません。",
                        new Proposal.Change() {
                            public void apply(Design x) {
                                x.saseBypass = false;
                            }
                        }, 120000);
            }
        } else {
            if (!d.guestVlan && best.guestVlan) {
                return new Weakness("来客と社員が同じセグメントです",
                        "悪気のない持ち込み端末が、そのまま社内に届く状態です。",
                        new Proposal.Change() {
                            public void apply(Design x) {
                                x.guestVlan = true;
                            }
                        }, 180000);
            }
            if (d.serverSharedWithWeb && !best.serverSharedWithWeb) {
                return new Weakness("社内サーバーが公開側と同じ区画にあります",
                        "公開側が破られた時点で、社内の資料も同じ場所にあります。",
                        new Proposal.Change() {
                            public void apply(Design x) {
                                x.serverSharedWithWeb = false;
                            }
                        }, 160000);
            }
            if (!d.proxy && best.proxy) {
                return new Weakness("外向き通信の記録が残っていません",
                        "端末が外部と勝手に通信していても気づけません。",
                        new Proposal.Change() {
                            public void apply(Design x) {
                                x.proxy = true;
                            }
                        }, 300000);
            }
            if (!d.vendorOnDemand && best.vendorOnDemand) {
                return new Weakness("保守業者の接続が開きっぱなしです",
                        "業者側が侵害されると、その経路がそのまま入口になります。",
                        new Proposal.Change() {
                            public void apply(Design x) {
                                x.vendorOnDemand = true;
                            }
                        }, 60000);
            }
        }
        if (!d.backup) {
            return new Weakness("バックアップがありません",
                    "暗号化されたり消されたりしたとき、戻す手段がありません。",
                    new Proposal.Change() {
                        public void apply(Design x) {
                            x.backup = true;
                        }
                    }, 110000);
        }
        if (!d.redundantWan) {
            return new Weakness("回線が1系統しかありません",
                    "切れた時点で、その拠点は外とやりとりできなくなります。",
                    new Proposal.Change() {
                        public void apply(Design x) {
                            x.redundantWan = true;
                        }
                    }, 140000);
        }
        if (!d.dnsRedundant && best.dnsRedundant) {
            return new Weakness("DNSが1台しかありません",
                    "止まった時点で、その拠点は名前解決ができなくなります。",
                    new Proposal.Change() {
                        public void apply(Design x) {
                            x.dnsRedundant = true;
                        }
                    }, 80000);
        }
        if (d.prefixLength > best.prefixLength) {
            return new Weakness("アドレスの余裕がありません",
                    "人が増えたときに、新しい端末をつなげなくなります。",
                    new Proposal.Change() {
                        public void apply(Design x) {
                            x.prefixLength = 24;
                        }
                    }, 120000);
        }
        return null;
    }

    public static class Weakness {
        public final String title;
        public final String reason;
        public final Proposal.Change change;
        public final int cost;

        Weakness(String title, String reason, Proposal.Change change, int cost) {
            this.title = title;
            this.reason = reason;
            this.change = change;
            this.cost = cost;
        }
    }

    public String riskLabel(Site site) {
        int c = incidentChance(site);
        if (c <= 5) {
            return "低";
        }
        if (c <= 12) {
            return "中";
        }
        return "高";
    }

    public void pay(int amount, int satisfactionDelta) {
        cash -= amount;
        totalCost += amount;
        satisfaction = Math.max(0, Math.min(100, satisfaction + satisfactionDelta));
    }

    public long profit() {
        return totalRevenue - totalCost;
    }

    // ------------------------------------------------------------------
    // 10年後の順位
    // ------------------------------------------------------------------

    public static class Standing {
        public final String name;
        public final long profit;
        public final int satisfaction;

        Standing(String name, long profit, int satisfaction) {
            this.name = name;
            this.profit = profit;
            this.satisfaction = satisfaction;
        }
    }

    /** 競合3社と並べる。競合の値は固定なので、腕前がそのまま順位になる。 */
    public List<Standing> standings() {
        List<Standing> list = new ArrayList<>();
        list.add(new Standing("あなたの会社", profit(), satisfaction));
        // 競合の規模も受け持ち拠点の数に比例させる
        int n = difficulty.siteCount;
        list.add(new Standing("安売り工務店ネットワーク", 2400000L * n, 38));
        list.add(new Standing("堅実システムズ", 2100000L * n, 82));
        list.add(new Standing("大手ITソリューション", 2600000L * n, 61));
        return list;
    }

    // ------------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------------

    public void save(Context context) {
        try {
            JSONObject root = new JSONObject();
            root.put("month", month);
            root.put("cash", cash);
            root.put("totalRevenue", totalRevenue);
            root.put("totalCost", totalCost);
            root.put("satisfaction", satisfaction);
            root.put("started", started);
            root.put("difficulty", difficulty.name());
            root.put("lastCheckupMonth", lastCheckupMonth);
            JSONArray arr = new JSONArray();
            for (Site s : sites) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("startedMonth", s.startedMonth);
                o.put("monthsSinceIncident", s.monthsSinceIncident);
                o.put("incidentCount", s.incidentCount);
                o.put("guestVlan", s.design.guestVlan);
                o.put("dmz", s.design.dmz);
                o.put("fwGuestDeny", s.design.fwGuestDeny);
                o.put("dnsRedundant", s.design.dnsRedundant);
                o.put("serverSharedWithWeb", s.design.serverSharedWithWeb);
                o.put("proxy", s.design.proxy);
                o.put("remoteVpn", s.design.remoteVpn);
                o.put("vendorOnDemand", s.design.vendorOnDemand);
                o.put("saseBypass", s.design.saseBypass);
                o.put("ztna", s.design.ztna);
                o.put("prefixLength", s.design.prefixLength);
                o.put("redundantWan", s.design.redundantWan);
                o.put("backup", s.design.backup);
                o.put("l3Switch", s.design.l3Switch);
                o.put("wifi", s.design.wifi);
                o.put("dhcp", s.design.dhcp);
                o.put("staticForServers", s.design.staticForServers);
                o.put("ipv6", s.design.ipv6);
                o.put("fileShare", s.design.fileShare);
                o.put("mfp", s.design.mfp);
                o.put("siteLink", s.design.siteLink);
                arr.put(o);
            }
            root.put("sites", arr);
            prefs(context).edit().putString(KEY, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public boolean load(Context context) {
        String stored = prefs(context).getString(KEY, null);
        if (stored == null) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(stored);
            month = root.optInt("month");
            cash = root.optLong("cash", 3000000);
            totalRevenue = root.optLong("totalRevenue");
            totalCost = root.optLong("totalCost");
            satisfaction = root.optInt("satisfaction", 60);
            started = root.optBoolean("started");
            lastCheckupMonth = root.optInt("lastCheckupMonth", -1);
            try {
                difficulty = Difficulty.valueOf(root.optString("difficulty", "NORMAL"));
            } catch (Exception ignored) {
                difficulty = Difficulty.NORMAL;
            }
            JSONArray arr = root.optJSONArray("sites");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Site s = site(o.optString("id"));
                    s.startedMonth = o.optInt("startedMonth", -1);
                    s.monthsSinceIncident = o.optInt("monthsSinceIncident", 99);
                    s.incidentCount = o.optInt("incidentCount");
                    s.design.guestVlan = o.optBoolean("guestVlan");
                    s.design.dmz = o.optBoolean("dmz");
                    s.design.fwGuestDeny = o.optBoolean("fwGuestDeny");
                    s.design.dnsRedundant = o.optBoolean("dnsRedundant");
                    s.design.serverSharedWithWeb = o.optBoolean("serverSharedWithWeb", true);
                    s.design.proxy = o.optBoolean("proxy");
                    s.design.remoteVpn = o.optBoolean("remoteVpn");
                    s.design.vendorOnDemand = o.optBoolean("vendorOnDemand");
                    s.design.saseBypass = o.optBoolean("saseBypass", true);
                    s.design.ztna = o.optBoolean("ztna");
                    s.design.prefixLength = o.optInt("prefixLength", 26);
                    s.design.redundantWan = o.optBoolean("redundantWan");
                    s.design.backup = o.optBoolean("backup");
                    s.design.l3Switch = o.optBoolean("l3Switch");
                    s.design.wifi = o.optBoolean("wifi");
                    s.design.dhcp = o.optBoolean("dhcp", true);
                    s.design.staticForServers = o.optBoolean("staticForServers");
                    s.design.ipv6 = o.optBoolean("ipv6");
                    s.design.fileShare = o.optBoolean("fileShare");
                    s.design.mfp = o.optBoolean("mfp");
                    s.design.siteLink = o.optBoolean("siteLink");
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
