package org.netbeans.modules.react.react.customizer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.awt.Mnemonics;
import org.openide.util.Lookup;

// jsx --watch -x jsx scripts/ scripts/

public class JSXProjectCustomizer implements ProjectCustomizer.CompositeCategoryProvider {

    // See javascript2.editor/src/org/netbeans/modules/javascript2/editor/api/package-info.java
    @ProjectCustomizer.CompositeCategoryProvider.Registrations({
        @ProjectCustomizer.CompositeCategoryProvider.Registration(
                projectType = "org.netbeans.modules.web.clientproject", // HTML5_CLIENT_PROJECT
                category = "jsframeworks",
                position = 10
//        ),
//        @ProjectCustomizer.CompositeCategoryProvider.Registration(
//                projectType = "org-netbeans-modules-php-project", // PHP_PROJECT
//                category = "jsframeworks",
//                position = 10
//        ),
//        @ProjectCustomizer.CompositeCategoryProvider.Registration(
//                projectType = "org-netbeans-modules-maven", // MAVEN_PROJECT
//                category = "jsframeworks",
//                position = 10
        )
    })
    public static JSXProjectCustomizer createCustomizer() {
        return new JSXProjectCustomizer();
    }

    @Override
    public ProjectCustomizer.Category createCategory(Lookup context) {
        return ProjectCustomizer.Category.create("react", "React", null);
    }

    @Override
    public JComponent createComponent(ProjectCustomizer.Category category, Lookup context) {
        Project project = context.lookup(Project.class);
        final Preferences prefs = ProjectUtils.getPreferences(project, JSXProjectCustomizer.class, true);

        JPanel panel = new JPanel();
        final JCheckBox compileOnSave = new JCheckBox((String) null,
                "true".equals(prefs.get("compileOnSave", null)));
        Mnemonics.setLocalizedText(compileOnSave, "&Compile on Save");
        panel.add(compileOnSave);

        category.setStoreListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (compileOnSave.isSelected()) {
                    prefs.put("compileOnSave", "true");
                } else {
                    prefs.remove("compileOnSave");
                }
            }
        });

        return panel;
    }
}