package mod.hilal.saif.activities.android_manifest;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import mod.remaker.view.CustomAttributeView;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class AndroidManifestListActivity extends BaseAppCompatActivity {

    private static class ActivityData {
        String name;
        String attributes;

        ActivityData(String name, String attributes) {
            this.name = name;
            this.attributes = attributes;
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

    // Fungsi Parsing Manual menggunakan String & Regex (Sangat Aman)
    private List<ActivityData> parseActivities(String xml) {
        List<ActivityData> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return result;

        // Hapus komentar XML agar tidak salah baca
        xml = xml.replaceAll("(?s)<!--.*?-->", "");

        int idx = 0;
        while (true) {
            int start = xml.indexOf("<activity", idx);
            if (start < 0) break;
            int end = xml.indexOf(">", start);
            if (end < 0) break;

            String tag = xml.substring(start, end);

            // Cari android:name="..."
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
                // Bersihkan nama (buang package & titik)
                if (name.contains(".")) {
                    name = name.substring(name.lastIndexOf(".") + 1);
                }

                // Ambil semua atribut menggunakan Regex
                StringBuilder attrs = new StringBuilder();
                Pattern pattern = Pattern.compile("([a-zA-Z0-9_:]+)=\"([^\"]*)\"");
                Matcher matcher = pattern.matcher(tag);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    String val = matcher.group(2);
                    // Jangan tampilkan atribut name di list
                    if (!key.equals("android:name") && !key.equals("name")) {
                        attrs.append("  ").append(key).append("=\"").append(val).append("\"\n");
                    }
                }

                result.add(new ActivityData(name, attrs.toString().trim()));
            }

            idx = end + 1;
        }
        return result;
    }

    private void deleteActivityData(String activityName) {
        String path = pro.sketchware.utility.FileUtil.getExternalStorageDir().concat("/.sketchware/data/").concat(sc_id).concat("/Injection/androidmanifest/attributes.json");
        if (pro.sketchware.utility.FileUtil.isExistFile(path)) {
            ArrayList<java.util.HashMap<String, Object>> data = pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            for (int i = data.size() - 1; i > -1; i--) {
                String name = (String) data.get(i).get("name");
                if (java.util.Objects.requireNonNull(name).equals(activityName)) {
                    data.remove(i);
                }
            }
            pro.sketchware.utility.FileUtil.writeFile(path, pro.sketchware.utility.GsonUtils.getGson().toJson(data));
            SketchwareUtil.toast("Activity attributes removed");
        }
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

            // Format Tampilan
            String displayText = "." + data.name;
            if (!data.attributes.isEmpty()) {
                displayText += "\n" + data.attributes;
                attributeView.getTextView().setTextSize(12); // Perkecil jika ada atribut
            } else {
                attributeView.getTextView().setTextSize(16);
            }

            attributeView.getTextView().setText(displayText);

            attributeView.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("file_name", data.name); 
                intent.putExtra("type", "activity");
                startActivity(intent);
            });

            attributeView.setOnLongClickListener(v -> {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(AndroidManifestListActivity.this);
                dialog.setIcon(R.drawable.icon_delete);
                dialog.setTitle(Helper.getResString(R.string.delete_custom_activity_dialog_title));
                dialog.setMessage(Helper.getResString(R.string.delete_custom_activity_dialog_message).replace("%1$s", data.name));
                dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (d, which) -> {
                    deleteActivityData(data.name);
                    loadActivities();
                    d.dismiss();
                });
                dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
                dialog.show();
                return true;
            });

            return attributeView;
        }
    }
}