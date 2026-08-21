package com.appathy.netbuild;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ステータス盤。設計の選択と障害の状態をそのまま図にする。
 * 触る場所ではなく、見て分かるための領域。
 */
public class TopologyView extends View {

    /**
     * 機器の見た目の登録表。
     * 機器を増やすときは、ここに1行足して Design.buildGraph() にノードを追加すれば図に出る。
     */
    private static class Slot {
        final int iconRes;
        final float portraitX;
        final float portraitY;
        final float landscapeX;
        final float landscapeY;
        /** 何ページ目に置くか。0 が中心のページ。 */
        final int page;

        Slot(int iconRes, float px, float py, float lx, float ly) {
            this(iconRes, px, py, lx, ly, 0);
        }

        Slot(int iconRes, float px, float py, float lx, float ly, int page) {
            this.iconRes = iconRes;
            this.portraitX = px;
            this.portraitY = py;
            this.landscapeX = lx;
            this.landscapeY = ly;
            this.page = page;
        }
    }

    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("guest", new Slot(R.drawable.ic_guest, 0.13f, 0.14f, 0.07f, 0.20f));
        SLOTS.put("pc", new Slot(R.drawable.ic_pc, 0.13f, 0.66f, 0.07f, 0.74f));
        SLOTS.put("sw", new Slot(R.drawable.ic_switch, 0.44f, 0.40f, 0.34f, 0.46f));
        SLOTS.put("fw", new Slot(R.drawable.node_firewall, 0.78f, 0.40f, 0.64f, 0.46f));
        SLOTS.put("net", new Slot(R.drawable.ic_cloud, 0.80f, 0.06f, 0.92f, 0.14f));
        SLOTS.put("web", new Slot(R.drawable.ic_server, 0.56f, 0.80f, 0.44f, 0.86f));
        SLOTS.put("srv", new Slot(R.drawable.ic_server, 0.30f, 0.86f, 0.24f, 0.88f));
        SLOTS.put("proxy", new Slot(R.drawable.ic_switch, 0.60f, 0.18f, 0.44f, 0.16f));
        SLOTS.put("dns1", new Slot(R.drawable.ic_server, 0.86f, 0.72f, 0.66f, 0.86f));
        SLOTS.put("dns2", new Slot(R.drawable.ic_server, 0.86f, 0.94f, 0.85f, 0.86f));
        SLOTS.put("cloud", new Slot(R.drawable.ic_cloud, 0.96f, 0.30f, 0.96f, 0.42f));
        SLOTS.put("vendor", new Slot(R.drawable.ic_server, 0.98f, 0.06f, 0.98f, 0.04f));
        SLOTS.put("home", new Slot(R.drawable.ic_guest, 0.62f, 0.02f, 0.80f, 0.02f));
        SLOTS.put("sase", new Slot(R.drawable.ic_cloud, 0.42f, 0.20f, 0.40f, 0.16f));
        SLOTS.put("l3", new Slot(R.drawable.ic_switch, 0.60f, 0.40f, 0.49f, 0.46f));
        SLOTS.put("ap", new Slot(R.drawable.ic_wifi, 0.30f, 0.28f, 0.20f, 0.30f));
        SLOTS.put("fs", new Slot(R.drawable.ic_server, 0.44f, 0.94f, 0.34f, 0.96f));
        SLOTS.put("mfp", new Slot(R.drawable.ic_printer, 0.16f, 0.94f, 0.12f, 0.96f));
        // 拠点を増やすと2ページ目・3ページ目に広がる
        SLOTS.put("site2", new Slot(R.drawable.ic_site, 0.14f, 0.30f, 0.10f, 0.30f, 1));
        SLOTS.put("site2sw", new Slot(R.drawable.ic_switch, 0.50f, 0.50f, 0.45f, 0.50f, 1));
        SLOTS.put("site2pc", new Slot(R.drawable.ic_pc, 0.84f, 0.70f, 0.80f, 0.70f, 1));
        SLOTS.put("site3", new Slot(R.drawable.ic_site, 0.20f, 0.34f, 0.16f, 0.34f, 2));
        SLOTS.put("site3pc", new Slot(R.drawable.ic_pc, 0.72f, 0.62f, 0.70f, 0.62f, 2));
    }

    private static class Spot {
        final String id;
        final String label;
        final int iconRes;
        final float portraitX;
        final float portraitY;
        final float landscapeX;
        final float landscapeY;
        final int page;
        float px;
        float py;

        Spot(String id, String label, int iconRes,
             float portraitX, float portraitY, float landscapeX, float landscapeY, int page) {
            this.page = page;
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
            this.portraitX = portraitX;
            this.portraitY = portraitY;
            this.landscapeX = landscapeX;
            this.landscapeY = landscapeY;
        }
    }

    /** 図の中の配線。触れる対象なので保持する。 */
    private static class Link {
        final String a;
        final String b;
        final String kind;

        Link(String a, String b, String kind) {
            this.a = a;
            this.b = b;
            this.kind = kind;
        }
    }

    public interface OnPickListener {
        void onNodePicked(String id);

        void onLinkPicked(String a, String b, String kind);
    }

    private final List<Spot> spots = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private OnPickListener listener;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint danger = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Design design = new Design();
    private Scenario scenario;
    private Incident.Cause cause;
    private boolean guestReachesInternal;
    private boolean internetReachesInternal;
    private boolean blinkOn = true;
    private float density = 3f;
    /** 横スクロール量（ピクセル）。ページ数が2以上のときだけ動く。 */
    private float scrollX;
    private float dragStartX;
    private float dragStartScroll;
    private boolean dragging;
    private int pageCount = 1;

    public TopologyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3f * density);
        danger.setStyle(Paint.Style.STROKE);
        danger.setStrokeWidth(3f * density);
        danger.setColor(Color.parseColor("#E5484D"));
        danger.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));
        text.setColor(Color.parseColor("#C8D4E0"));
        text.setTextSize(10f * density);
        text.setTextAlign(Paint.Align.CENTER);
        badge.setStyle(Paint.Style.FILL);

        postDelayed(blinker, 600);
    }

    private final Runnable blinker = new Runnable() {
        public void run() {
            blinkOn = !blinkOn;
            if (cause != null) {
                invalidate();
            }
            postDelayed(this, 600);
        }
    };

    public void setOnPickListener(OnPickListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartScroll = scrollX;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pageCount > 1) {
                    float dx = event.getX() - dragStartX;
                    if (Math.abs(dx) > 12 * density) {
                        dragging = true;
                    }
                    if (dragging) {
                        scrollX = dragStartScroll - dx;
                        layoutSpots();
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    snapToPage();
                    return true;
                }
                break;
            default:
                return true;
        }

        layoutSpots();
        float x = event.getX();
        float y = event.getY();

        for (Spot s : spots) {
            if (Math.hypot(x - s.px, y - s.py) < 30 * density) {
                performClick();
                listener.onNodePicked(s.id);
                return true;
            }
        }
        for (Link l : links) {
            Spot a = spot(l.a);
            Spot b = spot(l.b);
            if (distanceToSegment(x, y, a.px, a.py, b.px, b.py) < 18 * density) {
                performClick();
                listener.onLinkPicked(l.a, l.b, l.kind);
                return true;
            }
        }
        return true;
    }

    /** 指を離したら一番近いページに寄せる。 */
    private void snapToPage() {
        int w = getWidth();
        if (w == 0) {
            return;
        }
        int page = Math.round(scrollX / w);
        page = Math.max(0, Math.min(pageCount - 1, page));
        scrollX = page * w;
        layoutSpots();
        invalidate();
    }

    public int currentPage() {
        int w = getWidth();
        return w == 0 ? 0 : Math.round(scrollX / w);
    }

    public int pageCount() {
        return pageCount;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return (float) Math.hypot(px - x1, py - y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    public void update(Design design, Incident.Cause cause) {
        update(design, cause, scenario);
    }

    public void update(Design design, Incident.Cause cause, Scenario scenario) {
        this.design = design;
        this.cause = cause;
        this.scenario = scenario;
        NetGraph g = design.buildGraph(scenario);
        rebuild(g);
        RuleEngine engine = new RuleEngine(design.buildRules(scenario));
        guestReachesInternal = g.find("guest") != null && g.find("pc") != null
                && engine.canReach(g, "guest", "pc").reachable;
        internetReachesInternal = g.find("pc") != null
                && engine.canReach(g, "net", "pc").reachable;
        invalidate();
    }

    /** 図の中身をグラフから作り直す。ノードが増えても描画側は変更不要。 */
    private void rebuild(NetGraph g) {
        scrollX = 0;
        spots.clear();
        links.clear();
        for (NetGraph.Node n : g.nodes) {
            Slot slot = SLOTS.get(n.id);
            if (slot != null) {
                spots.add(new Spot(n.id, n.label, slot.iconRes,
                        slot.portraitX, slot.portraitY, slot.landscapeX, slot.landscapeY, slot.page));
            }
        }
        List<String> seen = new ArrayList<>();
        for (NetGraph.Edge e : g.edges) {
            String key = e.from.compareTo(e.to) < 0 ? e.from + "|" + e.to : e.to + "|" + e.from;
            if (seen.contains(key) || SLOTS.get(e.from) == null || SLOTS.get(e.to) == null) {
                continue;
            }
            seen.add(key);
            links.add(new Link(e.from, e.to, kindOf(g, e)));
        }
        layoutSpots();
    }

    /** 線の意味づけ。どちら側の機器かで色を変えるために使う。 */
    private String kindOf(NetGraph g, NetGraph.Edge e) {
        NetGraph.Node a = g.find(e.from);
        NetGraph.Node b = g.find(e.to);
        String zoneA = a == null ? "" : String.valueOf(a.attr("zone"));
        String zoneB = b == null ? "" : String.valueOf(b.attr("zone"));
        if ("guest".equals(zoneA) || "guest".equals(zoneB)) {
            return "guest";
        }
        if ("dmz".equals(zoneA) || "dmz".equals(zoneB)) {
            return "server";
        }
        if ("internet".equals(zoneA) || "internet".equals(zoneB)) {
            return "wan";
        }
        if ("firewall".equals(a == null ? "" : a.type) || "firewall".equals(b == null ? "" : b.type)) {
            return "uplink";
        }
        return "internal";
    }

    /**
     * 機器の配置。画面いっぱいには広げず、中央に寄せた箱の中に収める。
     * 箱の最小サイズはタップしやすさのために確保し、最大サイズで広がりすぎを止める。
     */
    private void layoutSpots() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        pageCount = 1;
        for (Spot s : spots) {
            pageCount = Math.max(pageCount, s.page + 1);
        }
        float maxScroll = (pageCount - 1) * w;
        scrollX = Math.max(0, Math.min(maxScroll, scrollX));
        boolean landscape = w > h;
        float margin = 26 * density;
        float maxW = (landscape ? 660 : 400) * density;
        float maxH = (landscape ? 330 : 520) * density;
        float boxW = Math.min(w - margin * 2, maxW);
        float boxH = Math.min(h - margin * 2, maxH);
        float left = (w - boxW) / 2f;
        float top = (h - boxH) / 2f;
        for (Spot s : spots) {
            float nx = landscape ? s.landscapeX : s.portraitX;
            float ny = landscape ? s.landscapeY : s.portraitY;
            s.px = left + nx * boxW + s.page * w - scrollX;
            s.py = top + ny * boxH;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutSpots();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        layoutSpots();

        for (Link l : links) {
            Spot from = spot(l.a);
            Spot to = spot(l.b);
            if (from.page != to.page) {
                drawCrossPageLink(canvas, from, to);
                continue;
            }
            drawLink(canvas, l.a, l.b, colorOf(l), faultyLink(l));
        }

        if (guestReachesInternal) {
            drawDanger(canvas, "guest", "pc", "来客→社内 到達");
        }
        if (internetReachesInternal) {
            drawDanger(canvas, "net", "pc", "外部→社内 到達");
        }

        for (Spot s : spots) {
            drawSpot(canvas, s);
        }

        if (pageCount > 1) {
            text.setColor(Color.parseColor("#8B96A5"));
            canvas.drawText("← " + (currentPage() + 1) + " / " + pageCount + " →",
                    w / 2f, h - 8 * density, text);
            text.setColor(Color.parseColor("#C8D4E0"));
        }

        if (design.guestVlan) {
            text.setColor(Color.parseColor("#E0B15C"));
            canvas.drawText("VLAN 20（来客）", spots.get(0).px + 40 * density,
                    spots.get(0).py - 28 * density, text);
            text.setColor(Color.parseColor("#C8D4E0"));
        }
        if (design.dmz) {
            text.setColor(Color.parseColor("#7FD1B9"));
            canvas.drawText("DMZ", spots.get(5).px, spots.get(5).py + 40 * density, text);
            text.setColor(Color.parseColor("#C8D4E0"));
        }
    }

    /** ページをまたぐ配線は、行き先を示す短い線と矢印で表す。 */
    private void drawCrossPageLink(Canvas canvas, Spot from, Spot to) {
        line.setColor(Color.parseColor("#5A6472"));
        line.setStrokeWidth(3f * density);
        int w = getWidth();
        for (int i = 0; i < 2; i++) {
            Spot s = i == 0 ? from : to;
            Spot other = i == 0 ? to : from;
            if (s.px < 0 || s.px > w) {
                continue;
            }
            float dir = other.page > s.page ? 1f : -1f;
            float endX = s.px + dir * 46 * density;
            canvas.drawLine(s.px, s.py, endX, s.py, line);
            canvas.drawLine(endX, s.py, endX - dir * 8 * density, s.py - 6 * density, line);
            canvas.drawLine(endX, s.py, endX - dir * 8 * density, s.py + 6 * density, line);
            text.setColor(Color.parseColor("#8B96A5"));
            canvas.drawText(other.label, endX + dir * 34 * density, s.py - 10 * density, text);
            text.setColor(Color.parseColor("#C8D4E0"));
        }
    }

    private String colorOf(Link l) {
        if ("guest".equals(l.kind)) {
            return design.guestVlan ? "#E0B15C" : "#5A6472";
        }
        if ("server".equals(l.kind)) {
            return "#7FD1B9";
        }
        return "#5A6472";
    }

    private boolean faultyLink(Link l) {
        if (cause == null) {
            return false;
        }
        switch (cause) {
            case LINK_DOWN:
                return "uplink".equals(l.kind);
            case WAN_DOWN:
                return "wan".equals(l.kind);
            case GUEST_INTRUSION:
                return "guest".equals(l.kind);
            case WEB_COMPROMISE:
                return "web".equals(l.a) || "web".equals(l.b);
            case DNS_DOWN:
                return l.a.startsWith("dns") || l.b.startsWith("dns");
            case SERVER_EXPOSED:
                return "srv".equals(l.a) || "srv".equals(l.b);
            case MALWARE_C2:
                return "uplink".equals(l.kind);
            default:
                return false;
        }
    }

    private Spot spot(String id) {
        for (Spot s : spots) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return spots.get(0);
    }

    private void drawLink(Canvas canvas, String a, String b, String color, boolean faulty) {
        Spot from = spot(a);
        Spot to = spot(b);
        if (faulty) {
            line.setColor(blinkOn ? Color.parseColor("#E5484D") : Color.parseColor("#5A2226"));
            line.setStrokeWidth(4.5f * density);
        } else {
            line.setColor(Color.parseColor(color));
            line.setStrokeWidth(3f * density);
        }
        canvas.drawLine(from.px, from.py, to.px, to.py, line);
    }

    private void drawDanger(Canvas canvas, String a, String b, String label) {
        Spot from = spot(a);
        Spot to = spot(b);
        Path path = new Path();
        path.moveTo(from.px, from.py);
        float midX = (from.px + to.px) / 2f - 26 * density;
        float midY = (from.py + to.py) / 2f;
        path.quadTo(midX, midY, to.px, to.py);
        canvas.drawPath(path, danger);
        text.setColor(Color.parseColor("#E5484D"));
        float labelX = Math.max(text.measureText(label) / 2f + 6 * density,
                Math.min(getWidth() - text.measureText(label) / 2f - 6 * density, midX));
        canvas.drawText(label, labelX, midY - 6 * density, text);
        text.setColor(Color.parseColor("#C8D4E0"));
    }

    private void drawSpot(Canvas canvas, Spot s) {
        int size = (int) (34 * density);
        Drawable icon = ContextCompat.getDrawable(getContext(), s.iconRes);
        if (icon != null) {
            icon.setBounds((int) (s.px - size / 2f), (int) (s.py - size / 2f),
                    (int) (s.px + size / 2f), (int) (s.py + size / 2f));
            icon.draw(canvas);
        }
        float labelX = Math.max(text.measureText(s.label) / 2f + 4 * density,
                Math.min(getWidth() - text.measureText(s.label) / 2f - 4 * density, s.px));
        canvas.drawText(s.label, labelX, s.py + size / 2f + 13 * density, text);
    }
}
