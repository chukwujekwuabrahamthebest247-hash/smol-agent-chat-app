package com.smolagent.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Enhanced Accessibility Service for SmolAgent.
 * Handles AI-guided UI interactions, scrolling, and search.
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SmolAgentAS"
        var instance: AgentAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "SmolAgent Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ---------------------------
    // UI TREE CAPTURE
    // ---------------------------
    fun captureUiTree(): String {
        val rootNode = rootInActiveWindow ?: return "No active window"
        val sb = StringBuilder()
        dumpNode(rootNode, sb, 0)
        return sb.toString()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val className = node.className?.toString()?.split(".")?.last() ?: "Unknown"

        if (node.isVisibleToUser) {
            sb.append("$indent[$className] $text (Clickable: ${node.isClickable})\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, sb, depth + 1)
        }
    }

    // ---------------------------
    // TAP / INPUT ACTIONS
    // ---------------------------
    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performInput(text: String) {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focusedNode?.let {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    // ---------------------------
    // SCROLLING ACTIONS
    // ---------------------------
    fun scrollForward(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---------------------------
    // SEARCH WITH SCROLLING
    // ---------------------------
    fun findNodeByText(targetText: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        return nodes.firstOrNull { it.isVisibleToUser }
    }

    /**
     * Searches for a node, scrolling vertically if necessary.
     * Returns the node if found, or null after maxScrolls attempts.
     */
    fun findNodeByTextWithScroll(targetText: String, maxScrolls: Int = 10): AccessibilityNodeInfo? {
        var attempts = 0
        while (attempts < maxScrolls) {
            val node = findNodeByText(targetText)
            if (node != null) return node
            if (!scrollForward()) break
            Thread.sleep(300)
            attempts++
        }
        attempts = 0
        while (attempts < maxScrolls) {
            val node = findNodeByText(targetText)
            if (node != null) return node
            if (!scrollBackward()) break
            Thread.sleep(300)
            attempts++
        }
        return null
    }

    // ---------------------------
    // AI-GUIDED ACTION EXECUTION
    // ---------------------------
    fun executeAiAction(action: AiAction) {
        when (action.type) {
            AiAction.Type.TAP -> performTap(action.x, action.y)
            AiAction.Type.INPUT -> performInput(action.text ?: "")
            AiAction.Type.SCROLL_FORWARD -> scrollForward()
            AiAction.Type.SCROLL_BACKWARD -> scrollBackward()
            AiAction.Type.SWIPE -> performSwipe(
                action.startX, action.startY,
                action.endX, action.endY
            )
        }
    }

    /**
     * Data class representing an AI-planned action.
     * Populated by Hugging Face SmolChat API.
     */
    data class AiAction(
        val type: Type,
        val x: Float = 0f,
        val y: Float = 0f,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f,
        val text: String? = null
    ) {
        enum class Type {
            TAP, INPUT, SCROLL_FORWARD, SCROLL_BACKWARD, SWIPE
        }
    }
}
