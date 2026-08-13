package com.appathy.netbuild;

/**
 * 経営モードで受け持つ拠点。
 * 拠点ごとに案件（前提）と設計を持ち、それぞれ別に稼働する。
 */
public class Site {

    public final String id;
    public final String name;
    public final Scenario scenario;
    public final Design design = new Design();

    /** 保守契約の月額収入。 */
    public final int monthlyRevenue;

    /** 稼働を始めた月（経営開始からの通算月数）。未稼働なら -1。 */
    public int startedMonth = -1;
    /** 直近の障害からの経過月数。連続発生を抑えるために使う。 */
    public int monthsSinceIncident = 99;
    /** これまでにこの拠点で起きた障害の回数。 */
    public int incidentCount;

    public Site(String id, String name, Scenario scenario, int monthlyRevenue) {
        this.id = id;
        this.name = name;
        this.scenario = scenario;
        this.monthlyRevenue = monthlyRevenue;
    }

    public boolean running() {
        return startedMonth >= 0;
    }

    /** システムの更新周期（月）。クラウド前提は3年、自前は5年。 */
    public int replacementCycle() {
        return scenario.boundary == Scenario.Boundary.SASE ? 36 : 60;
    }

    /** 更新までの残り月数。未稼働なら -1。 */
    public int monthsToReplacement(int currentMonth) {
        if (!running()) {
            return -1;
        }
        return startedMonth + replacementCycle() - currentMonth;
    }

    public static java.util.List<Site> defaultSites() {
        java.util.List<Site> list = new java.util.ArrayList<>();
        list.add(new Site("office", "オフィス", Scenario.office(), 45000));
        list.add(new Site("factory", "工場", Scenario.factory(), 55000));
        list.add(new Site("outdoor", "屋外現場", Scenario.outdoor(), 25000));
        return list;
    }
}
