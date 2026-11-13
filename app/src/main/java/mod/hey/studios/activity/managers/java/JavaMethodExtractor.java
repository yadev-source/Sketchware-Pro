package mod.hey.studios.activity.managers.java;

import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class JavaMethodExtractor {

    public static class MethodInfo {
        public String methodName;
        public String blockType; // c (void), s (String), d (Number), b (Boolean), v (Object/View)
        public String returnType; // void, String, int, etc.
        public boolean requiresContext;
        public String parameters;
        public int inputCount;
        public List<String> paramTypes; // ["Context", "String"]
        public List<String> paramNames; // ["context", "message"] <-- TAMBAHKAN INI

        public MethodInfo(String methodName, String returnType, String blockType, boolean requiresContext, String parameters) {
            this.methodName = methodName;
            this.returnType = returnType;
            this.blockType = blockType;
            this.requiresContext = requiresContext;
            this.parameters = parameters;
            
            // Hitung jumlah input, tipe parameter, dan nama parameter
            this.paramTypes = new ArrayList<>();
            this.paramNames = new ArrayList<>();
            
            if (!parameters.trim().isEmpty()) {
                String[] params = parameters.split(",");
                this.inputCount = params.length;
                for (int i = 0; i < params.length; i++) {
                    String[] parts = params[i].trim().split("\\s+");
                    if (parts.length >= 2) {
                        this.paramTypes.add(parts[0]); // Tipe data (misal "Context", "String")
                        this.paramNames.add(parts[1]); // Nama variabel (misal "context", "message")
                    } else if (parts.length == 1) {
                        this.paramTypes.add(parts[0]);
                        this.paramNames.add("arg" + (i + 1)); // Fallback jika tidak ada nama
                    }
                }
            } else {
                this.inputCount = 0;
            }
        }
    }

    public static class JavaFileInfo {
        public String className;
        public List<MethodInfo> methods;

        public JavaFileInfo(String className, List<MethodInfo> methods) {
            this.className = className;
            this.methods = methods;
        }
    }

    /**
     * Mengembalikan data dalam bentuk JSON String yang rapi.
     */
    public static String getJson(String sc_id) {
        List<JavaFileInfo> resultList = scanProject(sc_id);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultList);
    }

    public static List<JavaFileInfo> scanProject(String sc_id) {
        FilePathUtil fpu = new FilePathUtil();
        String javaDirPath = Uri.parse(fpu.getPathJava(sc_id)).getPath();
        File javaDir = new File(javaDirPath);

        List<JavaFileInfo> resultList = new ArrayList<>();

        if (javaDir.exists() && javaDir.isDirectory()) {
            scanDirectory(javaDir, resultList);
        }
        return resultList;
    }

    private static void scanDirectory(File dir, List<JavaFileInfo> resultList) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, resultList);
            } else if (file.getName().endsWith(".java")) {
                String content = FileUtil.readFile(file.getAbsolutePath());
                if (content != null && !content.trim().isEmpty()) {
                    String className = file.getName().replace(".java", "");
                    List<MethodInfo> methods = extractMethods(content);

                    if (!methods.isEmpty()) {
                        resultList.add(new JavaFileInfo(className, methods));
                    }
                }
            }
        }
    }

    private static List<MethodInfo> extractMethods(String code) {
        List<MethodInfo> methodList = new ArrayList<>();
        code = code.replaceAll("//.*|/\\*(?s:.*?)\\*/", "");

        // Regex untuk menangkap public method (void & non-void)
        String regex = "public\\s+" +
                       "(?:static\\s+)?" +
                       "([a-zA-Z_$][a-zA-Z\\d_$<>\\[\\]\\.]*)\\s+" + // 1. Return Type
                       "([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*" +           // 2. Method Name
                       "\\(([^)]*)\\)";                              // 3. Parameters

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String returnType = matcher.group(1);
            String methodName = matcher.group(2);
            String parameters = matcher.group(3).trim();
            
            boolean requiresContext = Pattern.compile("\\bContext\\b").matcher(parameters).find();

            // Pemetaan Tipe Java ke Tipe Blok Sketchware
            String blockType;
            if (returnType.equals("void")) {
                blockType = " "; // Command
            } else if (returnType.equals("String")) {
                blockType = "s";
            } else if (returnType.equals("boolean") || returnType.equals("Boolean")) {
                blockType = "b";
            } else if (returnType.equals("int") || returnType.equals("double") || returnType.equals("float") || returnType.equals("long") || returnType.equals("Integer")) {
                blockType = "d"; // Number
            } else if (returnType.contains("List") || returnType.contains("ArrayList")) {
                blockType = "l";
            } else {
                blockType = "v"; // Object/View
            }

            methodList.add(new MethodInfo(methodName, returnType, blockType, requiresContext, parameters));
        }
        return methodList;
    }
}