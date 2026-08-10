package com.appathy.netbuild;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RouteHQApp と共有するスキーマ（schema: netgraph/1）。
 * source = "device" なら実機の実測、"game" ならゲーム内の設計。
 * 両者を同じ RuleEngine に渡せることがこのアプリの前提。
 */
public class NetGraph {

    public static final String SOURCE_DEVICE = "device";
    public static final String SOURCE_GAME = "game";

    public static class Node {
        public final String id;
        public final String type;
        public final String label;
        public final Map<String, String> attrs = new LinkedHashMap<>();

        public Node(String id, String type, String label) {
            this.id = id;
            this.type = type;
            this.label = label;
        }

        public Node put(String key, String value) {
            if (value != null) {
                attrs.put(key, value);
            }
            return this;
        }

        public String attr(String key) {
            return attrs.get(key);
        }
    }

    public static class Edge {
        public final String from;
        public final String to;
        public final String kind;

        public Edge(String from, String to, String kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }

    public static class Route {
        public final String destination;
        public final String gateway;
        public final String iface;
        public final boolean defaultRoute;

        public Route(String destination, String gateway, String iface, boolean defaultRoute) {
            this.destination = destination;
            this.gateway = gateway;
            this.iface = iface;
            this.defaultRoute = defaultRoute;
        }
    }

    public static class Reachability {
        public final String from;
        public final String to;
        public final String method;
        public final boolean reached;
        public final String detail;

        public Reachability(String from, String to, String method, boolean reached, String detail) {
            this.from = from;
            this.to = to;
            this.method = method;
            this.reached = reached;
            this.detail = detail;
        }
    }

    public final String source;
    public final List<Node> nodes = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    public final List<Route> routes = new ArrayList<>();
    public final List<Reachability> reachability = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    public NetGraph(String source) {
        this.source = source;
    }

    public Node node(String id, String type, String label) {
        Node n = new Node(id, type, label);
        nodes.add(n);
        return n;
    }

    public void edge(String from, String to, String kind) {
        edges.add(new Edge(from, to, kind));
        edges.add(new Edge(to, from, kind));
    }

    public Node find(String id) {
        for (Node n : nodes) {
            if (n.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    public List<String> neighbors(String id) {
        List<String> out = new ArrayList<>();
        for (Edge e : edges) {
            if (e.from.equals(id)) {
                out.add(e.to);
            }
        }
        return out;
    }

    /** RouteHQApp が共有した JSON を読み込む。 */
    public static NetGraph fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        NetGraph g = new NetGraph(root.optString("source", SOURCE_DEVICE));
        JSONArray nodes = root.optJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject o = nodes.getJSONObject(i);
                Node n = g.node(o.optString("id"), o.optString("type"), o.optString("label"));
                JSONObject attrs = o.optJSONObject("attrs");
                if (attrs != null) {
                    Iterator<String> keys = attrs.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        n.put(key, attrs.optString(key));
                    }
                }
            }
        }
        JSONArray edges = root.optJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.length(); i++) {
                JSONObject o = edges.getJSONObject(i);
                g.edges.add(new Edge(o.optString("from"), o.optString("to"), o.optString("kind")));
            }
        }
        JSONArray routes = root.optJSONArray("routes");
        if (routes != null) {
            for (int i = 0; i < routes.length(); i++) {
                JSONObject o = routes.getJSONObject(i);
                g.routes.add(new Route(o.optString("destination"),
                        o.isNull("gateway") ? null : o.optString("gateway"),
                        o.isNull("interface") ? null : o.optString("interface"),
                        o.optBoolean("default")));
            }
        }
        JSONArray reach = root.optJSONArray("reachability");
        if (reach != null) {
            for (int i = 0; i < reach.length(); i++) {
                JSONObject o = reach.getJSONObject(i);
                g.reachability.add(new Reachability(o.optString("from"), o.optString("to"),
                        o.optString("method"), o.optBoolean("reached"),
                        o.isNull("detail") ? null : o.optString("detail")));
            }
        }
        JSONArray notes = root.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                g.notes.add(notes.optString(i));
            }
        }
        return g;
    }
}
