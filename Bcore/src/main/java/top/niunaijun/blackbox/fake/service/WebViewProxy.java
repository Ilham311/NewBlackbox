package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

import java.io.File;
import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.app.BActivityThread;

public class WebViewProxy extends ClassInvocationStub {
    public static final String TAG = "WebViewProxy";

    public WebViewProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null; 
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("<init>")
    public static class Constructor extends MethodHook {
        private static boolean sDirectorySuffixSet = false;

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (!sDirectorySuffixSet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // Use process name instead of PID for persistent session/cookies
                    String appProcessName = BActivityThread.getAppProcessName();
                    String processSuffix = BActivityThread.getUserId() + "_" + (appProcessName != null ? appProcessName : "unknown");
                    // Replace colons as they might be invalid in directory names
                    processSuffix = processSuffix.replace(":", "_");
                    android.webkit.WebView.setDataDirectorySuffix(processSuffix);
                    sDirectorySuffixSet = true;
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to set WebView data directory suffix", e);
                }
            }
            try {
                return method.invoke(who, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            } catch (Exception e) {
                throw e;
            }
        }
    }

    @ProxyMethod("setDataDirectorySuffix")
    public static class SetDataDirectorySuffix extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (args != null && args.length > 0) {
                    String suffix = (String) args[0];
                    Slog.d(TAG, "WebView: setDataDirectorySuffix called with: " + suffix);
                    
                    String userId = String.valueOf(BActivityThread.getUserId());
                    // Combine app's requested suffix with user ID to isolate users, but keep cookies persistent per process logic
                    String uniqueSuffix = suffix + "_" + userId;
                    args[0] = uniqueSuffix;
                    Slog.d(TAG, "WebView: Using unique suffix: " + uniqueSuffix);
                }
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "WebView: setDataDirectorySuffix failed, continuing without suffix", e);
                return null; 
            }
        }
    }

    @ProxyMethod("getDataDirectory")
    public static class GetDataDirectory extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Context context = BlackBoxCore.getContext();
                if (context != null) {
                    String userId = String.valueOf(BActivityThread.getUserId());
                    String appProcessName = BActivityThread.getAppProcessName();
                    String processName = appProcessName != null ? appProcessName.replace(":", "_") : "unknown";
                    // Use processName instead of myPid() to ensure persistent directory
                    String uniqueDir = context.getApplicationInfo().dataDir + "/webview_" + userId + "_" + processName;
                    
                    File dir = new File(uniqueDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    Slog.d(TAG, "WebView: Returning unique data directory: " + uniqueDir);
                    return uniqueDir;
                }
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "WebView: getDataDirectory failed, returning fallback", e);
                return "/data/data/" + BlackBoxCore.getHostPkg() + "/webview_fallback";
            }
        }
    }

    @ProxyMethod("getInstance")
    public static class GetWebViewDatabaseInstance extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Context context = BlackBoxCore.getContext();
                if (context != null) {
                    String userId = String.valueOf(BActivityThread.getUserId());
                    String appProcessName = BActivityThread.getAppProcessName();
                    String processName = appProcessName != null ? appProcessName.replace(":", "_") : "unknown";
                    // Use processName instead of PID for persistent DB
                    String uniqueDbPath = context.getApplicationInfo().dataDir + "/webview_db_" + userId + "_" + processName;
                    
                    System.setProperty("webview.database.path", uniqueDbPath);
                    Slog.d(TAG, "WebView: Set unique database path: " + uniqueDbPath);
                }
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "WebView: Failed to get WebViewDatabase instance", e);
                return null;
            }
        }
    }

    @ProxyMethod("loadUrl")
    public static class LoadUrl extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0) {
                String url = (String) args[0];
                Slog.d(TAG, "WebView: loadUrl called with: " + url);
            }
            return method.invoke(who, args);
        }
    }
}
