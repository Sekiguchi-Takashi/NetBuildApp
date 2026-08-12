package com.appathy.netbuild;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** 実端末の現在の接続状態を NetGraph に落とし込む。 */
public class DeviceNetworkCollector {

    public static final String SELF = "self";

    private final Context context;

    public DeviceNetworkCollector(Context context) {
        this.context = context.getApplicationContext();
    }

    public NetGraph collect() {
        NetGraph graph = new NetGraph(NetGraph.SOURCE_DEVICE);
        NetGraph.Node self = graph.node(SELF, "host", "この端末");

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            graph.notes.add("ConnectivityManager を取得できません");
            return graph;
        }

        Network active = cm.getActiveNetwork();
        if (active == null) {
            graph.notes.add("有効なネットワークがありません");
            return graph;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        if (caps != null) {
            self.put("transport", transportName(caps));
            self.put("validated", String.valueOf(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
            self.put("captivePortal", String.valueOf(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)));
            self.put("vpn", String.valueOf(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)));
            self.put("meteredNot", String.valueOf(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)));
            self.put("downstreamKbps", String.valueOf(caps.getLinkDownstreamBandwidthKbps()));
            self.put("upstreamKbps", String.valueOf(caps.getLinkUpstreamBandwidthKbps()));
        }

        LinkProperties lp = cm.getLinkProperties(active);
        if (lp != null) {
            self.put("interface", lp.getInterfaceName());
            self.put("mtu", String.valueOf(lp.getMtu()));
            self.put("domains", lp.getDomains());

            StringBuilder addrs = new StringBuilder();
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (addrs.length() > 0) {
                    addrs.append(", ");
                }
                addrs.append(la.toString());
                SubnetCalc subnet = SubnetCalc.from(la.getAddress(), la.getPrefixLength());
                if (subnet != null) {
                    self.put("subnet", subnet.network + "/" + subnet.prefixLength);
                    self.put("netmask", subnet.mask);
                    self.put("broadcast", subnet.broadcast);
                    self.put("capacity", subnet.capacityNote(0));
                }
            }
            self.put("addresses", addrs.toString());

            int dnsIndex = 0;
            for (InetAddress dns : lp.getDnsServers()) {
                String id = "dns" + (dnsIndex++);
                graph.node(id, "dns", dns.getHostAddress());
                graph.edge(SELF, id, "resolver");
            }

            int gwIndex = 0;
            for (RouteInfo route : lp.getRoutes()) {
                String destination = route.getDestination() == null ? "-" : route.getDestination().toString();
                InetAddress gwAddr = route.getGateway();
                String gateway = gwAddr == null ? null : gwAddr.getHostAddress();
                graph.routes.add(new NetGraph.Route(destination, gateway, route.getInterface(), route.isDefaultRoute()));
                if (gateway != null && !isUnspecified(gateway)) {
                    String id = "gw" + (gwIndex++);
                    graph.node(id, "gateway", gateway).put("default", String.valueOf(route.isDefaultRoute()));
                    graph.edge(SELF, id, route.isDefaultRoute() ? "default-route" : "route");
                }
            }
        }

        collectWifi(graph, self);
        return graph;
    }

    private void collectWifi(NetGraph graph, NetGraph.Node self) {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) {
            return;
        }
        WifiInfo info = wm.getConnectionInfo();
        if (info == null || info.getNetworkId() == -1) {
            return;
        }
        String ssid = info.getSSID();
        if (ssid == null || ssid.contains("unknown ssid")) {
            graph.notes.add("SSID が取得できません。位置情報の権限を確認してください");
        }
        NetGraph.Node ap = graph.node("ap", "accessPoint", ssid == null ? "(SSID不明)" : ssid);
        ap.put("bssid", info.getBSSID());
        ap.put("rssi", String.valueOf(info.getRssi()));
        ap.put("linkSpeedMbps", String.valueOf(info.getLinkSpeed()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ap.put("frequencyMHz", String.valueOf(info.getFrequency()));
            ap.put("band", info.getFrequency() >= 5000 ? "5GHz以上" : "2.4GHz");
        }
        graph.edge(SELF, "ap", "wifi");
        self.put("wifiConnected", "true");
    }

    private boolean isUnspecified(String address) {
        return "0.0.0.0".equals(address) || "::".equals(address);
    }

    private String transportName(NetworkCapabilities caps) {
        List<String> found = new ArrayList<>();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            found.add("Wi-Fi");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            found.add("Cellular");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            found.add("Ethernet");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            found.add("VPN");
        }
        return found.isEmpty() ? "unknown" : String.join("+", found);
    }
}
