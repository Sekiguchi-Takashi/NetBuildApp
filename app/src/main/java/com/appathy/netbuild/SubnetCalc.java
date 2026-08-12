package com.appathy.netbuild;

import java.net.Inet4Address;
import java.net.InetAddress;

/** IPv4 のプレフィックスから収容可能数などを計算する。ゲーム側の IP 設計評価と同じ計算を使う。 */
public class SubnetCalc {

    public final String address;
    public final int prefixLength;
    public final String network;
    public final String broadcast;
    public final String mask;
    public final long usableHosts;

    private SubnetCalc(String address, int prefixLength, String network, String broadcast,
                       String mask, long usableHosts) {
        this.address = address;
        this.prefixLength = prefixLength;
        this.network = network;
        this.broadcast = broadcast;
        this.mask = mask;
        this.usableHosts = usableHosts;
    }

    public static SubnetCalc from(InetAddress addr, int prefixLength) {
        if (!(addr instanceof Inet4Address) || prefixLength < 0 || prefixLength > 32) {
            return null;
        }
        byte[] raw = addr.getAddress();
        long ip = 0;
        for (byte b : raw) {
            ip = (ip << 8) | (b & 0xFF);
        }
        long maskBits = prefixLength == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
        long net = ip & maskBits;
        long bcast = net | (~maskBits & 0xFFFFFFFFL);
        long hosts = prefixLength >= 31 ? 0 : (1L << (32 - prefixLength)) - 2;
        return new SubnetCalc(toDotted(ip), prefixLength, toDotted(net), toDotted(bcast),
                toDotted(maskBits), hosts);
    }

    /** 将来台数に対して現在のプレフィックスが足りるかの簡易判定。 */
    public String capacityNote(int plannedDevices) {
        if (usableHosts == 0) {
            return "ホスト割り当て不可のプレフィックス";
        }
        if (plannedDevices <= 0) {
            return usableHosts + " 台まで収容可能";
        }
        if (plannedDevices > usableHosts) {
            return "収容 " + usableHosts + " 台 < 想定 " + plannedDevices + " 台（ScalabilityPenalty）";
        }
        long headroom = usableHosts - plannedDevices;
        return "収容 " + usableHosts + " 台、余裕 " + headroom + " 台";
    }

    private static String toDotted(long value) {
        return ((value >> 24) & 0xFF) + "." + ((value >> 16) & 0xFF) + "."
                + ((value >> 8) & 0xFF) + "." + (value & 0xFF);
    }
}
