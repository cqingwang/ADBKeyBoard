package com.sogou.edge;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import org.apache.commons.io.http.HttpServer;
import org.apache.commons.io.http.bean.Headers;
import org.apache.commons.io.http.bean.HttpAnswer;
import org.apache.commons.io.offical.http_sun.HttpExchange;
import org.apache.commons.io.utils.Callback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Keyboard extends InputMethodService {
    private String IME_MESSAGE = "EDGE_INPUT_TEXT";
    private String IME_CHARS = "EDGE_INPUT_CHARS";
    private String IME_KEYCODE = "EDGE_INPUT_CODE";
    private String IME_META_KEYCODE = "EDGE_INPUT_MCODE";
    private String IME_EDITORCODE = "EDGE_EDITOR_CODE";
    private String IME_MESSAGE_B64 = "EDGE_INPUT_B64";
    private String IME_CLEAR_TEXT = "EDGE_CLEAR_TEXT";
    private BroadcastReceiver mReceiver = null;
    private boolean httpServiced = false;
    private View inputView;

    @Override
    public View onCreateInputView() {
        inputView = getLayoutInflater().inflate(R.layout.view, null);

        if (mReceiver == null) {
            IntentFilter filter = new IntentFilter(IME_MESSAGE);
            filter.addAction(IME_CHARS);
            filter.addAction(IME_KEYCODE);
            filter.addAction(IME_MESSAGE); // IME_META_KEYCODE // Change IME_MESSAGE to get more values.
            filter.addAction(IME_EDITORCODE);
            filter.addAction(IME_MESSAGE_B64);
            filter.addAction(IME_CLEAR_TEXT);
            mReceiver = new KeyboardReceiver();
            registerReceiver(mReceiver, filter);
        }
        if (!httpServiced) listen(8000);
        return inputView;
    }

    private void listen(int port) {
        Map<String, Callback<HttpExchange>> handlers = new ConcurrentHashMap<>();
        HttpServer.register(handlers, "api/input/text", ex -> {
            Map<String, Object> params = HttpServer.dicted(ex, new String[]{"text"});
            HttpServer.anything(ex, params, (HttpServer.Call<HttpAnswer>) headers -> {
                String text = params.get("text") + "";
                return new HttpAnswer(200, new Headers(), "ok");
            });
            return true;
        });

        HttpServer.serve(handlers, port, 2);
        httpServiced = true;
    }

    public void onDestroy() {
        if (mReceiver != null)
            unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private boolean inputText(String text) {
        if (text == null) return false;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            return true;
        }
        return false;
    }

    class KeyboardReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(IME_MESSAGE)) {
                show();
                String msg = intent.getStringExtra("msg");
                Log.e(IME_MESSAGE, msg + "");
                inputText(msg);
            }

            if (intent.getAction().equals(IME_MESSAGE_B64)) {
                show();
                String data = intent.getStringExtra("msg");

                byte[] b64 = Base64.decode(data, Base64.DEFAULT);
                String msg = "NOT SUPPORTED";
                try {
                    msg = new String(b64, "UTF-8");
                } catch (Exception e) {

                }
                inputText(msg);
            }

            if (intent.getAction().equals(IME_CHARS)) {
                show();
                int[] chars = intent.getIntArrayExtra("chars");
                if (chars != null) {
                    String msg = new String(chars, 0, chars.length);
                    inputText(msg);
                }
            }

            if (intent.getAction().equals(IME_KEYCODE)) {
                int code = intent.getIntExtra("code", -1);
                Log.e(IME_KEYCODE, code + "");
                if (code != -1) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                    if (code == 66) dismiss();
                }
            }

            if (intent.getAction().equals(IME_MESSAGE)) {
                show();
                String msg = intent.getStringExtra("mcode"); // Get message.
                if (msg != null) {
                    String[] mcodes = msg.split(","); // Get mcodes in string.
                    int i;
                    InputConnection ic = getCurrentInputConnection();
                    for (i = 0; i < mcodes.length - 1; i = i + 2) {
                        if (ic != null) {
                            KeyEvent ke;
                            if (mcodes[i].contains("+")) { // Check metaState if more than one. Use '+' as delimiter
                                String[] arrCode = mcodes[i].split("\\+"); // Get metaState if more than one.
                                ke = new KeyEvent(
                                        0,
                                        0,
                                        KeyEvent.ACTION_DOWN, // Action code.
                                        Integer.parseInt(mcodes[i + 1].toString()), // Key code.
                                        0, // Repeat. // -1
                                        Integer.parseInt(arrCode[0].toString()) | Integer.parseInt(arrCode[1].toString()), // Flag
                                        0, // The device ID that generated the key event.
                                        0, // Raw device scan code of the event.
                                        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
                                        InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
                                );
                            } else { // Only one metaState.
                                ke = new KeyEvent(
                                        0,
                                        0,
                                        KeyEvent.ACTION_DOWN, // Action code.
                                        Integer.parseInt(mcodes[i + 1].toString()), // Key code.
                                        0, // Repeat.
                                        Integer.parseInt(mcodes[i].toString()), // Flag
                                        0, // The device ID that generated the key event.
                                        0, // Raw device scan code of the event.
                                        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
                                        InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
                                );
                            }
                            ic.sendKeyEvent(ke);
                        }
                    }
                }
            }

            if (intent.getAction().equals(IME_EDITORCODE)) {
                int code = intent.getIntExtra("code", -1);
                if (code != -1) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                        ic.performEditorAction(code);
                }
            }

            if (intent.getAction().equals(IME_CLEAR_TEXT)) {
                show();
                Log.e(IME_CLEAR_TEXT, "true");
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    //REF: stackoverflow/33082004 author: Maxime Epain
                    CharSequence curPos = ic.getExtractedText(new ExtractedTextRequest(), 0).text;
                    CharSequence beforePos = ic.getTextBeforeCursor(curPos.length(), 0);
                    CharSequence afterPos = ic.getTextAfterCursor(curPos.length(), 0);
                    ic.deleteSurroundingText(beforePos.length(), afterPos.length());
                }
            }
        }
    }

    public void show() {
//        showWindow(true);
        Log.e("EDGE", "show");
//        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

//        imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY); // show
    }

    public void dismiss() {
        Log.e("EDGE", "dismiss");

//        hideWindow();
//        requestHideSelf(0);
//
//        inputView.clearFocus();

//        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);


    }
}
