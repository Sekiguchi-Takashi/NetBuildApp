package com.appathy.netbuild;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/** Day・信頼・設計・進行中の障害を保存する。再起動しても続きから遊べるようにする。 */
public class GameState {

    private static final String PREF = "netbuild_state";
    private static final String KEY = "state";

    private static String keyFor(Scenario scenario) {
        return KEY + ":" + scenario.id;
    }

    public int day;
    public int trust = 50;
    public int extraCost;
    public boolean easyMode;
    public int extraBudget;
    public int negotiations;
    public final Set<String> occurredFaults = new LinkedHashSet<>();

    public void save(Context context, Design design, Scenario scenario, Incident incident) {
        try {
            JSONObject root = new JSONObject();
            root.put("day", day);
            root.put("trust", trust);
            root.put("extraCost", extraCost);
            root.put("easyMode", easyMode);
            root.put("extraBudget", extraBudget);
            root.put("negotiations", negotiations);
            root.put("occurred", new JSONArray(occurredFaults));

            JSONObject d = new JSONObject();
            d.put("guestVlan", design.guestVlan);
            d.put("dmz", design.dmz);
            d.put("fwGuestDeny", design.fwGuestDeny);
            d.put("dnsRedundant", design.dnsRedundant);
            d.put("serverSharedWithWeb", design.serverSharedWithWeb);
            d.put("proxy", design.proxy);
            d.put("remoteVpn", design.remoteVpn);
            d.put("vendorOnDemand", design.vendorOnDemand);
            d.put("saseBypass", design.saseBypass);
            d.put("ztna", design.ztna);
            if (design.customRules != null) {
                JSONArray rules = new JSONArray();
                for (FirewallRule rule : design.customRules) {
                    JSONObject o = new JSONObject();
                    o.put("src", rule.sourceZone);
                    o.put("dst", rule.destZone);
                    o.put("proto", rule.protocol);
                    o.put("port", rule.port);
                    o.put("allow", rule.allow);
                    rules.put(o);
                }
                d.put("rules", rules);
            }
            d.put("prefixLength", design.prefixLength);
            root.put("design", d);

            JSONArray revealed = new JSONArray();
            for (Scenario.Hidden h : scenario.hidden) {
                revealed.put(h.revealed);
            }
            root.put("revealed", revealed);

            if (incident != null) {
                JSONObject inc = new JSONObject();
                inc.put("cause", incident.cause.name());
                inc.put("day", incident.day);
                inc.put("resolved", incident.resolved);
                inc.put("log", new JSONArray(incident.log));
                JSONObject belief = new JSONObject();
                for (java.util.Map.Entry<Incident.Cause, Double> e : incident.belief.entrySet()) {
                    belief.put(e.getKey().name(), e.getValue());
                }
                inc.put("belief", belief);
                root.put("incident", inc);
            }
            prefs(context).edit().putString(keyFor(scenario), root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** 保存済みの状態を読み込み、進行中の障害があれば返す。 */
    public Incident load(Context context, Design design, Scenario scenario) {
        String stored = prefs(context).getString(keyFor(scenario), null);
        if (stored == null) {
            // その案件の記録が無いときは、前の案件の進行を引き継がない
            clear();
            return null;
        }
        try {
            JSONObject root = new JSONObject(stored);
            day = root.optInt("day");
            trust = root.optInt("trust", 50);
            extraCost = root.optInt("extraCost");
            easyMode = root.optBoolean("easyMode");
            extraBudget = root.optInt("extraBudget");
            negotiations = root.optInt("negotiations");
            JSONArray occurred = root.optJSONArray("occurred");
            if (occurred != null) {
                for (int i = 0; i < occurred.length(); i++) {
                    occurredFaults.add(occurred.optString(i));
                }
            }
            JSONObject d = root.optJSONObject("design");
            if (d != null) {
                design.guestVlan = d.optBoolean("guestVlan");
                design.dmz = d.optBoolean("dmz");
                design.fwGuestDeny = d.optBoolean("fwGuestDeny");
                design.dnsRedundant = d.optBoolean("dnsRedundant");
                design.serverSharedWithWeb = d.optBoolean("serverSharedWithWeb", true);
                design.proxy = d.optBoolean("proxy");
                design.remoteVpn = d.optBoolean("remoteVpn");
                design.vendorOnDemand = d.optBoolean("vendorOnDemand");
                design.saseBypass = d.optBoolean("saseBypass", true);
                design.ztna = d.optBoolean("ztna");
                JSONArray rules = d.optJSONArray("rules");
                if (rules != null) {
                    design.customRules = new java.util.ArrayList<>();
                    for (int i = 0; i < rules.length(); i++) {
                        JSONObject o = rules.getJSONObject(i);
                        design.customRules.add(new FirewallRule(o.optString("src"), o.optString("dst"),
                                o.optString("proto"), o.optString("port"), o.optBoolean("allow")));
                    }
                }
                design.prefixLength = d.optInt("prefixLength", 26);
            }
            JSONArray revealed = root.optJSONArray("revealed");
            if (revealed != null) {
                for (int i = 0; i < revealed.length() && i < scenario.hidden.size(); i++) {
                    scenario.hidden.get(i).revealed = revealed.optBoolean(i);
                }
            }
            JSONObject inc = root.optJSONObject("incident");
            if (inc == null) {
                return null;
            }
            Incident.Cause cause = Incident.Cause.valueOf(inc.getString("cause"));
            Incident incident = new Incident(cause, inc.optInt("day"), new java.util.ArrayList<>(incident_causes(inc)));
            incident.resolved = inc.optBoolean("resolved");
            JSONArray log = inc.optJSONArray("log");
            if (log != null) {
                for (int i = 0; i < log.length(); i++) {
                    incident.log.add(log.optString(i));
                }
            }
            JSONObject belief = inc.optJSONObject("belief");
            if (belief != null) {
                java.util.Iterator<String> keys = belief.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    incident.belief.put(Incident.Cause.valueOf(key), belief.optDouble(key));
                }
            }
            return incident;
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<Incident.Cause> incident_causes(JSONObject inc) {
        java.util.List<Incident.Cause> list = new java.util.ArrayList<>();
        JSONObject belief = inc.optJSONObject("belief");
        if (belief != null) {
            java.util.Iterator<String> keys = belief.keys();
            while (keys.hasNext()) {
                try {
                    list.add(Incident.Cause.valueOf(keys.next()));
                } catch (Exception ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            list.add(Incident.Cause.LINK_DOWN);
        }
        return list;
    }

    /** メモリ上の進行だけを初期化する。保存データには触らない。 */
    private void clear() {
        day = 0;
        trust = 50;
        extraCost = 0;
        extraBudget = 0;
        negotiations = 0;
        occurredFaults.clear();
    }

    public void reset(Context context, Scenario scenario) {
        prefs(context).edit().remove(keyFor(scenario)).apply();
        clear();
    }

    private SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
