package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** MD §8 / §9。表面要求と隠れた要求を分けて持つ。 */
public class Scenario {

    /** 案件の難しさ。使える選択肢の数と、聞き出す要求の量が変わる。 */
    public enum Level {
        BEGINNER("初級", "起業したての会社。決めることは少ない"),
        INTERMEDIATE("中級", "一通りの機器がある1拠点。人数は多くない"),
        ADVANCED("上級", "複数拠点・数千人規模。条件を聞き出すところから");

        public final String label;
        public final String detail;

        Level(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }
    }

    /**
     * その案件で本当に必要なもの。
     * 聞き出せていれば加点され、必要なのに入れていなければ減点。
     * 逆に、ここに無いものを入れると無駄な出費になる。
     */
    public static class Need {
        public final String key;
        public final String label;
        public final int hiddenIndex;

        public Need(String key, String label, int hiddenIndex) {
            this.key = key;
            this.label = label;
            this.hiddenIndex = hiddenIndex;
        }
    }

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
    public Level level = Level.INTERMEDIATE;
    /** この案件で必要なもの。 */
    public final List<Need> needs = new ArrayList<>();
    /** この案件で選べる項目のキー。needs に無いものも含めて、提示だけはされる。 */
    public String[] options = {"vlan", "dmz", "proxy", "vpn", "l3", "wifi",
            "fileshare", "mfp", "sitelink", "dhcp", "static", "ipv6"};
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
        s.level = Level.INTERMEDIATE;
        s.options = new String[]{"vlan", "dmz", "proxy", "vpn", "wifi", "fileshare",
                "mfp", "dhcp", "static", "l3"};
        s.needs.add(new Need("wifi", "無線LAN", 1));
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", 0));
        s.needs.add(new Need("vlan", "来客の分離", 1));

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
        s.level = Level.INTERMEDIATE;
        s.options = new String[]{"wifi", "dhcp", "ipv6", "fileshare"};
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", -1));
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

    /** 工場。外に公開するものが無く、余計な許可を消せるかが問われる。 */
    public static Scenario factory() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("外から見せるサイトや資料はありますか？",
                        "ありません。うちは取引先とも電話とメールだけです。",
                        "外部に公開するサーバーは不要。外から入る経路は塞ぐ",
                        "会社案内のパンフレットに、ホームページのURLが載っていない"),
                new Hidden("生産管理はどこで動かしていますか？",
                        "クラウドのサービスです。現場のタブレットからも見ています。",
                        "社内からクラウドへの接続が必要",
                        "現場にタブレットが置かれていて、同じ画面が開いたままになっている"),
                new Hidden("装置の保守は誰がやっていますか？",
                        "メーカーさんです。何かあると遠隔で入って直してくれます。",
                        "保守業者の接続は必要なときだけ開ける",
                        "装置の脇に、メーカー名の入った通信機器が付いている"),
                new Hidden("従業員は増えますか？",
                        "第2ラインを立ち上げるので、120人まで増えます。",
                        "将来120人分のアドレスが必要",
                        "工場の奥に、まだ使っていない区画がある")
        ));

        Scenario s = new Scenario("factory", Boundary.ON_PREM,
                "サンプル工業株式会社", "工場のネットワークを引き直したい",
                1200000, 80, 120,
                "現場の話は具体的だが、ITの話になると「お任せします」と言う",
                hidden);
        s.level = Level.INTERMEDIATE;
        s.zones = new String[]{"guest", "internal", "internet", "cloud", "vendor"};
        s.options = new String[]{"vlan", "proxy", "wifi", "fileshare", "mfp",
                "dhcp", "static", "l3", "dmz"};
        s.needs.add(new Need("wifi", "無線LAN（現場のタブレット）", 1));
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", -1));
        s.needs.add(new Need("static", "装置と複合機の固定IP", -1));
        s.needs.add(new Need("mfp", "複合機の接続", -1));

        s.allowances.add(new Allowance("internal", "cloud", "pc", "cloud",
                "生産管理クラウドへの接続", 1, true, 20, 25));
        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "社員のインターネット利用", -1, false, 0, 0));
        s.allowances.add(new Allowance("guest", "internet", "guest", "net",
                "来客のインターネット利用", -1, false, 0, 0));

        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから社内へ到達できます",
                "外に見せるものが無いのに、外から入れる経路が開いています", 35, 25));
        s.prohibitions.add(new Prohibition("net", "srv",
                "インターネットから社内サーバーへ到達できます",
                "生産の記録が外から触れる状態です", 25, 15));
        s.prohibitions.add(new Prohibition("vendor", "pc",
                "保守業者から社内へ常時到達できます",
                "装置メーカーの回線が開きっぱなしです。必要なときだけ開けます", 20, 15));
        s.prohibitions.add(new Prohibition("guest", "pc",
                "来客端末から社内へ到達できます",
                "現場に入る業者や見学者の端末が、社内と同じ場所にいます", 25, 15));
        return s;
    }

    /** 屋外の現場事務所。仮設で、回線も設備も限られる。 */
    public static Scenario outdoor() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("現場には何人くらい入りますか？",
                        "20人前後です。工期によって増減します。",
                        "小規模だが、出入りが多い",
                        "プレハブの前に、ヘルメットが日によって違う数だけ並んでいる"),
                new Hidden("図面はどこで見ていますか？",
                        "タブレットでクラウドから落としています。現場でも見ます。",
                        "屋外からクラウドへの接続が必要",
                        "作業員がタブレットを持って歩いている"),
                new Hidden("この事務所はいつまで使いますか？",
                        "工期のあいだだけです。終わったら畳みます。",
                        "長く使わない前提。大きな設備投資は見合わない",
                        "建物がプレハブで、配線が仮設のまま這わせてある")
        ));

        Scenario s = new Scenario("outdoor", Boundary.ON_PREM,
                "現場事務所（仮設）", "工事の間だけネットを使えるようにしたい",
                500000, 20, 25,
                "急いでいる。細かい話より、いつ使えるようになるかを気にする",
                hidden);
        s.level = Level.BEGINNER;
        s.zones = new String[]{"guest", "internal", "cloud", "internet"};
        s.options = new String[]{"vlan", "wifi", "dhcp"};
        s.needs.add(new Need("wifi", "無線LAN", 0));
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", 0));

        s.allowances.add(new Allowance("internal", "cloud", "pc", "cloud",
                "現場から図面クラウドへの接続", 1, true, 20, 25));
        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "作業員のインターネット利用", -1, false, 0, 0));
        s.allowances.add(new Allowance("guest", "internet", "guest", "net",
                "協力会社のインターネット利用", -1, false, 0, 0));

        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから現場の端末へ到達できます",
                "仮設でも、外から入れる経路は塞ぎます", 30, 20));
        s.prohibitions.add(new Prohibition("guest", "pc",
                "協力会社の端末から自社端末へ到達できます",
                "出入りの多い現場ほど、分けておく意味があります", 20, 15));
        return s;
    }

    /** 初級A：クラウドだけで回す。社内にネットワーク機器を置かない。 */
    public static Scenario ventureCloud() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("業務で使うものはどこにありますか？",
                        "全部クラウドです。会計も、チャットも、ファイルも。",
                        "社内にサーバーを置く必要がない",
                        "オフィスに機器らしいものが1つも置かれていない"),
                new Hidden("事務所では何人が同時に使いますか？",
                        "5人です。ノートPCと、たまにスマホです。",
                        "無線があれば足りる。有線の配線工事は不要",
                        "机にLANの口がなく、全員ノートPCを使っている"),
                new Hidden("印刷はどうしていますか？",
                        "コンビニです。社判を押す書類くらいしか印刷しません。",
                        "複合機は不要",
                        "オフィスにプリンタが置かれていない")
        ));

        Scenario s = new Scenario("venture-cloud", Boundary.SASE,
                "スタートアップA（クラウド専業）", "事務所でネットが使えるようにしたい",
                300000, 5, 15,
                "判断が速い。要らないものは要らないとはっきり言う",
                hidden);
        s.level = Level.BEGINNER;
        s.zones = new String[]{"internal", "cloud", "internet"};
        s.options = new String[]{"wifi", "dhcp", "ipv6"};
        s.needs.add(new Need("wifi", "無線LAN", 1));
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", -1));

        s.allowances.add(new Allowance("internal", "cloud", "pc", "cloud",
                "クラウド業務システムへの接続", 0, true, 25, 30));
        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "社員のインターネット利用", -1, false, 0, 0));
        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから社内端末へ到達できます",
                "置いている機器が少なくても、外から入れる経路は塞ぎます", 25, 20));
        return s;
    }

    /** 初級B：社内にサーバーとファイル共有と複合機を置く。外向けは無し。 */
    public static Scenario ventureOffice() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("資料はどうやって共有していますか？",
                        "USBメモリで手渡ししています。そろそろ限界です。",
                        "ファイル共有が必要",
                        "机の上にUSBメモリが何本も転がっている"),
                new Hidden("印刷やスキャンはどれくらい使いますか？",
                        "毎日です。見積書と図面を刷ります。スキャンも使います。",
                        "複合機をネットワークにつなぐ必要がある",
                        "複合機の前に順番待ちの列ができている"),
                new Hidden("席は決まっていますか？",
                        "固定席です。人の増減も年に数人くらいです。",
                        "端末は自動割り当てで足りる。サーバーと複合機は固定にする",
                        "机に名札が貼ってあり、配置が変わった様子がない")
        ));

        Scenario s = new Scenario("venture-office", Boundary.ON_PREM,
                "スタートアップB（社内共有あり）", "USBのやりとりをやめたい",
                600000, 12, 25,
                "現場の困りごとは具体的に話すが、機器の名前は出てこない",
                hidden);
        s.level = Level.BEGINNER;
        s.zones = new String[]{"internal", "internet"};
        s.options = new String[]{"fileshare", "mfp", "wifi", "dhcp", "static"};
        s.needs.add(new Need("fileshare", "ファイル共有", 0));
        s.needs.add(new Need("mfp", "複合機の接続", 1));
        s.needs.add(new Need("dhcp", "端末へのDHCP", 2));
        s.needs.add(new Need("static", "サーバーと複合機の固定IP", 2));

        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "社員のインターネット利用", -1, false, 0, 0));
        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから社内へ到達できます",
                "外に見せるものが無いので、入る経路は塞ぎます", 30, 25));
        return s;
    }

    /** 上級：3拠点・数千人規模。条件を全部聞き出さないと組み立てられない。 */
    public static Scenario enterprise() {
        List<Hidden> hidden = new ArrayList<>(Arrays.asList(
                new Hidden("拠点はいくつありますか？",
                        "本社と、工場が2つです。工場は県外にあります。",
                        "3拠点を結ぶ必要がある",
                        "受付に「本社・第二工場・第三工場」と書かれた内線表がある"),
                new Hidden("全社で何人くらいですか？",
                        "3,200人です。来年、統合でもう1,000人増える予定です。",
                        "4,000人以上を収容できるアドレス設計が必要",
                        "駐車場が広く、社員用のバスが何台も停まっている"),
                new Hidden("在宅勤務はありますか？",
                        "本社の事務部門は半分が在宅です。工場は現場なので出社です。",
                        "在宅から社内へ接続する手段が必要",
                        "本社の席が半分ほど空いている"),
                new Hidden("外部に公開しているものはありますか？",
                        "採用サイトと、取引先向けの受発注システムがあります。",
                        "公開サーバーをDMZに置く必要がある",
                        "名刺にURLが2つ印刷されている"),
                new Hidden("装置の保守はどうしていますか？",
                        "工場の装置はメーカーが遠隔で見ています。常時つながっています。",
                        "保守接続は必要なときだけ開ける",
                        "工場の装置に、メーカー名の通信機器が付いている"),
                new Hidden("印刷や資料の共有はどうしていますか？",
                        "各フロアに複合機があります。資料は部門ごとの共有サーバーです。",
                        "複合機とファイル共有が必要",
                        "フロアの角に複合機が並んでいる"),
                new Hidden("無線は使っていますか？",
                        "会議室と工場の現場で使います。来客もよく来ます。",
                        "無線LANと、来客の分離が必要",
                        "会議室にアクセスポイントが見える")
        ));

        Scenario s = new Scenario("enterprise", Boundary.ON_PREM,
                "大手製造業（3拠点・3,200人）", "老朽化した社内ネットワークを刷新したい",
                2800000, 3200, 4200,
                "情報システム部の担当。聞けば答えるが、聞かれないことは言わない",
                hidden);
        s.level = Level.ADVANCED;
        s.zones = new String[]{"guest", "internal", "dmz", "internet", "remote", "vendor", "cloud"};
        s.options = new String[]{"vlan", "dmz", "proxy", "vpn", "l3", "wifi",
                "fileshare", "mfp", "sitelink", "dhcp", "static", "ipv6"};
        s.needs.add(new Need("sitelink", "拠点間の接続", 0));
        s.needs.add(new Need("vpn", "在宅からの接続", 2));
        s.needs.add(new Need("dmz", "公開サーバーのDMZ", 3));
        s.needs.add(new Need("mfp", "複合機の接続", 5));
        s.needs.add(new Need("fileshare", "ファイル共有", 5));
        s.needs.add(new Need("wifi", "無線LAN", 6));
        s.needs.add(new Need("vlan", "来客の分離", 6));
        s.needs.add(new Need("l3", "VLAN間のルーティング", 6));
        s.needs.add(new Need("dhcp", "DHCPでの自動割り当て", 1));
        s.needs.add(new Need("static", "サーバー・複合機の固定IP", -1));

        s.allowances.add(new Allowance("guest", "internet", "guest", "net",
                "来客のインターネット利用", 6, true, 10, 15));
        s.allowances.add(new Allowance("internet", "dmz", "net", "web",
                "受発注システムと採用サイトの公開", 3, true, 20, 25));
        s.allowances.add(new Allowance("remote", "internal", "home", "pc",
                "在宅から社内システムへの接続", 2, true, 20, 25));
        s.allowances.add(new Allowance("internal", "internet", "pc", "net",
                "社員のインターネット利用", -1, false, 0, 0));
        s.allowances.add(new Allowance("internal", "dmz", "pc", "web",
                "公開サーバーの管理", -1, false, 0, 0));
        s.allowances.add(new Allowance("internal", "cloud", "pc", "cloud",
                "クラウドサービスの利用", -1, false, 0, 0));

        s.prohibitions.add(new Prohibition("guest", "pc",
                "来客端末から社内へ到達できます",
                "3,200人が使う社内が、来客の端末から見える状態です", 35, 25));
        s.prohibitions.add(new Prohibition("net", "pc",
                "インターネットから社内へ到達できます",
                "公開しているものと社内が同じ場所にあります", 40, 25));
        s.prohibitions.add(new Prohibition("net", "srv",
                "インターネットから社内サーバーへ到達できます",
                "部門の共有資料が外から触れる状態です", 30, 20));
        s.prohibitions.add(new Prohibition("vendor", "pc",
                "保守業者から社内へ常時到達できます",
                "工場の装置メーカーの回線が開きっぱなしです", 25, 20));
        return s;
    }

    public static List<Scenario> all() {
        return new ArrayList<>(Arrays.asList(ventureCloud(), ventureOffice(), outdoor(), office(), factory(), distributed(), enterprise()));
    }

    /** その案件の話に出てくるノードかどうか。図に何を出すかの判断に使う。 */
    public boolean usesNode(String id) {
        for (Allowance a : allowances) {
            if (a.fromNode.equals(id) || a.toNode.equals(id)) {
                return true;
            }
        }
        for (Prohibition p : prohibitions) {
            if (p.fromNode.equals(id) || p.toNode.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean usesZone(String zone) {
        for (String z : zones) {
            if (z.equals(zone)) {
                return true;
            }
        }
        return false;
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
