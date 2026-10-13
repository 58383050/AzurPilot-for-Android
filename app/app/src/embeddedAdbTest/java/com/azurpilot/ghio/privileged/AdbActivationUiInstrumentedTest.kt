package com.azurpilot.ghio.privileged

import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.azurpilot.ghio.R
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证已激活设备上的内置启动页能显示状态和操作，不依赖主页面导航。
 *
 * Verifies activation status and actions on an already activated device without depending on
 * main-page navigation.
 */
@RunWith(AndroidJUnit4::class)
class AdbActivationUiInstrumentedTest {
    @Test
    fun displaysReadyStatusAndActivationAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // 先模拟用户打开主页面，避免 MIUI 把测试进程发起的 Activity 当作后台弹窗拒绝。
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${context.packageName}/com.azurpilot.ghio.MainActivity",
        )).use { it.bufferedReader().readText() }
        val foregroundDeadline = SystemClock.elapsedRealtime() + 10_000
        var opened = false
        while (!opened && SystemClock.elapsedRealtime() < foregroundDeadline) {
            instrumentation.runOnMainSync {
                val main = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<com.azurpilot.ghio.MainActivity>().firstOrNull()
                if (main != null) {
                    main.startActivity(Intent(main, AdbActivationActivity::class.java))
                    opened = true
                }
            }
            if (!opened) SystemClock.sleep(100)
        }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        try {
            // 系统界面树不等待整台设备的 Compose 动画停下，只检查本页可见状态。
            while (SystemClock.elapsedRealtime() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val nodes = root?.let(::visibleNodes).orEmpty()
                val title = context.getString(R.string.adb_title)
                val ready = context.getString(R.string.adb_ready)
                val action = context.getString(R.string.adb_start)
                if (nodes.any { it.text?.toString() == title }
                    && nodes.any { it.text?.toString() == ready }
                    && nodes.any { it.text?.toString() == action && it.isEnabled }) return
                SystemClock.sleep(100)
            }
            fail("Embedded activation page unavailable: opened=$opened status=${EmbeddedAdbManager.status.value}")
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AdbActivationActivity>().forEach { it.finish() }
            }
        }
    }

    private fun visibleNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        if (root.isVisibleToUser) add(root)
        repeat(root.childCount) { index -> root.getChild(index)?.let { addAll(visibleNodes(it)) } }
    }
}
