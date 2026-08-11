package com.appathy.netbuild;

import java.util.List;

/** MD §14。順序を持ち、先にマッチしたルールが勝つ。 */
public class FirewallRule {

    public static final String ANY = "any";
    /** 手編集で選べるゾーン。機器を増やすときはここも増やす。 */
    public static final String[] ZONES = {"guest", "internal", "dmz", "internet"};

    public final String sourceZone;
    public final String destZone;
    public final String protocol;
    public final String port;
    public final boolean allow;

    public FirewallRule(String sourceZone, String destZone, String protocol, String port, boolean allow) {
        this.sourceZone = sourceZone;
        this.destZone = destZone;
        this.protocol = protocol;
        this.port = port;
        this.allow = allow;
    }

    public boolean matches(String src, String dst) {
        return (ANY.equals(sourceZone) || sourceZone.equals(src))
                && (ANY.equals(destZone) || destZone.equals(dst));
    }

    public String describe(int index) {
        return index + ". " + sourceZone + " → " + destZone + " : " + protocol + "/" + port
                + " " + (allow ? "Allow" : "Deny");
    }

    /** このルールが受け持つゾーンの組み合わせ。順序評価の重なり判定に使う。 */
    public java.util.List<String> coveredPairs() {
        return coveredPairs(ZONES);
    }

    public java.util.List<String> coveredPairs(String[] zones) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        for (String src : zones) {
            for (String dst : zones) {
                if (matches(src, dst)) {
                    pairs.add(src + ">" + dst);
                }
            }
        }
        return pairs;
    }

    /** 先にマッチしたルールを返す。どれにも当たらなければ null（暗黙 Deny）。 */
    public static FirewallRule firstMatch(List<FirewallRule> rules, String src, String dst) {
        for (FirewallRule r : rules) {
            if (r.matches(src, dst)) {
                return r;
            }
        }
        return null;
    }
}
