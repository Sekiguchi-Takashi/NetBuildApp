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
    /** 在宅からのリモートアクセスVPNを用意するか。 */
    public boolean remoteVpn = false;
    /** 保守業者の接続を、常時つなぎっぱなしではなく必要なときだけ開ける運用にするか。 */
    public boolean vendorOnDemand = false;

    /** SASE案件のみ：検査を通さない例外経路を残すか。初期値は残したまま。 */
    public boolean saseBypass = true;
    /** SASE案件のみ：事務所に残るシステムへのアクセスもSASE経由の認証つきにするか。 */
    public boolean ztna = false;

    /** 手で並べたルール。null なら自動生成のルールを使う。 */
    public List<FirewallRule> customRules = null;
    public int prefixLength = 26;

    public static final int COST_BASE = 420000;
    public static final int COST_VLAN = 90000;
    public static final int COST_DMZ = 260000;
    public static final int COST_LARGE_SUBNET = 60000;
    public static final int COST_DNS_SECONDARY = 40000;
    public static final int COST_SERVER_SEGMENT = 80000;
    public static final int COST_PROXY = 150000;
    public static final int COST_REMOTE_VPN = 120000;
    public static final int COST_VENDOR_PROCESS = 30000;
    public static final int COST_SASE_BASE = 380000;
    public static final int COST_SASE_NO_BYPASS = 50000;
    public static final int COST_ZTNA = 120000;

    public int cost() {
        return cost(null);
    }

    public int cost(Scenario scenario) {
        if (scenario != null && scenario.boundary == Scenario.Boundary.SASE) {
            int total = COST_SASE_BASE;
            if (!saseBypass) {
                total += COST_SASE_NO_BYPASS;
            }
            if (ztna) {
                total += COST_ZTNA;
            }
            if (dnsRedundant) {
                total += COST_DNS_SECONDARY;
            }
            if (prefixLength <= 24) {
                total += COST_LARGE_SUBNET;
            }
            return total;
        }
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
        if (remoteVpn) {
            total += COST_REMOTE_VPN;
        }
        if (vendorOnDemand) {
            total += COST_VENDOR_PROCESS;
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
        return buildGraph(null);
    }

    public NetGraph buildGraph(Scenario scenario) {
        if (scenario != null && scenario.boundary == Scenario.Boundary.SASE) {
            return buildSaseGraph();
        }
        return buildOnPremGraph();
    }

    /**
     * SASE構成。拠点に境界を置かず、端末はどこにいてもSASEを通す。
     * 例外を残すと、そこだけ検査を通らない経路になる。
     */
    private NetGraph buildSaseGraph() {
        NetGraph g = new NetGraph(NetGraph.SOURCE_GAME);
        g.node("home", "host", "社員端末").put("zone", "remote");
        g.node("sase", "sase", "SASE").put("zone", "infra");
        g.node("net", "internet", "インターネット").put("zone", "internet");
        g.node("cloud", "cloud", "クラウド業務システム").put("zone", "cloud");
        g.node("sw", "switch", "事務所スイッチ").put("zone", "infra");
        g.node("fw", "firewall", "事務所ルーター").put("zone", "infra");
        g.node("srv", "server", "受発注システム").put("zone", "internal").put("vlan", "10");
        g.node("dns1", "dns", "DNS").put("zone", "internal").put("vlan", "10");

        g.edge("home", "sase", "wan");
        g.edge("sase", "net", "wan");
        g.edge("cloud", "net", "wan");
        g.edge("srv", "sw", "lan");
        g.edge("dns1", "sw", "lan");
        g.edge("sw", "fw", "uplink");
        g.edge("fw", "net", "wan");
        if (saseBypass) {
            g.edge("home", "net", "wan");
        }
        if (dnsRedundant) {
            g.node("dns2", "dns", "DNS副").put("zone", "internal").put("vlan", "10");
            g.edge("dns2", "sw", "lan");
        }
        return g;
    }

    private NetGraph buildOnPremGraph() {
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
        g.node("cloud", "cloud", "クラウド").put("zone", "cloud");
        g.node("vendor", "server", "保守業者").put("zone", "vendor");
        g.node("home", "host", "在宅端末").put("zone", "remote");
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
        g.edge("cloud", "net", "wan");
        g.edge("vendor", "net", "wan");
        g.edge("home", "net", "wan");
        return g;
    }

    /** 上から順に評価される。順序そのものが評価対象（MD §14）。 */
    public List<FirewallRule> buildRules() {
        return buildRules(null);
    }

    public List<FirewallRule> buildRules(Scenario scenario) {
        if (customRules != null) {
            return new ArrayList<>(customRules);
        }
        if (scenario != null && scenario.boundary == Scenario.Boundary.SASE) {
            return saseRules();
        }
        return defaultRules();
    }

    /** SASE案件のルール。事務所側は最小限で、判断の中心はアクセスの認可になる。 */
    private List<FirewallRule> saseRules() {
        List<FirewallRule> rules = new ArrayList<>();
        rules.add(new FirewallRule("remote", "cloud", "any", "any", true));
        rules.add(new FirewallRule("remote", "internet", "any", "any", true));
        rules.add(new FirewallRule("internal", "cloud", "any", "any", true));
        rules.add(new FirewallRule("internal", "internet", "any", "any", true));
        if (ztna) {
            rules.add(new FirewallRule("remote", "internal", "ztna", "any", true));
        }
        rules.add(new FirewallRule("internet", "internal", "any", "any", false));
        return rules;
    }

    /** 設計の選択から自動生成されるルール。手編集の初期値にもなる。 */
    public List<FirewallRule> defaultRules() {
        List<FirewallRule> rules = new ArrayList<>();
        if (fwGuestDeny) {
            rules.add(new FirewallRule("guest", "internal", "any", "any", false));
            rules.add(new FirewallRule("guest", "dmz", "any", "any", false));
        }
        rules.add(new FirewallRule("guest", "internet", "any", "any", true));
        rules.add(new FirewallRule("internal", "internet", "any", "any", true));
        rules.add(new FirewallRule("internal", "cloud", "any", "any", true));
        if (remoteVpn) {
            rules.add(new FirewallRule("remote", "internal", "vpn", "any", true));
        }
        if (!vendorOnDemand) {
            rules.add(new FirewallRule("vendor", "internal", "any", "any", true));
        }
        rules.add(new FirewallRule("internal", "dmz", "any", "any", true));
        if (dmz) {
            rules.add(new FirewallRule("internet", "dmz", "tcp", "443", true));
            rules.add(new FirewallRule("internet", "internal", "any", "any", false));
        } else {
            rules.add(new FirewallRule("internet", "internal", "tcp", "443", true));
        }
        return rules;
    }

    public String summary(Scenario scenario) {
        if (scenario != null && scenario.boundary == Scenario.Boundary.SASE) {
            return "検査の例外: " + (saseBypass ? "あり（迂回できる）" : "なし（全通信を検査）")
                    + " / 事務所システムへの接続: " + (ztna ? "SASE経由の認証つき" : "手段なし")
                    + " / DNS: " + (dnsRedundant ? "2台" : "1台")
                    + " / 事務所サブネット: /" + prefixLength;
        }
        return summary();
    }

    public String summary() {
        return "来客VLAN分離: " + (guestVlan ? "あり" : "なし")
                + " / DMZ: " + (dmz ? "あり" : "なし")
                + " / 来客Denyルール: " + (fwGuestDeny ? "あり" : "なし")
                + " / 社内サブネット: /" + prefixLength + "（" + usableHosts() + "台）"
                + " / DNS: " + (dnsRedundant ? "2台" : "1台")
                + " / 社内サーバー: " + (serverSharedWithWeb ? "公開サーバーと同区画" : "内部に分離")
                + " / プロキシ: " + (proxy ? "あり" : "なし")
                + " / リモートVPN: " + (remoteVpn ? "あり" : "なし")
                + " / 保守接続: " + (vendorOnDemand ? "必要時のみ" : "常時")
                + " / FWルール: " + (customRules == null ? "自動" : "手編集" + customRules.size() + "件");
    }
}
