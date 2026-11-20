package tr.olgun.pusula;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;

public class ReceivePlace extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent incoming = getIntent();
        if (Intent.ACTION_SEND.equals(incoming.getAction())) {
            CharSequence text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT);
            ClipData data = incoming.getClipData();
            if (text == null && data != null && data.getItemCount() > 0) {
                ClipData.Item item = data.getItemAt(0);
                text = item.getText();
                if (text == null && item.getUri() != null) text = item.getUri().toString();
            }
            Intent open = new Intent(this, MainActivity.class);
            open.setAction(Intent.ACTION_SEND);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra(Intent.EXTRA_TEXT, text);
            open.putExtra(Intent.EXTRA_SUBJECT, incoming.getStringExtra(Intent.EXTRA_SUBJECT));
            startActivity(open);
        }
        finish();
    }
}
