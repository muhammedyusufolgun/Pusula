package tr.olgun.pusula;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class Dial extends View {
    float heading = Float.NaN, target = Float.NaN;
    final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path triangle = new Path();
    final String[] points = {"K", "D", "G", "B"};
    final String[] directions = {"Kuzey","Kuzeydoğu","Doğu","Güneydoğu","Güney","Güneybatı","Batı","Kuzeybatı"};
    Dial(Context context) { super(context); pen.setTextAlign(Paint.Align.CENTER); }
    void label(Canvas c, String s, float x, float y, float size, int color) {
        pen.setColor(color); pen.setTextSize(size);
        c.drawText(s, x, y-(pen.ascent()+pen.descent())/2, pen);
    }
    @Override protected void onDraw(Canvas c) {
        float r = Math.min(getWidth(),getHeight()) * .39f;
        c.translate(getWidth()/2f,getHeight()/2f);
        boolean live = !Float.isNaN(heading);
        float h = live ? heading : 0;
        for (int i=0; i<360; i+=2) {
            c.save(); c.rotate(i-h);
            boolean major = i%30 == 0;
            pen.setColor(i==0 ? 0xffff454c : major ? 0xffe8ecf2 : 0xff515763);
            pen.setStrokeWidth(r*(major ? .015f : .008f));
            c.drawLine(0,-r,0,-r*(major ? .88f : .93f),pen);
            if (major) {
                c.save(); c.translate(0,-1.11f*r); c.rotate(h-i);
                label(c,""+i,0,0,r*.094f,0xffa7adb6); c.restore();
            }
            if(i%90==0) {
                c.translate(0,-.74f*r); c.rotate(h-i);
                label(c,points[i/90],0,0,r*.15f,i==0 ? 0xffff555d : -1);
            }
            c.restore();
        }
        if(live && !Float.isNaN(target)) {
            c.save(); c.rotate(target-h); pen.setColor(0xff3ad98f);
            c.drawCircle(0,-r*.99f,r*.037f,pen); c.restore();
        }
        triangle.reset(); triangle.moveTo(0,-r*.88f); triangle.lineTo(-r*.052f,-r*.775f);
        triangle.lineTo(r*.052f,-r*.775f); triangle.close(); pen.setColor(0xff4ba5ff);
        c.drawPath(triangle,pen);
        label(c,live ? (Math.round(h)%360)+"°" : "—",0,-r*.06f,r*.33f,-1);
        if(live) label(c,directions[Math.round(h/45)%8],0,r*.19f,r*.10f,0xffaeb7c4);
    }
}
