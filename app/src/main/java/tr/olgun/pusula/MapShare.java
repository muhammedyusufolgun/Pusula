package tr.olgun.pusula;

import java.net.*;
import java.util.regex.*;

final class MapShare {
    private static final String VALUE = "(-?\\d{1,3}(?:\\.\\d+)?)";
    private static final String PAIR = VALUE + ",\\s*" + VALUE;
    private static final Pattern RAW = Pattern.compile("^\\s*" + PAIR + "\\s*$");
    private static final Pattern DATA = Pattern.compile("!3d" + VALUE + "!4d" + VALUE + "(?![.\\d])");
    private static final Pattern PIN = Pattern.compile("(?:[?&](?:q|query)=|/place/)" + PAIR + "(?=[/&#?(+\\s]|$)");
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>]+");

    static double[] match(Pattern pattern,String text){
        Matcher m=pattern.matcher(text);
        if(!m.find())return null;
        double lat=Double.parseDouble(m.group(1)),lon=Double.parseDouble(m.group(2));
        return Math.abs(lat)<=90 && Math.abs(lon)<=180 ? new double[]{lat,lon}:null;
    }
    static double[] resolve(String text) throws Exception {
        double[] pair=match(RAW,text);
        if(pair!=null)return pair;
        Matcher link=LINK.matcher(text);
        if(!link.find())return null;
        URL url=new URL(link.group());
        for(int step=0;step<7;step++){
            String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
            boolean shortLink=host.equals("maps.app.goo.gl") || (host.equals("goo.gl") && url.getPath().startsWith("/maps"));
            boolean maps=host.matches("(?:(?:maps|www)\\.)?google\\.(?:com|[a-z]{2}|com\\.[a-z]{2}|co\\.[a-z]{2})");
            if(!url.getProtocol().equals("https") || url.getPort()!=-1 || url.getUserInfo()!=null || !(shortLink||maps))return null;
            if(maps){
                String decoded=URLDecoder.decode(url.toExternalForm(),"UTF-8");
                pair=match(DATA,decoded);
                if(pair==null)pair=match(PIN,decoded);
                if(pair!=null)return pair;
            }
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(5000);c.setReadTimeout(5000);
            try{
                int status=c.getResponseCode();
                String next=c.getHeaderField("Location");
                if(status<300 || status>=400 || next==null)return null;
                url=new URL(url,next);
            }finally{c.disconnect();}
        }
        return null;
    }
}
