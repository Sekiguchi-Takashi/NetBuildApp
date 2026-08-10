package com.appathy.netbuild;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MD §13 / §14 / §16 の判定部分。
 * LLM に判定させず、ここで決定的に解く。
 */
public class RuleEngine {

    public static class Path {
        public final boolean reachable;
        public final List<String> hops;
        public final String blockedBy;

        Path(boolean reachable, List<String> hops, String blockedBy) {
            this.reachable = reachable;
            this.hops = hops;
            this.blockedBy = blockedBy;
        }

        public String describe(NetGraph g) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hops.size(); i++) {
                NetGraph.Node n = g.find(hops.get(i));
                sb.append(n == null ? hops.get(i) : n.label);
                if (i < hops.size() - 1) {
                    sb.append(" → ");
                }
            }
            if (!reachable && blockedBy != null) {
                sb.append("  [遮断: ").append(blockedBy).append("]");
            }
            return sb.toString();
        }
    }

    private final List<FirewallRule> rules;

    public RuleEngine(List<FirewallRule> rules) {
        this.rules = rules;
    }

    /**
     * CanReach(source, destination)。
     * L2スイッチは VLAN が一致する場合のみ中継し、Firewall はゾーン間ルールで判定する。
     */
    public Path canReach(NetGraph g, String fromId, String toId) {
        Set<String> visited = new HashSet<>();
        Deque<List<String>> queue = new ArrayDeque<>();
        List<String> start = new ArrayList<>();
        start.add(fromId);
        queue.add(start);
        visited.add(fromId);
        String lastBlock = null;

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);
            if (current.equals(toId)) {
                return new Path(true, path, null);
            }
            for (String next : g.neighbors(current)) {
                if (visited.contains(next)) {
                    continue;
                }
                String block = blockReason(g, path, current, next, toId);
                if (block != null) {
                    lastBlock = block;
                    continue;
                }
                visited.add(next);
                List<String> extended = new ArrayList<>(path);
                extended.add(next);
                queue.add(extended);
            }
        }
        List<String> failed = new ArrayList<>();
        failed.add(fromId);
        failed.add(toId);
        return new Path(false, failed, lastBlock);
    }

    /** 中継ノードを通過できない理由を返す。通過できるなら null。 */
    private String blockReason(NetGraph g, List<String> path, String current, String next, String finalDest) {
        NetGraph.Node currentNode = g.find(current);
        if (currentNode == null || path.size() < 2) {
            return null;
        }
        String previous = path.get(path.size() - 2);
        NetGraph.Node prevNode = g.find(previous);
        NetGraph.Node nextNode = g.find(next);
        if (prevNode == null || nextNode == null) {
            return null;
        }

        if ("switch".equals(currentNode.type)) {
            String prevVlan = prevNode.attr("vlan");
            String nextVlan = nextNode.attr("vlan");
            if (prevVlan != null && nextVlan != null && !prevVlan.equals(nextVlan)) {
                return "VLAN " + prevVlan + " と " + nextVlan + " はL2では中継されない";
            }
        }

        if ("firewall".equals(currentNode.type)) {
            String srcZone = zoneOf(g, path.get(0));
            String dstZone = zoneOf(g, finalDest);
            FirewallRule matched = FirewallRule.firstMatch(rules, srcZone, dstZone);
            if (matched == null) {
                return "Firewall 暗黙Deny (" + srcZone + " → " + dstZone + ")";
            }
            if (!matched.allow) {
                return "Firewall ルール: " + srcZone + " → " + dstZone + " Deny";
            }
        }
        return null;
    }

    private String zoneOf(NetGraph g, String id) {
        NetGraph.Node n = g.find(id);
        if (n == null) {
            return "unknown";
        }
        String zone = n.attr("zone");
        return zone == null ? "unknown" : zone;
    }

    public List<FirewallRule> rules() {
        return rules;
    }
}
