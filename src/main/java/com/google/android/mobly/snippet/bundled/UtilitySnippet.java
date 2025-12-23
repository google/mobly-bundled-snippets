package com.google.android.mobly.snippet.bundled;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

/**
 * Snippet class exposing utility methods to Mobly Python scripts.
 */
public class UtilitySnippet implements Snippet {

    private final Context mContext;

    public UtilitySnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Rpc(description = "Drops the shell permission. This is no-op if shell"
            + " permission identity is not adopted.")
    public void utilityDropShellPermission() {
        Utils.dropShellPermissionIdentity();
    }

    @Rpc(description = "Adopts shell permission, with each invocation"
            + " overwriting preceding adoptions. If no permissions are"
            + " specified, all permissions will be granted.")
    public void utilityAdoptShellPermission(@RpcOptional String[] permissions) throws Throwable {
        if (permissions == null) {
            Utils.adaptShellPermissionIfRequired(mContext);
        } else {
            Utils.adaptShellPermissionIfRequired(mContext, permissions);
        }
    }

    @Override
    public void shutdown() {
        Utils.dropShellPermissionIdentity();
    }
}
