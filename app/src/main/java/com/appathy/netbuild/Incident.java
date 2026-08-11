package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MD §25。発生した障害と、プレイヤーが持つ仮説の確率を保持する。 */
public class Incident {

    public enum Cause {
        LINK_DOWN("スイッチ〜Firewall間のリンク断",
                "全員インターネットに繋がりません。社内の共有フォルダは見えています",
                "配線とポート状態を確認して復旧"),
        WAN_DOWN("ISP側の回線障害",
                "ネットが繋がりません。ルーターのランプは点いています",
                "ISPに連絡。復旧待ちの間はモバイル回線で代替"),
        DNS_DOWN("DNSサーバー停止",
                "サイトが開きません。社内システムはIPを直接入れれば使えます",
                "DNSを冗長化し、セカンダリを設定"),
        IP_EXHAUSTED("IPアドレス枯渇",
                "今週入った人の端末だけ繋がりません",
                "サブネットを広げるかDHCPプールを見直す"),
        GUEST_INTRUSION("来客セグメントから社内への侵入",
                "共有サーバーに見覚えのないアクセス履歴があります",
                "来客をVLANで分離する"),
        MALWARE_C2("端末のマルウェア感染（外部への通信）",
                "回線業者から連絡が来ました。うちの中の端末が、知らない海外のサイトに大量にアクセスしているそうです",
                "プロキシで外向き通信を集約し、記録を残して危険な宛先を遮断する"),
        SERVER_EXPOSED("社内サーバーが外部から見えている",
                "取引先から連絡がありました。うちの見積書がネットで開けるそうです",
                "社内サーバーを公開サーバーとは別の区画に移す"),
        WEB_COMPROMISE("公開サーバーの侵害",
                "採用ページが書き換えられています",
                "公開サーバーをDMZに分離する");

        public final String label;
        public final String symptom;
        public final String fix;

        Cause(String label, String symptom, String fix) {
            this.label = label;
            this.symptom = symptom;
            this.fix = fix;
        }
    }

    public final Cause cause;
    public final int day;
    public final Map<Cause, Double> belief = new LinkedHashMap<>();
    public final List<String> log = new ArrayList<>();
    public boolean resolved;

    public Incident(Cause cause, int day, List<Cause> candidates) {
        this.cause = cause;
        this.day = day;
        double even = 1.0 / candidates.size();
        for (Cause c : candidates) {
            belief.put(c, even);
        }
    }

    public List<Map.Entry<Cause, Double>> ranked() {
        List<Map.Entry<Cause, Double>> list = new ArrayList<>(belief.entrySet());
        list.sort(new java.util.Comparator<Map.Entry<Cause, Double>>() {
            public int compare(Map.Entry<Cause, Double> a, Map.Entry<Cause, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        return list;
    }

    public String describeBelief() {
        StringBuilder sb = new StringBuilder("現在の仮説\n");
        for (Map.Entry<Cause, Double> e : ranked()) {
            int percent = (int) Math.round(e.getValue() * 100);
            sb.append("  ").append(bar(percent)).append(' ')
                    .append(percent).append("%  ").append(e.getKey().label).append('\n');
        }
        return sb.toString();
    }

    private String bar(int percent) {
        int filled = Math.max(0, Math.min(10, percent / 10));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '#' : '.');
        }
        return sb.toString();
    }
}
