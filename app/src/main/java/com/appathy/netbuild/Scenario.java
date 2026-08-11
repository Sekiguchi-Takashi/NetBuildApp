package com.appathy.netbuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** MD §8 / §9。表面要求と隠れた要求を分けて持つ。 */
public class Scenario {

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
    public final String personality;
    public final List<Hidden> hidden;

    private Scenario(String client, String explicitRequirement, int budget,
                     int currentUsers, int futureUsers, String personality, List<Hidden> hidden) {
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
                new Hidden("社内のWebサイトを外部から見る予定はありますか？",
                        "あります。採用ページを社内サーバーで公開したいです。",
                        "Webサーバーの外部公開が必要",
                        "机に採用パンフレットの校正刷りが置いてある")
        ));
        return new Scenario("株式会社サンプル商事", "新しいオフィスでネットを使いたい",
                1000000, 50, 200,
                "専門用語を出すと黙ってうなずくだけになる。要望は聞かれるまで自分からは言わない",
                hidden);
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
