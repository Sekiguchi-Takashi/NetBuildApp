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
import java.util.List;

/**
 * ステータス盤。設計の選択と障害の状態をそのまま図にする。
 * 触る場所ではなく、見て分かるための領域。
 */
public class TopologyView extends View {

    private static class Spot {
        final String id;
        final String label;
        final int iconRes;
        final float portraitX;
        final float portraitY;
        final float landscapeX;
        final float landscapeY;
        float px;
        float py;

        Spot(String id, String label, int iconRes,
             float portraitX, float portraitY, float landscapeX, float landscapeY) {
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
    private Incident.Cause cause;
    private boolean guestReachesInternal;
    private boolean internetReachesInternal;
    private boolean blinkOn = true;
    private float density = 3f;

    public TopologyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        spots.add(new Spot("guest", "来客端末", R.drawable.ic_guest, 0.14f, 0.16f, 0.08f, 0.22f));
        spots.add(new Spot("pc", "社員PC", R.drawable.ic_pc, 0.14f, 0.72f, 0.08f, 0.78f));
        spots.add(new Spot("sw", "スイッチ", R.drawable.ic_switch, 0.44f, 0.44f, 0.34f, 0.50f));
        spots.add(new Spot("fw", "Firewall", R.drawable.node_firewall, 0.76f, 0.44f, 0.64f, 0.50f));
        spots.add(new Spot("net", "インターネット", R.drawable.ic_cloud, 0.80f, 0.10f, 0.92f, 0.18f));
        spots.add(new Spot("web", "Webサーバー", R.drawable.ic_server, 0.60f, 0.82f, 0.44f, 0.88f));

        links.add(new Link("guest", "sw", "guest"));
        links.add(new Link("pc", "sw", "internal"));
        links.add(new Link("web", "sw", "server"));
        links.add(new Link("sw", "fw", "uplink"));
        links.add(new Link("fw", "net", "wan"));

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
        if (event.getAction() != MotionEvent.ACTION_DOWN || listener == null) {
            return super.onTouchEvent(event);
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
        this.design = design;
        this.cause = cause;
        NetGraph g = design.buildGraph();
        RuleEngine engine = new RuleEngine(design.buildRules());
        guestReachesInternal = engine.canReach(g, "guest", "pc").reachable;
        internetReachesInternal = engine.canReach(g, "net", "pc").reachable;
        invalidate();
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
            s.px = left + nx * boxW;
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

        drawLink(canvas, "guest", "sw", design.guestVlan ? "#E0B15C" : "#5A6472",
                cause == Incident.Cause.GUEST_INTRUSION);
        drawLink(canvas, "pc", "sw", "#5A6472", false);
        drawLink(canvas, "web", "sw", design.dmz ? "#7FD1B9" : "#5A6472",
                cause == Incident.Cause.WEB_COMPROMISE);
        drawLink(canvas, "sw", "fw", "#5A6472", cause == Incident.Cause.LINK_DOWN);
        drawLink(canvas, "fw", "net", "#5A6472", cause == Incident.Cause.WAN_DOWN);

        if (guestReachesInternal) {
            drawDanger(canvas, "guest", "pc", "来客→社内 到達");
        }
        if (internetReachesInternal) {
            drawDanger(canvas, "net", "pc", "外部→社内 到達");
        }

        for (Spot s : spots) {
            drawSpot(canvas, s);
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
