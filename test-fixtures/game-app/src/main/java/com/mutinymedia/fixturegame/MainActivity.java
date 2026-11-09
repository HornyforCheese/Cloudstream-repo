package com.mutinymedia.fixturegame;
public class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Fixture game launched");
        text.setGravity(android.view.Gravity.CENTER);
        setContentView(text);
    }
}
