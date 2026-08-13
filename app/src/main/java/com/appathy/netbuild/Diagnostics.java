package com.appathy.netbuild;

import java.util.Map;

/**
 * MD §27。診断コマンドを打ち、結果から仮説の確率を更新する。
 * 結果は原因から決定的に決まる（判定はLLMではなくここ）。
 */
public class Diagnostics {

    public enum Command {
        GATEWAY_PING("ping デフォルトゲートウェイ"),
        EXTERNAL_PING("ping 8.8.8.8"),
        RESOLVE("名前解決 example.com"),
        DHCP_LEASE("新規端末のDHCP取得を確認"),
        INTERNAL_LOG("社内サーバーのアクセスログを確認"),
        PUBLIC_LOG("公開サーバーの改ざん検知ログを確認"),
        EXTERNAL_REACH("外部から社内サーバーに届くか確認"),
        PROXY_LOG("プロキシの通信ログを確認"),
        FILE_CHECK("共有フォルダのファイルを開いて確認");

        public final String label;

        Command(String label) {
            this.label = label;
        }
    }

    /** その原因のとき、そのコマンドが正常に見えるかどうか。 */
    public boolean normal(Command command, Incident.Cause cause) {
        switch (command) {
            case GATEWAY_PING:
                return cause != Incident.Cause.LINK_DOWN;
            case EXTERNAL_PING:
                return cause != Incident.Cause.LINK_DOWN && cause != Incident.Cause.WAN_DOWN;
            case RESOLVE:
                return cause != Incident.Cause.LINK_DOWN
                        && cause != Incident.Cause.WAN_DOWN
                        && cause != Incident.Cause.DNS_DOWN;
            case DHCP_LEASE:
                return cause != Incident.Cause.IP_EXHAUSTED;
            case INTERNAL_LOG:
                return cause != Incident.Cause.GUEST_INTRUSION
                        && cause != Incident.Cause.SERVER_EXPOSED;
            case EXTERNAL_REACH:
                return cause != Incident.Cause.SERVER_EXPOSED;
            case PROXY_LOG:
                return cause != Incident.Cause.MALWARE_C2
                        && cause != Incident.Cause.RANSOMWARE;
            case FILE_CHECK:
                return cause != Incident.Cause.RANSOMWARE;
            case PUBLIC_LOG:
                return cause != Incident.Cause.WEB_COMPROMISE;
            default:
                return true;
        }
    }

    public String resultText(Command command, boolean normal) {
        switch (command) {
            case GATEWAY_PING:
                return normal ? "応答あり（1.2 ms）" : "応答なし（100% packet loss）";
            case EXTERNAL_PING:
                return normal ? "応答あり（14 ms）" : "応答なし";
            case RESOLVE:
                return normal ? "93.184.216.34 を取得" : "名前解決に失敗";
            case DHCP_LEASE:
                return normal ? "アドレスを取得できた" : "取得できず（プールに空きなし）";
            case INTERNAL_LOG:
                return normal ? "不審なアクセスなし" : "来客セグメントのIPからの認証試行を多数検出";
            case PUBLIC_LOG:
                return normal ? "改ざん検知なし" : "公開ディレクトリのファイル改変を検出";
            case EXTERNAL_REACH:
                return normal ? "外部からは応答しない" : "外部から共有フォルダに接続できてしまう";
            case PROXY_LOG:
                return normal ? "不審な宛先への通信なし" : "1台の端末から、見覚えのない宛先へ短い間隔で接続";
            case FILE_CHECK:
                return normal ? "問題なく開ける" : "拡張子が変わっていて開けない。脅迫文が置かれている";
            default:
                return normal ? "正常" : "異常";
        }
    }

    /** 観測結果で belief を更新する。一致 0.95、不一致 0.05 の尤度。 */
    public void update(Incident incident, Command command, boolean observedNormal) {
        double total = 0;
        for (Map.Entry<Incident.Cause, Double> e : incident.belief.entrySet()) {
            double likelihood = normal(command, e.getKey()) == observedNormal ? 0.95 : 0.05;
            double posterior = e.getValue() * likelihood;
            e.setValue(posterior);
            total += posterior;
        }
        if (total <= 0) {
            return;
        }
        for (Map.Entry<Incident.Cause, Double> e : incident.belief.entrySet()) {
            e.setValue(e.getValue() / total);
        }
    }

    /**
     * いま打つと一番情報が得られるコマンド。
     * 仮説の確率を正常側と異常側にどれだけ均等に割れるかで選ぶ。
     */
    public Command suggest(Incident incident) {
        return suggest(incident, true);
    }

    public Command suggest(Incident incident, boolean proxyAvailable) {
        Command best = null;
        double bestSplit = -1;
        for (Command c : Command.values()) {
            if (alreadyRun(incident, c) || (c == Command.PROXY_LOG && !proxyAvailable)) {
                continue;
            }
            double normalMass = 0;
            for (Map.Entry<Incident.Cause, Double> e : incident.belief.entrySet()) {
                if (normal(c, e.getKey())) {
                    normalMass += e.getValue();
                }
            }
            double split = Math.min(normalMass, 1 - normalMass);
            if (split > bestSplit) {
                bestSplit = split;
                best = c;
            }
        }
        return bestSplit <= 0.001 ? null : best;
    }

    private boolean alreadyRun(Incident incident, Command command) {
        for (String entry : incident.log) {
            if (entry.startsWith(command.label)) {
                return true;
            }
        }
        return false;
    }

    public String run(Incident incident, Command command) {
        return run(incident, command, true);
    }

    /** プロキシが無い状態でログを見ても、記録そのものが存在しない。 */
    public String run(Incident incident, Command command, boolean proxyAvailable) {
        boolean noRecord = command == Command.PROXY_LOG && !proxyAvailable;
        boolean observed = normal(command, incident.cause);
        if (!noRecord) {
            update(incident, command, observed);
        }
        String line = command.label + "  →  "
                + (noRecord ? "プロキシが無いため記録が残っていません（何も分からない）"
                : resultText(command, observed));
        incident.log.add(line);
        StringBuilder sb = new StringBuilder("診断\n");
        for (String entry : incident.log) {
            sb.append("  ").append(entry).append('\n');
        }
        sb.append('\n').append(incident.describeBelief());
        return sb.toString();
    }
}
