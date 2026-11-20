package tr.olgun.pusula;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.Context;
import android.Manifest;
import android.hardware.*;
import android.location.*;
import android.view.*;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {
    static final String FINE=Manifest.permission.ACCESS_FINE_LOCATION, COARSE=Manifest.permission.ACCESS_COARSE_LOCATION;
    SensorManager sensors;
    LocationManager locations;
    Sensor rotation, acceleration, magnet;
    final float[] matrix=new float[9], adjusted=new float[9], angles=new float[3];
    float[] gravity, field;
    Dial dial;
    TextView status, coords, destination;
    Location current, goal;
    boolean active, ready, inaccurate, offeredSettings;
    final Locale tr=new Locale("tr","TR");
    int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
    TextView line(String s,int size,int color){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(color);
        t.setTextSize(size); t.setGravity(Gravity.CENTER); return t;
    }
    void chip(TextView v,int color){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(24));
        v.setBackground(d); v.setPadding(dp(15),dp(8),dp(15),dp(8));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        rotation=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acceleration=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnet=sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(0xff030405);
        root.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(dp(10)+i.getSystemWindowInsetLeft(),dp(5)+i.getSystemWindowInsetTop(),
                dp(10)+i.getSystemWindowInsetRight(),dp(6)+i.getSystemWindowInsetBottom()); return i; });
        TextView title=line("Pusula",22,-1); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        status=line("",13,0xffaeb7c4); root.addView(status);
        dial=new Dial(this); root.addView(dial,new LinearLayout.LayoutParams(-1,0,1));
        coords=line("",15,0xffedf2fa);
        root.addView(coords);
        destination=line("Hedef belirle",15,0xffb7f6cd); chip(destination,0xff103c26);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.topMargin=dp(5);
        root.addView(destination,lp); destination.setOnClickListener(v->editGoal());
        TextView signature=line("Muhammed Yusuf OLGUN",14,0xffcccccc);
        LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,-2);
        footer.topMargin=signature.getLineHeight();
        root.addView(signature,footer);
        if(getPreferences(0).contains("lat")){
            goal=new Location("hedef");
            goal.setLatitude(Double.parseDouble(getPreferences(0).getString("lat","0")));
            goal.setLongitude(Double.parseDouble(getPreferences(0).getString("lon","0")));
        }
        setContentView(root); updateInfo();
        receiveMap(getIntent());
        if(!getPreferences(0).getBoolean("locationPromptHandled",false)){
            getPreferences(0).edit().putBoolean("locationPromptHandled",true).apply();
            if(!permitted()) requestPermissions(new String[]{FINE,COARSE},1);
        }
    }
    AlertDialog goalDialog;
    int shareGeneration;
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);receiveMap(intent);
    }
    void receiveMap(Intent intent){
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;
        String share=intent.getStringExtra(Intent.EXTRA_TEXT);
        if(share==null || share.length()>16384)return;
        String subject=intent.getStringExtra(Intent.EXTRA_SUBJECT);
        int generation=++shareGeneration;
        Toast.makeText(this,"Paylaşılan konum okunuyor…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            double[] result;
            try{result=MapShare.resolve(share);}catch(Exception e){result=null;}
            final double[] point=result;
            runOnUiThread(()->{
                if(isDestroyed() || isFinishing() || generation!=shareGeneration)return;
                if(point==null){
                    new AlertDialog.Builder(this).setTitle("Koordinatlar okunamadı")
                        .setMessage("Google Haritalar’da yerin üzerine basılı tutarak bir iğne bırakın ve iğneyi Pusula ile paylaşın. Kısa bağlantılar için internet gerekir.")
                        .setPositiveButton("Tamam",null).show();
                }else editGoal(point,subject);
            });
        }).start();
    }
    void editGoal(){editGoal(null,null);}
    void editGoal(double[] shared,String subject){
        if(goalDialog!=null && goalDialog.isShowing())goalDialog.dismiss();
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(dp(22),dp(6),dp(22),0);
        TextView guide=line("Google Haritalar’da kayıtlı yeri veya arama sonucunu açın. Haritada başka bir nokta için basılı tutun. Yer kartından Paylaş → Pusula’yı seçin veya koordinatları aşağıya girin.",14,0xffcccccc);
        guide.setGravity(Gravity.START); box.addView(guide);
        Button maps=new Button(this);maps.setText("Google Haritalar’dan seç");box.addView(maps);
        maps.setOnClickListener(v->{
            String center=current==null?"0,0":current.getLatitude()+","+current.getLongitude();
            Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+center)).setPackage("com.google.android.apps.maps");
            try{startActivity(view);}catch(android.content.ActivityNotFoundException e){
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/")));}
                catch(android.content.ActivityNotFoundException ignored){Toast.makeText(this,"Harita uygulaması bulunamadı.",Toast.LENGTH_SHORT).show();}
            }
        });
        EditText name=input(box,"Hedef adı",false), lat=input(box,"Enlem (−90 … 90)",true), lon=input(box,"Boylam (−180 … 180)",true);
        if(shared!=null){
            name.setText(subject==null?"":subject);lat.setText(Double.toString(shared[0]));lon.setText(Double.toString(shared[1]));
        }else if(goal!=null){name.setText(getPreferences(0).getString("name","Hedef"));
            lat.setText(""+goal.getLatitude()); lon.setText(""+goal.getLongitude());}
        ScrollView scroll=new ScrollView(this); scroll.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Hedef belirle").setView(scroll).setNegativeButton("Vazgeç",null)
            .setNeutralButton("Hedefi kaldır",(a,b)->{goal=null;getPreferences(0).edit().remove("lat").remove("lon").remove("name").apply();updateInfo();})
            .setPositiveButton("Kaydet",null).create(); goalDialog=d; d.show();
        d.getButton(-1).setOnClickListener(v->{
            double la=number(lat),lo=number(lon);
            if(!(Math.abs(la)<=90)){lat.setError("Geçerli bir enlem girin");return;}
            if(!(Math.abs(lo)<=180)){lon.setError("Geçerli bir boylam girin");return;}
            goal=new Location("hedef");goal.setLatitude(la);goal.setLongitude(lo);
            String n=name.getText().toString().trim();
            getPreferences(0).edit().putString("name",n.isEmpty()?"Hedef":n)
                .putString("lat",""+la).putString("lon",""+lo).apply();updateInfo();d.dismiss();
        });
    }
    EditText input(LinearLayout box,String hint,boolean numeric){
        EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();
        if(numeric)e.setInputType(2|4096|8192);box.addView(e);return e;
    }
    double number(EditText t){try{return Double.parseDouble(t.getText().toString().trim().replace(',','.'));}catch(NumberFormatException e){return Double.NaN;}}
    @Override protected void onResume(){
        super.onResume();active=true;gravity=field=null;dial.heading=Float.NaN;inaccurate=false;
        ready=rotation!=null && sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_UI);
        if(!ready && acceleration!=null && magnet!=null){
            ready=sensors.registerListener(this,acceleration,SensorManager.SENSOR_DELAY_UI)
                && sensors.registerListener(this,magnet,SensorManager.SENSOR_DELAY_UI);
        }
        updateInfo();listenLocation();
    }
    @Override protected void onPause(){active=false;sensors.unregisterListener(this);locations.removeUpdates(this);super.onPause();}
    boolean permitted(){return checkSelfPermission(COARSE)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(FINE)==PackageManager.PERMISSION_GRANTED;}
    String[] providers(){return new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER};}
    boolean hasProvider(){for(String p:providers())if(locations.isProviderEnabled(p))return true;return false;}
    void listenLocation(){
        if(!active)return;
        if(!permitted()){
            current=null; updateInfo(); return;
        }
        if(!hasProvider()){
            current=null; updateInfo();
            if(!offeredSettings){
                offeredSettings=true;
                android.content.Intent settings=new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                new AlertDialog.Builder(this).setTitle("Telefonun konumu kapalı")
                    .setMessage("Koordinatları ve hedef mesafesini görmek için telefonun konumunu açın.")
                    .setNegativeButton("Şimdi değil",null)
                    .setPositiveButton("Ayarlara git",(dialog,which)->{
                        try{startActivity(settings);}catch(android.content.ActivityNotFoundException ignored){
                            Toast.makeText(this,"Telefon ayarlarından konumu açabilirsiniz.",Toast.LENGTH_LONG).show();
                        }
                    }).show();
            }
            return;
        }
        locations.removeUpdates(this);
        for(String p:providers())if(locations.isProviderEnabled(p)){
            try{locations.requestLocationUpdates(p,2000,2,this);}catch(SecurityException ignored){}
        }
        updateInfo();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==1)listenLocation();}
    @Override public void onLocationChanged(Location l){current=l;updateInfo();}
    @Override public void onProviderDisabled(String p){if(current!=null && p.equals(current.getProvider()))current=null;updateInfo();}
    @Override public void onProviderEnabled(String p){listenLocation();}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    float declination(){
        if(current==null)return 0;
        return new GeomagneticField((float)current.getLatitude(),(float)current.getLongitude(),(float)current.getAltitude(),System.currentTimeMillis()).getDeclination();
    }
    float correction;
    void updateInfo(){
        correction=declination();
        status.setText(!ready?"Pusula sensörü kullanılamıyor":inaccurate?"Kalibrasyon için telefonu 8 çizerek hareket ettirin":current==null?"Manyetik kuzey":"Gerçek kuzey");
        if(current==null)coords.setText(!permitted()?"Konum izni kapalı":hasProvider()?"Konum bekleniyor…":"Konum kapalı");
        else coords.setText(String.format(tr,"%.5f° %s   %.5f° %s",Math.abs(current.getLatitude()),current.getLatitude()<0?"G":"K",Math.abs(current.getLongitude()),current.getLongitude()<0?"B":"D")+
            (current.hasAltitude()?String.format(tr,"   %.0f m",current.getAltitude()):""));
        String n=getPreferences(0).getString("name","Hedef");
        dial.target=Float.NaN;
        if(goal==null) destination.setText("Hedef belirle");
        else if(current==null)destination.setText(n+" · Konum gerekli");
        else {float m=current.distanceTo(goal);dial.target=current.bearingTo(goal);
            destination.setText(n+(m<1000?String.format(tr," · %.0f m",m):String.format(tr," · %.1f km",m/1000)));}
        dial.invalidate();
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){
        if(s.getType()!=Sensor.TYPE_ACCELEROMETER){inaccurate=accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE;updateInfo();}
    }
    @Override public void onSensorChanged(SensorEvent e){
        switch(e.sensor.getType()){
        case Sensor.TYPE_ROTATION_VECTOR: SensorManager.getRotationMatrixFromVector(matrix,e.values);break;
        case Sensor.TYPE_ACCELEROMETER: gravity=e.values.clone();break;
        case Sensor.TYPE_MAGNETIC_FIELD: field=e.values.clone();break;
        default:return;
        }
        if(e.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR && (gravity==null||field==null||!SensorManager.getRotationMatrix(matrix,null,gravity,field)))return;
        int orientation=getWindowManager().getDefaultDisplay().getRotation();
        int[] xs={1,2,129,130},ys={2,129,130,1};
        SensorManager.remapCoordinateSystem(matrix,xs[orientation],ys[orientation],adjusted);
        SensorManager.getOrientation(adjusted,angles);
        float h=((float)Math.toDegrees(angles[0])+correction+360)%360;
        dial.heading=Float.isNaN(dial.heading)?h:(dial.heading+.35f*((h-dial.heading+540)%360-180)+360)%360;
        dial.invalidate();
    }
}
