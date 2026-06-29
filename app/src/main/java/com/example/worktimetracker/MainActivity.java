package com.example.worktimetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private boolean dark;
    private long currentDayMs;
    private FrameLayout content;
    private LinearLayout dayPage;
    private LinearLayout reportPage;
    private Button navDay;
    private Button navReport;
    private Button themeButton;
    private Button dateButton;
    private TextView goodLabel, badLabel, monthLabel, startLabel, endLabel, trackedLabel, durationLabel;
    private TimelineView timelineView;
    private EditText searchInput;
    private Spinner filterSpinner;
    private LinearLayout tableContainer;
    private TextView showingLabel;
    private LinearLayout topAppsContainer;
    private TextView footerStatus;
    private TextView footerDb;
    private final List<ActivitySegment> rows = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();

    private EditText fromInput;
    private EditText toInput;
    private TextView totalCardValue;
    private TextView periodCardValue;
    private TextView systemCardValue;
    private LinearLayout reportTopContainer;
    private LinearLayout reportDailyContainer;
    private long reportFromMs;
    private long reportToMs;
    private ReportData currentReport = new ReportData();

    private int bg, panel, card, text, muted, border, accent, green, red, blue, orange;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshData();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new DatabaseHelper(this);
        dark = "dark".equals(db.getSetting("theme", "dark"));
        currentDayMs = TimeUtils.dayStart(System.currentTimeMillis());
        applyPalette();
        requestNotificationPermission();
        startTrackerService();
        buildUi();
        refreshData();
        if (!UsageTrackerService.hasUsageAccess(this)) showUsageDialog();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshData();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(UsageTrackerService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refreshReceiver, filter);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(refreshReceiver); } catch (Exception ignored) { }
        super.onStop();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1183);
        }
    }

    private void startTrackerService() {
        Intent service = new Intent(this, UsageTrackerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    private void applyPalette() {
        accent = Color.parseColor("#00C896");
        green = Color.parseColor("#34D399");
        red = Color.parseColor("#F87171");
        blue = Color.parseColor("#7DD3FC");
        orange = Color.parseColor("#F97316");
        if (dark) {
            bg = Color.parseColor("#0F1416");
            panel = Color.parseColor("#151B1E");
            card = Color.parseColor("#10171B");
            text = Color.parseColor("#E8EEF0");
            muted = Color.parseColor("#8D9AA0");
            border = Color.parseColor("#2D373C");
        } else {
            bg = Color.parseColor("#EEF2F7");
            panel = Color.WHITE;
            card = Color.parseColor("#F8FAFC");
            text = Color.parseColor("#0F172A");
            muted = Color.parseColor("#64748B");
            border = Color.parseColor("#CBD5E1");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(bg);
        setContentView(root);
        root.addView(buildTopNav(), lpMatchWrap());
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        dayPage = buildDayPage();
        reportPage = buildReportPage();
        content.addView(dayPage);
        content.addView(reportPage);
        root.addView(buildFooter(), lpMatchWrap());
        showDayPage();
    }

    private View buildTopNav() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(6));
        navDay = navButton("📅  Day");
        navReport = navButton("▦  Timesheet");
        navDay.setOnClickListener(v -> showDayPage());
        navReport.setOnClickListener(v -> showReportPage());
        bar.addView(navDay);
        bar.addView(navReport);
        Space(bar, 1);
        Button permission = smallButton("Usage Access");
        permission.setOnClickListener(v -> UsageTrackerService.openUsageAccessSettings(this));
        themeButton = smallButton(dark ? "🌙" : "☀️");
        themeButton.setOnClickListener(v -> toggleTheme());
        Button settings = smallButton("⚙");
        settings.setOnClickListener(v -> openSettingsDialog());
        bar.addView(permission);
        bar.addView(themeButton);
        bar.addView(settings);
        return bar;
    }

    private LinearLayout buildDayPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);
        page.addView(buildToolbar(), lpMatchWrap());
        timelineView = new TimelineView(this);
        page.addView(timelineView, new LinearLayout.LayoutParams(-1, dp(230)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom.addView(buildTablePanel(), new LinearLayout.LayoutParams(0, -1, 3));
        bottom.addView(buildTopAppsPanel(), new LinearLayout.LayoutParams(0, -1, 2));
        return page;
    }

    private View buildToolbar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        hsv.addView(row);
        Button prev = smallButton("‹");
        prev.setOnClickListener(v -> { currentDayMs = TimeUtils.addDays(currentDayMs, -1); refreshData(); });
        Button next = smallButton("›");
        next.setOnClickListener(v -> { currentDayMs = TimeUtils.addDays(currentDayMs, 1); refreshData(); });
        Button today = smallButton("Today");
        today.setOnClickListener(v -> { currentDayMs = TimeUtils.dayStart(System.currentTimeMillis()); refreshData(); });
        dateButton = smallButton("");
        dateButton.setOnClickListener(v -> openDateDialog());
        Button tag = smallButton("+ Add tag");
        tag.setOnClickListener(v -> addTagToSelected());
        row.addView(dateButton, new LinearLayout.LayoutParams(dp(180), dp(38)));
        row.addView(prev, new LinearLayout.LayoutParams(dp(40), dp(38)));
        row.addView(next, new LinearLayout.LayoutParams(dp(40), dp(38)));
        row.addView(today, new LinearLayout.LayoutParams(dp(74), dp(38)));
        row.addView(tag, new LinearLayout.LayoutParams(dp(110), dp(38)));
        goodLabel = pill("✓ 0:00", green, Color.parseColor(dark ? "#063D35" : "#DCFCE7"));
        badLabel = pill("✕ 0:00", red, Color.parseColor(dark ? "#3B1414" : "#FEE2E2"));
        monthLabel = pill("Month 0:00", blue, Color.parseColor(dark ? "#11283F" : "#DBEAFE"));
        startLabel = pill("Start --", text, card);
        endLabel = pill("End --", text, card);
        trackedLabel = pill("Tracked 0:00", text, card);
        durationLabel = pill("Duration 0:00", text, card);
        row.addView(goodLabel);
        row.addView(badLabel);
        row.addView(monthLabel);
        row.addView(startLabel);
        row.addView(endLabel);
        row.addView(trackedLabel);
        row.addView(durationLabel);
        return hsv;
    }

    private View buildTablePanel() {
        LinearLayout panelView = cardLayout();
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        searchInput = editText("Search activities...");
        searchInput.setSingleLine(true);
        searchInput.setOnEditorActionListener((v, actionId, event) -> { fillTable(); return false; });
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { fillTable(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        filterSpinner = new Spinner(this);
        String[] modes = {"All", "Tagged", "Untagged", "Applications", "Documents"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        filterSpinner.setAdapter(adapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { fillTable(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        filters.addView(searchInput, new LinearLayout.LayoutParams(0, dp(42), 1));
        filters.addView(filterSpinner, new LinearLayout.LayoutParams(dp(145), dp(42)));
        panelView.addView(filters, lpMatchWrap());

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        ScrollView scroll = new ScrollView(this);
        tableContainer = new LinearLayout(this);
        tableContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(tableContainer);
        horizontal.addView(scroll);
        panelView.addView(horizontal, new LinearLayout.LayoutParams(-1, 0, 1));
        showingLabel = mutedText("Showing 0 activities");
        panelView.addView(showingLabel, lpMatchWrap());
        return panelView;
    }

    private View buildTopAppsPanel() {
        LinearLayout panelView = cardLayout();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionTitle("Top usage");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        panelView.addView(header);
        topAppsContainer = new LinearLayout(this);
        topAppsContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(topAppsContainer);
        panelView.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return panelView;
    }

    private LinearLayout buildReportPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);
        LinearLayout controls = cardLayout();
        TextView title = pageTitle("Timesheet / Reports");
        TextView subtitle = mutedText("گزارش بازه انتخابی، گزارش ماهانه و خروجی PDF");
        controls.addView(title);
        controls.addView(subtitle);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label("از:"));
        fromInput = editText("1405/03/01");
        row.addView(fromInput, new LinearLayout.LayoutParams(dp(120), dp(42)));
        row.addView(label("تا:"));
        toInput = editText("1405/03/31");
        row.addView(toInput, new LinearLayout.LayoutParams(dp(120), dp(42)));
        Button month = smallButton("ماه جاری");
        month.setOnClickListener(v -> setCurrentMonthReport());
        Button show = smallButton("نمایش گزارش");
        show.setOnClickListener(v -> generateReport());
        Button pdf = orangeButton("خروجی PDF");
        pdf.setOnClickListener(v -> exportPdf());
        row.addView(month);
        row.addView(show);
        row.addView(pdf);
        HorizontalScrollView rowScroll = new HorizontalScrollView(this);
        rowScroll.addView(row);
        controls.addView(rowScroll);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        SummaryCard totalCard = summaryCard("جمع ساعت بازه", "0:00:00", green);
        totalCardValue = totalCard.value;
        SummaryCard periodCard = summaryCard("بازه گزارش", "-", blue);
        periodCardValue = periodCard.value;
        SummaryCard systemCard = summaryCard("سیستم / کاربر", "Android", orange);
        systemCardValue = systemCard.value;
        cards.addView(totalCard.frame, new LinearLayout.LayoutParams(0, dp(86), 1));
        cards.addView(periodCard.frame, new LinearLayout.LayoutParams(0, dp(86), 1));
        cards.addView(systemCard.frame, new LinearLayout.LayoutParams(0, dp(86), 1));
        controls.addView(cards);
        page.addView(controls, lpMatchWrap());

        LinearLayout lists = new LinearLayout(this);
        lists.setOrientation(LinearLayout.VERTICAL);
        lists.addView(reportListCard("۵ فعالیت / برنامه با بیشترین زمان", true), new LinearLayout.LayoutParams(-1, 0, 1));
        lists.addView(reportListCard("جدول گزارش روزانه / ماهانه", false), new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(lists, new LinearLayout.LayoutParams(-1, 0, 1));
        setCurrentMonthReport();
        return page;
    }

    private View reportListCard(String title, boolean top) {
        LinearLayout panelView = cardLayout();
        panelView.addView(sectionTitle(title));
        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body);
        panelView.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        if (top) reportTopContainer = body; else reportDailyContainer = body;
        return panelView;
    }

    private void showDayPage() {
        dayPage.setVisibility(View.VISIBLE);
        reportPage.setVisibility(View.GONE);
        styleNav();
    }

    private void showReportPage() {
        dayPage.setVisibility(View.GONE);
        reportPage.setVisibility(View.VISIBLE);
        styleNav();
        setCurrentMonthReport();
        generateReport();
    }

    private void styleNav() {
        styleButton(navDay, dayPage.getVisibility() == View.VISIBLE ? accent : border, dayPage.getVisibility() == View.VISIBLE ? (dark ? Color.parseColor("#12342F") : Color.parseColor("#DBEAFE")) : bg, dayPage.getVisibility() == View.VISIBLE ? accent : text);
        styleButton(navReport, reportPage.getVisibility() == View.VISIBLE ? accent : border, reportPage.getVisibility() == View.VISIBLE ? (dark ? Color.parseColor("#12342F") : Color.parseColor("#DBEAFE")) : bg, reportPage.getVisibility() == View.VISIBLE ? accent : text);
    }

    private void toggleTheme() {
        dark = !dark;
        db.setSetting("theme", dark ? "dark" : "light");
        applyPalette();
        buildUi();
        refreshData();
    }

    private void refreshData() {
        if (db == null || tableContainer == null) return;
        rows.clear();
        rows.addAll(db.queryDay(currentDayMs));
        Collections.sort(rows, (a, b) -> Long.compare(a.startMs, b.startMs));
        dateButton.setText(JalaliUtils.fullDate(currentDayMs));
        timelineView.setData(currentDayMs, rows, dark);
        fillTable();
        fillTopApps();
        updateSummary();
        updateMonthTotal();
    }

    private void fillTable() {
        if (tableContainer == null) return;
        tableContainer.removeAllViews();
        tableContainer.addView(tableRow(new String[]{"Title", "App", "Start", "End", "Duration", "Tag", "Screenshot"}, true, null));
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.US);
        String filter = filterSpinner == null || filterSpinner.getSelectedItem() == null ? "All" : filterSpinner.getSelectedItem().toString();
        List<ActivitySegment> filtered = new ArrayList<>();
        for (ActivitySegment r : rows) {
            String hay = ((r.title == null ? "" : r.title) + " " + (r.appName == null ? "" : r.appName) + " " + (r.tag == null ? "" : r.tag) + " " + (r.category == null ? "" : r.category)).toLowerCase(Locale.US);
            if (!query.isEmpty() && !hay.contains(query)) continue;
            if ("Tagged".equals(filter) && (r.tag == null || r.tag.isEmpty())) continue;
            if ("Untagged".equals(filter) && r.tag != null && !r.tag.isEmpty()) continue;
            if ("Applications".equals(filter) && r.category != null && r.category.toLowerCase(Locale.US).contains("document")) continue;
            if ("Documents".equals(filter) && (r.category == null || !r.category.toLowerCase(Locale.US).contains("document"))) continue;
            filtered.add(r);
        }
        Collections.sort(filtered, (a, b) -> Long.compare(b.startMs, a.startMs));
        for (ActivitySegment r : filtered) {
            View row = tableRow(new String[]{"■  " + value(r.title, r.appName), value(r.appName, "Unknown"), TimeUtils.hhmmss(r.startMs), TimeUtils.hhmmss(r.endMs), TimeUtils.fmtHms(r.durationSeconds), value(r.tag, ""), r.screenshotPath == null || r.screenshotPath.isEmpty() ? "—" : "نمایش"}, false, r);
            tableContainer.addView(row);
        }
        showingLabel.setText("Showing " + filtered.size() + " of " + rows.size() + " activities");
    }

    private View tableRow(String[] values, boolean header, ActivitySegment segment) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(3), dp(4), dp(3));
        int rowBg = header ? panel : (segment != null && selectedIds.contains(segment.id) ? (dark ? Color.parseColor("#12342F") : Color.parseColor("#DBEAFE")) : card);
        rounded(row, rowBg, border, 6);
        int[] widths = {dp(280), dp(125), dp(88), dp(88), dp(96), dp(105), dp(96)};
        for (int i = 0; i < values.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(values[i]);
            tv.setSingleLine(false);
            tv.setTextColor(header ? text : (i == 1 && segment != null ? AppUtil.parseColor(segment.color, accent) : text));
            tv.setTypeface(Typeface.DEFAULT, header ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextSize(header ? 12 : 11);
            tv.setPadding(dp(7), dp(5), dp(7), dp(5));
            tv.setGravity(i < 2 ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            row.addView(tv, new LinearLayout.LayoutParams(widths[i], -2));
        }
        if (segment != null) {
            row.setOnClickListener(v -> {
                if (segment.id > 0) {
                    if (selectedIds.contains(segment.id)) selectedIds.remove(segment.id); else selectedIds.add(segment.id);
                    fillTable();
                }
            });
            row.setOnLongClickListener(v -> { showSegmentDialog(segment); return true; });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    private void fillTopApps() {
        topAppsContainer.removeAllViews();
        Map<String, Long> totals = new HashMap<>();
        long total = 0;
        for (ActivitySegment r : rows) {
            String app = value(r.appName, "Unknown");
            totals.put(app, totals.containsKey(app) ? totals.get(app) + r.durationSeconds : r.durationSeconds);
            total += r.durationSeconds;
        }
        if (totals.isEmpty()) {
            topAppsContainer.addView(mutedText("No activity yet"));
            return;
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(totals.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        int i = 1;
        for (Map.Entry<String, Long> e : entries) {
            topAppsContainer.addView(topAppRow(i++, e.getKey(), e.getValue(), total));
        }
    }

    private View topAppRow(int idx, String app, long seconds, long total) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        rounded(box, card, border, 8);
        TextView rank = label(String.valueOf(idx));
        rank.setGravity(Gravity.CENTER);
        rounded(rank, dark ? Color.parseColor("#263035") : Color.parseColor("#E2E8F0"), Color.TRANSPARENT, 10);
        TextView dot = label("■");
        dot.setTextColor(AppUtil.parseColor(AppUtil.colorFor(app), accent));
        TextView name = label(app);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        int pct = total == 0 ? 0 : (int) ((seconds * 100L) / total);
        bar.setMax(100);
        bar.setProgress(pct);
        TextView p = label(pct + "%");
        TextView dur = label(TimeUtils.fmtHms(seconds));
        box.addView(rank, new LinearLayout.LayoutParams(dp(28), dp(28)));
        box.addView(dot, new LinearLayout.LayoutParams(dp(24), -2));
        box.addView(name, new LinearLayout.LayoutParams(dp(110), -2));
        box.addView(bar, new LinearLayout.LayoutParams(0, dp(22), 1));
        box.addView(p, new LinearLayout.LayoutParams(dp(44), -2));
        box.addView(dur, new LinearLayout.LayoutParams(dp(76), -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        box.setLayoutParams(lp);
        return box;
    }

    private void updateSummary() {
        if (rows.isEmpty()) {
            startLabel.setText("Start --"); endLabel.setText("End --"); trackedLabel.setText("Tracked 0:00"); durationLabel.setText("Duration 0:00"); goodLabel.setText("✓ 0:00"); badLabel.setText("✕ 0:00");
            return;
        }
        long start = Long.MAX_VALUE, end = 0, active = 0;
        for (ActivitySegment r : rows) {
            start = Math.min(start, r.startMs);
            end = Math.max(end, r.endMs);
            active += r.durationSeconds;
        }
        long span = Math.max(0, (end - start) / 1000L);
        long away = Math.max(0, span - active);
        startLabel.setText("Start " + TimeUtils.hhmm(start));
        endLabel.setText("End " + TimeUtils.hhmm(end));
        trackedLabel.setText("Tracked " + TimeUtils.fmtHms(active));
        durationLabel.setText("Duration " + TimeUtils.fmtHms(span));
        goodLabel.setText("✓ " + TimeUtils.fmtHms(active));
        badLabel.setText("✕ " + TimeUtils.fmtHms(away));
    }

    private void updateMonthTotal() {
        long monthStart = JalaliUtils.monthStartMillis(currentDayMs);
        long total = db.totalBetweenDays(monthStart, currentDayMs);
        monthLabel.setText("Month " + JalaliUtils.monthLabel(currentDayMs) + ": " + TimeUtils.fmtHms(total));
    }

    private void openDateDialog() {
        final EditText input = editText("1405/03/07");
        input.setText(JalaliUtils.dateString(currentDayMs));
        new AlertDialog.Builder(this)
                .setTitle("انتخاب تاریخ شمسی")
                .setView(input)
                .setPositiveButton("نمایش", (d, w) -> {
                    try { currentDayMs = JalaliUtils.parseDateToMillis(input.getText().toString()); refreshData(); }
                    catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void openSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), 0);
        final EditText idle = editText("10");
        idle.setInputType(InputType.TYPE_CLASS_NUMBER);
        long minutes = Long.parseLong(db.getSetting("idle_limit_seconds", String.valueOf(TimeUtils.DEFAULT_IDLE_LIMIT_MS / 1000))) / 60L;
        idle.setText(String.valueOf(minutes));
        final String[] options = {"true", "false"};
        final Spinner auto = new Spinner(this);
        auto.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"ثبت خودکار روشن", "ثبت خودکار خاموش"}));
        auto.setSelection("true".equals(db.getSetting("auto_track", "true")) ? 0 : 1);
        box.addView(label("بعد از چند دقیقه بیکاری/خاموشی صفحه ثبت متوقف شود؟"));
        box.addView(idle, new LinearLayout.LayoutParams(-1, dp(42)));
        box.addView(auto, new LinearLayout.LayoutParams(-1, dp(42)));
        new AlertDialog.Builder(this)
                .setTitle("تنظیمات")
                .setView(box)
                .setPositiveButton("ذخیره", (d, w) -> {
                    try {
                        long m = Math.max(1, Math.min(180, Long.parseLong(idle.getText().toString())));
                        db.setSetting("idle_limit_seconds", String.valueOf(m * 60L));
                        db.setSetting("auto_track", options[auto.getSelectedItemPosition()]);
                        startTrackerService();
                        Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(this, "عدد معتبر وارد کن.", Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void addTagToSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "اول یک یا چند ردیف را انتخاب کن.", Toast.LENGTH_LONG).show();
            return;
        }
        final EditText tagInput = editText("نام پروژه / تگ");
        new AlertDialog.Builder(this)
                .setTitle("Add tag")
                .setView(tagInput)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String tag = tagInput.getText().toString().trim();
                    if (!tag.isEmpty()) {
                        db.updateTag(new ArrayList<>(selectedIds), tag);
                        selectedIds.clear();
                        refreshData();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showSegmentDialog(ActivitySegment r) {
        String msg = "App: " + value(r.appName, "Unknown") + "\n" +
                "Package: " + value(r.packageName, "") + "\n" +
                "Category: " + value(r.category, "Other") + "\n" +
                "Start: " + TimeUtils.hhmmss(r.startMs) + "\n" +
                "End: " + TimeUtils.hhmmss(r.endMs) + "\n" +
                "Duration: " + TimeUtils.fmtHms(r.durationSeconds) + "\n" +
                "Tag: " + value(r.tag, "-") + "\n\n" +
                "در اندروید عنوان پنجره و اسکرین‌شات مخفی توسط سیستم محدود شده است؛ نام برنامه از Usage Access ثبت می‌شود.";
        new AlertDialog.Builder(this).setTitle("Activity details").setMessage(msg).setPositiveButton("بستن", null).show();
    }

    private void setCurrentMonthReport() {
        long today = TimeUtils.dayStart(System.currentTimeMillis());
        fromInput.setText(JalaliUtils.dateString(JalaliUtils.monthStartMillis(today)));
        toInput.setText(JalaliUtils.dateString(today));
    }

    private void generateReport() {
        try {
            reportFromMs = JalaliUtils.parseDateToMillis(fromInput.getText().toString());
            reportToMs = JalaliUtils.parseDateToMillis(toInput.getText().toString());
            if (reportToMs < reportFromMs) throw new IllegalArgumentException("تاریخ پایان نباید قبل از تاریخ شروع باشد.");
            currentReport = db.buildRangeReport(db.queryBetweenDays(reportFromMs, reportToMs));
            totalCardValue.setText(TimeUtils.fmtHms(currentReport.totalSeconds));
            periodCardValue.setText(JalaliUtils.dateString(reportFromMs) + " تا " + JalaliUtils.dateString(reportToMs));
            systemCardValue.setText(Build.MODEL == null ? "Android" : Build.MODEL);
            fillReportLists();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void fillReportLists() {
        reportTopContainer.removeAllViews();
        reportTopContainer.addView(tableRow(new String[]{"ردیف", "فعالیت / برنامه", "دسته", "مدت", "درصد", "", ""}, true, null));
        int i = 1;
        for (ReportData.TopItem t : currentReport.topActivities) {
            reportTopContainer.addView(tableRow(new String[]{String.valueOf(i++), t.app, t.category, TimeUtils.fmtHms(t.seconds), t.percent + "%", "", ""}, false, null));
        }
        if (currentReport.topActivities.isEmpty()) reportTopContainer.addView(mutedText("فعالیتی ثبت نشده است."));

        reportDailyContainer.removeAllViews();
        reportDailyContainer.addView(tableRow(new String[]{"ردیف", "تاریخ شمسی", "روز", "شروع", "پایان", "جمع ساعت", "فعالیت برتر"}, true, null));
        i = 1;
        for (ReportData.DailyItem d : currentReport.dailyRows) {
            reportDailyContainer.addView(tableRow(new String[]{String.valueOf(i++), d.jalaliDate, d.weekday, d.start, d.end, TimeUtils.fmtHms(d.total), d.topApp}, false, null));
        }
        if (currentReport.dailyRows.isEmpty()) reportDailyContainer.addView(mutedText("اطلاعاتی وجود ندارد."));
    }

    private void exportPdf() {
        generateReport();
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            String name = "timesheet_" + JalaliUtils.dateString(reportFromMs).replace('/', '-') + "_to_" + JalaliUtils.dateString(reportToMs).replace('/', '-') + ".pdf";
            File file = new File(dir, name);
            writeReportPdf(file, currentReport);
            Toast.makeText(this, "PDF ساخته شد:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "خطا در ساخت PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeReportPdf(File file, ReportData report) throws Exception {
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        int pageW = 842, pageH = 595;
        int pageNum = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create());
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        p.setColor(Color.parseColor("#0F172A"));
        p.setTextSize(18); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("گزارش ساعت کاری", pageW - 36, 42, p);
        p.setTextSize(10); p.setFakeBoldText(false); p.setColor(Color.parseColor("#475569"));
        c.drawText("بازه: " + JalaliUtils.dateString(reportFromMs) + " تا " + JalaliUtils.dateString(reportToMs), pageW - 36, 62, p);
        c.drawText("جمع ساعت: " + TimeUtils.fmtHmFa(report.totalSeconds) + " - " + TimeUtils.fmtHms(report.totalSeconds), pageW - 36, 80, p);
        int y = 116;
        p.setColor(Color.parseColor("#0F172A")); p.setTextSize(13); p.setFakeBoldText(true);
        c.drawText("۵ فعالیت / برنامه با بیشترین زمان", pageW - 36, y, p);
        y += 22;
        p.setTextSize(10); p.setFakeBoldText(false);
        int idx = 1;
        for (ReportData.TopItem t : report.topActivities) {
            c.drawText(idx++ + ". " + t.app + " | " + t.category + " | " + TimeUtils.fmtHms(t.seconds) + " | " + t.percent + "%", pageW - 36, y, p);
            y += 18;
        }
        if (report.topActivities.isEmpty()) { c.drawText("فعالیتی ثبت نشده است.", pageW - 36, y, p); y += 18; }
        y += 16;
        p.setTextSize(13); p.setFakeBoldText(true);
        c.drawText("جدول گزارش روزانه / ماهانه", pageW - 36, y, p);
        y += 22;
        p.setTextSize(9); p.setFakeBoldText(false);
        idx = 1;
        for (ReportData.DailyItem d : report.dailyRows) {
            if (y > pageH - 40) {
                pdf.finishPage(page);
                page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNum).create());
                c = page.getCanvas(); c.drawColor(Color.WHITE); y = 40;
            }
            c.drawText(idx++ + ". " + d.jalaliDate + " | " + d.weekday + " | " + d.start + " تا " + d.end + " | " + TimeUtils.fmtHms(d.total) + " | " + d.topApp, pageW - 36, y, p);
            y += 16;
        }
        if (report.dailyRows.isEmpty()) c.drawText("اطلاعاتی وجود ندارد.", pageW - 36, y, p);
        pdf.finishPage(page);
        try (FileOutputStream out = new FileOutputStream(file)) { pdf.writeTo(out); }
        pdf.close();
    }

    private void showUsageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("فعال‌سازی Usage Access")
                .setMessage("برای اینکه اندروید اجازه بدهد زمان استفاده از برنامه‌ها ثبت شود، در صفحه بعد WorkTimeTracker Pro را فعال کنید.")
                .setPositiveButton("باز کردن تنظیمات", (d, w) -> UsageTrackerService.openUsageAccessSettings(this))
                .setNegativeButton("بعداً", null)
                .show();
    }

    private Button navButton(String textValue) {
        Button b = new Button(this);
        b.setText(textValue);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(128), dp(38));
        lp.setMargins(0, 0, dp(6), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String textValue) {
        Button b = new Button(this);
        b.setText(textValue);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(dp(8), 0, dp(8), 0);
        styleButton(b, border, panel, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button orangeButton(String textValue) {
        Button b = smallButton(textValue);
        styleButton(b, orange, orange, Color.WHITE);
        return b;
    }

    private void styleButton(Button b, int stroke, int fill, int textColor) {
        b.setTextColor(textColor);
        rounded(b, fill, stroke, 8);
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(text);
        v.setTextSize(12);
        v.setPadding(dp(6), dp(4), dp(6), dp(4));
        return v;
    }

    private TextView mutedText(String s) {
        TextView v = label(s);
        v.setTextColor(muted);
        return v;
    }

    private TextView pageTitle(String s) {
        TextView v = label(s);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = label(s);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView pill(String s, int fg, int fill) {
        TextView v = label(s);
        v.setTextColor(fg);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        rounded(v, fill, Color.TRANSPARENT, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(dp(4), 0, dp(4), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private EditText editText(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setSingleLine(true);
        e.setTextSize(12);
        e.setPadding(dp(10), 0, dp(10), 0);
        rounded(e, dark ? Color.parseColor("#101517") : Color.WHITE, border, 8);
        return e;
    }

    private LinearLayout cardLayout() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        rounded(l, panel, border, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -1);
        lp.setMargins(0, 0, dp(8), dp(8));
        l.setLayoutParams(lp);
        return l;
    }

    private SummaryCard summaryCard(String title, String value, int color) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(12), dp(9), dp(12), dp(9));
        rounded(frame, card, border, 12);
        TextView t = mutedText(title);
        TextView v = label(value);
        v.setTextColor(color);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        frame.addView(t);
        frame.addView(v);
        SummaryCard sc = new SummaryCard();
        sc.frame = frame;
        sc.value = v;
        return sc;
    }

    private static class SummaryCard {
        View frame;
        TextView value;
    }

    private void rounded(View v, int fill, int stroke, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) gd.setStroke(dp(1), stroke);
        v.setBackground(gd);
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String value(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    private void Space(LinearLayout layout, float weight) {
        View v = new View(this);
        layout.addView(v, new LinearLayout.LayoutParams(0, 1, weight));
    }

    public static class TimelineView extends View {
        private final List<ActivitySegment> data = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long day;
        private boolean dark;
        public TimelineView(Context context) { super(context); setMinimumHeight(220); }
        void setData(long dayMs, List<ActivitySegment> rows, boolean isDark) {
            day = TimeUtils.dayStart(dayMs);
            dark = isDark;
            data.clear();
            data.addAll(rows);
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int bg = Color.parseColor(dark ? "#101517" : "#EEF2F7");
            int panel = Color.parseColor(dark ? "#151B1E" : "#FFFFFF");
            int grid = Color.parseColor(dark ? "#2B3438" : "#D7DEE8");
            int text = Color.parseColor(dark ? "#DCE3E5" : "#0F172A");
            c.drawColor(bg);
            int left = dpLocal(72), right = getWidth() - dpLocal(12), top = dpLocal(20), bottom = getHeight() - dpLocal(28);
            p.setColor(panel); c.drawRoundRect(new RectF(dpLocal(6), dpLocal(8), getWidth() - dpLocal(6), getHeight() - dpLocal(8)), dpLocal(10), dpLocal(10), p);
            p.setColor(grid); p.setStrokeWidth(1);
            for (int h = 0; h <= 24; h += 2) {
                float x = left + (right - left) * (h / 24f);
                c.drawLine(x, top, x, bottom, p);
                p.setColor(text); p.setTextSize(dpLocal(10)); p.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.format(Locale.US, "%02d", h), x, bottom + dpLocal(16), p);
                p.setColor(grid);
            }
            p.setColor(text); p.setTextSize(dpLocal(12)); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.LEFT);
            c.drawText("Timeline", dpLocal(18), dpLocal(26), p);
            p.setFakeBoldText(false);
            long dayEnd = TimeUtils.nextDayStart(day);
            int laneTop = top + dpLocal(20);
            int laneH = Math.max(dpLocal(24), (bottom - laneTop) / Math.max(1, Math.min(5, data.size())));
            int idx = 0;
            for (ActivitySegment r : data) {
                if (idx >= 5) break;
                float x1 = left + (right - left) * ((Math.max(r.startMs, day) - day) / (float) (dayEnd - day));
                float x2 = left + (right - left) * ((Math.min(r.endMs, dayEnd) - day) / (float) (dayEnd - day));
                if (x2 < x1 + dpLocal(3)) x2 = x1 + dpLocal(3);
                int color = AppUtil.parseColor(r.color, Color.parseColor("#00C896"));
                p.setColor(color);
                RectF rect = new RectF(x1, laneTop + idx * laneH, x2, laneTop + idx * laneH + laneH - dpLocal(6));
                c.drawRoundRect(rect, dpLocal(6), dpLocal(6), p);
                p.setColor(text); p.setTextSize(dpLocal(10)); p.setTextAlign(Paint.Align.LEFT);
                c.drawText(r.appName == null ? "Unknown" : r.appName, dpLocal(14), rect.centerY() + dpLocal(4), p);
                idx++;
            }
            if (data.isEmpty()) {
                p.setColor(Color.parseColor(dark ? "#8D9AA0" : "#64748B")); p.setTextSize(dpLocal(12)); p.setTextAlign(Paint.Align.CENTER);
                c.drawText("No activity yet", getWidth() / 2f, getHeight() / 2f, p);
            }
        }
        private int dpLocal(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    }
}
