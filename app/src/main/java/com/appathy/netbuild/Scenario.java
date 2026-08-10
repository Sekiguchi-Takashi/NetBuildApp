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
        public boolean revealed;

        Hidden(String question, String answer, String requirement) {
            this.question = question;
            this.answer = answer;
            this.requirement = requirement;
        }
    }

    public final String client;
    public final String explicitRequirement;
    public final int budget;
    public final int currentUsers;
    public final int futureUsers;
    public final List<Hidden> hidden;

    private Scenario(String client, String explicitRequirement, int budget,
                     int currentUsers, int futureUsers, List<Hidden> hidden) {
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
                        "将来200人分のIPアドレスとポートが必要"),
                new Hidden("来客の方もWi-Fiを使いますか？",
                        "はい。打ち合わせが多いので来客用も欲しいです。",
                        "来客用Wi-Fi。社内リソースには触らせない"),
                new Hidden("社内のWebサイトを外部から見る予定はありますか？",
                        "あります。採用ページを社内サーバーで公開したいです。",
                        "Webサーバーの外部公開が必要")
        ));
        return new Scenario("株式会社サンプル商事", "新しいオフィスでネットを使いたい",
                1000000, 50, 200, hidden);
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
