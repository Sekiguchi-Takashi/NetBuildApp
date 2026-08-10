package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.List;

/** RouteHQApp から共有された実測 NetGraph に対して、同じ観点でレビューする。 */
public class DeviceReview {

    public List<String> review(NetGraph g) {
        List<String> out = new ArrayList<>();
        NetGraph.Node self = g.find("self");

        boolean hasDefault = false;
        for (NetGraph.Route r : g.routes) {
            if (r.defaultRoute) {
                hasDefault = true;
                out.add("良: デフォルトルート " + r.destination + " via "
                        + (r.gateway == null ? "-" : r.gateway));
            }
        }
        if (!hasDefault) {
            out.add("指摘: デフォルトルートがありません。外部へ出られない状態です");
        }

        int dnsCount = 0;
        int lanDevices = 0;
        for (NetGraph.Node n : g.nodes) {
            if ("dns".equals(n.type)) {
                dnsCount++;
            }
            if ("lanDevice".equals(n.type)) {
                lanDevices++;
            }
        }
        if (dnsCount == 0) {
            out.add("指摘: DNSサーバーが設定されていません");
        } else if (dnsCount == 1) {
            out.add("指摘: DNSが1台のみ。冗長性がありません");
        } else {
            out.add("良: DNS " + dnsCount + " 台");
        }

        if (self != null) {
            if ("true".equals(self.attr("vpn"))) {
                out.add("注意: VPN経由の接続です。経路の判定はVPN側の設定に依存します");
            }
            if ("true".equals(self.attr("captivePortal"))) {
                out.add("指摘: キャプティブポータル配下です。認証前は外部通信が遮断されます");
            }
            if ("false".equals(self.attr("validated"))) {
                out.add("指摘: インターネット接続が検証されていません");
            }
            String capacity = self.attr("capacity");
            if (capacity != null) {
                out.add("IP設計: " + capacity + "（将来台数と比較してください）");
            }
            String mtu = self.attr("mtu");
            if (mtu != null && !"0".equals(mtu)) {
                out.add("MTU: " + mtu);
            }
        }

        if (lanDevices > 0) {
            out.add("LAN上で " + lanDevices + " 台のmDNS機器を検出。同一セグメントに見えている範囲です");
        }

        boolean dnsFailed = false;
        boolean externalOk = false;
        for (NetGraph.Reachability r : g.reachability) {
            if ("dns".equals(r.method) && !r.reached) {
                dnsFailed = true;
            }
            if ("8.8.8.8".equals(r.to) && r.reached) {
                externalOk = true;
            }
        }
        if (externalOk && dnsFailed) {
            out.add("切り分け: IP到達はできて名前解決が失敗。DNS側の問題です");
        }

        if (out.isEmpty()) {
            out.add("評価できる情報がありません。RouteHQApp で接続情報を取得してから共有してください");
        }
        return out;
    }
}
