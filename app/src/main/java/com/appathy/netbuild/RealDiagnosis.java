package com.appathy.netbuild;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 実機の測定結果を、ゲームで使っている観点でそのまま点検する。
 * ゲーム内の設計と同じ NetGraph に落とすので、見る目線が揃う。
 */
public class RealDiagnosis {

    public static class Report {
        public NetGraph graph;
        public final List<String> facts = new ArrayList<>();
        public final List<String> findings = new ArrayList<>();
        public final List<String> lessons = new ArrayList<>();
    }

    private final ProbeRunner probes = new ProbeRunner();

    /** 接続情報の取得だけ。ネットワークへ何も投げない。 */
    public Report inspect(Context context) {
        Report report = new Report();
        NetGraph g = new DeviceNetworkCollector(context).collect();
        report.graph = g;

        NetGraph.Node self = g.find(DeviceNetworkCollector.SELF);
        if (self != null) {
            addFact(report, "接続方式", self.attr("transport"));
            addFact(report, "インターフェース", self.attr("interface"));
            addFact(report, "IPアドレス", self.attr("addresses"));
            addFact(report, "サブネット", self.attr("subnet"));
            addFact(report, "MTU", self.attr("mtu"));
        }
        for (NetGraph.Node n : g.nodes) {
            if ("accessPoint".equals(n.type)) {
                addFact(report, "接続先SSID", n.label);
                addFact(report, "電波強度", n.attr("rssi") == null ? null : n.attr("rssi") + " dBm");
                addFact(report, "周波数帯", n.attr("band"));
            }
        }

        String defaultGw = null;
        int dnsCount = 0;
        for (NetGraph.Route r : g.routes) {
            if (r.defaultRoute && r.gateway != null) {
                defaultGw = r.gateway;
            }
        }
        for (NetGraph.Node n : g.nodes) {
            if ("dns".equals(n.type)) {
                dnsCount++;
            }
        }
        addFact(report, "デフォルトゲートウェイ", defaultGw);
        addFact(report, "DNSサーバー", String.valueOf(dnsCount) + " 台");

        review(report, g, self, defaultGw, dnsCount);
        return report;
    }

    /** 疎通も測る。ping / 名前解決 / TCP を実際に投げる。 */
    public Report probe(Context context) {
        Report report = inspect(context);
        NetGraph g = report.graph;

        String gateway = null;
        String dns = null;
        for (NetGraph.Node n : g.nodes) {
            if ("gateway".equals(n.type) && gateway == null) {
                gateway = n.label;
            }
            if ("dns".equals(n.type) && dns == null) {
                dns = n.label;
            }
        }
        if (gateway != null) {
            g.reachability.add(probes.ping(gateway));
        }
        if (dns != null) {
            g.reachability.add(probes.ping(dns));
        }
        g.reachability.add(probes.ping("8.8.8.8"));
        g.reachability.add(probes.resolve("example.com"));
        g.reachability.add(probes.tcp("example.com", 443, 3000));

        boolean localOk = gateway != null && reached(g, gateway);
        boolean externalOk = reached(g, "8.8.8.8");
        boolean dnsOk = false;
        for (NetGraph.Reachability r : g.reachability) {
            if ("dns".equals(r.method)) {
                dnsOk = r.reached;
            }
        }

        report.findings.add("--- 疎通の結果 ---");
        for (NetGraph.Reachability r : g.reachability) {
            report.findings.add((r.reached ? "○ " : "× ") + r.to
                    + "（" + r.method + "）" + (r.detail == null ? "" : " " + r.detail));
        }

        if (!localOk && !externalOk) {
            report.findings.add("切り分け: ローカルも外部も不通。リンク層かIP設定の問題です");
        } else if (externalOk && !dnsOk) {
            report.findings.add("切り分け: IPでは届くのに名前解決だけ失敗。DNS側の問題です");
            report.lessons.add("ゲームの「DNSサーバー停止」と同じ症状です。"
                    + "この切り分けは、ゲーム内でping・名前解決の順に打つのと同じ考え方です");
        } else if (!externalOk) {
            report.findings.add("切り分け: ローカルは通るが外部がNG。"
                    + "デフォルトルートかゲートウェイ以遠を疑います");
            report.lessons.add("ゲームの「リンク断」「回線障害」の切り分けと同じ形です");
        } else {
            report.findings.add("切り分け: 経路・名前解決とも正常です");
        }
        return report;
    }

    private boolean reached(NetGraph g, String target) {
        for (NetGraph.Reachability r : g.reachability) {
            if (target.equals(r.to) && r.reached) {
                return true;
            }
        }
        return false;
    }

    /** ゲームで使っている観点を、そのまま実機に当てる。 */
    private void review(Report report, NetGraph g, NetGraph.Node self,
                        String defaultGw, int dnsCount) {
        if (defaultGw == null) {
            report.findings.add("指摘: デフォルトルートがありません。外に出られない状態です");
        }

        if (dnsCount == 0) {
            report.findings.add("指摘: DNSサーバーが設定されていません");
        } else if (dnsCount == 1) {
            report.findings.add("指摘: DNSが1台だけです。単一障害点になっています");
            report.lessons.add("ゲームでDNSを2台にするかを選ぶのと同じ話です。"
                    + "いま使っているネットワークも、この1台が止まると名前解決ができなくなります");
        } else {
            report.findings.add("良: DNSが " + dnsCount + " 台あります");
        }

        if (self != null) {
            if ("true".equals(self.attr("vpn"))) {
                report.findings.add("注意: VPN経由の接続です。経路の判定はVPN側の設定に依存します");
                report.lessons.add("ゲームのリモートアクセスVPNと同じ仕組みです。"
                        + "つないだ端末が社内と同じ扱いになるかどうかが、設計上の分かれ目でした");
            }
            if ("true".equals(self.attr("captivePortal"))) {
                report.findings.add("注意: キャプティブポータル配下です。認証前は外部通信が遮断されます");
            }
            if ("false".equals(self.attr("validated"))) {
                report.findings.add("注意: インターネット接続が検証されていません");
            }
            String capacity = self.attr("capacity");
            if (capacity != null) {
                report.findings.add("IP設計: " + capacity);
                report.lessons.add("ゲームで /24 と /26 を選ぶのと同じ計算です。"
                        + "いまつないでいるネットワークが何台まで収容できるかが分かります");
            }
        }

        boolean guestLike = false;
        for (NetGraph.Node n : g.nodes) {
            if ("accessPoint".equals(n.type) && n.label != null) {
                String ssid = n.label.toLowerCase();
                if (ssid.contains("guest") || ssid.contains("free") || ssid.contains("public")) {
                    guestLike = true;
                }
            }
        }
        if (guestLike) {
            report.findings.add("注意: 来客用や公衆のWi-Fiに見えるSSIDです");
            report.lessons.add("ゲームで来客セグメントを分離したのと同じ立場に、いま自分がいます。"
                    + "分離されていないネットワークなら、同じAPの他の端末から見えている可能性があります");
        }

        if (report.lessons.isEmpty()) {
            report.lessons.add("いま見えている範囲では、ゲームで扱った論点に当てはまるものはありません");
        }
    }

    public String format(Report report, boolean probed) {
        StringBuilder sb = new StringBuilder();
        for (String f : report.facts) {
            sb.append(f).append('\n');
        }
        sb.append('\n');
        for (String f : report.findings) {
            sb.append(f).append('\n');
        }
        if (!report.lessons.isEmpty()) {
            sb.append("\n--- ゲームとのつながり ---\n");
            for (String l : report.lessons) {
                sb.append("・").append(l).append('\n');
            }
        }
        if (!probed) {
            sb.append("\n※ここまでは設定を読んだだけです。"
                    + "実際に通信して確かめるには「疎通も測る」を選んでください。");
        }
        return sb.toString();
    }

    private void addFact(Report report, String label, String value) {
        if (value != null && !value.isEmpty() && !"null".equals(value)) {
            report.facts.add(label + ": " + value);
        }
    }
}
