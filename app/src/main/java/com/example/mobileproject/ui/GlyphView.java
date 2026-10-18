package com.example.mobileproject.ui;

import android.graphics.*;
import android.view.View;

public class GlyphView extends View {
    private String name = "search";
    private int color = Color.BLACK;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean solid;

    public GlyphView(android.content.Context context, String name, int color) {
        super(context);
        setGlyph(name, color);
    }

    public GlyphView(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setGlyph(String name, int color) {
        this.name = name;
        this.color = color;
        invalidate();
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void line(Canvas c, float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        c.drawPath(p, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        float side = Math.min(getWidth(), getHeight());
        c.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
        c.scale(side / 24f, side / 24f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        switch (name) {
            case "plus":
                line(c, 12, 4, 12, 20);
                line(c, 4, 12, 20, 12);
                break;
            case "menu":
                line(c, 4, 6, 20, 6);
                line(c, 4, 12, 20, 12);
                line(c, 4, 18, 20, 18);
                break;
            case "search":
                c.drawCircle(10.5f, 10.5f, 7.5f, paint);
                line(c, 16, 16, 22, 22);
                break;
            case "back":
                line(c, 15, 5, 8, 12, 15, 19);
                line(c, 8, 12, 21, 12);
                break;
            case "next":
                line(c, 10, 5, 17, 12, 10, 19);
                line(c, 3, 12, 17, 12);
                break;
            case "chevron":
                line(c, 9, 5, 16, 12, 9, 19);
                break;
            case "bell": {
                Path p = new Path();
                p.moveTo(5, 17);
                p.cubicTo(8, 15, 6, 10, 8, 7);
                p.cubicTo(10, 3, 15, 3, 17, 7);
                p.cubicTo(19, 10, 17, 15, 20, 17);
                p.close();
                c.drawPath(p, paint);
                line(c, 10, 21, 14, 21);
                line(c, 12, 2, 12, 4);
                break;
            }
            case "calendar":
                c.drawRoundRect(3, 5, 21, 22, 2, 2, paint);
                line(c, 3, 10, 21, 10);
                line(c, 8, 2, 8, 7);
                line(c, 16, 2, 16, 7);
                paint.setStyle(Paint.Style.FILL);
                for (int x = 7; x <= 17; x += 5)
                    for (int y = 14; y <= 18; y += 4) c.drawCircle(x, y, .8f, paint);
                break;
            case "chat":
                line(c, 3, 3, 21, 3, 21, 18, 9, 18, 3, 23, 3, 3);
                break;
            case "userCircle":
                c.drawCircle(12, 12, 10, paint);
                c.drawCircle(12, 8.5f, 3.2f, paint);
                c.drawArc(5, 13, 19, 25, 190, 160, false, paint);
                break;
            case "user":
                c.drawCircle(12, 7, 4, paint);
                c.drawArc(3, 12, 21, 31, 180, 180, false, paint);
                break;
            case "exchange":
                c.drawCircle(6, 5, 2.3f, paint);
                c.drawCircle(18, 19, 2.3f, paint);
                line(c, 6, 8, 6, 21);
                line(c, 18, 16, 18, 3);
                line(c, 12, 3, 18, 3, 22, 7);
                break;
            case "settings":
                line(c, 3, 7, 21, 7);
                line(c, 3, 17, 21, 17);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                c.drawCircle(8, 7, 3, paint);
                c.drawCircle(16, 17, 3, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(color);
                c.drawCircle(8, 7, 3, paint);
                c.drawCircle(16, 17, 3, paint);
                break;
            case "info":
                c.drawCircle(12, 12, 10, paint);
                line(c, 12, 11, 12, 17);
                c.drawCircle(12, 7, 0.5f, paint);
                break;
            case "logout":
                line(c, 9, 3, 3, 3, 3, 21, 9, 21);
                line(c, 9, 12, 23, 12);
                line(c, 17, 6, 23, 12, 17, 18);
                break;
            case "save":
                c.drawRoundRect(3, 3, 21, 21, 2, 2, paint);
                line(c, 7, 3, 7, 10, 17, 10, 17, 3);
                line(c, 7, 21, 7, 15, 17, 15, 17, 21);
                break;
            case "trash":
                line(c, 3, 6, 21, 6);
                line(c, 6, 6, 7, 22, 17, 22, 18, 6);
                line(c, 8, 6, 8, 2, 16, 2, 16, 6);
                line(c, 10, 10, 10, 18);
                line(c, 14, 10, 14, 18);
                break;
            case "heart": {
                Path p = new Path();
                p.moveTo(12, 21);
                p.cubicTo(9, 18, 2, 13, 2, 7);
                p.cubicTo(2, 1, 9, 1, 12, 6);
                p.cubicTo(15, 1, 22, 1, 22, 7);
                p.cubicTo(22, 13, 15, 18, 12, 21);
                p.close();
                if (solid) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFD92D38);
                    c.drawPath(p, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.WHITE);
                }
                c.drawPath(p, paint);
                break;
            }
        }
        c.restore();
    }
}
