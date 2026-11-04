package nl.gtaac.governor;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class GovernorInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            String raw;
            try(InputStream in=getTargetContext().getAssets().open("snapshot.json")) {
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                while((n=in.read(b))!=-1)out.write(b,0,n);
                raw=out.toString("UTF-8");
            }
            MainActivity.validate(raw);
            JSONObject bad=new JSONObject(raw);bad.getJSONArray("tasks").getJSONObject(0).put("status","delivered");
            boolean rejected=false;
            try { MainActivity.validate(bad.toString()); } catch(IllegalArgumentException expected) { rejected=true; }
            if(!rejected)throw new AssertionError("Incomplete delivery accepted");
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();final String[] value={null};
            for(int i=0;i<60;i++) {
                CountDownLatch latch=new CountDownLatch(1);
                runOnMainSync(() -> {
                    WebView view=(WebView)((android.view.ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                    view.evaluateJavascript("document.getElementById('delivered') ? document.getElementById('delivered').textContent : ''", result->{value[0]=result;latch.countDown();});
                });
                if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("UI timeout");
                if(value[0]!=null&&value[0].contains(" / "))break;
                Thread.sleep(250);
            }
            if(value[0]==null||!value[0].contains(" / "))throw new AssertionError("Bundled snapshot did not render: "+value[0]);
            report.putString("stream","OK (3 tests): schema, incomplete-delivery rejection, installed activity and bundled UI\n");
            finish(Activity.RESULT_OK,report);
        } catch(Throwable error) {
            report.putString("stream","FAILURES: "+error+"\n");finish(Activity.RESULT_CANCELED,report);
        }
    }
}
