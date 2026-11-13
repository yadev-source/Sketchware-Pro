package mod.hey.studios.activity.managers.java;

import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class JavaMethodExtractor {

    public static class MethodInfo {
        public String name;
        public boolean requiresContext;
        public String rawParameters;

        public MethodInfo(String name, boolean requiresContext, String rawParameters) {
            this.name = name;
            this.requiresContext = requiresContext;
            this.rawParameters = rawParameters;
        }
    }

    public static class JavaFileInfo {
        public String className;
        public List<MethodInfo> voidMethods;

        public JavaFileInfo(String className, List<MethodInfo> voidMethods) {
            this.className = className;
            this.voidMethods = voidMethods;
        }
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
                    List<MethodInfo> methods = extractVoidMethods(content);
                    
                    if (!methods.isEmpty()) {
                        resultList.add(new JavaFileInfo(className, methods));
                    }
                }
            }
        }
    }

    private static List<MethodInfo> extractVoidMethods(String code) {
        List<MethodInfo> methodList = new ArrayList<>();
        code = code.replaceAll("//.*|/\\*(?s:.*?)\\*/", "");

        // Regex hanya untuk "public void"
        String regex = "public\\s+" +
                       "(?:static\\s+)?" +
                       "void\\s+" +
                       "([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*" +
                       "\\(([^)]*)\\)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String methodName = matcher.group(1);
            String parameters = matcher.group(2).trim();
            boolean needsContext = Pattern.compile("\\bContext\\b").matcher(parameters).find();
            
            methodList.add(new MethodInfo(methodName, needsContext, parameters));
        }
        return methodList;
    }
}