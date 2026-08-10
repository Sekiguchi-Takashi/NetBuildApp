package com.appathy.netbuild;

import java.util.List;

/** MD §14。順序を持ち、先にマッチしたルールが勝つ。 */
public class FirewallRule {

    public static final String ANY = "any";

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
