package com.omarchytv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * "> ask": a terminal-style voice assistant pane. Speech is recognised on the TV (system recognizer,
 * Italian/English); the text goes to the paired Omarchy Linux PC, which calls the chosen AI provider and
 * sends the answer back. The TV holds no API keys and calls no AI service itself. This never replaces
 * Google Assistant: it only opens from the logo menu or a colour key the user picked.
 *
 * Transport: Omarchy Link (omarchy-link/docs/PROTOCOL.md §6 "Assistant"). The TV is the server, so the
 * question goes out as an "ask" event on the PC's event socket and the answer streams back as "answer"
 * events (typewriter), ending with done:true (+ "final" for TTS), or one "answer_error".
 */
public class AskActivity extends Activity implements OmarchyLink.Listener {

    /** The PC gives up after 90 s (answer_error "timeout"); the TV waits a little longer. */
    private static final long ANSWER_TIMEOUT_MS = 100_000L;
    private String pendingId, session;
    /** Streamed text not yet typed, and where the current reply starts in the transcript. */
    private final StringBuilder queue = new StringBuilder();
    private int replyStart = -1;
    private String finalText;
    private boolean typing;
    private final Runnable timeout = () -> {
        if (pendingId != null) {
            pendingId = null;
            line(getString(R.string.ask_sys), "timeout", Theme.m(0xFFF7768E));
        }
    };

    private static final int REQ_SPEECH = 21;
    private TextView log, hint, header;
    private ScrollView scroll;
    private final SpannableStringBuilder transcript = new SpannableStringBuilder();
    private TextToSpeech tts;
    private boolean ttsReady;

    @Override
    protected void onStart() {
        super.onStart();
        OmarchyLink.addListener(this);
    }

    @Override
    protected void onStop() {
        OmarchyLink.removeListener(this);
        super.onStop();
    }

    @Override
    public void onLinkEvent(String type, JSONObject d) {
        if (pendingId == null || !pendingId.equals(d.optString("id"))) return;
        if ("answer_error".equals(type)) {
            pendingId = null;
            log.removeCallbacks(timeout);
            String msg = d.optString("message", "");
            line(getString(R.string.ask_sys), msg.isEmpty() ? d.optString("error", "error") : msg, Theme.m(0xFFF7768E));
            return;
        }
        if (!"answer".equals(type)) return;
        if (replyStart < 0) startReply();
        // Plain text only, never HTML (§6); appended typewriter style.
        enqueue(d.optString("text", ""));
        if (d.optBoolean("done")) {
            pendingId = null;
            log.removeCallbacks(timeout);
            if (d.has("session") && !d.isNull("session")) session = d.optString("session");
            finalText = d.isNull("final") ? null : d.optString("final", null);
            if (!typing) endReply();
        } else {
            log.removeCallbacks(timeout);
            log.postDelayed(timeout, ANSWER_TIMEOUT_MS);
        }
    }

    private void startReply() {
        LauncherConfig cfg = LauncherConfig.get(this);
        if (transcript.length() > 0) transcript.append("\n\n");
        int s = transcript.length();
        transcript.append(providerName(cfg).toLowerCase(Locale.ROOT)).append("> ");
        transcript.setSpan(new ForegroundColorSpan(Theme.m(0xFF7DCFFF)), s, transcript.length(), 0);
        replyStart = transcript.length();
        log.setText(transcript);
    }

    private void enqueue(String text) {
        if (text.isEmpty()) return;
        queue.append(text);
        if (!typing) {
            typing = true;
            log.post(typer);
        }
    }

    private final Runnable typer = new Runnable() {
        @Override
        public void run() {
            int n = Math.min(queue.length(), 3);
            transcript.append(queue, 0, n);
            queue.delete(0, n);
            log.setText(transcript);
            scroll.fullScroll(View.FOCUS_DOWN);
            if (queue.length() > 0) log.postDelayed(this, 16);
            else {
                typing = false;
                if (pendingId == null && replyStart >= 0) endReply();
            }
        }
    };

    /** End of a reply: use the cleaned "final" if it differs, and speak it. */
    private void endReply() {
        String shown = transcript.subSequence(replyStart, transcript.length()).toString();
        String text = finalText != null && !finalText.isEmpty() ? finalText : shown;
        if (!text.equals(shown)) {
            transcript.replace(replyStart, transcript.length(), text);
            log.setText(transcript);
        }
        replyStart = -1;
        finalText = null;
        if (ttsReady && !text.isEmpty()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "otv-ask");
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherConfig.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fonts.install(this);
        setContentView(R.layout.activity_ask);
        Fonts.apply(getWindow().getDecorView());
        Theme.apply(getWindow().getDecorView());
        log = findViewById(R.id.askLog);
        hint = findViewById(R.id.askHint);
        header = findViewById(R.id.askHeader);
        scroll = findViewById(R.id.askScroll);
        LauncherConfig cfg = LauncherConfig.get(this);
        header.setTextColor(cfg.accent);
        TextFx.type(header, "> ask  ·  " + providerName(cfg), 260);
        hint.setText(R.string.ask_hint);
        // A new short-term memory per visit (PROTOCOL.md: "and tomorrow?" works within one visit).
        session = "tv-" + System.currentTimeMillis() / 1000;
        if (!linked()) line(getString(R.string.ask_sys), getString(R.string.ask_need_link), Theme.m(0xFFFF9E64));
        else if (!OmarchyLink.connected()) line(getString(R.string.ask_sys), getString(R.string.ask_not_connected), Theme.m(0xFFFF9E64));
        if (cfg.assistantTts) {
            tts = new TextToSpeech(getApplicationContext(), status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) tts.setLanguage(Locale.forLanguageTag(MainActivity.speechLanguage(this)));
            });
        }
    }

    static String providerName(LauncherConfig cfg) {
        for (LauncherConfig.Provider p : cfg.aiProviders) if (p.id.equals(cfg.assistantProvider)) return p.name;
        return cfg.aiProviders.isEmpty() ? "—" : cfg.aiProviders.get(0).name;
    }

    /** A PC is paired (omarchytv.json link.pcs[]). */
    private boolean linked() {
        JSONObject link = LauncherConfig.readJson(this).optJSONObject("link");
        JSONArray pcs = link != null ? link.optJSONArray("pcs") : null;
        return pcs != null && pcs.length() > 0;
    }

    private void line(String who, String text, int color) {
        if (transcript.length() > 0) transcript.append("\n\n");
        int s = transcript.length();
        transcript.append(who).append("> ");
        transcript.setSpan(new ForegroundColorSpan(color), s, transcript.length(), 0);
        transcript.append(text);
        log.setText(transcript);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, MainActivity.speechLanguage(this))
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.ask_prompt));
        try {
            startActivityForResult(i, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            typed();
        }
    }

    private void typed() {
        android.widget.EditText q = new android.widget.EditText(this);
        q.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("> ask").setView(q)
                .setPositiveButton(android.R.string.ok, (d, w) -> ask(q.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        List<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) ask(r.get(0));
    }

    private void ask(String text) {
        if (text.isEmpty()) return;
        line(getString(R.string.ask_you), text, Theme.m(0xFF9ECE6A));
        if (!linked()) {
            line(getString(R.string.ask_sys), getString(R.string.ask_need_link), Theme.m(0xFFFF9E64));
            return;
        }
        if (pendingId != null) {
            line(getString(R.string.ask_sys), getString(R.string.ask_busy), Theme.m(0xFFFF9E64));
            return;
        }
        LauncherConfig cfg = LauncherConfig.get(this);
        String id = Long.toHexString(new java.security.SecureRandom().nextLong());
        String err;
        try {
            JSONObject d = new JSONObject().put("id", id).put("text", text.length() > 2000 ? text.substring(0, 2000) : text).put("lang", MainActivity.speechLanguage(this));
            // Provider ids come from the PC (link.aiProviders); nothing is hard-coded here.
            String prov = cfg.assistantProvider;
            if (!prov.isEmpty()) {
                boolean known = false;
                for (LauncherConfig.Provider p : cfg.aiProviders) known |= p.id.equals(prov);
                if (known) d.put("provider", prov);
            }
            if (session != null) d.put("session", session);
            err = OmarchyLink.ask(d);
        } catch (org.json.JSONException e) {
            err = "bad_request";
        }
        if (err != null) {
            line(getString(R.string.ask_sys), getString("busy".equals(err) ? R.string.ask_busy : R.string.ask_not_connected), Theme.m(0xFFFF9E64));
            return;
        }
        pendingId = id;
        replyStart = -1;
        log.postDelayed(timeout, ANSWER_TIMEOUT_MS);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            listen();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MENU) {
            typed();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
