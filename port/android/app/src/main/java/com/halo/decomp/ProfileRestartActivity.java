package com.halo.decomp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/** Restarts only the game process, after its graphics files have been saved. */
public final class ProfileRestartActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int gamePid = getIntent().getIntExtra("game-pid", -1);
        if (gamePid > 0 && gamePid != Process.myPid())
            Process.killProcess(gamePid);
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
    }
}
