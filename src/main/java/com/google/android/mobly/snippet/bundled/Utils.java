/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.mobly.snippet.bundled;

public final class Utils {
    private Utils() {}

    /**
     * Waits for asynchronous operations to finish.
     *
     * @param predicate A lambda function that specifies the condition for waiting. This function
     *     should return true when the aysnc operation to wait for is not finished.
     * @param timeout The number of seconds to wait for before giving up.
     * @return true if the operation finished before timeout, false otherwise.
     * @throws InterruptedException
     */
    public static boolean waitAndCheck(Utils.Predicate predicate, int timeout)
            throws InterruptedException {
        while (predicate.waitCondition() && timeout >= 0) {
            Thread.sleep(1000);
            timeout -= 1;
        }
        if (predicate.waitCondition()) {
            return false;
        }
        return true;
    }

    /**
     * A function interface that is used by lambda functions signaling an async operation is still
     * going on.
     */
    public interface Predicate {
        boolean waitCondition();
    }
}
