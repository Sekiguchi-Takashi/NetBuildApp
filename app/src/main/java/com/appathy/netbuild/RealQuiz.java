package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 実機の測定結果から問題を作る。
 *
 * 架空の案件ではなく、いま自分がつないでいるネットワークの実際の値を使う。
 * 正解も解説も測定値から組み立てるので、環境が変われば問題も変わる。
 */
public class RealQuiz {

    public static class Question {
        public final String text;
        public final List<String> choices = new ArrayList<>();
        public final int answer;
        public final String explanation;

        Question(String text, List<String> choices, int answer, String explanation) {
            this.text = text;
            this.choices.addAll(choices);
            this.answer = answer;
            this.explanation = explanation;
        }
    }

    /** 測定できた内容に応じて出せる問題だけを返す。 */
    public List<Question> build(NetGraph g) {
        List<Question> out = new ArrayList<>();
        NetGraph.Node self = g.find(DeviceNetworkCollector.SELF);

        String gateway = null;
        int dnsCount = 0;
        String firstDns = null;
        for (NetGraph.Node n : g.nodes) {
            if ("gateway".equals(n.type) && gateway == null) {
                gateway = n.label;
            }
            if ("dns".equals(n.type)) {
                dnsCount++;
                if (firstDns == null) {
                    firstDns = n.label;
                }
            }
        }

        if (gateway != null) {
            out.add(new Question(
                    "このネットワークのデフォルトゲートウェイは " + gateway + " です。\n\n"
                            + gateway + " へのpingは応答するのに、8.8.8.8 へのpingが応答しません。"
                            + "どこを疑いますか。",
                    list("端末のIPアドレス設定", "ゲートウェイより先（回線かISP）",
                            "端末のDNS設定", "LANケーブルの断線"),
                    1,
                    "ゲートウェイまで届いているので、端末からゲートウェイまでの経路は生きています。"
                            + "名前ではなくIPアドレスで試しているのでDNSも関係ありません。"
                            + "残るのはゲートウェイから外側です。\n\n"
                            + "ゲームの「回線障害」と同じ切り分けです。"));
        }

        if (firstDns != null) {
            out.add(new Question(
                    "このネットワークのDNSサーバーは " + firstDns
                            + (dnsCount > 1 ? " ほか " + (dnsCount - 1) + " 台" : " の1台だけ") + " です。\n\n"
                            + "このDNSが停止したとき、どういう症状になりますか。",
                    list("何も通信できなくなる",
                            "IPアドレスを直接指定すれば通じるが、サイト名では開けない",
                            "通信速度だけが遅くなる",
                            "同じLAN内の機器も見えなくなる"),
                    1,
                    "DNSは名前をIPアドレスに変える係なので、止まっても経路自体は生きています。"
                            + "IPを直接打てば通じるのに名前で開けない、という形になります。\n\n"
                            + (dnsCount > 1
                            ? "このネットワークはDNSが " + dnsCount + " 台あるので、"
                            + "1台止まってももう1台が答えます。"
                            : "このネットワークはDNSが1台だけです。"
                            + "止まった時点で名前解決の手段がなくなります。単一障害点というやつです。")));
        }

        if (self != null && self.attr("subnet") != null) {
            String capacity = self.attr("capacity");
            String subnet = self.attr("subnet");
            out.add(new Question(
                    "このネットワークのサブネットは " + subnet + " です。"
                            + (capacity == null ? "" : "（" + capacity + "）") + "\n\n"
                            + "この範囲を使い切ると、新しくつないだ端末はどうなりますか。",
                    list("既存の端末を追い出して接続する",
                            "自動的に別のサブネットが作られる",
                            "アドレスを取得できず、ネットワークに参加できない",
                            "通信は遅くなるが接続はできる"),
                    2,
                    "割り当てるアドレスが無くなるので、その端末はネットワークに入れません。\n\n"
                            + "ゲームで /24 と /26 を選ぶ場面と同じ話です。"
                            + "足りなくなってから広げると、既存端末のアドレス振り直しが要るので高くつきます。"));
        }

        if (self != null && "true".equals(self.attr("vpn"))) {
            out.add(new Question(
                    "いまこの端末はVPN経由で通信しています。\n\n"
                            + "VPNでつないだ端末を「社内にいる端末と同じ扱い」にすると、"
                            + "何が問題になりますか。",
                    list("通信速度が落ちる",
                            "家庭側で感染した端末が、そのまま社内に入れてしまう",
                            "VPNの費用が上がる",
                            "IPアドレスが重複する"),
                    1,
                    "VPNは通信の中身を守りますが、つないでくる端末が安全かどうかは保証しません。"
                            + "社内と同じ権限を与えると、その端末が持ち込んだものも一緒に入ります。\n\n"
                            + "ゲームのリモートアクセスVPNで扱った論点です。"));
        }

        for (NetGraph.Node n : g.nodes) {
            if ("accessPoint".equals(n.type) && n.label != null) {
                String ssid = n.label.toLowerCase();
                if (ssid.contains("guest") || ssid.contains("free") || ssid.contains("public")) {
                    out.add(new Question(
                            "いま接続しているSSIDは " + n.label + " です。"
                                    + "来客用か公衆のWi-Fiに見えます。\n\n"
                                    + "この種のネットワークで、設計側が本来やっておくべきことは何ですか。",
                            list("接続台数の上限を決める",
                                    "利用者を社内セグメントから分離する",
                                    "通信速度を制限する",
                                    "接続時間に制限をかける"),
                            1,
                            "来客用の回線は、社内のリソースに触れさせないことが前提です。"
                                    + "分離されていなければ、同じネットワークにいる他の端末から見えている可能性があります。\n\n"
                                    + "ゲームで来客VLANを分離した場面の、今度は自分が来客側です。"));
                    break;
                }
            }
        }

        if (self != null && "true".equals(self.attr("captivePortal"))) {
            out.add(new Question(
                    "この接続はキャプティブポータル配下です。\n\n"
                            + "認証前の状態で名前解決だけ通ることがあるのはなぜですか。",
                    list("DNSは認証の対象外に置かれることが多いから",
                            "DNSは暗号化されているから",
                            "認証が壊れているから",
                            "端末がキャッシュを持っているから"),
                    0,
                    "認証ページ自体に到達させるため、DNSは先に通す作りになっているものが多いです。"
                            + "この性質が悪用されることもあります（DNSトンネリング）。"));
        }

        boolean externalOk = false;
        boolean dnsFailed = false;
        for (NetGraph.Reachability r : g.reachability) {
            if ("8.8.8.8".equals(r.to) && r.reached) {
                externalOk = true;
            }
            if ("dns".equals(r.method) && !r.reached) {
                dnsFailed = true;
            }
        }
        if (externalOk && dnsFailed) {
            out.add(new Question(
                    "測定の結果、8.8.8.8 には届くのに example.com の名前解決が失敗しています。\n\n"
                            + "いま起きているのは何ですか。",
                    list("回線が切れている", "ゲートウェイが停止している",
                            "名前解決の経路に問題がある", "端末のIPが重複している"),
                    2,
                    "IPアドレス指定では届いているので、経路は生きています。"
                            + "名前解決だけが失敗しているので、DNSサーバーかその手前が原因です。\n\n"
                            + "実際にいまこの端末で起きている状態です。"));
        }

        Collections.shuffle(out);
        return out;
    }

    private List<String> list(String... items) {
        List<String> out = new ArrayList<>();
        Collections.addAll(out, items);
        return out;
    }
}
