package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.List;

/** プレイヤーが選ぶ設計。ここから構成グラフと Firewall ルールを生成する。 */
public class Design {

    public boolean guestVlan = false;
    public boolean dmz = false;
    public boolean fwGuestDeny = false;
    public boolean dnsRedundant = false;
    /** 社内サーバーを公開サーバーと同じ区画に置いたままにするか。初期値は同居（安いが危ない）。 */
    public boolean serverSharedWithWeb = true;
    public boolean proxy = false;
    public int prefixLength = 26;

    public static final int COST_BASE = 420000;
    public static final int COST_VLAN = 90000;
    public static final int COST_DMZ = 260000;
    public static final int COST_LARGE_SUBNET = 60000;
    public static final int COST_DNS_SECONDARY = 40000;
    public static final int COST_SERVER_SEGMENT = 80000;
    public static final int COST_PROXY = 150000;

    public int cost() {
        int total = COST_BASE;
        if (guestVlan) {
            total += COST_VLAN;
        }
        if (dmz) {
            total += COST_DMZ;
        }
        if (prefixLength <= 24) {
            total += COST_LARGE_SUBNET;
        }
        if (dnsRedundant) {
            total += COST_DNS_SECONDARY;
        }
        if (!serverSharedWithWeb) {
            total += COST_SERVER_SEGMENT;
        }
        if (proxy) {
            total += COST_PROXY;
        }
        return total;
    }

    public long usableHosts() {
        if (prefixLength >= 31) {
            return 0;
        }
        return (1L << (32 - prefixLength)) - 2;
    }

    public NetGraph buildGraph() {
        NetGraph g = new NetGraph(NetGraph.SOURCE_GAME);
        String employeeVlan = "10";
        String guestVlanId = guestVlan ? "20" : "10";
        String serverVlan = dmz ? "30" : "10";

        g.node("pc", "host", "社員PC").put("zone", "internal").put("vlan", employeeVlan);
        g.node("guest", "host", "来客端末").put("zone", "guest").put("vlan", guestVlanId);
        g.node("web", "server", "Webサーバー")
                .put("zone", dmz ? "dmz" : "internal").put("vlan", serverVlan);
        g.node("sw", "switch", "スイッチ").put("zone", "infra");
        g.node("fw", "firewall", "Firewall").put("zone", "infra");
        g.node("net", "internet", "インターネット").put("zone", "internet");
        // 同居のままだと、社内サーバーは公開サーバーと同じ区画に置かれる
        String serverZone = serverSharedWithWeb ? (dmz ? "dmz" : "internal") : "internal";
        String serverVlanId = serverSharedWithWeb ? serverVlan : employeeVlan;
        g.node("srv", "server", "社内サーバー").put("zone", serverZone).put("vlan", serverVlanId);
        if (proxy) {
            g.node("proxy", "proxy", "プロキシ").put("zone", "internal").put("vlan", employeeVlan);
        }
        g.node("dns1", "dns", "DNS").put("zone", "internal").put("vlan", employeeVlan);
        if (dnsRedundant) {
            g.node("dns2", "dns", "DNS副").put("zone", "internal").put("vlan", employeeVlan);
        }

        g.edge("pc", "sw", "lan");
        g.edge("guest", "sw", "lan");
        g.edge("web", "sw", "lan");
        g.edge("srv", "sw", "lan");
        if (proxy) {
            g.edge("proxy", "sw", "lan");
        }
        g.edge("dns1", "sw", "lan");
        if (dnsRedundant) {
            g.edge("dns2", "sw", "lan");
        }
        g.edge("sw", "fw", "uplink");
        g.edge("fw", "net", "wan");
        return g;
    }

    /** 上から順に評価される。順序そのものが評価対象（MD §14）。 */
    public List<FirewallRule> buildRules() {
        List<FirewallRule> rules = new ArrayList<>();
        if (fwGuestDeny) {
            rules.add(new FirewallRule("guest", "internal", "any", "any", false));
            rules.add(new FirewallRule("guest", "dmz", "any", "any", false));
        }
        rules.add(new FirewallRule("guest", "internet", "any", "any", true));
        rules.add(new FirewallRule("internal", "internet", "any", "any", true));
        rules.add(new FirewallRule("internal", "dmz", "any", "any", true));
        if (dmz) {
            rules.add(new FirewallRule("internet", "dmz", "tcp", "443", true));
            rules.add(new FirewallRule("internet", "internal", "any", "any", false));
        } else {
            rules.add(new FirewallRule("internet", "internal", "tcp", "443", true));
        }
        return rules;
    }

    public String summary() {
        return "来客VLAN分離: " + (guestVlan ? "あり" : "なし")
                + " / DMZ: " + (dmz ? "あり" : "なし")
                + " / 来客Denyルール: " + (fwGuestDeny ? "あり" : "なし")
                + " / 社内サブネット: /" + prefixLength + "（" + usableHosts() + "台）"
                + " / DNS: " + (dnsRedundant ? "2台" : "1台")
                + " / 社内サーバー: " + (serverSharedWithWeb ? "公開サーバーと同区画" : "内部に分離")
                + " / プロキシ: " + (proxy ? "あり" : "なし");
    }
}
