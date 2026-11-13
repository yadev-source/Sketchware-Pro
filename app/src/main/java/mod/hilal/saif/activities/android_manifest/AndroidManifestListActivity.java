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

        ActivityData(String name, String displayAttributes) {
            this.name = name;
            this.displayAttributes = displayAttributes;
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
            // ───── SINKRONISASI DUA ARAH (MUTUAL OVERRIDE) ─────
            syncFromJsonToViewManager();

            // ───── BACA MANIFEST TERBARU ─────
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

    // Fungsi untuk memindahkan orientasi dari attributes.json ke View Manager
    private void syncFromJsonToViewManager() {
        String attrsPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/Injection/androidmanifest/attributes.json";
        if (FileUtil.isExistFile(attrsPath)) {
            ArrayList<HashMap<String, Object>> attrsData = getGson().fromJson(FileUtil.readFile(attrsPath), Helper.TYPE_MAP_LIST);
            boolean isChanged = false;

            for (int i = attrsData.size() - 1; i >= 0; i--) {
                String name = (String) attrsData.get(i).get("name");
                String value = (String) attrsData.get(i).get("value");

                if (value != null && value.startsWith("android:screenOrientation=")) {
                    String oriStr = value.replace("android:screenOrientation=\"", "").replace("\"", "");
                    int ori = -1;
                    
                    // Konversi string ke int View Manager (0=Portrait, 1=Landscape, 2=Both)
                    if (oriStr.equals("portrait")) ori = 0;
                    else if (oriStr.equals("landscape")) ori = 1;
                    else if (oriStr.equals("sensor") || oriStr.equals("fullSensor") || oriStr.equals("user")) ori = 2;

                    if (ori != -1) {
                        // Update View Manager (ProjectFileBean)
                        for (var bean : jC.c(sc_id).getActivityFiles()) {
                            if (bean.getJavaName().equals(name + ".java")) {
                                bean.setOrientation(ori);
                                break;
                            }
                        }
                        // Hapus dari JSON agar tidak mengunci manifest (biarkan View Manager yang bekerja)
                        attrsData.remove(i);
                        isChanged = true;
                    }
                }
            }

            if (isChanged) {
                FileUtil.writeFile(attrsPath, getGson().toJson(attrsData));
            }
        }
    }

    private List<ActivityData> parseActivities(String xml) {
        List<ActivityData> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return result;

        xml = xml.replaceAll("(?s)<!--.*?-->", "");

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
                Pattern pattern = Pattern.compile("([a-zA-Z0-9_:]+)=\"([^\"]*)\"");
                Matcher matcher = pattern.matcher(tag);
                
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!key.equals("android:name") && !key.equals("name")) {
                        String cleanKey = key.replace("android:", "");
                        attrKeys.add(cleanKey);
                    }
                }

                String displayAttrs = String.join(" | ", attrKeys);
                result.add(new ActivityData(name, displayAttrs));
            }
            idx = end + 1;
        }
        return result;
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

            // Saat diklik, langsung buka Detail TANPA mengubah JSON
            attributeView.setOnClickListener(v -> {
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