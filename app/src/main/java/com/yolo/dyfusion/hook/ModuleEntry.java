package com.yolo.dyfusion.hook;

import android.app.*;
import android.content.*;
import com.yolo.dyfusion.config.SettingsProvider;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ModuleEntry implements IXposedHookLoadPackage {
  @Override
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
    if (p.packageName.equals("com.yolo.dyfusion")) {
      XposedHelpers.findAndHookMethod(
          "com.yolo.dyfusion.ui.MainActivity",
          p.classLoader,
          "isModuleActive",
          new XC_MethodReplacement() {
            protected Object replaceHookedMethod(MethodHookParam p) {
              return true;
            }
          });
      return;
    }
    if (!SettingsProvider.HOSTS.contains(p.packageName) || !p.packageName.equals(p.processName))
      return;
    XposedHelpers.findAndHookMethod(
        Application.class,
        "attach",
        Context.class,
        new XC_MethodHook() {
          private boolean started;

          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (started) return;
            started = true;
            Context c = (Context) param.args[0];
            RuntimeState r = new RuntimeState(c);
            new HookInstaller(r, c.getClassLoader()).install();
            new UiController(r, (Application) param.thisObject).install();
            android.util.Log.i("DyFusion", "attached " + p.packageName + " " + r.hostVersion);
          }
        });
  }
}
