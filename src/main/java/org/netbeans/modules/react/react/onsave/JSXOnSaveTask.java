package org.netbeans.modules.react.react.onsave;

import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.react.react.customizer.JSXProjectCustomizer;
import org.netbeans.spi.editor.document.OnSaveTask;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;

public class JSXOnSaveTask implements OnSaveTask {

    Context context;

    private JSXOnSaveTask(Context context) {
        this.context = context;
    }

    @Override
    public void performTask() {
        FileObject fo = NbEditorUtilities.getFileObject(context.getDocument());
        Project proj = FileOwnerQuery.getOwner(fo);
        compileIfEnabled(proj.getProjectDirectory(), fo);
    }

    private void compileIfEnabled(FileObject root, FileObject fo) {
        Project project = FileOwnerQuery.getOwner(root);
        if (project == null) {
            return;
        }
        Preferences prefs = ProjectUtils.getPreferences(project, JSXProjectCustomizer.class, true);
        if (!"true".equals(prefs.get("compileOnSave", null))) {
            return;
        }
        JSXUtils.command(fo.getParent());
        StatusDisplayer.getDefault().setStatusText("Compile " + fo.getNameExt(), 100);
    }

    @Override
    public void runLocked(Runnable r) {
        performTask();
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @MimeRegistration(mimeType = "text/x-jsx", service = OnSaveTask.Factory.class, position = 100)
    public static class Factory implements OnSaveTask.Factory {
        @Override
        public OnSaveTask createTask(Context cntxt) {
            return new JSXOnSaveTask(cntxt);
        }
    }

}
