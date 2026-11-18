package mod.hey.studios.activity.managers.java;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.yadev.SrcMainJava;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ManageFileBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileResConfig;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ManageJavaActivity extends BaseAppCompatActivity {

    /**
     * Toleran terhadap CRLF, indentasi, tab, dan package tanpa semicolon (Kotlin).
     */
    private static final Pattern PACKAGE_DECL_PATTERN =
            Pattern.compile("(?m)^[ \\t]*package[ \\t]+([\\w.]+)[ \\t]*;?");

    private final ArrayList<String> currentTree = new ArrayList<>();
    ManageFileBinding binding;
    private String current_path;
    private FilePathUtil fpu;
    private FileResConfig frc;
    private String sc_id;
    private FilesAdapter filesAdapter;
    private boolean showSrcMain = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        Helper.fixFileprovider();

        frc = new FileResConfig(sc_id);
        fpu = new FilePathUtil();
        current_path = getJavaRoot();

        setupUI();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Objects.equals(current_path, getJavaRoot())) {
                    finish();
                } else {
                    current_path = current_path.substring(0, current_path.lastIndexOf("/"));
                    refresh();
                }
            }
        });

        refresh();
    }

    private void setupUI() {
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.topAppBar.setTitle(R.string.text_title_menu_java);

        binding.topAppBar.getMenu().clear();
        binding.topAppBar.getMenu()
                .add(Menu.NONE, 1001, Menu.NONE, showSrcMain ? "Default" : "SrcMain")
                .setIcon(R.drawable.ic_mtrl_folder)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1001) {
                showSrcMain = !showSrcMain;
                item.setTitle(showSrcMain ? "Default" : "SrcMain");
                updateSubtitle();
                current_path = getJavaRoot();
                refresh();
                return true;
            }
            return false;
        });

        binding.showOptionsButton.setOnClickListener(view -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog();
            hideShowOptionsButton(true);
        });
        binding.importNewButton.setOnClickListener(v -> {
            showImportDialog();
            hideShowOptionsButton(true);
        });

        updateSubtitle();
    }

    /**
     * Default mode  -> package name (mis. "com.myproject.app")
     * SrcMain mode  -> "src/main/java"
     */
    private void updateSubtitle() {
        String subtitle;
        if (showSrcMain) {
            subtitle = "src/main/java";
        } else {
            String pkgName = getIntent().getStringExtra("pkgName");
            subtitle = (pkgName != null && !pkgName.isEmpty()) ? pkgName : "Default";
        }
        binding.topAppBar.setSubtitle(subtitle);
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate()
                .translationY(isHide ? 300 : 0)
                .alpha(isHide ? 0 : 1)
                .setInterpolator(new OvershootInterpolator());
        binding.showOptionsButton.animate()
                .translationY(isHide ? 0 : 300)
                .alpha(isHide ? 1 : 0)
                .setInterpolator(new OvershootInterpolator());
    }

    // ─────────────────────────── Package helpers ───────────────────────────

    private String getCurrentPkgName() {
        if (showSrcMain) {
            try {
                String root = getJavaRoot();
                String substring = current_path.substring(root.length());
                if (substring.startsWith("/")) substring = substring.substring(1);
                if (substring.endsWith("/")) substring = substring.substring(0, substring.length() - 1);
                return substring.replace("/", ".");
            } catch (Exception e) {
                return "";
            }
        }

        String pkgName = getIntent().getStringExtra("pkgName");
        if (pkgName == null) pkgName = "";
        try {
            String trimmedPath = Helper.trimPath(fpu.getPathJava(sc_id));
            String substring = current_path.substring(
                    current_path.indexOf(trimmedPath) + trimmedPath.length());
            if (substring.endsWith("/")) substring = substring.substring(0, substring.length() - 1);
            if (substring.startsWith("/")) substring = substring.substring(1);
            String replace = substring.replace("/", ".");
            if (replace.isEmpty()) return pkgName;
            return pkgName.isEmpty() ? replace : pkgName + "." + replace;
        } catch (Exception e) {
            return pkgName;
        }
    }

    // ─────────────────────────── Create ───────────────────────────

    private void showCreateDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        dialogBinding.chipFolder.setVisibility(View.VISIBLE);
        dialogBinding.chipJavaClass.setVisibility(View.VISIBLE);
        dialogBinding.chipJavaActivity.setVisibility(View.VISIBLE);
        dialogBinding.chipKotlinClass.setVisibility(View.VISIBLE);
        dialogBinding.chipKotlinActivity.setVisibility(View.VISIBLE);

        final int[] selectedChipId = new int[]{-1};
        dialogBinding.chipGroupTypes.setOnCheckedChangeListener((group, checkedId) ->
                selectedChipId[0] = checkedId);

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle(R.string.assets_manager_add_new)
                .setMessage("File extension will be added automatically based on the file type you select")
                .setNegativeButton("Cancel", (d, i) -> d.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputText.requestFocus();

            Button positiveButton = ((androidx.appcompat.app.AlertDialog) dialogInterface)
                    .getButton(DialogInterface.BUTTON_POSITIVE);

            positiveButton.setOnClickListener(view -> {
                String name = Helper.getText(inputText);
                if (name.isEmpty()) {
                    SketchwareUtil.toastError("Invalid file name");
                    return;
                }

                String packageName = getCurrentPkgName();
                int checkedChipId = selectedChipId[0];
                if (checkedChipId == -1) checkedChipId = dialogBinding.chipGroupTypes.getCheckedChipId();

                // ── Folder ──
                if (checkedChipId == R.id.chip_folder) {
                    File created;
                    if (showSrcMain) {
                        String root = SrcMainJava.getInjectJavaDir(sc_id).getAbsolutePath();
                        String rel = current_path.substring(root.length());
                        if (rel.startsWith("/")) rel = rel.substring(1);
                        String folderPath = rel.isEmpty() ? name : rel + "/" + name;
                        created = SrcMainJava.createFolder(sc_id).name(folderPath).build();
                    } else {
                        String targetPath = new File(current_path, name).getAbsolutePath();
                        if (FileUtil.isExistFile(targetPath)) {
                            SketchwareUtil.toastError("Folder already exists");
                            return;
                        }
                        FileUtil.makeDir(targetPath);
                        created = new File(targetPath);
                    }
                    if (created != null && created.exists()) {
                        refresh();
                        SketchwareUtil.toast("Folder was created successfully");
                        dialog.dismiss();
                    } else {
                        SketchwareUtil.toastError("Failed to create folder");
                    }
                    return;
                }

                // ── File ──
                String format;
                boolean isActivity;
                if (checkedChipId == R.id.chip_java_class) {
                    format = "java"; isActivity = false;
                } else if (checkedChipId == R.id.chip_java_activity) {
                    format = "java"; isActivity = true;
                } else if (checkedChipId == R.id.chip_kotlin_class) {
                    format = "kt"; isActivity = false;
                } else if (checkedChipId == R.id.chip_kotlin_activity) {
                    format = "kt"; isActivity = true;
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                String ext = format.equals("kt") ? ".kt" : ".java";
                File outFile;

                if (showSrcMain) {
                    outFile = SrcMainJava.createFile(sc_id)
                            .format(format)
                            .isActivity(isActivity)
                            .packageName(packageName)
                            .name(name)
                            .build();
                } else {
                    String content;
                    if (format.equals("kt")) {
                        content = isActivity
                                ? SrcMainJava.kotlinActivityTemplate(packageName, name)
                                : SrcMainJava.kotlinClassTemplate(packageName, name);
                    } else {
                        content = isActivity
                                ? SrcMainJava.javaActivityTemplate(packageName, name)
                                : SrcMainJava.javaClassTemplate(packageName, name);
                    }
                    String targetPath = new File(current_path, name + ext).getAbsolutePath();
                    if (FileUtil.isExistFile(targetPath)) {
                        SketchwareUtil.toastError("File already exists");
                        return;
                    }
                    FileUtil.writeFile(targetPath, content);
                    outFile = new File(targetPath);
                }

                if (outFile != null && outFile.exists()) {
                    refresh();
                    SketchwareUtil.toast("File was created successfully");
                    dialog.dismiss();
                } else {
                    SketchwareUtil.toastError("Failed to create file");
                }
            });
        });

        dialog.show();
    }

    // ─────────────────────────── Import ───────────────────────────

    private void showImportDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setMultipleSelection(true);
        options.setExtensions(new String[]{"java", "kt"});
        options.setTitle("Select Java/Kotlin file(s)");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    for (File file : files) {
                        String fileContent = FileUtil.readFile(file.getAbsolutePath());
                        if (fileContent != null && fileContent.contains("package")) {
                            String pkg = getCurrentPkgName();
                            String header = pkg.isEmpty()
                                    ? ""
                                    : "package " + pkg + (file.getName().endsWith(".java") ? ";" : "") + "\n";
                            fileContent = PACKAGE_DECL_PATTERN.matcher(fileContent)
                                    .replaceFirst(Matcher.quoteReplacement(header));
                        }
                        FileUtil.writeFile(
                                new File(current_path, file.getName()).getAbsolutePath(),
                                fileContent);
                    }
                    runOnUiThread(ManageJavaActivity.this::refresh);
                    executor.shutdown();
                });
            }
        };

        new FilePickerDialogFragment(options, callback)
                .show(getSupportFragmentManager(), "filePicker");
    }

    // ─────────────────────────── Rename ───────────────────────────

    private void showRenameDialog(int position) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;
        var renameOccurrencesCheckBox = dialogBinding.renameOccurrencesCheckBox;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename " + filesAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (d, i) -> d.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    if (!Helper.getText(inputText).isEmpty()) {
                        if (!filesAdapter.isFolder(position)) {
                            String oldFull = filesAdapter.getFullName(position);
                            String newName = FileUtil.getFileNameNoExtension(Helper.getText(inputText));
                            String newFull = composeFullName(position, newName);

                            // Update manifest lists before renaming
                            boolean changedManifest = false;
                            if (frc.getJavaManifestList().remove(oldFull)) {
                                frc.getJavaManifestList().add(newFull);
                                changedManifest = true;
                            }
                            if (frc.getServiceManifestList().remove(oldFull)) {
                                frc.getServiceManifestList().add(newFull);
                                changedManifest = true;
                            }
                            if (changedManifest) {
                                FileUtil.writeFile(fpu.getManifestJava(sc_id),
                                        new Gson().toJson(frc.listJavaManifest));
                                FileUtil.writeFile(fpu.getManifestService(sc_id),
                                        new Gson().toJson(frc.listServiceManifest));
                            }

                            if (renameOccurrencesCheckBox.isChecked()) {
                                String fileContent = FileUtil.readFile(filesAdapter.getItem(position));
                                String oldNameNoExt = filesAdapter.getFileNameWoExt(position);
                                if (fileContent != null) {
                                    FileUtil.writeFile(filesAdapter.getItem(position),
                                            fileContent.replaceAll(oldNameNoExt, newName));
                                }
                            }
                        }
                        FileUtil.renameFile(filesAdapter.getItem(position),
                                new File(current_path, Helper.getText(inputText)).getAbsolutePath());
                        refresh();
                        SketchwareUtil.toast("Renamed successfully");
                    }
                    dialogInterface.dismiss();
                }).create();

        inputText.setText(filesAdapter.getFileName(position));
        boolean isFolder = filesAdapter.isFolder(position);
        if (!isFolder) {
            renameOccurrencesCheckBox.setVisibility(View.VISIBLE);
            renameOccurrencesCheckBox.setText("Rename occurrences of \""
                    + filesAdapter.getFileNameWoExt(position) + "\" in file");
        }
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    /** Bangun full-name (package + class) untuk file yang akan diganti nama. */
    private String composeFullName(int position, String newName) {
        String oldFull = filesAdapter.getFullName(position);
        int lastDot = oldFull.lastIndexOf('.');
        return lastDot > 0 ? oldFull.substring(0, lastDot) + "." + newName : newName;
    }

    // ─────────────────────────── Delete ───────────────────────────

    private void showDeleteDialog(int position) {
        String fullName = filesAdapter.getFullName(position);
        boolean isFolder = filesAdapter.isFolder(position);
        boolean inActivity = frc.getJavaManifestList().contains(fullName);
        boolean inService  = frc.getServiceManifestList().contains(fullName);

        String msg = "Are you sure you want to delete this "
                + (isFolder ? "folder" : "file") + "? "
                + ((inActivity || inService) ? "This will also remove it from AndroidManifest. " : "")
                + "This action cannot be undone.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + filesAdapter.getFileName(position) + "?")
                .setMessage(msg)
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    boolean changed = false;
                    if (inActivity) {
                        frc.getJavaManifestList().remove(fullName);
                        FileUtil.writeFile(fpu.getManifestJava(sc_id),
                                new Gson().toJson(frc.listJavaManifest));
                        changed = true;
                    }
                    if (inService) {
                        frc.getServiceManifestList().remove(fullName);
                        FileUtil.writeFile(fpu.getManifestService(sc_id),
                                new Gson().toJson(frc.listServiceManifest));
                        changed = true;
                    }
                    if (isFolder) {
                        deleteRecursiveFile(new File(filesAdapter.getItem(position)));
                    } else {
                        FileUtil.deleteFile(filesAdapter.getItem(position));
                    }
                    refresh();
                    SketchwareUtil.toast("Deleted successfully");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .create().show();
    }

    private void deleteRecursiveFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursiveFile(c);
            }
        }
        file.delete();
    }

    // ─────────────────────────── Refresh ───────────────────────────

    private void refresh() {
        if (showSrcMain) {
            SrcMainJava.ensureProjectInjectJavaDir(sc_id);
        } else {
            if (!FileUtil.isExistFile(fpu.getPathJava(sc_id))) {
                FileUtil.makeDir(fpu.getPathJava(sc_id));
            }
            if (!FileUtil.isExistFile(fpu.getManifestJava(sc_id))) {
                FileUtil.writeFile(fpu.getManifestJava(sc_id), "");
            }
        }

        currentTree.clear();
        FileUtil.listDir(current_path, currentTree);
        Helper.sortPaths(currentTree);
        filesAdapter = new FilesAdapter(currentTree);
        binding.filesListRecyclerView.setAdapter(filesAdapter);
        binding.noContentLayout.setVisibility(currentTree.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────────── Adapter ───────────────────────────

    public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ViewHolder> {
        private final List<String> currentTree;

        public FilesAdapter(List<String> currentTree) {
            this.currentTree = currentTree;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            var binding = holder.binding;
            String fileName = getFileName(position);
            binding.title.setText(fileName);

            binding.getRoot().setOnClickListener(view -> {
                if (isFolder(position)) {
                    current_path = getItem(position);
                    refresh();
                } else {
                    goEditFile(position);
                }
            });
            binding.getRoot().setOnLongClickListener(view -> {
                itemContextMenu(view, position, Gravity.CENTER);
                return true;
            });

            if (isFolder(position)) {
                binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
            } else if (fileName.endsWith(".java")) {
                binding.icon.setImageResource(R.drawable.ic_mtrl_java);
            } else if (fileName.endsWith(".kt")) {
                binding.icon.setImageResource(R.drawable.ic_mtrl_kotlin);
            }
            Helper.applyRipple(ManageJavaActivity.this, binding.more);
            binding.more.setOnClickListener(v -> itemContextMenu(v, position, Gravity.RIGHT));
        }

        @Override
        public int getItemCount() {
            return currentTree.size();
        }

        public String getItem(int position) {
            return currentTree.get(position);
        }

        /**
         * Ambil fully-qualified class name (package + class) dari file.
         * Tahan terhadap CRLF, whitespace aneh, dan file tanpa package
         * (fallback: derive dari struktur folder).
         */
        public String getFullName(int position) {
            String readFile = FileUtil.readFile(getItem(position));
            if (readFile != null) {
                Matcher m = PACKAGE_DECL_PATTERN.matcher(readFile);
                if (m.find()) {
                    String pkg = m.group(1).trim();
                    if (!pkg.isEmpty()) {
                        return pkg + "." + getFileNameWoExt(position);
                    }
                }
            }

            // Fallback: derive package dari relative folder
            String path = getItem(position);
            String root = getJavaRoot();
            if (path.startsWith(root)) {
                String rel = path.substring(root.length());
                if (rel.startsWith("/")) rel = rel.substring(1);
                int lastSlash = rel.lastIndexOf('/');
                if (lastSlash > 0) {
                    String folder = rel.substring(0, lastSlash).replace('/', '.');
                    if (!showSrcMain) {
                        String pkgName = getIntent().getStringExtra("pkgName");
                        if (pkgName != null && !pkgName.isEmpty()) {
                            return pkgName + "." + folder + "." + getFileNameWoExt(position);
                        }
                    }
                    return folder + "." + getFileNameWoExt(position);
                }
            }
            return getFileNameWoExt(position);
        }

        public String getFileName(int position) {
            String item = getItem(position);
            return item.substring(item.lastIndexOf("/") + 1);
        }

        public String getFileNameWoExt(int position) {
            return FileUtil.getFileNameNoExtension(getItem(position));
        }

        public boolean isFolder(int position) {
            return FileUtil.isDirectory(getItem(position));
        }

        public void goEditFile(int position) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("java", "");
            intent.putExtra("title", getFileName(position));
            intent.putExtra("content", getItem(position));
            startActivity(intent);
        }

        private void itemContextMenu(View v, int position, int gravity) {
            PopupMenu popupMenu = new PopupMenu(ManageJavaActivity.this, v, gravity);
            popupMenu.inflate(R.menu.popup_menu_double);
            Menu menu = popupMenu.getMenu();
            menu.clear();

            final boolean isFolder = isFolder(position);
            final String fullName = isFolder ? null : getFullName(position);
            final boolean inActivity = !isFolder && frc.getJavaManifestList().contains(fullName);
            final boolean inService  = !isFolder && frc.getServiceManifestList().contains(fullName);

            if (!isFolder) {
                // Activity actions
                if (inActivity) {
                    menu.add("Remove Activity from manifest");
                } else if (!inService) {
                    menu.add("Add as Activity to manifest");
                }
                // Service actions
                if (inService) {
                    menu.add("Remove Service from manifest");
                } else if (!inActivity) {
                    menu.add("Add as Service to manifest");
                }
                menu.add("Edit");
                menu.add("Edit with...");
            }

            menu.add("Rename");
            menu.add("Delete");

            popupMenu.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                switch (title) {
                    case "Add as Activity to manifest" -> {
                        frc.getJavaManifestList().add(fullName);
                        FileUtil.writeFile(fpu.getManifestJava(sc_id),
                                new Gson().toJson(frc.listJavaManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position)
                                + " as Activity to AndroidManifest");
                    }
                    case "Remove Activity from manifest" -> {
                        if (frc.getJavaManifestList().remove(fullName)) {
                            FileUtil.writeFile(fpu.getManifestJava(sc_id),
                                    new Gson().toJson(frc.listJavaManifest));
                            SketchwareUtil.toast("Successfully removed Activity "
                                    + getFileNameWoExt(position) + " from AndroidManifest");
                        } else {
                            SketchwareUtil.toast("Activity was not defined in AndroidManifest.");
                        }
                    }
                    case "Add as Service to manifest" -> {
                        frc.getServiceManifestList().add(fullName);
                        FileUtil.writeFile(fpu.getManifestService(sc_id),
                                new Gson().toJson(frc.listServiceManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position)
                                + " as Service to AndroidManifest");
                    }
                    case "Remove Service from manifest" -> {
                        if (frc.getServiceManifestList().remove(fullName)) {
                            FileUtil.writeFile(fpu.getManifestService(sc_id),
                                    new Gson().toJson(frc.listServiceManifest));
                            SketchwareUtil.toast("Successfully removed Service "
                                    + getFileNameWoExt(position) + " from AndroidManifest");
                        } else {
                            SketchwareUtil.toast("Service was not defined in AndroidManifest.");
                        }
                    }
                    case "Edit" -> goEditFile(position);
                    case "Edit with..." -> {
                        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                        launchIntent.setDataAndType(
                                Uri.fromFile(new File(getItem(position))), "text/plain");
                        startActivity(launchIntent);
                    }
                    case "Rename" -> showRenameDialog(position);
                    case "Delete" -> showDeleteDialog(position);
                    default -> { return false; }
                }
                refresh();
                return true;
            });
            popupMenu.show();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ManageJavaItemHsBinding binding;

            public ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private String getJavaRoot() {
        if (showSrcMain) {
            return SrcMainJava.ensureProjectInjectJavaDir(sc_id).getAbsolutePath();
        } else {
            return Uri.parse(fpu.getPathJava(sc_id)).getPath();
        }
    }
}