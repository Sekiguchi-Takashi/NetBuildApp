package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** MD §8 / §9。表面要求と隠れた要求を分けて持つ。 */
public class Scenario {

    /** 境界をどこに置くかは案件の前提として決まっている。プレイヤーは選べない。 */
    public enum Boundary {
        ON_PREM("自前の境界", "拠点にFirewallを置き、そこを通して外とやりとりする"),
        SASE("クラウド境界（SASE）", "検査をクラウド側に置き、拠点に境界を持たない");

        public final String label;
        public final String detail;

        Boundary(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }
    }

    public static class Hidden {
        public final String question;
        public final String answer;
        public final String requirement;
        /** 聞き出す前に観察で気づけること。MD §9 の潜在要求への手がかり。 */
        public final String hint;
        public boolean revealed;

        Hidden(String question, String answer, String requirement, String hint) {
            this.question = question;
            this.answer = answer;
            this.requirement = requirement;
            this.hint = hint;
        }
    }

    public final String client;
    public final String explicitRequirement;
    public final int budget;
    public final int currentUsers;
    public final int futureUsers;
    /** 業務上必要な通信。過剰許可の判定基準であり、要求達成の判定基準でもある。 */
    public static class Allowance {
        public final String fromZone;
        public final String toZone;
        public final String fromNode;
        public final String toNode;
        public final String label;
        /** 根拠となる隠れた要求の番号。-1 は表面要求。 */
        public final int hiddenIndex;
        public final boolean scored;
        public final int points;
        public final int penalty;

        Allowance(String fromZone, String toZone, String fromNode, String toNode,
                  String label, int hiddenIndex, boolean scored, int points, int penalty) {
            this.fromZone = fromZone;
            this.toZone = toZone;
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.label = label;
            this.hiddenIndex = hiddenIndex;
            this.scored = scored;
            this.points = points;
            this.penalty = penalty;
        }

        public String pair() {
            return fromZone + ">" + toZone;
        }
    }

    /** 通ってはいけない経路。 */
    public static class Prohibition {
        public final String fromNode;
        public final String toNode;
        public final String label;
        public final String detail;
        public final int penalty;
        public final int reward;

        Prohibition(String fromNode, String toNode, String label, String detail,
                    int penalty, int reward) {
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.label = label;
            this.detail = detail;
            this.penalty = penalty;
            this.reward = reward;
        }
    }

    public final String id;
    public final Boundary boundary;
    public final String personality;
    public final List<Allowance> allowances = new ArrayList<>();
    public final List<Prohibition> prohibitions = new ArrayList<>();
    /** この案件に登場するゾーン。ルール編集の選択肢になる。 */
    public String[] zones = {"guest", "internal", "dmz", "internet", "remote", "vendor", "cloud"};
    public final List<Hidden> hidden;

    private Scenario(String id, Boundary boundary, String client, String explicitRequirement,
                     int budget, int currentUsers, int futureUsers,
                     String personality, List<Hidden> hidden) {
        this.id = id;
        this.boundary = boundary;
        this.personality = personality;
        this.client = client;
        this.explicitRequirement = explicitRequirement;
        this.budget = budget;
        this.currentUsers = currentUsers;
        this.futureUsers = futureUsers;
        this.hidden = hidden;
    }

    public static Scenario office() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("利用者は何人くらいですか？",
                        "今は50人です。3年で200人まで増やす計画があります。",
                        "将来200人分のIPアドレスとポートが必要",
                        "空席が20席ほどある。壁際に未開封のデスクが積んである"),
                new Hidden("来客の方もWi-Fiを使いますか？",
                        "はい。打ち合わせが多いので来客用も欲しいです。",
                        "来客用Wi-Fi。社内リソースには触らせない",
                        "打ち合わせスペースが広く、来客予定がホワイトボードに並んでいる"),
                new Hidden("社内サーバーには何が入っていますか？",
                        "顧客リストと図面です。外に出たら取引が止まります。",
                        "社内サーバーは外部から触れさせない",
                        "サーバー室の扉に「関係者以外立入禁止」の貼り紙がある"),
                new Hidden("在宅で働く人はいますか？",
                        "週2で在宅の社員が10人ほどいます。社内システムを家からも使いたいそうです。",
                        "在宅から社内システムへ安全に接続する手段が必要",
                        "空いている席が多い曜日がある。ノートPCを持ち帰る人が目立つ"),
                new Hidden("保守業者はどうやって社内に入ってきますか？",
                        "業者さんの回線がつなぎっぱなしです。いつでも入れるようにしてあると聞いています。",
                        "保守用の接続は必要なときだけ開ける",
                        "サーバー室に、業者名のシールが貼られた見慣れない機器がある"),
                new Hidden("社内のWebサイトを外部から見る予定はありますか？",
                        "あります。採用ページを社内サーバーで公開したいです。",
                        "Webサーバーの外部公開が必要",
                        "机に採用パンフレットの校正刷りが置いてある")
        ));
        Scenario s = new Scenario("office", Boundary.ON_PREM,
                "株式会社サンプル商事", "新しいオフィスでネットを使いたい",
                1000000, 50, 200,
                "専門用語を出すと黙ってうなずくだけになる。要望は聞かれるまで自分からは言わない",
                hidden);

        // 点数がつく要求
        s.allowances.add(new Allowance("guest", "internet", "guest", "net",
                "来客のインターネット利用", 1, true, 15, 20));
        s.allowances.add(new Allowance("internet", "dmz", "net", "web",
                "Webサーバーの外部公開", 5, true, 15, 15));
        s.allowances.add(new Allowance("remote", "internal", "home", "pc",
                "在宅から社内システムへの接続", 3, true, 15, 15));
        s.allowances.add(new Allowance("internal", "cloud", "pc", "cloud",
                "社員からクラウド業務システムへの接続", -1, false, 0, 0));
        // 業務に要るが、点数ではなく過剰許可の判定に使うもの
        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "社員のインターネット利用", -1, false, 0, 0));
        s.allowances.add(new Allowance("internal", "dmz", "pc", "web",
                "社員から公開サーバーへの管理アクセス", -1, false, 0, 0));

        s.prohibitions.add(new Prohibition("guest", "pc",
                "来客端末から社内PCへ到達できます",
                "来客は社内リソースに触れてはいけません", 30, 20));
        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから社内PCへ到達できます",
                "PublicExposureRisk。公開サーバーはDMZに分離してください", 35, 20));
        s.prohibitions.add(new Prohibition("vendor", "pc",
                "保守業者から社内へ常時到達できます",
                "業者側が侵害されると、その経路がそのまま入口になります。"
                        + "保守用の接続は必要なときだけ開けます", 20, 15));
        s.prohibitions.add(new Prohibition("net", "srv",
                "インターネットから社内サーバーへ到達できます",
                "公開側が破られた時点で、顧客情報も同じ区画にあります", 25, 15));
        return s;
    }

    /** SASE前提の案件。拠点に境界を持たない会社。 */
    public static Scenario distributed() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("社員のみなさんはどこで働いていますか？",
                        "全員バラバラです。事務所には誰も常駐していません。",
                        "拠点に境界を置いても意味がない。検査はクラウド側で行う",
                        "事務所を訪ねても人がいない。郵便物だけが溜まっている"),
                new Hidden("業務システムはどこにありますか？",
                        "ほとんどクラウドです。ただ古い受発注システムだけ事務所に残っています。",
                        "クラウドへの接続と、事務所に残るシステムへの接続の両方が要る",
                        "事務所の隅に、古い機器が1台だけ動いている"),
                new Hidden("会社の端末以外も使いますか？",
                        "個人のPCから業務システムに入っている人がいるようです。",
                        "端末を問わず、同じ検査を通す必要がある",
                        "個人名義のクラウドアカウントに業務ファイルが置かれている")
        ));

        Scenario s = new Scenario("distributed", Boundary.SASE,
                "合同会社リモートワークス", "在宅中心にしたので、拠点のネットを見直したい",
                800000, 40, 80,
                "ITに詳しくないが判断は速い。決めたことは自分から周知してくれる",
                hidden);
        s.zones = new String[]{"internal", "remote", "cloud", "internet"};

        s.allowances.add(new Allowance("remote", "cloud", "home", "cloud",
                "在宅からクラウド業務システムへの接続", 1, true, 15, 20));
        s.allowances.add(new Allowance("remote", "internal", "home", "srv",
                "在宅から事務所の受発注システムへの接続", 1, true, 15, 15));
        s.allowances.add(new Allowance("internal", "cloud", "srv", "cloud",
                "事務所の機器からクラウドへの接続", -1, false, 0, 0));
        s.allowances.add(new Allowance("internal", "internet", "srv", "net",
                "事務所の機器の更新通信", -1, false, 0, 0));

        s.prohibitions.add(new Prohibition("net", "srv",
                "インターネットから事務所のシステムへ到達できます",
                "拠点に人がいなくても、機器は残っています。外から直接触れる状態にはしません", 30, 20));
        return s;
    }

    public static List<Scenario> all() {
        return new ArrayList<>(Arrays.asList(office(), distributed()));
    }

    public int revealedCount() {
        int n = 0;
        for (Hidden h : hidden) {
            if (h.revealed) {
                n++;
            }
        }
        return n;
    }

    public Hidden nextUnrevealed() {
        for (Hidden h : hidden) {
            if (!h.revealed) {
                return h;
            }
        }
        return null;
    }
}
