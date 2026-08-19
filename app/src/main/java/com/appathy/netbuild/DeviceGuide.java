package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 機器の簡易説明。
 *
 * 機器を増やすときは entries() に1件足すだけでよい。
 * status = PLANNED のものは説明だけ読めて、まだ図には出ない。
 */
public class DeviceGuide {

    public static final String IMPLEMENTED = "実装済み";
    public static final String PLANNED = "未実装";

    public final String key;
    public final String name;
    public final String oneLine;
    public final String role;
    public final String placement;
    public final String pitfall;
    public final String status;

    private DeviceGuide(String key, String name, String oneLine, String role,
                        String placement, String pitfall, String status) {
        this.key = key;
        this.name = name;
        this.oneLine = oneLine;
        this.role = role;
        this.placement = placement;
        this.pitfall = pitfall;
        this.status = status;
    }

    public String body() {
        return oneLine + "\n\n"
                + "[ 何をするもの ]\n" + role + "\n\n"
                + "[ どこに置くか ]\n" + placement + "\n\n"
                + "[ よくある間違い ]\n" + pitfall + "\n\n"
                + "[ このゲームでの扱い ]\n" + status;
    }

    public static List<DeviceGuide> entries() {
        return new ArrayList<>(Arrays.asList(
                new DeviceGuide("sw", "L2スイッチ（ハブ）",
                        "同じセグメントの機器をつなぐ箱。",
                        "MACアドレスを見て、宛先のポートにだけフレームを流します。"
                                + "VLANを設定すると、1台のスイッチを複数の独立したセグメントとして使えます。",
                        "端末とサーバーの収容場所。境界より内側に置きます。",
                        "同じVLANの中はFirewallを通らないので、ルールを書いても止まりません。"
                                + "分離したいならVLANで分けるのが先です。",
                        IMPLEMENTED),

                new DeviceGuide("l3", "L3スイッチ",
                        "VLANで分けたセグメント同士をつなぐスイッチ。",
                        "L2スイッチの機能に加えて、IPアドレスを見て転送します。"
                                + "VLANで分けたセグメントは、L2のままでは互いに届きません。"
                                + "行き来させたいときに、この機器かルーターが要ります。",
                        "セグメントを分けたうえで、必要なところだけ通したい場所。",
                        "分離のために VLAN を切ったのに、L3で全部通してしまうと分けた意味がなくなります。"
                                + "通す先を絞るところまでが設計です。",
                        IMPLEMENTED),

                new DeviceGuide("ap", "無線AP（Wi-Fi）",
                        "端末を電波でネットワークにつなぐ機器。",
                        "有線の口が無い場所でも端末をつなげます。"
                                + "SSIDごとに別のVLANに載せることができ、来客用と社員用を分けるのが基本です。",
                        "利用者のいる場所。天井や壁に付けます。",
                        "来客用と社員用を同じSSID・同じVLANにしてしまうと、"
                                + "電波が届く範囲の全員が社内と同じ場所に入れます。",
                        IMPLEMENTED),

                new DeviceGuide("mfp", "複合機",
                        "印刷・スキャン・コピーをまとめた機器。",
                        "ネットワークにつなぐと、各席から印刷でき、"
                                + "スキャンした書類をファイル共有やメールに送れます。",
                        "内部セグメント。アドレスが変わると呼べなくなるので固定IPにします。",
                        "スキャンしたデータの保存先や、内蔵ストレージに残る履歴が見落とされがちです。"
                                + "外部から届く場所に置くと、そこから情報が漏れます。",
                        IMPLEMENTED),

                new DeviceGuide("dhcp", "DHCP / 固定IP",
                        "アドレスを自動で配るか、手で決めるか。",
                        "DHCPは端末にアドレスを自動で貸し出します。"
                                + "サーバーや複合機のように「呼ばれる側」は、"
                                + "アドレスが変わると困るので固定にします。",
                        "端末はDHCP、機器は固定。これが基本の組み合わせです。",
                        "全部を固定にすると管理が破綻し、"
                                + "全部をDHCPにするとサーバーを呼べなくなります。"
                                + "貸出期間が切れて別の端末に同じアドレスが渡ることもあります。",
                        IMPLEMENTED),

                new DeviceGuide("ipv6", "IPv4 / IPv6",
                        "住所の書き方が2種類あります。",
                        "IPv4は数が足りなくなったため、IPv6が用意されました。"
                                + "両方を同時に通すことができます（デュアルスタック）。",
                        "要件があるときだけ有効にします。",
                        "IPv4側だけを塞いでIPv6を放置すると、そこが抜け道になります。"
                                + "有効にするなら、ルールも両方ぶん書く必要があります。",
                        IMPLEMENTED),

                new DeviceGuide("fw", "Firewall",
                        "通していい通信だけを選ぶ関所。",
                        "送信元・宛先・プロトコル・ポートの条件を上から順に見て、"
                                + "最初に当たったルールを適用します。どれにも当たらなければ通しません（暗黙Deny）。",
                        "内部と外部の境界。DMZを作る場合は3つ目の口をDMZに向けます。",
                        "ルールの順序を間違えると、後ろの厳しいルールが効きません。"
                                + "また、Firewallを通らない経路（同一セグメント内）は制御できません。",
                        IMPLEMENTED),

                new DeviceGuide("router", "ルーター",
                        "違うネットワークの間で荷物を中継する係。",
                        "宛先IPアドレスとルーティングテーブルを見て、次にどこへ渡すかを決めます。"
                                + "デフォルトルートは「表に載っていない宛先はここへ」という指定です。",
                        "拠点の出入口。小規模ではFirewallと一体の機器になっていることが多いです。",
                        "デフォルトルートがないと外に出られません。"
                                + "経路はあるのに戻りの経路がなくて通信できない、という片道の設定ミスもよくあります。",
                        IMPLEMENTED),

                new DeviceGuide("web", "公開Webサーバー",
                        "外部の人に見せるためのサーバー。",
                        "インターネットからの接続を受けます。狙われる前提で置き場所を決める必要があります。",
                        "DMZ。内部セグメントに置いて外部公開するのは避けます。",
                        "内部に置いたまま外部公開すると、乗っ取られた時点で社内と同じ区画にいることになります。",
                        IMPLEMENTED),

                new DeviceGuide("pc", "社員PC / 来客端末",
                        "利用者が使う端末。",
                        "どのセグメントに属するかで、届く範囲が決まります。",
                        "社員は内部セグメント、来客は分離した来客用セグメント。",
                        "来客と社員を同じセグメントに入れると、悪意がなくても社内が見えてしまいます。",
                        IMPLEMENTED),

                new DeviceGuide("internal_server", "社内サーバー",
                        "社内の人だけが使うファイルサーバーや業務システム。",
                        "共有ファイル、認証（AD）、勤怠や販売管理などを動かします。"
                                + "外部に出す必要がないぶん、守り方は「触れる人を絞る」ことが中心になります。",
                        "内部セグメント。公開サーバーとは別区画にします。",
                        "公開サーバーと同じ場所に置くと、公開側が破られたときに一緒に持っていかれます。",
                        IMPLEMENTED),

                new DeviceGuide("cloud_server", "クラウドサーバー",
                        "自社で持たず、事業者の設備を借りて動かすサーバー。",
                        "IaaSなら仮想マシンを、SaaSならサービスそのものを利用します。"
                                + "機器の故障は事業者が見ますが、設定と権限の責任は利用者側に残ります（責任共有モデル）。",
                        "社内の外。インターネット経由か、専用線・VPNでつなぎます。",
                        "「クラウドだから安全」ではありません。"
                                + "公開範囲の設定ミスでストレージが全世界に見えていた、という事故が典型です。",
                        IMPLEMENTED),

                new DeviceGuide("vendor_server", "ベンダーのサーバー",
                        "保守業者や取引先が持っているサーバー。",
                        "業務システムの保守や、受発注データのやりとりに使います。"
                                + "自社の管理が及ばない相手と、限定的につなぐ形になります。",
                        "社外。必要な通信だけを、必要な期間だけ通します。",
                        "保守用の常時接続を開けっぱなしにすると、相手が侵害されたときにそこから入られます。"
                                + "サプライチェーン攻撃の入口として実際に狙われています。",
                        IMPLEMENTED),

                new DeviceGuide("vpn", "VPN",
                        "公衆回線の上に、暗号化した専用の通り道を作る仕組み。",
                        "拠点間VPNは事業所同士を、リモートアクセスVPNは在宅の端末を社内につなぎます。"
                                + "通信を暗号化し、相手が本物かを認証します。",
                        "境界のFirewallやVPN装置。",
                        "つないだ端末を「社内と同じ」扱いにすると、家庭の感染端末がそのまま社内に入ります。"
                                + "VPN装置自体の脆弱性が侵入経路になった事例も多くあります。",
                        IMPLEMENTED),

                new DeviceGuide("proxy", "プロキシ",
                        "社内から外へ出る通信を、代わりに中継する係。",
                        "誰がどこへアクセスしたかの記録を残し、危険なサイトを遮断します。"
                                + "キャッシュによる通信量の削減にも使われます。",
                        "内部セグメントと境界の間。",
                        "全通信をプロキシ経由にする設定をしていないと、直接出ていく通信が抜け道になります。",
                        IMPLEMENTED),

                new DeviceGuide("sase", "SASE",
                        "境界の防御をクラウド側にまとめた考え方。",
                        "プロキシ、Firewall、VPNに相当する機能をクラウド上のサービスとして提供します。"
                                + "利用者がどこにいても同じ検査を通るため、在宅と社内で守りに差が出ません。",
                        "端末とインターネットの間。自社の出入口を通さずに済みます。",
                        "「境界がなくなる」のではなく、境界がクラウドに移るだけです。"
                                + "誰に何を許可するかの設計は、結局これまでと同じだけ必要になります。",
                        IMPLEMENTED),

                new DeviceGuide("dns", "DNSサーバー",
                        "名前をIPアドレスに変える案内係。",
                        "www.example.com のような名前を、実際の宛先であるIPアドレスに変換します。",
                        "内部向けと外部向けを分けて持つのが基本です。",
                        "ここが止まると「IPでは通じるのに名前で開けない」状態になります。"
                                + "1台構成だと止まった瞬間に全社が止まるので、冗長化が要ります。",
                        IMPLEMENTED)
        ));
    }

    public static DeviceGuide byKey(String key) {
        for (DeviceGuide g : entries()) {
            if (g.key.equals(key)) {
                return g;
            }
        }
        return null;
    }
}
