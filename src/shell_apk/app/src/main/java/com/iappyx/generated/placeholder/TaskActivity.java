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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Android UI for Task-only Apps running V8 engine.
 * Shows editable script editor, real-time console log output, and Run/Stop controls.
 */
public class TaskActivity extends Activity {

    private static final String PREF_SCRIPT_KEY = "saved_task_script";

    private V8TaskEngine v8Engine;
    private EditText scriptEditor;
    private TextView logOutput;
    private ScrollView logScrollView;
    private TextView statusBadge;
    private Button runStopBtn;

    private boolean isScriptTabActive = true;
    private LinearLayout scriptViewLayout;
    private LinearLayout consoleViewLayout;
    private Button tabScriptBtn;
    private Button tabConsoleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        v8Engine = new V8TaskEngine(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#0D0D1A"));
        }

        // Root container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0D1A"));
        root.setPadding(32, 48, 32, 32);

        // Header: Title + Status
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleText = new TextView(this);
        titleText.setText(getAppName());
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleText.setTypeface(null, Typeface.BOLD);
        headerLayout.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = new TextView(this);
        statusBadge.setText(" IDLE ");
        statusBadge.setTextColor(Color.parseColor("#4FC3F7"));
        statusBadge.setBackgroundColor(Color.parseColor("#1A1A2E"));
        statusBadge.setPadding(16, 8, 16, 8);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusBadge.setTypeface(null, Typeface.BOLD);
        headerLayout.addView(statusBadge);

        root.addView(headerLayout);

        // Run/Stop Button
        runStopBtn = new Button(this);
        runStopBtn.setText("▶ Run Script");
        runStopBtn.setTextColor(Color.WHITE);
        runStopBtn.setBackgroundColor(Color.parseColor("#0F3460"));
        runStopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        runStopBtn.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 24, 0, 20);
        runStopBtn.setLayoutParams(btnParams);
        runStopBtn.setOnClickListener(v -> toggleRunStop());
        root.addView(runStopBtn);

        // Tabs: Script / Console Output
        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);

        tabScriptBtn = new Button(this);
        tabScriptBtn.setText("Script Editor");
        tabScriptBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tabScriptBtn.setOnClickListener(v -> switchTab(true));

        tabConsoleBtn = new Button(this);
        tabConsoleBtn.setText("Console Output");
        tabConsoleBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tabConsoleBtn.setOnClickListener(v -> switchTab(false));

        LinearLayout.LayoutParams tabParam = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabLayout.addView(tabScriptBtn, tabParam);
        tabLayout.addView(tabConsoleBtn, tabParam);
        root.addView(tabLayout);

        // Content Area Frame
        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        frameParams.setMargins(0, 16, 0, 0);
        contentFrame.setLayoutParams(frameParams);

        // Script View Layout
        scriptViewLayout = new LinearLayout(this);
        scriptViewLayout.setOrientation(LinearLayout.VERTICAL);

        scriptEditor = new EditText(this);
        scriptEditor.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scriptEditor.setTextColor(Color.parseColor("#EAEAEA"));
        scriptEditor.setHint("Write JavaScript code...");
        scriptEditor.setHintTextColor(Color.parseColor("#666666"));
        scriptEditor.setFontFeatureSettings("monospace");
        scriptEditor.setTypeface(Typeface.MONOSPACE);
        scriptEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        scriptEditor.setPadding(20, 20, 20, 20);
        scriptEditor.setGravity(Gravity.TOP | Gravity.START);
        scriptEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        ScrollView scriptScrollView = new ScrollView(this);
        scriptScrollView.addView(scriptEditor, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scriptViewLayout.addView(scriptScrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button saveScriptBtn = new Button(this);
        saveScriptBtn.setText("💾 Save Script");
        saveScriptBtn.setTextColor(Color.parseColor("#4FC3F7"));
        saveScriptBtn.setBackgroundColor(Color.parseColor("#1A1A2E"));
        saveScriptBtn.setOnClickListener(v -> {
            saveScript(scriptEditor.getText().toString());
            Toast.makeText(this, "Script saved", Toast.LENGTH_SHORT).show();
        });
        scriptViewLayout.addView(saveScriptBtn);

        contentFrame.addView(scriptViewLayout);

        // Console View Layout
        consoleViewLayout = new LinearLayout(this);
        consoleViewLayout.setOrientation(LinearLayout.VERTICAL);

        logOutput = new TextView(this);
        logOutput.setTextColor(Color.parseColor("#69F0AE"));
        logOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logOutput.setTypeface(Typeface.MONOSPACE);
        logOutput.setPadding(20, 20, 20, 20);
        logOutput.setText("Awaiting execution...\n");

        logScrollView = new ScrollView(this);
        logScrollView.setBackgroundColor(Color.parseColor("#0A0A14"));
        logScrollView.addView(logOutput, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        consoleViewLayout.addView(logScrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout consoleActions = new LinearLayout(this);
        consoleActions.setOrientation(LinearLayout.HORIZONTAL);

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear");
        clearBtn.setTextColor(Color.parseColor("#EAEAEA"));
        clearBtn.setBackgroundColor(Color.parseColor("#1A1A2E"));
        clearBtn.setOnClickListener(v -> logOutput.setText(""));

        Button copyBtn = new Button(this);
        copyBtn.setText("Copy Output");
        copyBtn.setTextColor(Color.parseColor("#4FC3F7"));
        copyBtn.setBackgroundColor(Color.parseColor("#1A1A2E"));
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Console Output", logOutput.getText()));
            Toast.makeText(this, "Output copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        consoleActions.addView(clearBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        consoleActions.addView(copyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        consoleViewLayout.addView(consoleActions);

        contentFrame.addView(consoleViewLayout);

        root.addView(contentFrame);
        setContentView(root);

        // Load initial script
        scriptEditor.setText(loadInitialScript());
        switchTab(true);
    }

    private void switchTab(bool showScript) {
        isScriptTabActive = showScript;
        scriptViewLayout.setVisibility(showScript ? View.VISIBLE : View.GONE);
        consoleViewLayout.setVisibility(showScript ? View.GONE : View.VISIBLE);

        if (showScript) {
            tabScriptBtn.setBackgroundColor(Color.parseColor("#0F3460"));
            tabScriptBtn.setTextColor(Color.WHITE);
            tabConsoleBtn.setBackgroundColor(Color.parseColor("#1A1A2E"));
            tabConsoleBtn.setTextColor(Color.parseColor("#888888"));
        } else {
            tabConsoleBtn.setBackgroundColor(Color.parseColor("#0F3460"));
            tabConsoleBtn.setTextColor(Color.WHITE);
            tabScriptBtn.setBackgroundColor(Color.parseColor("#1A1A2E"));
            tabScriptBtn.setTextColor(Color.parseColor("#888888"));
        }
    }

    private void toggleRunStop() {
        if (v8Engine.isRunning()) {
            v8Engine.stop();
            appendLog("sys", "Execution stopped by user.");
            updateStateUI(false, "STOPPED");
        } else {
            String script = scriptEditor.getText().toString();
            saveScript(script);

            switchTab(false); // Switch to console view
            updateStateUI(true, "RUNNING...");
            appendLog("sys", "Starting V8 Execution...");

            v8Engine.runScript(script, (type, message) -> {
                appendLog(type, message);
            }, () -> {
                updateStateUI(false, "IDLE");
            });
        }
    }

    private void updateStateUI(boolean running, String statusText) {
        runOnUiThread(() -> {
            statusBadge.setText(" " + statusText + " ");
            if (running) {
                statusBadge.setTextColor(Color.parseColor("#69F0AE"));
                runStopBtn.setText("⏹ Stop Execution");
                runStopBtn.setBackgroundColor(Color.parseColor("#FF6B6B"));
            } else {
                statusBadge.setTextColor(Color.parseColor("#4FC3F7"));
                runStopBtn.setText("▶ Run Script");
                runStopBtn.setBackgroundColor(Color.parseColor("#0F3460"));
            }
        });
    }

    private void appendLog(String type, String message) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String prefix = "[LOG]";
            if ("error".equals(type)) prefix = "[ERR]";
            else if ("warn".equals(type)) prefix = "[WRN]";
            else if ("info".equals(type)) prefix = "[INF]";
            else if ("sys".equals(type)) prefix = "[SYS]";

            logOutput.append("[" + time + "] " + prefix + " " + message + "\n");
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private String loadInitialScript() {
        String saved = BridgeUtils.load(this, PREF_SCRIPT_KEY);
        if (saved != null && !saved.trim().isEmpty()) {
            return saved;
        }
        // Load from assets/app/index.html or assets/app/script.js
        try {
            InputStream is = getAssets().open("app/index.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            String content = sb.toString().trim();
            // If asset is JS or contains code
            if (content.startsWith("<")) {
                // Strip HTML wrappers if present
                content = content.replaceAll("(?s)<script.*?>", "").replaceAll("</script>", "").replaceAll("(?s)<.*?>", "").trim();
            }
            return content.isEmpty() ? getFallbackScript() : content;
        } catch (Exception e) {
            return getFallbackScript();
        }
    }

    private String getFallbackScript() {
        return "console.log('Task initialized');\n" +
            "var lastRun = iappyx.load('last_run');\n" +
            "console.log('Last run:', lastRun || 'never');\n" +
            "iappyx.save('last_run', new Date().toISOString());\n" +
            "console.log('Task completed');\n";
    }

    private void saveScript(String script) {
        BridgeUtils.save(this, PREF_SCRIPT_KEY, script);
    }

    private String getAppName() {
        try {
            return getPackageManager().getApplicationLabel(getApplicationInfo()).toString();
        } catch (Exception e) {
            return "Task App";
        }
    }

    @Override
    protected void onDestroy() {
        if (v8Engine != null) {
            v8Engine.stop();
        }
        super.onDestroy();
    }
}
