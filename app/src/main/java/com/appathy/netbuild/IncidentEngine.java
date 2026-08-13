package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 設計の弱点を種にして障害を起こす。
 * 弱点由来の原因は重みを高くするため、手抜き設計ほど自分に返ってくる。
 */
public class IncidentEngine {

    private final Random random = new Random();

    /** 設計から見て起こりうる原因の一覧。診断の候補にもなる。 */
    public List<Incident.Cause> candidates(Scenario scenario, Design design) {
        List<Incident.Cause> list = new ArrayList<>();
        list.add(Incident.Cause.LINK_DOWN);
        list.add(Incident.Cause.WAN_DOWN);
        if (!design.dnsRedundant) {
            list.add(Incident.Cause.DNS_DOWN);
        }
        if (design.usableHosts() < scenario.futureUsers) {
            list.add(Incident.Cause.IP_EXHAUSTED);
        }
        if (!design.guestVlan) {
            list.add(Incident.Cause.GUEST_INTRUSION);
        }
        if (!design.dmz) {
            list.add(Incident.Cause.WEB_COMPROMISE);
        }
        if (design.serverSharedWithWeb) {
            list.add(Incident.Cause.SERVER_EXPOSED);
        }
        list.add(Incident.Cause.MALWARE_C2);
        if (!design.backup || !design.proxy) {
            list.add(Incident.Cause.RANSOMWARE);
        }
        if (design.redundantWan) {
            list.remove(Incident.Cause.WAN_DOWN);
            list.remove(Incident.Cause.LINK_DOWN);
        }
        return list;
    }

    /** 1日進めたときに障害が起きるかどうか。起きなければ null。 */
    public Incident nextDay(int day, Scenario scenario, Design design, Set<String> occurredFaults) {
        List<Incident.Cause> candidates = candidates(scenario, design);
        if (random.nextInt(100) >= 45) {
            return null;
        }
        List<Incident.Cause> weighted = new ArrayList<>();
        for (Incident.Cause c : candidates) {
            weighted.add(c);
            if (isWeaknessCause(c, design)) {
                weighted.add(c);
                weighted.add(c);
                if (occurredFaults.contains(c.name())) {
                    weighted.add(c);
                    weighted.add(c);
                    weighted.add(c);
                }
            }
        }
        Incident.Cause picked = weighted.get(random.nextInt(weighted.size()));
        return new Incident(picked, day, candidates);
    }

    public boolean isWeaknessCause(Incident.Cause c) {
        return isWeaknessCause(c, null);
    }

    public boolean isWeaknessCause(Incident.Cause c, Design design) {
        return c == Incident.Cause.IP_EXHAUSTED
                || c == Incident.Cause.GUEST_INTRUSION
                || c == Incident.Cause.WEB_COMPROMISE
                || c == Incident.Cause.SERVER_EXPOSED
                || (c == Incident.Cause.MALWARE_C2 && design != null && !design.proxy)
                || (c == Incident.Cause.RANSOMWARE && design != null && !design.backup);
    }
}
