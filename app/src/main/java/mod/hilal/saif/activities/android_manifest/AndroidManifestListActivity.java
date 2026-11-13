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
import pro.sketchware.utility.SketchwareUtil;

public class AndroidManifestListActivity extends BaseAppCompatActivity {

    private final List<String> activityNames = new ArrayList<>();
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
            getSupportActionBar().setTitle(R.string.design_drawer_menu_androidmanifest);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        listView = findViewById(R.id.list_view);
        loadActivities();
    }

    private void loadActivities() {
        k();
        new Thread(() -> {
            String source = new yq(getApplicationContext(), sc_id)
                    .getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));

            List<String> names = parseActivityNames(source);

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
                    out.add(tag.substring(nStart, nEnd));
                }
            }
            idx = end + 1;
        }
    }

    /** Adapter yang meniru tampilan AndroidManifestInjection.ListAdapter (pakai CustomAttributeView). */
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

            // Samakan tampilan dengan daftar di AndroidManifestInjection:
            // ikon disembunyikan, teks = nama activity
            attributeView.getImageView().setVisibility(View.GONE);
            attributeView.getTextView().setText(getItem(position));

            final int pos = position;
            attributeView.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("file_name", getItem(pos));
                intent.putExtra("type", "activity");
                startActivity(intent);
            });

            attributeView.setOnLongClickListener(v -> {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(AndroidManifestListActivity.this);
                dialog.setIcon(R.drawable.icon_delete);
                dialog.setTitle(Helper.getResString(R.string.delete_custom_activity_dialog_title));
                dialog.setMessage(Helper.getResString(R.string.delete_custom_activity_dialog_message)
                        .replace("%1$s", getItem(pos)));
                dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (d, which) -> {
                    // Opsional: hapus dari list saja (tidak menghapus dari manifest)
                    activityNames.remove(pos);
                    notifyDataSetChanged();
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