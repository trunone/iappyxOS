/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.iappyx.generated.placeholder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppAccessibilityService extends AccessibilityService {

    private static final String TAG = "iappyxOS";
    private static AppAccessibilityService instance;
    private static String eventCallbackFn;

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static boolean isServiceEnabled(Context context) {
        if (context == null) return false;
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {}

        if (accessibilityEnabled == 1) {
            String services = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services != null) {
                String pkg = context.getPackageName().toLowerCase();
                String cls = AppAccessibilityService.class.getName().toLowerCase();
                String simpleCls = AppAccessibilityService.class.getSimpleName().toLowerCase();
                String servicesLower = services.toLowerCase();
                return servicesLower.contains(pkg) && (servicesLower.contains(cls) || servicesLower.contains(simpleCls));
            }
        }
        return false;
    }

    public static void setEventCallback(String callbackFn) {
        eventCallbackFn = callbackFn;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "AppAccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || eventCallbackFn == null) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("eventType", event.getEventType());
            obj.put("eventTypeName", eventTypeToString(event.getEventType()));
            obj.put("packageName", event.getPackageName() != null ? event.getPackageName().toString() : "");
            obj.put("className", event.getClassName() != null ? event.getClassName().toString() : "");
            obj.put("text", event.getText() != null ? event.getText().toString() : "");
            obj.put("eventTime", event.getEventTime());

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                obj.put("sourceNode", nodeToBriefJson(source));
                source.recycle();
            }

            Intent intent = new Intent("com.iappyx.ACCESSIBILITY_EVENT");
            intent.setPackage(getPackageName());
            intent.putExtra("json", obj.toString());
            intent.putExtra("callbackFn", eventCallbackFn);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent error: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "AppAccessibilityService interrupted");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    // ── Static methods called by AccessibilityBridge ──

    public static boolean doGlobalAction(int action) {
        if (instance == null) return false;
        return instance.performGlobalAction(action);
    }

    public static String getRootWindowJson(int maxDepth) {
        if (instance == null) return null;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return null;
        try {
            JSONObject json = nodeToJson(root, 0, maxDepth > 0 ? maxDepth : 20);
            root.recycle();
            return json.toString();
        } catch (Exception e) {
            root.recycle();
            return null;
        }
    }

    public static String findNodesByText(String text) {
        if (instance == null || text == null) return "[]";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "[]";

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        JSONArray array = new JSONArray();
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    try {
                        array.put(nodeToBriefJson(node));
                    } catch (Exception ignored) {}
                    node.recycle();
                }
            }
        }
        root.recycle();
        return array.toString();
    }

    public static String findNodesById(String viewId) {
        if (instance == null || viewId == null) return "[]";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "[]";

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        JSONArray array = new JSONArray();
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    try {
                        array.put(nodeToBriefJson(node));
                    } catch (Exception ignored) {}
                    node.recycle();
                }
            }
        }
        root.recycle();
        return array.toString();
    }

    public static boolean performActionOnNode(String targetType, String targetValue, int action, String textArg) {
        if (instance == null) return false;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo targetNode = findTargetNode(root, targetType, targetValue);
        if (targetNode == null) {
            root.recycle();
            return false;
        }

        boolean result = false;
        if (action == AccessibilityNodeInfo.ACTION_SET_TEXT && textArg != null) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textArg);
            result = targetNode.performAction(action, arguments);
        } else {
            result = targetNode.performAction(action);
        }

        targetNode.recycle();
        root.recycle();
        return result;
    }

    public static boolean gestureClick(float x, float y, long durationMs) {
        if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
            path, 0, Math.max(1, durationMs));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return instance.dispatchGesture(gesture, null, null);
    }

    public static boolean gestureSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
            path, 0, Math.max(1, durationMs));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return instance.dispatchGesture(gesture, null, null);
    }

    // ── Helper methods ──

    private static AccessibilityNodeInfo findTargetNode(AccessibilityNodeInfo root, String targetType, String targetValue) {
        if (root == null || targetValue == null) return null;

        if ("id".equalsIgnoreCase(targetType)) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(targetValue);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo result = AccessibilityNodeInfo.obtain(nodes.get(0));
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return result;
            }
        } else if ("text".equalsIgnoreCase(targetType)) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(targetValue);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo result = AccessibilityNodeInfo.obtain(nodes.get(0));
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return result;
            }
        }
        return null;
    }

    private static JSONObject nodeToBriefJson(AccessibilityNodeInfo node) throws Exception {
        JSONObject obj = new JSONObject();
        if (node == null) return obj;

        obj.put("packageName", node.getPackageName() != null ? node.getPackageName().toString() : "");
        obj.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
        obj.put("text", node.getText() != null ? node.getText().toString() : "");
        obj.put("viewId", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        obj.put("contentDescription", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        obj.put("clickable", node.isClickable());
        obj.put("editable", node.isEditable());
        obj.put("enabled", node.isEnabled());
        obj.put("focused", node.isFocused());
        obj.put("visibleToUser", node.isVisibleToUser());

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        JSONObject boundsJson = new JSONObject();
        boundsJson.put("left", bounds.left);
        boundsJson.put("top", bounds.top);
        boundsJson.put("right", bounds.right);
        boundsJson.put("bottom", bounds.bottom);
        boundsJson.put("width", bounds.width());
        boundsJson.put("height", bounds.height());
        obj.put("bounds", boundsJson);

        return obj;
    }

    private static JSONObject nodeToJson(AccessibilityNodeInfo node, int currentDepth, int maxDepth) throws Exception {
        JSONObject obj = nodeToBriefJson(node);

        if (currentDepth < maxDepth) {
            JSONArray children = new JSONArray();
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    children.put(nodeToJson(child, currentDepth + 1, maxDepth));
                    child.recycle();
                }
            }
            obj.put("children", children);
        }
        return obj;
    }

    private static String eventTypeToString(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "TYPE_VIEW_CLICKED";
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED: return "TYPE_VIEW_LONG_CLICKED";
            case AccessibilityEvent.TYPE_VIEW_SELECTED: return "TYPE_VIEW_SELECTED";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED: return "TYPE_VIEW_FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED: return "TYPE_VIEW_TEXT_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "TYPE_WINDOW_STATE_CHANGED";
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: return "TYPE_NOTIFICATION_STATE_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER: return "TYPE_VIEW_HOVER_ENTER";
            case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT: return "TYPE_VIEW_HOVER_EXIT";
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START: return "TYPE_TOUCH_EXPLORATION_GESTURE_START";
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END: return "TYPE_TOUCH_EXPLORATION_GESTURE_END";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: return "TYPE_WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: return "TYPE_VIEW_SCROLLED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED: return "TYPE_VIEW_TEXT_SELECTION_CHANGED";
            case AccessibilityEvent.TYPE_ANNOUNCEMENT: return "TYPE_ANNOUNCEMENT";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED: return "TYPE_VIEW_ACCESSIBILITY_FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED: return "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED";
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_START: return "TYPE_GESTURE_DETECTION_START";
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_END: return "TYPE_GESTURE_DETECTION_END";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START: return "TYPE_TOUCH_INTERACTION_START";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END: return "TYPE_TOUCH_INTERACTION_END";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED: return "TYPE_WINDOWS_CHANGED";
            default: return "TYPE_UNKNOWN (" + eventType + ")";
        }
    }
}
