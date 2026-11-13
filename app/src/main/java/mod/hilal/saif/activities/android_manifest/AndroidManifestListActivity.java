package mod.hilal.saif.activities.android_manifest;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import mod.remaker.view.CustomAttributeView;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class AndroidManifestListActivity extends BaseAppCompatActivity {

    private final List<String> activityNames = new ArrayList<>();
    private ListView listView;
    private String sc_id;
    
    // Menyimpan daftar atribut per activity untuk ditampilkan di list
    private HashMap<String, String> activityAttributesMap = new HashMap<>();

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
    protected void onResume() {
        super.onResume();
        // Load ulang saat kembali dari halaman detail (agar atribut baru langsung terlihat)
        loadActivities();
    }

    private void loadActivities() {
        k();
        new Thread(() -> {
            String source = new yq(getApplicationContext(), sc_id)
                    .getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));

            List<String> names = parseActivityNames(source);
            
            // Baca file attributes.json untuk melihat atribut yang sudah ada
            activityAttributesMap.clear();
            String attrsPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/Injection/androidmanifest/attributes.json";
            if (FileUtil.isExistFile(attrsPath)) {
                ArrayList<HashMap<String, Object>> attrsData = getGson().fromJson(FileUtil.readFile(attrsPath), Helper.TYPE_MAP_LIST);
                for (HashMap<String, Object> map : attrsData) {
                    String name = (String) map.get("name");
                    String value = (String) map.get("value");
                    if (name != null && value != null) {
                        String existing = activityAttributesMap.getOrDefault(name, "");
                        activityAttributesMap.put(name, existing + value + "\n");
                    }
                }
            }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                activityNames.clear();
                activityNames.addAll(names);
                if (activityNames.isEmpty()) {
                    SketchwareUtil.toast("No <activity> tags found");
                }
                listView.setAdapter(new ActivityListAdapter());
            });
        }).start();
    }

    private List<String> parseActivityNames(String xml) {
        List<String> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return result;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList activityNodes = doc.getElementsByTagName("activity");
            for (int i = 0; i < activityNodes.getLength(); i++) {
                Node node = activityNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    String name = el.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (name == null || name.isEmpty()) {
                        name = el.getAttribute("android:name");
                    }
                    if (name != null && !name.isEmpty()) {
                        if (name.contains(".")) {
                            name = name.substring(name.lastIndexOf(".") + 1);
                        }
                        result.add(name);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fallbackParse(xml, result);
        }
        return result;
    }

    private void fallbackParse(String xml, List<String> out) {
        int idx = 0;
        while (true) {
            int start = xml.indexOf("<activity", idx);
            if (start < 0) break;
            int end = xml.indexOf(">", start);
            if (end < 0) break;
            String tag = xml.substring(start, end);
            int nStart = tag.indexOf("android:name=\"");
            if (nStart >= 0) {
                nStart += "android:name=\"".length();
                int nEnd = tag.indexOf('"', nStart);
                if (nEnd > nStart) {
                    String name = tag.substring(nStart, nEnd);
                    if (name.contains(".")) {
                        name = name.substring(name.lastIndexOf(".") + 1);
                    }
                    out.add(name);
                }
            }
            idx = end + 1;
        }
    }

    private void deleteActivityData(String activityName) {
        String path = FileUtil.getExternalStorageDir().concat("/.sketchware/data/").concat(sc_id).concat("/Injection/androidmanifest/attributes.json");
        if (FileUtil.isExistFile(path)) {
            ArrayList<HashMap<String, Object>> data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            for (int i = data.size() - 1; i > -1; i--) {
                String name = (String) data.get(i).get("name");
                if (Objects.requireNonNull(name).equals(activityName)) {
                    data.remove(i);
                }
            }
            FileUtil.writeFile(path, getGson().toJson(data));
            SketchwareUtil.toast("Activity attributes removed");
        }
    }

    private class ActivityListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return activityNames.size();
        }

        @Override
        public String getItem(int position) {
            return activityNames.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CustomAttributeView attributeView;
            if (convertView == null) {
                attributeView = new CustomAttributeView(parent.getContext());
            } else {
                attributeView = (CustomAttributeView) convertView;
            }

            final String cleanName = getItem(position);
            
            attributeView.getImageView().setVisibility(View.GONE);
            
            // Tampilkan nama activity
            attributeView.getTextView().setText("." + cleanName);

            // Tampilkan preview atribut di bawah nama (jika ada)
            String attrs = activityAttributesMap.get(cleanName);
            if (attrs != null && !attrs.isEmpty()) {
                // Gunakan setDescription atau text kecil jika CustomAttributeView mendukungnya
                // Jika tidak, kita bisa memanipulasi textView-nya langsung
                attributeView.getTextView().append("\n" + attrs.trim());
                attributeView.getTextView().setTextSize(14); 
            }

            attributeView.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
                intent.putExtra("sc_id", sc_id);
                // KUNCI PERBAIKAN: Hanya kirim nama class tanpa ".java" agar cocok dengan database
                intent.putExtra("file_name", cleanName); 
                intent.putExtra("type", "activity");
                startActivity(intent);
            });

            attributeView.setOnLongClickListener(v -> {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(AndroidManifestListActivity.this);
                dialog.setIcon(R.drawable.icon_delete);
                dialog.setTitle(Helper.getResString(R.string.delete_custom_activity_dialog_title));
                dialog.setMessage(Helper.getResString(R.string.delete_custom_activity_dialog_message).replace("%1$s", cleanName));
                dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (d, which) -> {
                    deleteActivityData(cleanName);
                    loadActivities(); // Refresh list
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