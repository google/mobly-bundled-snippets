package com.google.android.mobly.snippet.bundled;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

/**
 * Snippet class exposing utility methods to Mobly Python scripts.
 */
public class UtilitySnippet implements Snippet {

    @Rpc(description = "Drops the shell permission identity.")
    public void utilityDropShellPermission() {
        Log.d("MY_DEBUG_TAG: utilityDropShellPermission is invoked successfully!");
        Utils.dropShellPermissionIdentity();
    }

    @Rpc(description = "Adopts shell permission identity (all permissions).")
    public void utilityAdoptShellPermission(@RpcOptional String[] permissions) {
        if (permissions == null || permissions.length == 0) {
            Utils.adoptShellPermissionIdentity();
        } else {
            Utils.adoptShellPermissionIdentity(permissions);
        }
    }

    @Override
    public void shutdown() {
        Utils.dropShellPermissionIdentity();
    }
}
