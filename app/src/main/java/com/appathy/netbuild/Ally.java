package com.appathy.netbuild;

/** 画面左に立つ自社側の人物。行動の実行役と、用語の解説役を切り替える。 */
public enum Ally {

    STAFF("自社の社員", "あなたの相棒",
            R.drawable.chara_normal, R.drawable.chara_worry, R.drawable.chara_angry),
    NEWBIE("自社の新人", "解説してもらう",
            R.drawable.chara_newbie_normal, R.drawable.chara_newbie_worry, R.drawable.chara_newbie_angry);

    public final String name;
    public final String role;
    private final int normal;
    private final int worry;
    private final int angry;

    Ally(String name, String role, int normal, int worry, int angry) {
        this.name = name;
        this.role = role;
        this.normal = normal;
        this.worry = worry;
        this.angry = angry;
    }

    public int face(boolean incidentActive, int trust) {
        if (trust < 35) {
            return angry;
        }
        if (incidentActive) {
            return worry;
        }
        return normal;
    }
}
