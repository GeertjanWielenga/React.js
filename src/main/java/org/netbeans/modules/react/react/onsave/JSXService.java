package org.netbeans.modules.react.react.onsave;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.Convertor;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.RequestProcessor;

/**
 * 
 * @author jeffrey
 */
public class JSXService {

    static final Logger log = Logger.getLogger(JSXService.class.getName());
    static final RequestProcessor RP = new RequestProcessor("TSService", 1, true);

    private static class ExceptionFromJS extends Exception {
        ExceptionFromJS(String msg) { super(msg); }
    }

    static void stringToJS(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                sb.append("\\u");
                for (int j = 12; j >= 0; j -= 4) {
                    sb.append("0123456789ABCDEF".charAt((c >> j) & 0x0F));
                }
            } else {
                if (c == '\\' || c == '"') {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        sb.append('"');
    }

    // All access to the TSService state below should be done with this lock acquired. This lock
    // has a fair ordering policy so error checking won't starve other user actions.
    private static final Lock lock = new ReentrantLock(true);

    private static NodeJSProcess nodejs = null;
    private static final Map<URL, ProgramData> programs = new HashMap<URL, ProgramData>();
    private static final Map<FileObject, FileData> allFiles = new HashMap<FileObject, FileData>();

    private static class NodeJSProcess {
        OutputStream stdin;
        BufferedReader stdout;
        String error;
        static final String builtinLibPrefix = "(builtin) ";
        Map<String, FileObject> builtinLibs = new HashMap<String, FileObject>();
        int nextProgId = 0;

        NodeJSProcess() throws Exception {
            log.info("Starting nodejs");
            File file = InstalledFileLocator.getDefault().locate("nbts-services.js", "netbeanstypescript", false);
            // Node installs to /usr/local/bin on OS X, but OS X doesn't put /usr/local/bin in the
            // PATH of applications started from the GUI
            for (String command: new String[] { "nodejs", "node", "/usr/local/bin/node" }) {
                try {
                    Process process = new ProcessBuilder()
                        .command(command, "--harmony", file.toString())
                        .start();
                    stdin = process.getOutputStream();
                    stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    process.getErrorStream().close();
                    error = null;
                    break;
                } catch (Exception e) {
                    error = "Error creating Node.js process. Make sure the \"nodejs\" or \"node\" executable is installed and on your PATH."
                            + "\n\nClose all TypeScript projects and reopen to retry."
                            + "\n\n" + e;
                }
            }
//
//            StringBuilder initLibs = new StringBuilder();
//            for (String lib: new String[] { "lib.d.ts", "lib.es6.d.ts" }) {
//                initLibs.append("void(builtinLibs[");
//                stringToJS(initLibs, builtinLibPrefix + lib);
//                initLibs.append("]=");
//                URL libURL = JSXService.class.getClassLoader().getResource("netbeanstypescript/resources/" + lib);
//                FileObject libObj = URLMapper.findFileObject(libURL);
//                stringToJS(initLibs, Source.create(libObj).createSnapshot().getText());
//                initLibs.append(");");
//                builtinLibs.put(builtinLibPrefix + lib, libObj);
//            }
//            eval(initLibs.append('\n').toString());
        }

        final Object eval(String code) throws ParseException, ExceptionFromJS {
            if (error != null) {
                return null;
            }
            log.log(Level.FINER, "OUT[{0}]: {1}", new Object[] {
                code.length(), code.length() > 120 ? code.substring(0, 120) + "...\n" : code});
            long t1 = System.currentTimeMillis();
            String s;
            try {
                stdin.write(code.getBytes());
                stdin.flush();
                while ((s = stdout.readLine()) != null && s.charAt(0) == 'L') {
                    log.fine((String) JSONValue.parseWithException(s.substring(1)));
                }
            } catch (Exception e) {
                error = "Error communicating with Node.js process."
                        + "\n\nClose all TypeScript projects and reopen to retry."
                        + "\n\n" + e;
                return null;
            }
            log.log(Level.FINER, "IN[{0},{1}]: {2}\n", new Object[] {
                s.length(), System.currentTimeMillis() - t1,
                s.length() > 120 ? s.substring(0, 120) + "..." : s});
            if (s.charAt(0) == 'X') {
                throw new ExceptionFromJS((String) JSONValue.parseWithException(s.substring(1)));
            } else if (s.equals("undefined")) {
                return null; // JSON parser doesn't like undefined
            } else {
                return JSONValue.parseWithException(s);
            }
        }

        void close() throws IOException {
            if (stdin != null) stdin.close();
            if (stdout != null) stdout.close();
        }
    }

    private static class ProgramData {
        final String progVar;
        final Map<String, FileObject> files = new HashMap<String, FileObject>();
        final Map<String, Indexable> indexables = new HashMap<String, Indexable>();
        boolean needErrorsUpdate;
        Object currentErrorsUpdate;

        ProgramData() throws Exception {
            progVar = "p" + nodejs.nextProgId++;
            nodejs.eval(progVar + " = new Program()\n");
        }

        Object call(String method, Object... args) {
            StringBuilder sb = new StringBuilder(progVar).append('.').append(method).append('(');
            for (Object arg: args) {
                if (sb.charAt(sb.length() - 1) != '(') sb.append(',');
                if (arg instanceof CharSequence) {
                    stringToJS(sb, (CharSequence) arg);
                } else {
                    sb.append(String.valueOf(arg));
                }
            }
            sb.append(")\n");
            try {
                return nodejs.eval(sb.toString());
            } catch (Exception e) {
                log.log(Level.INFO, "Exception in nodejs.eval", e);
                return null;
            }
        }

        final void setFileSnapshot(String relPath, Indexable indexable, Snapshot s, boolean modified) {
            call("updateFile", relPath, s.getText(), modified);
            files.put(relPath, s.getSource().getFileObject());
            if (indexable != null) {
                indexables.put(relPath, indexable);
                needErrorsUpdate = true;
            }
        }

        FileObject getFile(String relPath) {
            return (relPath.startsWith(NodeJSProcess.builtinLibPrefix) ? nodejs.builtinLibs : files).get(relPath);
        }

        FileObject removeFile(String relPath) throws Exception {
            FileObject fileObj = files.remove(relPath);
            if (fileObj != null) {
                needErrorsUpdate = true;
                call("deleteFile", relPath);
            }
            indexables.remove(relPath);
            return fileObj;
        }

        void dispose() throws Exception {
            nodejs.eval("delete " + progVar + "\n");
        }
    }

    private static class FileData {
        ProgramData program;
        String relPath;
    }

    static void addFile(Snapshot snapshot, FileObject fo) {
        lock.lock();
        try {
//            URL rootURL = cntxt.getRootURI();
//
//            ProgramData program = programs.get(rootURL);
//            if (program == null) {
//                if (nodejs == null) {
//                    nodejs = new NodeJSProcess();
//                }
//                program = new ProgramData();
//            }
////            programs.put(rootURL, program);
//
//            FileData fi = new FileData();
//            fi.program = program;
////            fi.relPath = indxbl.getRelativePath();
//            allFiles.put(snapshot.getSource().getFileObject(), fi);

//            program.setFileSnapshot(fi.relPath, indxbl, snapshot, cntxt.checkForEditorModifications());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    static void removeFile(Indexable indxbl, Context cntxt) {
        lock.lock();
        try {
            ProgramData program = programs.get(cntxt.getRootURI());
            if (program != null) {
                try {
                    FileObject fileObj = program.removeFile(indxbl.getRelativePath());
                    allFiles.remove(fileObj);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    static final Convertor<JSONObject> errorConvertor = new Convertor<JSONObject>() {
        @Override
        public ErrorsCache.ErrorKind getKind(JSONObject err) {
            int category = ((Number) err.get("category")).intValue();
            return category == 0 ? ErrorsCache.ErrorKind.WARNING
                                 : ErrorsCache.ErrorKind.ERROR;
        }
        @Override
        public int getLineNumber(JSONObject err) {
            return ((Number) err.get("line")).intValue();
        }
        @Override
        public String getMessage(JSONObject err) {
            return (String) err.get("messageText");
        }
    };

    static void updateErrors(final URL rootURI) {
        final ProgramData program;
        final Object currentUpdate;
        final Indexable[] files;
        lock.lock();
        try {
            program = programs.get(rootURI);
            if (program == null || ! program.needErrorsUpdate) {
                return;
            }
            program.needErrorsUpdate = false;
            program.currentErrorsUpdate = currentUpdate = new Object();
            files = program.indexables.values().toArray(new Indexable[0]);
        } finally {
            lock.unlock();
        }
        new Runnable() {
            RequestProcessor.Task task = RP.create(this);
            ProgressHandle progress = ProgressHandleFactory.createHandle("TypeScript error checking", task);
            @Override
            public void run() {
                progress.start(files.length);
                try {
                    long t1 = System.currentTimeMillis();
                    for (int i = 0; i < files.length; i++) {
                        Indexable indexable = files[i];
                        String fileName = indexable.getRelativePath();
                        progress.progress(fileName, i);
                        if (fileName.endsWith(".json")) {
                            continue;
                        }
                        lock.lockInterruptibly();
                        try {
                            if (program.currentErrorsUpdate != currentUpdate) {
                                return; // this task has been superseded
                            }
                            JSONObject errors = (JSONObject) program.call("getDiagnostics", fileName);
                            if (errors != null) {
                                ErrorsCache.setErrors(rootURI, indexable,
                                        (List<JSONObject>) errors.get("errs"), errorConvertor);
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                    log.log(Level.FINE, "updateErrors for {0} completed in {1}ms",
                            new Object[] { rootURI, System.currentTimeMillis() - t1 });
                } catch (InterruptedException e) {
                    log.log(Level.INFO, "updateErrors for {0} cancelled by user", rootURI);
                } finally {
                    progress.finish();
                }
            }
        }.task.schedule(0);
    }

    static void removeProgram(URL rootURL) {
        lock.lock();
        try {
            ProgramData program = programs.remove(rootURL);
            if (program == null) {
                return;
            }
            program.currentErrorsUpdate = null; // stop any updateErrors task

            Iterator<FileData> iter = allFiles.values().iterator();
            while (iter.hasNext()) {
                FileData fd = iter.next();
                if (fd.program == program) {
                    iter.remove();
                }
            }

            try {
                program.dispose();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (programs.isEmpty()) {
                log.info("No programs left; shutting down nodejs");
                try {
                    nodejs.close();
                } catch (IOException e) {}
                nodejs = null;
            }
        } finally {
            lock.unlock();
        }
    }

    static void updateFile(Snapshot snapshot) {
        lock.lock();
        try {
            FileData fd = allFiles.get(snapshot.getSource().getFileObject());
            if (fd != null) {
                fd.program.setFileSnapshot(fd.relPath, null, snapshot, true);
            }
        } finally {
            lock.unlock();
        }
    }

    static List<DefaultError> getDiagnostics(Snapshot snapshot) {
        FileObject fo = snapshot.getSource().getFileObject();
        lock.lock();
        try {
            FileData fd = allFiles.get(fo);
            if (fd == null) {
                return Arrays.asList(new DefaultError(null,
                    "Unknown source root for file " + fo.getPath(),
                    null, fo, 0, 1, true, Severity.ERROR));
            }

            JSONObject diags = (JSONObject) fd.program.call("getDiagnostics", fd.relPath);
            if (diags == null) {
                return Arrays.asList(new DefaultError(null,
                    nodejs.error != null ? nodejs.error : "Error in getDiagnostics",
                    null, fo, 0, 1, true, Severity.ERROR));
            }

            List<DefaultError> errors = new ArrayList<DefaultError>();
            String metaError = (String) diags.get("metaError");
            if (metaError != null) {
                errors.add(new DefaultError(null, metaError, null, fo, 0, 1, true, Severity.ERROR));
            }
            for (JSONObject err: (List<JSONObject>) diags.get("errs")) {
                int start = ((Number) err.get("start")).intValue();
                int length = ((Number) err.get("length")).intValue();
                String messageText = (String) err.get("messageText");
                int category = ((Number) err.get("category")).intValue();
                //int code = ((Number) err.get("code")).intValue();
                errors.add(new DefaultError(null, messageText, null,
                        fo, start, start + length, false,
                        category == 0 ? Severity.WARNING : Severity.ERROR));
            }
            return errors;
        } finally {
            lock.unlock();
        }
    }

    static Object call(String method, FileObject fileObj, Object... args) {
        lock.lock();
        try {
            FileData fd = allFiles.get(fileObj);
            if (fd == null) {
                return null;
            }
            Object[] filenameAndArgs = new Object[args.length + 1];
            filenameAndArgs[0] = fd.relPath;
            System.arraycopy(args, 0, filenameAndArgs, 1, args.length);
            Object ret = fd.program.call(method, filenameAndArgs);
            // Translate file names back to file objects
            if (ret instanceof JSONArray) {
                for (Object item: (JSONArray) ret) {
                    if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        Object fileName = obj.get("fileName");
                        if (fileName instanceof String) {
                            obj.put("fileObject", fd.program.getFile((String) fileName));
                        }
                    }
                }
            }
            return ret;
        } finally {
            lock.unlock();
        }
    }
}
