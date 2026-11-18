package mod.yadev;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import pro.sketchware.utility.FileUtil;

public class SrcMainJava {

    public static final String SKETCHWARE_DATA = ".sketchware";
    public static final String SRC_MAIN = "src_main";
    public static final String JAVA_DIR = "java";

    /** Main inject java root: .sketchware/data/<sc_id>/src_main/java/ */
    public static File getInjectJavaDir(String sc_id) {
        return new File(
            new File(FileUtil.getExternalStorageDir(), SKETCHWARE_DATA),
            "data" + File.separator + sc_id + File.separator + SRC_MAIN + File.separator + JAVA_DIR
        );
    }

    /** Ensure inject java dir exists and create readme.txt if missing */
    public static File ensureProjectInjectJavaDir(String sc_id) {
        File javaDir = getInjectJavaDir(sc_id);
        if (!javaDir.exists()) {
            javaDir.mkdirs();
        }
        File readme = new File(javaDir.getParentFile(), "readme.txt");
        if (!readme.exists()) {
            writeFile(readme,
                "This folder is parallel to files/java\n"
                + "files/java/ -> package forced to com.myproject.app\n"
                + "src_main/java/ -> package follows folder, like app/src/main/java\n"
                + "Example: src_main/java/my/package/util/Util.java -> my.package.util.Util\n");
        }
        return javaDir;
    }

    // ─────────────────────────── Templates ───────────────────────────

    public static String javaActivityTemplate(String packageName, String name) {
        String pkg = (packageName == null || packageName.isEmpty())
                ? "" : "package " + packageName + ";\n\n";
        return pkg
            + "import android.app.Activity;\n"
            + "import android.os.Bundle;\n"
            + "import android.widget.TextView;\n\n"
            + "public class " + name + " extends Activity {\n"
            + "    @Override\n"
            + "    protected void onCreate(Bundle savedInstanceState) {\n"
            + "        super.onCreate(savedInstanceState);\n"
            + "        TextView tv = new TextView(this);\n"
            + "        tv.setText(\"Hello from Inject\");\n"
            + "        tv.setTextSize(20);\n"
            + "        tv.setPadding(32, 32, 32, 32);\n"
            + "        setContentView(tv);\n"
            + "    }\n"
            + "}\n";
    }

    public static String javaClassTemplate(String packageName, String name) {
        String pkg = (packageName == null || packageName.isEmpty())
                ? "" : "package " + packageName + ";\n\n";
        return pkg
            + "public class " + name + " {\n"
            + "    public String hello() {\n"
            + "        return \"Hello from Inject\";\n"
            + "    }\n"
            + "}\n";
    }

    public static String kotlinActivityTemplate(String packageName, String name) {
        String pkg = (packageName == null || packageName.isEmpty())
                ? "" : "package " + packageName + "\n\n";
        return pkg
            + "import android.app.Activity\n"
            + "import android.os.Bundle\n"
            + "import android.widget.TextView\n\n"
            + "class " + name + " : Activity() {\n"
            + "    override fun onCreate(savedInstanceState: Bundle?) {\n"
            + "        super.onCreate(savedInstanceState)\n"
            + "        val tv = TextView(this)\n"
            + "        tv.text = \"Hello from Inject\"\n"
            + "        tv.textSize = 20f\n"
            + "        tv.setPadding(32, 32, 32, 32)\n"
            + "        setContentView(tv)\n"
            + "    }\n"
            + "}\n";
    }

    public static String kotlinClassTemplate(String packageName, String name) {
        String pkg = (packageName == null || packageName.isEmpty())
                ? "" : "package " + packageName + "\n\n";
        return pkg
            + "class " + name + " {\n"
            + "    fun hello(): String {\n"
            + "        return \"Hello from Inject\"\n"
            + "    }\n"
            + "}\n";
    }

    // ─────────────────────────── Delete ───────────────────────────

    public static void deleteInjectDir(String sc_id) {
        File srcMainDir = getInjectJavaDir(sc_id).getParentFile();
        if (srcMainDir != null && srcMainDir.exists()) {
            deleteRecursive(srcMainDir);
        }
    }

    public static FileDeleter deleteFile(String sc_id) {
        return new FileDeleter(sc_id);
    }

    public static FolderDeleter deleteFolder(String sc_id) {
        return new FolderDeleter(sc_id);
    }

    public static class FileDeleter {
        private final String sc_id;
        private String format = "java";
        private String name = "MainActivity";
        private String packageName = "my.package";

        public FileDeleter(String sc_id) { this.sc_id = sc_id; }
        public FileDeleter format(String format) { this.format = format; return this; }
        public FileDeleter name(String name) { this.name = name; return this; }
        public FileDeleter packageName(String packageName) { this.packageName = packageName; return this; }

        public boolean build() {
            File javaRoot = getInjectJavaDir(sc_id);
            String packagePath = packageName.replace('.', File.separatorChar);
            String ext = format.equalsIgnoreCase("kt") ? "kt" : "java";
            File target = new File(new File(javaRoot, packagePath), name + "." + ext);
            return target.exists() && target.delete();
        }
    }

    public static class FolderDeleter {
        private final String sc_id;
        private String path = "my/package/util";

        public FolderDeleter(String sc_id) { this.sc_id = sc_id; }
        public FolderDeleter name(String path) { this.path = path; return this; }
        public FolderDeleter path(String path) { this.path = path; return this; }

        public boolean build() {
            File javaRoot = getInjectJavaDir(sc_id);
            String folderPath = path.replace('.', File.separatorChar);
            File dir = new File(javaRoot, folderPath);
            return dir.exists() && deleteRecursive(dir);
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        return file.delete();
    }

    // ─────────────────────────── Create ───────────────────────────

    public static FileCreator createFile(String sc_id) {
        return new FileCreator(sc_id);
    }

    public static FolderCreator createFolder(String sc_id) {
        return new FolderCreator(sc_id);
    }

    public static class FileCreator {
        private final String sc_id;
        private String format = "java";
        private boolean isActivity = false;
        private String name = "MainActivity";
        private String packageName = "my.package";

        public FileCreator(String sc_id) { this.sc_id = sc_id; }
        public FileCreator format(String format) { this.format = format; return this; }
        public FileCreator isActivity(boolean isActivity) { this.isActivity = isActivity; return this; }
        public FileCreator name(String name) { this.name = name; return this; }
        public FileCreator packageName(String packageName) { this.packageName = packageName; return this; }

        public File build() {
            File javaRoot = ensureProjectInjectJavaDir(sc_id);
            String packagePath = packageName.replace('.', File.separatorChar);
            File packageDir = packagePath.isEmpty() ? javaRoot : new File(javaRoot, packagePath);
            if (!packageDir.exists()) packageDir.mkdirs();

            String ext = format.equalsIgnoreCase("kt") ? "kt" : "java";
            File outFile = new File(packageDir, name + "." + ext);

            String content;
            if (format.equalsIgnoreCase("kt")) {
                content = isActivity
                    ? kotlinActivityTemplate(packageName, name)
                    : kotlinClassTemplate(packageName, name);
            } else {
                content = isActivity
                    ? javaActivityTemplate(packageName, name)
                    : javaClassTemplate(packageName, name);
            }

            writeFile(outFile, content);
            return outFile;
        }
    }

    public static class FolderCreator {
        private final String sc_id;
        private String path = "my/package/util";

        public FolderCreator(String sc_id) { this.sc_id = sc_id; }
        public FolderCreator name(String path) { this.path = path; return this; }
        public FolderCreator path(String path) { this.path = path; return this; }

        public File build() {
            File javaRoot = ensureProjectInjectJavaDir(sc_id);
            String folderPath = path.replace('.', File.separatorChar);
            File dir = new File(javaRoot, folderPath);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
    }

    private static void writeFile(File file, String content) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes("UTF-8"));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}