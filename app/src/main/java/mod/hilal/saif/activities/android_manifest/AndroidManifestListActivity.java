package mod.hilal.saif.activities.android_manifest;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import mod.remaker.view.CustomAttributeView;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class AndroidManifestListActivity extends BaseAppCompatActivity {

    private static class ActivityData {
        String name;
        String displayAttributes;
        List<String[]> rawAttributes;

        ActivityData(String name, String displayAttributes, List<String[]> rawAttributes) {
            this.name = name;
            this.displayAttributes = displayAttributes;
            this.rawAttributes = rawAttributes;
        }
    }

    private final List<ActivityData> activityList = new ArrayList<>();
    private ListView listView;
    private String sc_id;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_manifest_list);

        if (getIntent().hasExtra("sc_id")) {
            sc_id = getIntent().getStringExtra("sc_id");
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("List Activity");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        listView = findViewById(R.id.list_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadActivities();
    }

    private void loadActivities() {
        k();
        new Thread(() -> {
            String source = new yq(getApplicationContext(), sc_id)
                    .getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));

            List<ActivityData> parsedData = parseActivities(source);

            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                activityList.clear();
                activityList.addAll(parsedData);
                if (activityList.isEmpty()) {
                    SketchwareUtil.toast("No <activity> tags found");
                }
                listView.setAdapter(new ActivityListAdapter());
            });
        }).start();
    }

    private List<ActivityData> parseActivities(String xml) {
        List<ActivityData> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return result;

        xml = xml.replaceAll("(?s)<!--.*?-->", ""); // Hapus komentar

        int idx = 0;
        while (true) {
            int start = xml.indexOf("<activity", idx);
            if (start < 0) break;
            int end = xml.indexOf(">", start);
            if (end < 0) break;

            String tag = xml.substring(start, end);
            String name = null;
            
            int nStart = tag.indexOf("android:name=\"");
            if (nStart >= 0) {
                nStart += "android:name=\"".length();
                int nEnd = tag.indexOf('"', nStart);
                if (nEnd > nStart) {
                    name = tag.substring(nStart, nEnd);
                }
            }

            if (name != null && !name.isEmpty()) {
                if (name.contains(".")) {
                    name = name.substring(name.lastIndexOf(".") + 1);
                }

                List<String> attrKeys = new ArrayList<>();
                List<String[]> rawAttrs = new ArrayList<>();
                Pattern pattern = Pattern.compile("([a-zA-Z0-9_:]+)=\"([^\"]*)\"");
                Matcher matcher = pattern.matcher(tag);
                
                while (matcher.find()) {
                    String key = matcher.group(1);
                    String val = matcher.group(2);
                    if (!key.equals("android:name") && !key.equals("name")) {
                        String cleanKey = key.replace("android:", "");
                        attrKeys.add(cleanKey);
                        rawAttrs.add(new String[]{key, val});
                    }
                }

                String displayAttrs = String.join(" | ", attrKeys);
                result.add(new ActivityData(name, displayAttrs, rawAttrs));
            }
            idx = end + 1;
        }
        return result;
    }

    // ───── PERUBAHAN UTAMA ADA DI SINI ─────
    private void syncAttributesToJson(ActivityData data) {
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/data/").concat(sc_id).concat("/Injection/androidmanifest/attributes.json");
        ArrayList<HashMap<String, Object>> jsonList = new ArrayList<>();
        
        if (FileUtil.isExistFile(path)) {
            jsonList = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
        }

        // 1. Hapus semua atribut lama untuk activity ini agar tidak terjadi duplikasi
        for (int i = jsonList.size() - 1; i >= 0; i--) {
            if (data.name.equals(jsonList.get(i).get("name"))) {
                jsonList.remove(i);
            }
        }

        // 2. Masukkan kembali SEMUA atribut yang ada di manifest (termasuk dari View Manager)
        for (String[] attr : data.rawAttributes) {
            String key = attr[0];
            String val = attr[1];
            
            // Jangan masukkan android:name karena Ix akan otomatis membuatnya
            if (key.equals("android:name") || key.equals("name")) continue;
            
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", data.name);
            map.put("value", key + "=\"" + val + "\"");
            jsonList.add(map);
        }

        FileUtil.writeFile(path, getGson().toJson(jsonList));
    }

    private class ActivityListAdapter extends BaseAdapter {

        @Override
        public int getCount() { return activityList.size(); }

        @Override
        public ActivityData getItem(int position) { return activityList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CustomAttributeView attributeView;
            if (convertView == null) {
                attributeView = new CustomAttributeView(parent.getContext());
            } else {
                attributeView = (CustomAttributeView) convertView;
            }

            ActivityData data = getItem(position);
            attributeView.getImageView().setVisibility(View.GONE);
            
            attributeView.getTextView().setSingleLine(false);

            String title = "." + data.name;
            String fullText = title;
            
            if (!data.displayAttributes.isEmpty()) {
                fullText += "\n" + data.displayAttributes;
            }

            SpannableString spannable = new SpannableString(fullText);
            
            if (!data.displayAttributes.isEmpty()) {
                spannable.setSpan(new ForegroundColorSpan(Color.GRAY), 
                        title.length(), fullText.length(), 
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            attributeView.getTextView().setText(spannable);

            attributeView.setOnClickListener(v -> {
                // Sinkronisasi semua atribut manifest (termasuk View Manager) ke JSON
                syncAttributesToJson(data);
                
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("file_name", data.name); 
                intent.putExtra("type", "activity");
                startActivity(intent);
            });

            return attributeView;
        }
    }
}