package net.accelf.devoptshide

import android.content.ContentResolver
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class HideDevOpts : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        hideSystemProps(lpparam)

        if (
            lpparam.packageName.startsWith("com.android.")
            || lpparam.packageName.startsWith("com.google.android.")
        ) {
            return
        }

        listOf(Settings.Secure::class.java, Settings.Global::class.java).forEach { parent ->
            findAndHookMethod(
                parent,
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                callback,
            )

            findAndHookMethod(
                parent,
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.java,
                callback,
            )

            findAndHookMethod(
                parent,
                "getString",
                ContentResolver::class.java,
                String::class.java,
                callback,
            )
        }
    }

    fun hideSystemProps(lpparam: LoadPackageParam) {
        XposedBridge.log("LOAD " + lpparam.packageName)

        val clazz = XposedHelpers.findClassIfExists(
            "android.os.SystemProperties", lpparam.classLoader)

        XposedBridge.log("CLASS " + clazz?.name)

        if (clazz != null) {
            val ffsReady = "sys.usb.ffs.ready";
            val usbState = "sys.usb.state";
            val usbConfig = "sys.usb.config";
            val rebootFunc = "persist.sys.usb.reboot.func";
            val methodGet = "get"
            val methodGetBoolean = "getBoolean"
            val methodGetInt = "getInt"
            val methodGetLong = "getLong"
            val overrideAdb = "mtp"

            listOf(methodGet, methodGetBoolean, methodGetInt, methodGetLong).forEach {
                XposedBridge.hookAllMethods(clazz, it,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam?) {
                            XposedBridge.log(
                                "ACCESS " + param!!.method.name + " PARAM " + param.args[0])

                            if (param.args[0] != ffsReady && param.method.name != methodGet) {
                                return
                            }

                            when(param.args[0]) {
                                ffsReady -> {
                                    when(param.method.name) {
                                        methodGet -> param.result = "0"
                                        methodGetBoolean -> param.result = false
                                        methodGetInt -> param.result = 0
                                        methodGetLong -> param.result = 0L
                                    }
                                }
                                usbState -> param.result = overrideAdb
                                usbConfig -> param.result = overrideAdb
                                rebootFunc -> param.result = overrideAdb
                            }
                        }
                    })
            }
        }
    }

    companion object {
        private val names = listOf(
            Settings.Global.ADB_ENABLED,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        )

        private val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (
                    param.args[1] !is String
                    || !names.contains(param.args[1] as String)
                ) {
                    return
                }

                param.result = when (param.method.getName()) {
                    "getInt" -> 0
                    "getString" -> "0"
                    else -> error("Illegal method name passed.")
                }
            }
        }
    }
}
