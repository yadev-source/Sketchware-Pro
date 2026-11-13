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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import mod.remaker.view.CustomAttributeView;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class AndroidManifestListActivity extends BaseAppCompatActivity {

    // Objek untuk menyimpan nama activity beserta atribut XML-nya
    private static class ActivityData {
        String name;
        String attributes; // Teks berisi daftar atribut

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

            List<ActivityData> parsedData = parseActivitiesWithAttributes(source);

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

    // Fungsi Parsing: Mengambil Nama Activity DAN Atributnya
    private List<ActivityData> parseActivitiesWithAttributes(String xml) {
        List<ActivityData> result = new ArrayList<>();
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
                        // Bersihkan nama (hapus package / titik di depan)
                        if (name.contains(".")) {
                            name = name.substring(name.lastIndexOf(".") + 1);
                        }

                        // Ambil semua atribut di dalam tag <activity ...>
                        StringBuilder attrs = new StringBuilder();
                        NamedNodeMap attributes = el.getAttributes();
                        for (int j = 0; j < attributes.getLength(); j++) {
                            Node attr = attributes.item(j);
                            String attrName = attr.getNodeName();
                            // Lewati atribut name karena sudah jadi judul
                            if (!attrName.equals("android:name") && !attrName.equals("name")) {
                                attrs.append(attrName).append("=\"").append(attr.getNodeValue()).append("\"\n");
                            }
                        }
                        
                        result.add(new ActivityData(name, attrs.toString().trim()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void deleteActivityData(String activityName) {
        // (Kode sama seperti sebelumnya - abaikan untuk ringkas)
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
            
            // Tampilkan Nama Activity
            String displayName = "." + data.name;
            if (!data.attributes.isEmpty()) {
                // Jika ada atribut, tampilkan di bawahnya dengan teks lebih kecil
                displayName += "\n" + data.attributes;
                attributeView.getTextView().setTextSize(12); // Perkecil sedikit agar muat
            } else {
                attributeView.getTextView().setTextSize(16);
            }
            
            attributeView.getTextView().setText(displayName);

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