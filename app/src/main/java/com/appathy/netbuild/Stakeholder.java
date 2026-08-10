package com.appathy.netbuild;

/**
 * 画面右に立つ相手。状況によって入れ替わる。
 * 同時に出すのは社員と1人だけにして、画面を渋滞させない。
 */
public enum Stakeholder {

    CLIENT("依頼者", "発注担当",
            R.drawable.chara_client, R.drawable.chara_client_worry, R.drawable.chara_client_angry),
    BOSS("決裁者", "依頼者の上司",
            R.drawable.chara_boss_normal, R.drawable.chara_boss_worry, R.drawable.chara_boss_angry),
    IT_STAFF("現場担当", "先方の情シス",
            R.drawable.chara_it_normal, R.drawable.chara_it_worry, R.drawable.chara_it_angry),
    OFFICE("総務担当", "先方の運用担当",
            R.drawable.chara_office_normal, R.drawable.chara_office_worry, R.drawable.chara_office_angry),
    INTERN("インターン", "先方の学生スタッフ",
            R.drawable.chara_intern_normal, R.drawable.chara_intern_worry, R.drawable.chara_intern_angry);

    public final String name;
    public final String role;
    private final int normal;
    private final int worry;
    private final int angry;

    Stakeholder(String name, String role, int normal, int worry, int angry) {
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
        if (incidentActive || trust < 55) {
            return worry;
        }
        return normal;
    }
}
