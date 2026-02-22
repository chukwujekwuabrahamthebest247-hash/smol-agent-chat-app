
package com.smolagent.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SmolAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SmolAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("SmolAccessibility", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Used for real-time UI monitoring if needed
    }

    override fun onInterrupt() {
        Log.d("SmolAccessibility", "Accessibility Interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Extract UI Tree as structured text
     */
    fun extractUiTree(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        traverseNode(rootNode, builder)
        return builder.toString()
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        builder.append(
            "Class: ${node.className} | " +
            "Text: ${node.text} | " +
            "ContentDesc: ${node.contentDescription} | " +
            "Clickable: ${node.isClickable}\n"
        )

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), builder)
        }
    }

    /**
     * Perform Tap by Coordinates
     */
    fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Perform Scroll Gesture
     */
    fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Input Text into focused field
     */
    fun inputText(text: String) {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        focusedNode?.let {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            it.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
        }
    }

    /**
     * Click node by visible text
     */
    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodeList = rootNode.findAccessibilityNodeInfosByText(text)

        for (node in nodeList) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    /**
     * Launch another app
     */
    fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
