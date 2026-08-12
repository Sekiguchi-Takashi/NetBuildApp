package com.appathy.netbuild;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 実機からの到達性測定。root 不要の範囲のみ。 */
public class ProbeRunner {

    private static final String PING = "/system/bin/ping";
    private static final Pattern RTT = Pattern.compile("time[=<]\\s*([0-9.]+)\\s*ms");
    private static final Pattern FROM_ADDR = Pattern.compile("[Ff]rom\\s+([0-9a-fA-F:.]+)");

    private Boolean pingUsable = null;

    /** 端末によっては exec 自体が塞がれているため、初回に一度だけ判定する。 */
    public synchronized boolean isPingUsable() {
        if (pingUsable == null) {
            String out = exec(new String[]{PING, "-c", "1", "-W", "1", "127.0.0.1"}, 3);
            pingUsable = out != null && (out.contains("bytes from") || out.contains("PING"));
        }
        return pingUsable;
    }

    /** ICMP が使えない場合の代替。TCP 接続で到達性を推定する。 */
    private NetGraph.Reachability fallback(String target) {
        long start = System.currentTimeMillis();
        try {
            InetAddress addr = InetAddress.getByName(target);
            if (addr.isReachable(2000)) {
                return new NetGraph.Reachability(DeviceNetworkCollector.SELF, target, "isReachable",
                        true, "ICMP代替", System.currentTimeMillis() - start);
            }
        } catch (Exception ignored) {
        }
        for (int port : new int[]{443, 80, 53}) {
            NetGraph.Reachability r = tcp(target, port, 1500);
            if (r.reached) {
                return new NetGraph.Reachability(DeviceNetworkCollector.SELF, target, "tcp-probe",
                        true, "port " + port + " 到達", System.currentTimeMillis() - start);
            }
        }
        return new NetGraph.Reachability(DeviceNetworkCollector.SELF, target, "tcp-probe",
                false, "ICMP不可・TCPも不通", System.currentTimeMillis() - start);
    }

    public NetGraph.Reachability ping(String target) {
        if (!isPingUsable()) {
            return fallback(target);
        }
        long start = System.currentTimeMillis();
        String output = exec(new String[]{PING, "-c", "1", "-W", "2", target}, 4);
        long elapsed = System.currentTimeMillis() - start;
        if (output == null) {
            return new NetGraph.Reachability(DeviceNetworkCollector.SELF, target, "icmp",
                    false, "ping コマンドを実行できません", elapsed);
        }
        boolean ok = output.contains("bytes from") || output.contains(" 0% packet loss");
        String detail = null;
        Matcher m = RTT.matcher(output);
        if (m.find()) {
            detail = m.group(1) + " ms";
        } else if (!ok) {
            detail = firstMeaningfulLine(output);
        }
        return new NetGraph.Reachability(DeviceNetworkCollector.SELF, target, "icmp", ok, detail, elapsed);
    }

    public NetGraph.Reachability tcp(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            return new NetGraph.Reachability(DeviceNetworkCollector.SELF, host + ":" + port,
                    "tcp", true, "connected", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new NetGraph.Reachability(DeviceNetworkCollector.SELF, host + ":" + port,
                    "tcp", false, e.getClass().getSimpleName(), elapsed);
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public NetGraph.Reachability resolve(String hostname) {
        long start = System.currentTimeMillis();
        try {
            InetAddress[] addrs = InetAddress.getAllByName(hostname);
            StringBuilder sb = new StringBuilder();
            for (InetAddress a : addrs) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(a.getHostAddress());
            }
            return new NetGraph.Reachability(DeviceNetworkCollector.SELF, hostname, "dns",
                    true, sb.toString(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new NetGraph.Reachability(DeviceNetworkCollector.SELF, hostname, "dns",
                    false, e.getClass().getSimpleName(), System.currentTimeMillis() - start);
        }
    }

    /** TTL を 1 から増やして応答元を集める簡易 traceroute。 */
    public List<NetGraph.Reachability> trace(String target, int maxHops) {
        List<NetGraph.Reachability> hops = new ArrayList<>();
        if (!isPingUsable()) {
            hops.add(new NetGraph.Reachability("hop1", target, "icmp-ttl", false,
                    "この端末では ping を実行できません", 0));
            return hops;
        }
        for (int ttl = 1; ttl <= maxHops; ttl++) {
            long start = System.currentTimeMillis();
            String output = exec(new String[]{PING, "-c", "1", "-W", "2", "-t", String.valueOf(ttl), target}, 4);
            long elapsed = System.currentTimeMillis() - start;
            if (output == null) {
                hops.add(new NetGraph.Reachability("hop" + ttl, target, "icmp-ttl", false,
                        "ping コマンドを実行できません", elapsed));
                break;
            }
            boolean arrived = output.contains("bytes from");
            String addr = null;
            Matcher m = FROM_ADDR.matcher(output);
            if (m.find()) {
                addr = m.group(1);
            }
            String detail = addr == null ? "* 応答なし" : addr;
            hops.add(new NetGraph.Reachability("hop" + ttl, target, "icmp-ttl", addr != null, detail, elapsed));
            if (arrived) {
                break;
            }
        }
        return hops;
    }

    private String exec(String[] command, int timeoutSec) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            process.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String firstMeaningfulLine(String output) {
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("PING")) {
                return trimmed;
            }
        }
        return "応答なし";
    }
}
