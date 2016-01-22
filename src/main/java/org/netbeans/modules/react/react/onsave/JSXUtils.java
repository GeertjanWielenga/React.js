package org.netbeans.modules.react.react.onsave;

import java.io.File;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.ExternalProcessBuilder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class JSXUtils {
    
    // jsx --watch -x jsx scripts/ scripts/
    
    public static void command(FileObject folder) {
        String jsxPath = "C:\\Users\\gwieleng\\AppData\\Roaming\\npm\\jsx.cmd";
        String folderPath = folder.getPath();
        File file = FileUtil.toFile(folder);
        ExecutionDescriptor executionDescriptor = new ExecutionDescriptor().
                frontWindow(true).
                controllable(true).
                showProgress(true);
        ExternalProcessBuilder externalProcessBuilder = new ExternalProcessBuilder(jsxPath).
                workingDirectory(file).
                addArgument("--watch").
                addArgument("--extension").
                addArgument("jsx").
                addArgument(folderPath).
                addArgument(folderPath);
        ExecutionService service = ExecutionService.newService(
                externalProcessBuilder, 
                executionDescriptor, 
                "React"
        );
        service.run();
    }
    
}
